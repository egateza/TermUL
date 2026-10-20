package dev.egateza.termul.core.highlight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Keyword sebuah profil yang sudah dikompilasi. Immutable dan thread-safe: satu instance dipakai bersama oleh semua tab
 * yang memakai profil itu. Keyword di atas menang: karakter yang sudah diwarnai keyword sebelumnya tidak diwarnai lagi.
 *
 * <p>Regex dari user bisa lambat (catastrophic backtracking). Setiap baris punya batas waktu; keyword yang melewatinya
 * dilewati untuk baris itu, dan baris yang terlalu panjang hanya dicocokkan sampai {@link #MAX_LINE}.
 */
public final class HighlightMatcher {

    /** Panjang maksimum teks satu baris (logis) yang dicocokkan. */
    public static final int MAX_LINE = 8192;

    /** Batas waktu pencocokan satu baris untuk semua keyword. */
    static final long LINE_BUDGET_NANOS = 25_000_000L;

    /** Bagian teks yang diwarnai keyword ke-{@code keyword} (indeks di daftar keyword profil). */
    public record Span(int start, int end, int keyword) {
    }

    private record Rule(int keyword, Pattern pattern, int groupNumber, String groupName) {
    }

    private final HighlightProfile profile;
    private final List<Rule> rules;

    private HighlightMatcher(HighlightProfile profile, List<Rule> rules) {
        this.profile = profile;
        this.rules = rules;
    }

    /** Kompilasi keyword yang aktif; keyword yang tidak valid dilewati (dialog sudah menolaknya). */
    public static HighlightMatcher compile(HighlightProfile profile) {
        var rules = new ArrayList<Rule>();
        List<HighlightKeyword> keywords = profile.keywords();
        for (int i = 0; i < keywords.size(); i++) {
            HighlightKeyword k = keywords.get(i);
            if (k.usable() && validate(k).isEmpty()) {
                rules.add(rule(i, k));
            }
        }
        return new HighlightMatcher(profile, List.copyOf(rules));
    }

    public HighlightProfile profile() {
        return profile;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /** @return pesan error (regex atau grup tidak valid), atau kosong kalau keyword bisa dipakai */
    public static Optional<String> validate(HighlightKeyword k) {
        if (k.js() || k.text().isEmpty()) {
            return Optional.empty();
        }
        try {
            Rule r = rule(0, k);
            if (r.groupNumber() > r.pattern().matcher("").groupCount()) {
                return Optional.of("Grup " + r.groupNumber() + " tidak ada di regex");
            }
            if (r.groupName() != null && !r.pattern().namedGroups().containsKey(r.groupName())) {
                return Optional.of("Grup \"" + r.groupName() + "\" tidak ada di regex");
            }
            return Optional.empty();
        } catch (PatternSyntaxException e) {
            return Optional.of(e.getDescription() + (e.getIndex() >= 0 ? " (posisi " + e.getIndex() + ")" : ""));
        }
    }

    private static Rule rule(int index, HighlightKeyword k) {
        int flags = k.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Pattern p = Pattern.compile(k.regExp() ? k.text() : Pattern.quote(k.text()), flags);
        int number = 0;
        String name = null;
        if (k.regExp() && k.matchGroup() && k.matchGroupName() != null && !k.matchGroupName().isBlank()) {
            String g = k.matchGroupName().strip();
            if (g.chars().allMatch(Character::isDigit) && g.length() < 4) {
                number = Integer.parseInt(g);
            } else {
                name = g;
            }
        }
        return new Rule(index, p, number, name);
    }

    /** Bagian teks yang diwarnai, urut posisi dan tidak tumpang tindih. */
    public List<Span> match(String text) {
        return match(text, LINE_BUDGET_NANOS);
    }

    List<Span> match(String text, long budgetNanos) {
        if (rules.isEmpty() || text.isEmpty()) {
            return List.of();
        }
        String line = text.length() > MAX_LINE ? text.substring(0, MAX_LINE) : text;
        var input = new DeadlineChars(line, System.nanoTime() + budgetNanos);
        boolean[] taken = new boolean[line.length()];
        var spans = new ArrayList<Span>();
        for (Rule r : rules) {
            try {
                Matcher m = r.pattern().matcher(input);
                while (m.find()) {
                    int start = r.groupName() != null ? m.start(r.groupName()) : m.start(r.groupNumber());
                    int end = r.groupName() != null ? m.end(r.groupName()) : m.end(r.groupNumber());
                    if (start >= 0 && end > start) {
                        claim(spans, taken, start, end, r.keyword());
                    }
                }
            } catch (DeadlineChars.Expired e) {
                break; // keyword sisanya dilewati untuk baris ini
            }
        }
        spans.sort(Comparator.comparingInt(Span::start));
        return spans;
    }

    private static void claim(List<Span> spans, boolean[] taken, int start, int end, int keyword) {
        int i = start;
        while (i < end) {
            while (i < end && taken[i]) {
                i++;
            }
            int from = i;
            while (i < end && !taken[i]) {
                taken[i++] = true;
            }
            if (i > from) {
                spans.add(new Span(from, i, keyword));
            }
        }
    }

    /** Teks yang menghentikan regex setelah tenggat lewat (dicek setiap beberapa ratus akses). */
    private static final class DeadlineChars implements CharSequence {

        static final class Expired extends RuntimeException {
            Expired() {
                super(null, null, false, false);
            }
        }

        private final String text;
        private final long deadline;
        private int calls;

        DeadlineChars(String text, long deadline) {
            this.text = text;
            this.deadline = deadline;
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public char charAt(int index) {
            if ((++calls & 0x3FF) == 0 && System.nanoTime() > deadline) {
                throw new Expired();
            }
            return text.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return text.subSequence(start, end);
        }

        @Override
        public String toString() {
            return text;
        }
    }
}

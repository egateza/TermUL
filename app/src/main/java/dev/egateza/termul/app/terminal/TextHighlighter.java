package dev.egateza.termul.app.terminal;

import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.LinesStorage;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.TextBufferChangesListener;
import com.jediterm.terminal.util.CharUtils;
import dev.egateza.termul.core.highlight.HighlightColor;
import dev.egateza.termul.core.highlight.HighlightKeyword;
import dev.egateza.termul.core.highlight.HighlightMatcher;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Highlight teks di satu terminal: keyword profil highlight diwarnai langsung di style karakter buffer JediTerm, dengan
 * style asli disimpan di {@link HighlightStyle} supaya bisa dikembalikan saat pengaturan berubah. Teks buffer tidak
 * berubah (copy, pencarian, dan log tetap teks asli).
 *
 * <p>Pencocokan per baris logis (baris yang di-soft-wrap digabung) dan tanpa penanda karakter lebar, sehingga keyword
 * yang terpotong wrap dan teks CJK tetap kena. Pekerjaan dijalankan di satu thread background bersama untuk semua tab:
 * listener buffer hanya menjadwalkan; teks dibaca dan style ditulis sambil memegang lock buffer (sebentar), sedangkan
 * regex dijalankan tanpa lock. Baris yang sudah diproses diingat ({@link #done}) dan dilewati sampai teksnya berubah.
 */
public final class TextHighlighter {

    private static final Logger log = LoggerFactory.getLogger(TextHighlighter.class);
    private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "termul-highlight");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final long DELAY_MS = 40;
    private static final int CHUNK = 200;           // baris logis per kali pegang lock
    private static final int MAX_TAIL_SCAN = 20_000; // batas mundur di history saat mencari baris yang belum diproses

    /** Profil yang sudah dikompilasi plus gaya setiap keyword. Immutable; boleh dipakai bersama banyak tab. */
    public record Rules(HighlightMatcher matcher, List<Patch> patches, boolean alternateDisable) {

        public static Rules of(HighlightMatcher matcher, boolean alternateDisable) {
            return new Rules(matcher, matcher.profile().keywords().stream().map(Patch::of).toList(), alternateDisable);
        }
    }

    /** Perubahan style dari satu keyword: warna (null = tidak diubah) dan opsi yang ditambahkan. */
    public record Patch(TerminalColor foreground, TerminalColor background, EnumSet<TextStyle.Option> options) {

        public static Patch of(HighlightKeyword k) {
            var options = EnumSet.noneOf(TextStyle.Option.class);
            if (k.bold()) {
                options.add(TextStyle.Option.BOLD);
            }
            if (k.italic()) {
                options.add(TextStyle.Option.ITALIC);
            }
            if (k.underline()) {
                options.add(TextStyle.Option.UNDERLINED);
            }
            if (k.dim()) {
                options.add(TextStyle.Option.DIM);
            }
            if (k.inverse()) {
                options.add(TextStyle.Option.INVERSE);
            }
            if (k.invisible()) {
                options.add(TextStyle.Option.HIDDEN);
            }
            return new Patch(k.foreground() ? color(k.foregroundColor()) : null,
                    k.background() ? color(k.backgroundColor()) : null, options);
        }

        /** Warna Tabby → warna JediTerm; 0–15 tetap indeks supaya ikut tema terminal. */
        public static TerminalColor color(String text) {
            return HighlightColor.parse(text).map(c -> switch (c) {
                case HighlightColor.Indexed(int index) -> ColorPalette.getIndexedTerminalColor(index);
                case HighlightColor.Rgb rgb -> TerminalColor.rgb(rgb.red(), rgb.green(), rgb.blue());
            }).orElse(null);
        }

        public TextStyle apply(TextStyle original) {
            var b = original.toBuilder();
            if (foreground != null || background != null) {
                b.setOption(TextStyle.Option.INVERSE, false); // warna highlight jangan tertukar
            }
            if (foreground != null) {
                b.setForeground(foreground);
            }
            if (background != null) {
                b.setBackground(background);
            }
            options.forEach(o -> b.setOption(o, true));
            return b.build();
        }
    }

    /** Style karakter yang diwarnai highlight; {@link #original} dipakai untuk mengembalikannya. */
    static final class HighlightStyle extends TextStyle {
        final TextStyle original;
        final int generation;

        HighlightStyle(TextStyle styled, TextStyle original, int generation) {
            super(styled.getForeground(), styled.getBackground(), options(styled));
            this.original = original;
            this.generation = generation;
        }

        private static EnumSet<Option> options(TextStyle style) {
            var set = EnumSet.noneOf(Option.class);
            for (Option o : Option.values()) {
                if (style.hasOption(o)) {
                    set.add(o);
                }
            }
            return set;
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o) && o instanceof HighlightStyle h && h.generation == generation
                    && h.original.equals(original);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), original, generation);
        }
    }

    /** @param rules null = highlight mati */
    private record State(Rules rules, int generation) {
    }

    /** Baris logis yang sudah diproses: teks mentah dan (kalau ada) letak span pertama untuk dicek ulang. */
    private record Stamp(int generation, String raw, int lines, int checkLine, int checkColumn) {
    }

    /** Baris logis: baris fisik (yang sebelum terakhir di-soft-wrap) dan teksnya. */
    private record Logical(List<TerminalLine> lines, String raw, boolean strip) {
    }

    private final TerminalTextBuffer buffer;
    private final Runnable repaint;
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final TextBufferChangesListener listener = new TextBufferChangesListener() {
        @Override
        public void linesChanged(int fromIndex) {
            schedule();
        }

        @Override
        public void widthResized() {
            schedule();
        }
    };
    private volatile State state = new State(null, 0);
    private volatile boolean closed;
    // hanya diakses di thread highlight:
    private final WeakHashMap<TerminalLine, Stamp> done = new WeakHashMap<>();
    private int processedGeneration = -1;
    private boolean everApplied;
    private boolean failureLogged;

    /**
     * @param repaint dipanggil (di thread highlight) setelah style berubah; implementasi meneruskannya ke EDT
     */
    public TextHighlighter(TerminalTextBuffer buffer, Runnable repaint) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.repaint = Objects.requireNonNull(repaint, "repaint");
        buffer.addChangesListener(listener);
    }

    /** @param rules profil untuk tab ini, atau null untuk mematikan (highlight yang ada dikembalikan) */
    public void setRules(Rules rules) {
        State current = state;
        if (current.rules() == rules) {
            return;
        }
        state = new State(rules, current.generation() + 1);
        schedule();
    }

    public void close() {
        closed = true;
        buffer.removeChangesListener(listener);
    }

    private void schedule() {
        if (!closed && scheduled.compareAndSet(false, true)) {
            EXEC.schedule(this::runSafely, DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void runSafely() {
        scheduled.set(false);
        try {
            if (pass()) {
                repaint.run();
            }
        } catch (RuntimeException e) {
            if (!failureLogged) {
                failureLogged = true;
                log.warn("Highlight teks gagal diterapkan: {}", e.toString(), e);
            }
        }
    }

    /** Satu putaran: proses baris yang berubah. Dipanggil di thread highlight (atau langsung oleh test). */
    boolean pass() {
        State st = state;
        if (closed || (st.rules() == null && !everApplied)) {
            return false;
        }
        boolean full = st.generation() != processedGeneration;
        if (full) {
            done.clear();
            processedGeneration = st.generation();
        }
        List<Logical> work;
        buffer.lock();
        try {
            work = collect(full, st);
        } finally {
            buffer.unlock();
        }
        boolean changed = false;
        for (int from = 0; from < work.size() && !closed && state == st; from += CHUNK) {
            changed |= process(work.subList(from, Math.min(work.size(), from + CHUNK)), st);
        }
        return changed;
    }

    /** Baris logis kandidat (memegang lock): seluruh buffer kalau pengaturan baru, selain itu ekor yang belum diproses. */
    private List<Logical> collect(boolean full, State st) {
        LinesStorage history = buffer.getHistoryLinesStorage();
        LinesStorage screen = buffer.getScreenLinesStorage();
        boolean strip = st.rules() == null || st.rules().matcher().isEmpty()
                || (buffer.isUsingAlternateBuffer() && st.rules().alternateDisable());
        int start = 0;
        if (!full) {
            start = history.getSize();
            int limit = Math.max(0, history.getSize() - MAX_TAIL_SCAN);
            while (start > limit) {
                TerminalLine line = history.get(start - 1);
                start--;
                Stamp s = done.get(line);
                if (s != null && s.generation() == st.generation()) {
                    break;
                }
            }
        }
        var lines = new ArrayList<TerminalLine>();
        for (int i = start; i < history.getSize(); i++) {
            lines.add(history.get(i));
        }
        for (TerminalLine line : screen) {
            lines.add(line);
        }
        var out = new ArrayList<Logical>();
        int i = 0;
        while (i < lines.size()) {
            int end = i;
            while (end < lines.size() - 1 && lines.get(end).isWrapped()) {
                end++;
            }
            List<TerminalLine> group = List.copyOf(lines.subList(i, end + 1));
            String raw = raw(group);
            if (!isDone(group, raw, st.generation())) {
                out.add(new Logical(group, raw, strip));
            }
            i = end + 1;
        }
        return out;
    }

    private boolean isDone(List<TerminalLine> group, String raw, int generation) {
        Stamp s = done.get(group.getFirst());
        if (s == null || s.generation() != generation || s.lines() != group.size() || !s.raw().equals(raw)) {
            return false;
        }
        if (s.checkLine() < 0) {
            return true;
        }
        // teks sama tapi baris ditulis ulang (mis. prompt digambar ulang): highlight-nya hilang
        return group.get(s.checkLine()).getStyleAt(s.checkColumn()) instanceof HighlightStyle h
                && h.generation == generation;
    }

    private static String raw(List<TerminalLine> group) {
        if (group.size() == 1) {
            return group.getFirst().getText();
        }
        var sb = new StringBuilder();
        group.forEach(l -> sb.append(l.getText()));
        return sb.toString();
    }

    private boolean process(List<Logical> chunk, State st) {
        // cocokkan tanpa lock
        var spans = new ArrayList<List<HighlightMatcher.Span>>(chunk.size());
        var maps = new ArrayList<int[][]>(chunk.size());
        for (Logical l : chunk) {
            int[][] map = new int[2][];
            String clean = clean(l.lines(), map);
            maps.add(map);
            spans.add(l.strip() || clean.isEmpty() ? List.of() : st.rules().matcher().match(clean));
        }
        boolean changed = false;
        buffer.lock();
        try {
            if (state != st) {
                return false;
            }
            var cache = new IdentityHashMap<TextStyle, HighlightStyle[]>();
            for (int i = 0; i < chunk.size(); i++) {
                Logical l = chunk.get(i);
                if (!raw(l.lines()).equals(l.raw())) {
                    continue; // berubah lagi sejak dibaca; putaran berikutnya
                }
                changed |= apply(l, spans.get(i), maps.get(i), st, cache);
            }
        } finally {
            buffer.unlock();
        }
        return changed;
    }

    /** Teks tanpa penanda karakter lebar; {@code map[0][i]}/{@code map[1][i]} = baris/kolom karakter ke-i. */
    private static String clean(List<TerminalLine> lines, int[][] map) {
        var sb = new StringBuilder();
        var lineOf = new ArrayList<Integer>();
        var colOf = new ArrayList<Integer>();
        for (int li = 0; li < lines.size(); li++) {
            String text = lines.get(li).getText();
            for (int col = 0; col < text.length(); col++) {
                char c = text.charAt(col);
                if (c != CharUtils.DWC) {
                    sb.append(c);
                    lineOf.add(li);
                    colOf.add(col);
                }
            }
        }
        map[0] = lineOf.stream().mapToInt(Integer::intValue).toArray();
        map[1] = colOf.stream().mapToInt(Integer::intValue).toArray();
        return sb.toString();
    }

    /** Tulis style baru ke baris fisik yang berbeda dari sekarang. Memegang lock buffer. */
    private boolean apply(Logical l, List<HighlightMatcher.Span> spans, int[][] map, State st,
                          IdentityHashMap<TextStyle, HighlightStyle[]> cache) {
        int lineCount = l.lines().size();
        int[][] keywordAt = new int[lineCount][];
        for (int li = 0; li < lineCount; li++) {
            keywordAt[li] = new int[l.lines().get(li).getText().length()];
            java.util.Arrays.fill(keywordAt[li], -1);
        }
        int checkLine = -1;
        int checkColumn = -1;
        for (HighlightMatcher.Span s : spans) {
            for (int i = s.start(); i < s.end(); i++) {
                int li = map[0][i];
                int col = map[1][i];
                keywordAt[li][col] = s.keyword();
                if (col + 1 < keywordAt[li].length && l.lines().get(li).getText().charAt(col + 1) == CharUtils.DWC) {
                    keywordAt[li][col + 1] = s.keyword();
                }
                if (checkLine < 0) {
                    checkLine = li;
                    checkColumn = col;
                }
            }
        }
        boolean changed = false;
        for (int li = 0; li < lineCount; li++) {
            changed |= rewrite(l.lines().get(li), keywordAt[li], st, cache);
        }
        if (changed || !spans.isEmpty()) {
            everApplied = true;
        }
        done.put(l.lines().getFirst(), new Stamp(st.generation(), l.raw(), lineCount, checkLine, checkColumn));
        return changed;
    }

    private boolean rewrite(TerminalLine line, int[] keywordAt, State st,
                            IdentityHashMap<TextStyle, HighlightStyle[]> cache) {
        int length = keywordAt.length;
        char[] chars = new char[length];
        TextStyle[] current = new TextStyle[length];
        int p = 0;
        for (TerminalLine.TextEntry e : line.getEntries()) {
            if (p >= length) {
                break;
            }
            CharBuffer text = e.getText();
            for (int i = 0; i < text.length() && p < length; i++, p++) {
                chars[p] = text.charAt(i);
                current[p] = e.getStyle();
            }
        }
        boolean changed = false;
        int col = 0;
        while (col < p) {
            TextStyle want = desired(current[col], keywordAt[col], st, cache);
            int end = col + 1;
            while (end < p && desired(current[end], keywordAt[end], st, cache) == want && current[end] == current[col]) {
                end++;
            }
            if (want != current[col]) {
                line.writeString(col, new CharBuffer(chars, col, end - col), want);
                changed = true;
            }
            col = end;
        }
        return changed;
    }

    private static TextStyle desired(TextStyle current, int keyword, State st,
                                     IdentityHashMap<TextStyle, HighlightStyle[]> cache) {
        TextStyle original = current instanceof HighlightStyle h ? h.original : current;
        if (keyword < 0 || original instanceof HyperlinkStyle) {
            return original;
        }
        HighlightStyle[] perKeyword = cache.computeIfAbsent(original, o -> new HighlightStyle[st.rules().patches().size()]);
        HighlightStyle style = perKeyword[keyword];
        if (style == null) {
            var fresh = new HighlightStyle(st.rules().patches().get(keyword).apply(original), original, st.generation());
            // sudah benar dari putaran sebelumnya: pakai objek yang ada supaya tidak ditulis ulang
            style = current instanceof HighlightStyle h && h.equals(fresh) ? h : fresh;
            perKeyword[keyword] = style;
        }
        return style;
    }
}

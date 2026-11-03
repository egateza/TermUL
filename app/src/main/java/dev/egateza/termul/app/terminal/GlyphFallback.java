package dev.egateza.termul.app.terminal;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Fallback font per karakter untuk terminal. JediTerm 3.76 menggambar setiap karakter dengan font terminal saja,
 * dan font fisik (JetBrains Mono, Consolas, ...) tidak di-fallback oleh Java2D. Akibatnya simbol seperti ✔ (U+2714)
 * atau spinner Braille (⠋⠙⠹, dipakai {@code docker compose}) tampil sebagai kotak. Kelas ini mencari font terpasang
 * yang punya glyph-nya, dengan style dan ukuran yang sama.
 *
 * <p>Ikon prompt (oh-my-posh, starship, Powerlevel10k) ada di Private Use Area dan hanya dimiliki Nerd Font. Nerd Font
 * yang terpasang ikut dicari (varian Mono dulu: ikonnya selebar satu sel), dan untuk karakter Private Use Area dicoba
 * paling awal, karena font simbol lain (mis. Segoe UI Symbol) juga punya glyph di sana dengan arti yang berbeda.
 */
final class GlyphFallback {

    /** Urutan pencarian: font simbol Windows/macOS dulu, lalu monospace lain, terakhir logical font (composite). */
    private static final List<String> CANDIDATES = List.of("Segoe UI Symbol", "Cascadia Mono", "Cascadia Code",
            "DejaVu Sans Mono", "Apple Symbols", "Menlo", "Segoe UI Emoji", "Apple Color Emoji", Font.MONOSPACED,
            Font.DIALOG);

    private static volatile GlyphFallback system;

    private final List<Font> prototypes;
    private final List<Font> icons;
    private final Map<Integer, Optional<Font>> byCodePoint = new ConcurrentHashMap<>();
    private final Map<String, Font> derived = new ConcurrentHashMap<>();

    /** @param prototypes font kandidat (ukuran bebas), urut prioritas */
    GlyphFallback(List<Font> prototypes) {
        this(prototypes, List.of());
    }

    /** @param icons Nerd Font (ukuran bebas), urut prioritas; dicoba paling awal untuk karakter Private Use Area */
    GlyphFallback(List<Font> prototypes, List<Font> icons) {
        this.prototypes = List.copyOf(prototypes);
        this.icons = List.copyOf(icons);
    }

    /** Instance bersama dari font yang terpasang di sistem (dibuat sekali, saat pertama dipakai). */
    static GlyphFallback system() {
        var s = system;
        if (s == null) {
            Set<String> available = Set.of(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            s = new GlyphFallback(CANDIDATES.stream()
                    .filter(f -> available.contains(f) || f.equals(Font.MONOSPACED) || f.equals(Font.DIALOG))
                    .map(f -> new Font(f, Font.PLAIN, 12))
                    .toList(),
                    nerdFamilies(available).stream().map(f -> new Font(f, Font.PLAIN, 12)).toList());
            system = s;
        }
        return s;
    }

    /**
     * @return {@code base} kalau bisa menampilkan {@code text[start, end)}, kalau tidak font kandidat pertama yang
     *         bisa (style dan ukuran mengikuti {@code base}); {@code base} lagi kalau tidak ada yang bisa
     */
    Font fontFor(Font base, char[] text, int start, int end) {
        if (start >= end || base.canDisplayUpTo(text, start, end) == -1) {
            return base;
        }
        int cp = Character.codePointAt(text, start, end);
        return byCodePoint.computeIfAbsent(cp, c -> candidates(c).filter(p -> p.canDisplay(c)).findFirst())
                .map(p -> derived.computeIfAbsent(p.getName() + '\0' + base.getStyle() + '\0' + base.getSize2D(),
                        k -> p.deriveFont(base.getStyle(), base.getSize2D())))
                .orElse(base);
    }

    private Stream<Font> candidates(int codePoint) {
        return privateUse(codePoint)
                ? Stream.concat(icons.stream(), prototypes.stream())
                : Stream.concat(prototypes.stream(), icons.stream());
    }

    static boolean privateUse(int codePoint) {
        return Character.getType(codePoint) == Character.PRIVATE_USE;
    }

    /**
     * Family Nerd Font dari {@code available}, urut: varian Mono ({@code Nerd Font Mono}, {@code NFM}), lalu varian
     * biasa ({@code Nerd Font}, {@code NF}); di dalamnya urut abjad. Varian proporsional ({@code Propo}, {@code NFP})
     * dan family per ketebalan (mis. {@code JetBrainsMono NFM Light}) tidak dipakai.
     */
    static List<String> nerdFamilies(Collection<String> available) {
        var mono = available.stream().filter(f -> f.endsWith(" Nerd Font Mono") || f.endsWith(" NFM")).sorted();
        var regular = available.stream().filter(f -> f.endsWith(" Nerd Font") || f.endsWith(" NF")).sorted();
        return Stream.concat(mono, regular).toList();
    }
}

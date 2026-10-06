package dev.egateza.termul.app.terminal;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback font per karakter untuk terminal. JediTerm 3.76 menggambar setiap karakter dengan font terminal saja,
 * dan font fisik (JetBrains Mono, Consolas, ...) tidak di-fallback oleh Java2D. Akibatnya simbol seperti ✔ (U+2714)
 * atau spinner Braille (⠋⠙⠹, dipakai {@code docker compose}) tampil sebagai kotak. Kelas ini mencari font terpasang
 * yang punya glyph-nya, dengan style dan ukuran yang sama.
 */
final class GlyphFallback {

    /** Urutan pencarian: font simbol Windows/macOS dulu, lalu monospace lain, terakhir logical font (composite). */
    private static final List<String> CANDIDATES = List.of("Segoe UI Symbol", "Cascadia Mono", "Cascadia Code",
            "DejaVu Sans Mono", "Apple Symbols", "Menlo", "Segoe UI Emoji", "Apple Color Emoji", Font.MONOSPACED,
            Font.DIALOG);

    private static volatile GlyphFallback system;

    private final List<Font> prototypes;
    private final Map<Integer, Optional<Font>> byCodePoint = new ConcurrentHashMap<>();
    private final Map<String, Font> derived = new ConcurrentHashMap<>();

    /** @param prototypes font kandidat (ukuran bebas), urut prioritas */
    GlyphFallback(List<Font> prototypes) {
        this.prototypes = List.copyOf(prototypes);
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
                    .toList());
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
        return byCodePoint.computeIfAbsent(cp, c -> prototypes.stream().filter(p -> p.canDisplay(c)).findFirst())
                .map(p -> derived.computeIfAbsent(p.getName() + '\0' + base.getStyle() + '\0' + base.getSize2D(),
                        k -> p.deriveFont(base.getStyle(), base.getSize2D())))
                .orElse(base);
    }
}

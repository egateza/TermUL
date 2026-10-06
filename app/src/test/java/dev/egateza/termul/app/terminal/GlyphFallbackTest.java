package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.util.List;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

class GlyphFallbackTest {

    private static final int CHECK = 0x2714; // ✔
    private static final int BRAILLE = 0x280B; // ⠋

    /** Font palsu yang hanya bisa menampilkan karakter tertentu, supaya test tidak bergantung pada font terpasang. */
    private static Font font(String name, IntPredicate displays) {
        return font(name, Font.PLAIN, 12, displays);
    }

    private static Font font(String name, int style, int size, IntPredicate displays) {
        return new Font(name, style, size) {
            @Override
            public boolean canDisplay(int codePoint) {
                return displays.test(codePoint);
            }

            @Override
            public int canDisplayUpTo(char[] text, int start, int limit) {
                for (int i = start; i < limit; i += Character.charCount(Character.codePointAt(text, i, limit))) {
                    if (!displays.test(Character.codePointAt(text, i, limit))) {
                        return i;
                    }
                }
                return -1;
            }
        };
    }

    private static final Font ASCII_ONLY = font("Base", cp -> cp < 0x80);

    @Test
    void keepsBaseFontWhenGlyphExists() {
        var fallback = new GlyphFallback(List.of(font("Symbols", cp -> true)));
        char[] text = "abc".toCharArray();

        assertThat(fallback.fontFor(ASCII_ONLY, text, 0, 1)).isSameAs(ASCII_ONLY);
    }

    @Test
    void usesFirstCandidateThatHasTheGlyphWithBaseStyleAndSize() {
        var fallback = new GlyphFallback(List.of(
                font("NoBraille", cp -> cp == CHECK),
                font("Symbols", cp -> cp == CHECK || cp == BRAILLE)));
        var base = font("Base", Font.BOLD, 17, cp -> cp < 0x80);
        char[] text = Character.toChars(BRAILLE);

        var chosen = fallback.fontFor(base, text, 0, text.length);

        assertThat(chosen.getName()).isEqualTo("Symbols");
        assertThat(chosen.getStyle()).isEqualTo(Font.BOLD);
        assertThat(chosen.getSize2D()).isEqualTo(17f);
    }

    @Test
    void reusesDerivedFontForSameStyleAndSize() {
        var fallback = new GlyphFallback(List.of(font("Symbols", cp -> true)));
        char[] text = Character.toChars(CHECK);

        assertThat(fallback.fontFor(ASCII_ONLY, text, 0, 1)).isSameAs(fallback.fontFor(ASCII_ONLY, text, 0, 1));
    }

    @Test
    void fallsBackToBaseWhenNoCandidateHasTheGlyph() {
        var fallback = new GlyphFallback(List.of(font("Nothing", cp -> false)));
        char[] text = Character.toChars(CHECK);

        assertThat(fallback.fontFor(ASCII_ONLY, text, 0, 1)).isSameAs(ASCII_ONLY);
    }
}

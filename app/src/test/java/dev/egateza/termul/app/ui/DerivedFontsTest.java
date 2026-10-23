package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import org.junit.jupiter.api.Test;

class DerivedFontsTest {

    private static final Font BASE = new Font(Font.DIALOG, Font.PLAIN, 12);

    /** Paint tiap frame tidak boleh membuat font baru (font komposit = CompositeFont + cache glyph baru per derive). */
    @Test
    void fontTurunanYangSamaDipakaiUlang() {
        Font bold = DerivedFonts.derive(BASE, Font.BOLD);

        assertThat(bold.getStyle()).isEqualTo(Font.BOLD);
        assertThat(bold.getSize2D()).isEqualTo(12f);
        assertThat(DerivedFonts.derive(BASE, Font.BOLD)).isSameAs(bold);
        assertThat(DerivedFonts.derive(BASE, Font.BOLD, 12f)).isSameAs(bold);
    }

    @Test
    void ukuranDanStyleDiterapkan() {
        Font f = DerivedFonts.derive(BASE, Font.BOLD | Font.ITALIC, 22f);

        assertThat(f.getStyle()).isEqualTo(Font.BOLD | Font.ITALIC);
        assertThat(f.getSize2D()).isEqualTo(22f);
        assertThat(DerivedFonts.derive(BASE, 9f).getStyle()).isEqualTo(Font.PLAIN);
        assertThat(DerivedFonts.derive(BASE, Font.PLAIN, 12f)).isSameAs(BASE);
    }
}

package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.font.TextAttribute;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class WordmarkTest {

    @Test
    void warnaPrototipeDiTemaGelapDanWarnaTemaDiTemaTerang() {
        assertThat(Wordmark.colors(true, Color.RED)).containsExactly(new Color(0xE8EEF0), new Color(0xF0B34A));
        assertThat(Wordmark.colors(false, Color.BLUE)).containsExactly(Color.BLUE, Wordmark.LIGHT_CURSOR);
    }

    @Test
    void garisBawahAmberTetapTerbacaDiTemaTerang() {
        assertThat(contrast(Wordmark.LIGHT_CURSOR, Color.WHITE)).isGreaterThanOrEqualTo(3.0);
    }

    @Test
    void fontBawaanJarSamaDenganTermulMobile() {
        var font = Wordmark.bundled();
        assertThat(font).as("JetBrains Mono Medium ada di jar").isNotNull();
        assertThat(font.getFontName()).isEqualTo("JetBrains Mono Medium");
        assertThat(font.canDisplayUpTo(Wordmark.NAME + Wordmark.CURSOR)).isEqualTo(-1);
        assertThat(Wordmark.class.getResource("/dev/egateza/termul/app/fonts/LICENSE.txt")).as("lisensi OFL ikut")
                .isNotNull();
    }

    @Test
    void statisTidakIkutPengaturanFont() {
        var old = UIManager.getFont("Label.font");
        try {
            UIManager.put("Label.font", new javax.swing.plaf.FontUIResource("Serif", java.awt.Font.BOLD, 20));
            var font = Wordmark.font();
            assertThat(font.getFontName()).isEqualTo("JetBrains Mono Medium");
            assertThat(font.getSize2D()).isEqualTo(Wordmark.SIZE).isEqualTo(13f);
            assertThat(font.getAttributes().get(TextAttribute.TRACKING)).isEqualTo(0.04f);
            assertThat(new Wordmark().getAccessibleContext().getAccessibleName()).isEqualTo("TermUL");
        } finally {
            UIManager.put("Label.font", old);
        }
    }

    /**
     * Langsung saat dibuat, di dalam menu bar: JComponent polos tidak memanggil updateUI() di constructor, jadi tanpa
     * font sendiri wordmark mewarisi font menu bar (Segoe UI) sampai tema diganti.
     */
    @Test
    void komponenBaruDiMenuBarLangsungMemakaiFontWordmark() {
        var bar = new javax.swing.JMenuBar();
        bar.setFont(new java.awt.Font("Serif", java.awt.Font.PLAIN, 12));
        var wordmark = new Wordmark();
        bar.add(wordmark);

        assertThat(wordmark.isFontSet()).isTrue();
        assertThat(wordmark.getFont().getFontName()).isEqualTo("JetBrains Mono Medium");
        assertThat(wordmark.getFont().getSize2D()).isEqualTo(13f);
    }

    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}

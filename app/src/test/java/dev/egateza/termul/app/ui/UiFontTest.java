package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UiFontTest {

    /** Simbol yang dipakai sebagai ikon/teks di UI (tombol mode, log aktivitas, badge snippet, shortcut Mac, kursor). */
    private static final String SYMBOLS = "☀☾✔✘✖▲●↵⌘⇧│█";

    @Test
    void fontPilihanTetapBisaMenampilkanSimbolLewatFallback() {
        assumeTrue(!GraphicsEnvironment.isHeadless());
        var installed = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        String family = installed.contains("Calibri") ? "Calibri" : installed.contains("Arial") ? "Arial" : null;
        assumeTrue(family != null, "butuh font tanpa simbol-simbol ini (Calibri/Arial)");

        var physical = new Font(family, Font.PLAIN, 12);
        assumeTrue(physical.canDisplayUpTo(SYMBOLS) >= 0, "font ini ternyata punya semua simbol");

        var font = UiFont.font(family);
        assertThat(font.getFamily()).isEqualTo(family);
        assertThat(font.canDisplayUpTo(SYMBOLS)).as("-1 = semua simbol bisa ditampilkan").isEqualTo(-1);
        assertThat(font.deriveFont(Font.BOLD, 14f).canDisplayUpTo(SYMBOLS))
                .as("turunan (Bold, ukuran lain) tetap punya fallback").isEqualTo(-1);
    }
}

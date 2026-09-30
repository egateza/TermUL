package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import dev.egateza.termul.core.theme.ThemeTemplates;
import org.junit.jupiter.api.Test;

class TerminalSettingsColorsTest {

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    @Test
    void withoutThemeUsesBlackOnWhiteAndJediTermPalette() {
        var settings = new TerminalSettings(14f);
        var defaults = new TerminalSettings(14f);

        assertThat(settings.getTerminalColorPalette()).isSameAs(defaults.getTerminalColorPalette());
        assertThat(hex(settings.getDefaultBackground().toColor())).isEqualTo("#FFFFFF");
        assertThat(hex(settings.getDefaultForeground().toColor())).isEqualTo("#000000");
    }

    @Test
    void paletteSuppliesDefaultColorsAnsiAndSelection() {
        var theme = ThemeTemplates.dark("x", "X").terminal();
        var settings = new TerminalSettings(14f);

        settings.setPalette(theme);

        assertThat(hex(settings.getDefaultBackground().toColor())).isEqualTo(theme.background());
        assertThat(hex(settings.getDefaultForeground().toColor())).isEqualTo(theme.foreground());
        // StyleState (sel terminal) membaca getDefaultStyle; harus RGB tema, bukan indeks palet 0/15 bawaan JediTerm
        var style = settings.getDefaultStyle();
        assertThat(style.getBackground().isIndexed()).isFalse();
        assertThat(hex(style.getBackground().toColor())).isEqualTo(theme.background());
        assertThat(hex(style.getForeground().toColor())).isEqualTo(theme.foreground());
        assertThat(hex(settings.getSelectionColor().getBackground().toColor())).isEqualTo(theme.selection());
        var palette = settings.getTerminalColorPalette();
        for (int i = 0; i < 16; i++) {
            assertThat(hex(palette.getForeground(TerminalColor.index(i)))).isEqualTo(theme.ansi().get(i));
            assertThat(hex(palette.getBackground(TerminalColor.index(i)))).isEqualTo(theme.ansi().get(i));
        }
    }

    @Test
    void copiesShareThePaletteAndNullRestoresDefaults() {
        var settings = new TerminalSettings(14f);
        var tab = settings.copy();
        var theme = ThemeTemplates.light("x", "X").terminal();

        settings.setPalette(theme);
        assertThat(hex(tab.getDefaultBackground().toColor())).isEqualTo(theme.background());

        settings.setPalette(null);
        assertThat(tab.getTerminalColorPalette()).isSameAs(new TerminalSettings(14f).getTerminalColorPalette());
    }

    @Test
    void backgroundLayerNeedsBothImageAndPalette() {
        var settings = new TerminalSettings(14f);
        var image = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var theme = ThemeTemplates.dark("x", "X").terminal();

        settings.setBackgroundImage(image, 40);
        assertThat(settings.backgroundLayer()).isNull(); // belum ada palet: tidak ada warna dasar

        settings.setPalette(theme);
        var layer = settings.backgroundLayer();
        assertThat(layer).isNotNull();
        assertThat(layer.image()).isSameAs(image);
        assertThat(layer.visibility()).isEqualTo(40);
        assertThat(layer.base()).isEqualTo(java.awt.Color.decode(theme.background()));

        settings.setBackgroundImage(null, 0);
        assertThat(settings.backgroundLayer()).isNull();
    }

    @Test
    void backgroundLayerIsSharedWithCopiesAndVisibilityIsClamped() {
        var settings = new TerminalSettings(14f);
        var tab = settings.copy();
        var image = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
        settings.setPalette(ThemeTemplates.dark("x", "X").terminal());

        settings.setBackgroundImage(image, 999);

        assertThat(tab.backgroundLayer()).isNotNull();
        assertThat(tab.backgroundLayer().visibility()).isEqualTo(100);
    }

    @Test
    void defaultStyleColorsFollowThemeChangesForCellsWrittenEarlier() {
        // JediTerm menyimpan style (objek TerminalColor) di tiap sel yang ditulis: objek yang sama harus
        // menghasilkan warna tema BARU setelah tema diganti, kalau tidak teks lama tertinggal berlatar tema lama
        var settings = new TerminalSettings(14f);
        var storedInCell = settings.getDefaultStyle();
        var dark = ThemeTemplates.dark("a", "A").terminal();
        var light = ThemeTemplates.light("b", "B").terminal();

        settings.setPalette(dark);
        assertThat(hex(storedInCell.getBackground().toColor())).isEqualTo(dark.background());

        settings.setPalette(light);
        assertThat(hex(storedInCell.getBackground().toColor())).isEqualTo(light.background());
        assertThat(hex(storedInCell.getForeground().toColor())).isEqualTo(light.foreground());

        settings.setPalette(null);
        assertThat(hex(storedInCell.getBackground().toColor())).isEqualTo("#FFFFFF");
    }

    @Test
    void defaultStyleIsSharedAcrossTabCopies() {
        var settings = new TerminalSettings(14f);
        var tab = settings.copy();
        var cellStyleFromTab = tab.getDefaultStyle();
        var theme = ThemeTemplates.dark("a", "A").terminal();

        settings.setPalette(theme);

        assertThat(hex(cellStyleFromTab.getBackground().toColor())).isEqualTo(theme.background());
    }
}

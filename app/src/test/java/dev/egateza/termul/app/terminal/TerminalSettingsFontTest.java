package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TerminalSettingsFontTest {

    @Test
    void familyTidakTerpasangKembaliKeOtomatis() {
        var settings = new TerminalSettings("Font Yang Tidak Ada 123", 14f);

        assertThat(settings.getTerminalFont().getFamily()).isNotEqualTo("Font Yang Tidak Ada 123");
    }

    @Test
    void gantiFamilyBerlakuDiSalinanSetelahReload() {
        var settings = new TerminalSettings(14f);
        var tab = settings.copy();
        var pilihan = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()[0];

        settings.setFamily(pilihan);
        tab.reloadFont();

        assertThat(tab.family()).isEqualTo(pilihan);
        assertThat(tab.getTerminalFont().getFamily()).isEqualTo(pilihan);
        assertThat(tab.getTerminalFontSize()).isEqualTo(14f);
    }

    @Test
    void zoomTabTidakBerubahSaatFamilyDiganti() {
        var settings = new TerminalSettings(14f);
        var tab = settings.copy();
        tab.setFontSize(20f);

        settings.setFamily(null);
        tab.reloadFont();

        assertThat(tab.getTerminalFontSize()).isEqualTo(20f);
    }
}

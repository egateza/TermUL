package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TerminalSettingsBellTest {

    @Test
    void bunyiMengikutiPengaturan() {
        var settings = new TerminalSettings(14f);
        assertThat(settings.audibleBell()).isTrue();

        settings.bell().setSound(false);

        assertThat(settings.audibleBell()).isFalse();
    }

    @Test
    void salinanTabBerbagiPengaturanBell() {
        var settings = new TerminalSettings(14f);
        var tabA = settings.copy();
        var tabB = settings.copy();

        settings.bell().setSound(false);
        settings.bell().setShake(false);

        assertThat(tabA.audibleBell()).isFalse();
        assertThat(tabB.bell().shake()).isFalse();
    }
}

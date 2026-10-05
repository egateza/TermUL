package dev.egateza.termul.app.ui.idle;

import static dev.egateza.termul.app.ui.idle.IdleState.Decision.PASS;
import static dev.egateza.termul.app.ui.idle.IdleState.Decision.SWALLOW;
import static dev.egateza.termul.app.ui.idle.IdleState.Decision.WAKE;
import static dev.egateza.termul.app.ui.idle.IdleState.Key.PRESSED;
import static dev.egateza.termul.app.ui.idle.IdleState.Key.RELEASED;
import static dev.egateza.termul.app.ui.idle.IdleState.Key.TYPED;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.KeyEvent;
import org.junit.jupiter.api.Test;

class IdleStateTest {

    private static final long MIN = 60_000;

    @Test
    void tampilSetelahWaktuIdleDanInputMenundanya() {
        var idle = new IdleState(0);

        assertThat(idle.shouldActivate(15 * MIN - 1, 15)).isFalse();
        assertThat(idle.shouldActivate(15 * MIN, 15)).isTrue();

        idle.activity(10 * MIN);
        assertThat(idle.shouldActivate(15 * MIN, 15)).isFalse();
        assertThat(idle.shouldActivate(25 * MIN, 15)).isTrue();
    }

    @Test
    void nolMenitBerartiMati() {
        assertThat(new IdleState(0).shouldActivate(1_000 * MIN, 0)).isFalse();
    }

    @Test
    void tidakTampilDuaKali() {
        var idle = new IdleState(0);
        idle.activate();
        assertThat(idle.shouldActivate(100 * MIN, 1)).isFalse();
    }

    @Test
    void tanpaLayarIdleSemuaTombolDiteruskan() {
        var idle = new IdleState(0);
        assertThat(idle.onKey(PRESSED, KeyEvent.VK_A, 1)).isEqualTo(PASS);
        assertThat(idle.onKey(TYPED, KeyEvent.VK_UNDEFINED, 1)).isEqualTo(PASS);
        assertThat(idle.onKey(RELEASED, KeyEvent.VK_A, 1)).isEqualTo(PASS);
    }

    @Test
    void enterPembangunTidakSampaiKeTerminal() {
        var idle = new IdleState(0);
        idle.activate();

        assertThat(idle.onKey(PRESSED, KeyEvent.VK_ENTER, 1)).isEqualTo(WAKE);
        assertThat(idle.isActive()).isFalse();
        assertThat(idle.onKey(TYPED, KeyEvent.VK_UNDEFINED, 1)).isEqualTo(SWALLOW); // '\n'
        assertThat(idle.onKey(PRESSED, KeyEvent.VK_ENTER, 2)).isEqualTo(SWALLOW);   // auto-repeat
        assertThat(idle.onKey(RELEASED, KeyEvent.VK_ENTER, 3)).isEqualTo(SWALLOW);

        assertThat(idle.onKey(PRESSED, KeyEvent.VK_L, 4)).isEqualTo(PASS); // tombol berikutnya normal lagi
    }

    @Test
    void ctrlCSaatMembangunkanDenganCtrlTidakTerkirim() {
        var idle = new IdleState(0);
        idle.activate();

        assertThat(idle.onKey(PRESSED, KeyEvent.VK_CONTROL, 1)).isEqualTo(WAKE);
        assertThat(idle.onKey(PRESSED, KeyEvent.VK_C, 2)).isEqualTo(SWALLOW);
        assertThat(idle.onKey(TYPED, KeyEvent.VK_UNDEFINED, 2)).isEqualTo(SWALLOW);
        assertThat(idle.onKey(RELEASED, KeyEvent.VK_C, 3)).isEqualTo(SWALLOW);
        assertThat(idle.onKey(RELEASED, KeyEvent.VK_CONTROL, 4)).isEqualTo(SWALLOW);

        assertThat(idle.onKey(PRESSED, KeyEvent.VK_C, 5)).isEqualTo(PASS);
    }

    @Test
    void lepasTombolShortcutSetelahLayarTampilDitelanTanpaMembangunkan() {
        var idle = new IdleState(0);
        idle.activate(); // Ctrl+Shift+I baru saja ditekan

        assertThat(idle.onKey(RELEASED, KeyEvent.VK_I, 1)).isEqualTo(SWALLOW);
        assertThat(idle.onKey(RELEASED, KeyEvent.VK_SHIFT, 1)).isEqualTo(SWALLOW);
        assertThat(idle.isActive()).isTrue();
    }

    @Test
    void kehilanganFokusBerhentiMenelan() {
        var idle = new IdleState(0);
        idle.activate();
        idle.onKey(PRESSED, KeyEvent.VK_A, 1);

        idle.focusLost(); // tombol dilepas di window lain

        assertThat(idle.onKey(PRESSED, KeyEvent.VK_B, 2)).isEqualTo(PASS);
    }

    @Test
    void dibangunkanMouseMenghitungUlangWaktuIdle() {
        var idle = new IdleState(0);
        idle.activate();

        idle.deactivate(20 * MIN);

        assertThat(idle.isActive()).isFalse();
        assertThat(idle.shouldActivate(30 * MIN, 15)).isFalse();
        assertThat(idle.onKey(PRESSED, KeyEvent.VK_A, 20 * MIN)).isEqualTo(PASS);
    }

    @Test
    void tombolMenundaLayarIdle() {
        var idle = new IdleState(0);
        idle.onKey(PRESSED, KeyEvent.VK_A, 14 * MIN);
        assertThat(idle.shouldActivate(15 * MIN, 15)).isFalse();
    }
}

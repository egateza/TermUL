package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.terminal.SensitiveLock.KeyAction;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import org.junit.jupiter.api.Test;

class SensitiveLockTest {

    private static final long T0 = 1_000_000;

    @Test
    void kembaliSetelahPindahKonteksMenguncikanProfilSensitif() {
        var lock = new SensitiveLock();
        lock.left(T0);

        assertThat(lock.returned(T0 + SensitiveLock.GRACE_MILLIS, true)).isTrue();
        assertThat(lock.isLocked()).isTrue();
    }

    @Test
    void perginyaSingkatAtauProfilTidakSensitifTidakMengunci() {
        var lock = new SensitiveLock();
        lock.left(T0);
        assertThat(lock.returned(T0 + SensitiveLock.GRACE_MILLIS - 1, true)).isFalse();

        lock.left(T0);
        assertThat(lock.returned(T0 + 60_000, false)).isFalse();
    }

    @Test
    void fokusPertamaKaliTanpaPergiTidakMengunci() {
        assertThat(new SensitiveLock().returned(T0, true)).isFalse(); // tab baru dibuka: pilihan sadar user
    }

    @Test
    void waktuPergiDihitungDariFokusLepasPertama() {
        var lock = new SensitiveLock();
        lock.left(T0);
        lock.left(T0 + 1_000); // fokus lepas lagi (mis. pindah antar tab lain) tidak mereset waktu

        assertThat(lock.returned(T0 + SensitiveLock.GRACE_MILLIS, true)).isTrue();
    }

    @Test
    void profilDieditJadiTidakSensitifMembukaKunciSaatKembali() {
        var lock = locked();
        assertThat(lock.returned(T0, false)).isFalse();
    }

    @Test
    void enterMembukaKunciKeyLainDitelan() {
        var lock = locked();

        assertThat(lock.onKeyPressed(KeyEvent.VK_L, 0, false)).isEqualTo(KeyAction.SWALLOW);
        assertThat(lock.onKeyPressed(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, false)).isEqualTo(KeyAction.SWALLOW);
        assertThat(lock.onKeyPressed(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK, false)).isEqualTo(KeyAction.SWALLOW);
        assertThat(lock.onKeyPressed(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK, false))
                .isEqualTo(KeyAction.SWALLOW);
        assertThat(lock.isLocked()).isTrue();

        assertThat(lock.onKeyPressed(KeyEvent.VK_ENTER, 0, false)).isEqualTo(KeyAction.UNLOCK);
        assertThat(lock.isLocked()).isFalse();
        assertThat(lock.onKeyPressed(KeyEvent.VK_L, 0, false)).isEqualTo(KeyAction.PASS);
    }

    @Test
    void shortcutAplikasiDanPindahTabTetapJalanSaatTerkunci() {
        var lock = locked();

        assertThat(lock.onKeyPressed(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, true))
                .isEqualTo(KeyAction.PASS);
        assertThat(lock.onKeyPressed(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK, false)).isEqualTo(KeyAction.PASS);
        assertThat(lock.onKeyPressed(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, false))
                .isEqualTo(KeyAction.PASS);
        assertThat(lock.onKeyPressed(KeyEvent.VK_TAB, 0, false)).isEqualTo(KeyAction.SWALLOW); // completion shell
        assertThat(lock.isLocked()).isTrue();
    }

    @Test
    void tidakTerkunciSemuaKeyDiteruskan() {
        assertThat(new SensitiveLock().onKeyPressed(KeyEvent.VK_ENTER, 0, false)).isEqualTo(KeyAction.PASS);
    }

    private static SensitiveLock locked() {
        var lock = new SensitiveLock();
        lock.left(T0 - 10_000);
        assertThat(lock.returned(T0, true)).isTrue();
        return lock;
    }
}

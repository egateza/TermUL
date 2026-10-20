package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.ui.ShortcutRules.Level;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class ShortcutRulesTest {

    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int CMD = InputEvent.META_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    private static final int ALT = InputEvent.ALT_DOWN_MASK;

    private static Level win(ShortcutAction a, int key, int mods) {
        return ShortcutRules.check(a, KeyStroke.getKeyStroke(key, mods), false).level();
    }

    private static Level mac(ShortcutAction a, int key, int mods) {
        return ShortcutRules.check(a, KeyStroke.getKeyStroke(key, mods), true).level();
    }

    @Test
    void wajibModifierDanTanpaAlt() {
        assertThat(win(ShortcutAction.NEW_HOST, KeyEvent.VK_N, 0)).isEqualTo(Level.ERROR);
        assertThat(win(ShortcutAction.NEW_HOST, KeyEvent.VK_F2, SHIFT)).isEqualTo(Level.ERROR);
        assertThat(win(ShortcutAction.NEW_HOST, KeyEvent.VK_N, CTRL | ALT)).isEqualTo(Level.ERROR);
        assertThat(mac(ShortcutAction.NEW_HOST, KeyEvent.VK_N, CMD | ALT)).isEqualTo(Level.ERROR);
        assertThat(win(ShortcutAction.NEW_HOST, KeyEvent.VK_N, CMD)).isEqualTo(Level.ERROR); // tombol Windows
        assertThat(win(ShortcutAction.NEW_HOST, KeyEvent.VK_H, CTRL | SHIFT)).isEqualTo(Level.OK);
        assertThat(mac(ShortcutAction.NEW_HOST, KeyEvent.VK_K, CMD)).isEqualTo(Level.OK);
    }

    @Test
    void shortcutSistemDitolak() {
        assertThat(mac(ShortcutAction.NEW_HOST, KeyEvent.VK_Q, CMD)).isEqualTo(Level.ERROR);
        assertThat(mac(ShortcutAction.NEW_HOST, KeyEvent.VK_SPACE, CMD)).isEqualTo(Level.ERROR);
        assertThat(mac(ShortcutAction.NEW_HOST, KeyEvent.VK_4, CMD | SHIFT)).isEqualTo(Level.ERROR);
        assertThat(mac(ShortcutAction.NEW_HOST, KeyEvent.VK_F, CMD | CTRL)).isEqualTo(Level.ERROR);
        assertThat(win(ShortcutAction.NEW_HOST, KeyEvent.VK_ESCAPE, CTRL | SHIFT)).isEqualTo(Level.ERROR);
    }

    @Test
    void injectWajibModifierUtama() {
        assertThat(mac(ShortcutAction.INJECT_SUDO, KeyEvent.VK_P, CTRL | SHIFT)).isEqualTo(Level.ERROR);
        assertThat(mac(ShortcutAction.NEW_HOST, KeyEvent.VK_P, CTRL | SHIFT)).isEqualTo(Level.WARNING);
        assertThat(mac(ShortcutAction.INJECT_SUDO, KeyEvent.VK_J, CMD | SHIFT)).isEqualTo(Level.OK);
        assertThat(win(ShortcutAction.INJECT_ROOT, KeyEvent.VK_J, CTRL | SHIFT)).isEqualTo(Level.OK);
    }

    @Test
    void peringatanTombolMilikShellDanCopyPaste() {
        assertThat(win(ShortcutAction.NEW_HOST, KeyEvent.VK_R, CTRL)).isEqualTo(Level.WARNING);
        assertThat(win(ShortcutAction.NEW_HOST, KeyEvent.VK_C, CTRL)).isEqualTo(Level.WARNING);
        assertThat(win(ShortcutAction.RECONNECT, KeyEvent.VK_F6, CTRL)).isEqualTo(Level.OK);
        assertThat(mac(ShortcutAction.NEW_HOST, KeyEvent.VK_R, CTRL)).isEqualTo(Level.WARNING);
        assertThat(mac(ShortcutAction.NEXT_TAB, KeyEvent.VK_TAB, CTRL)).isEqualTo(Level.OK);
        assertThat(win(ShortcutAction.NEW_HOST, KeyEvent.VK_C, CTRL | SHIFT)).isEqualTo(Level.WARNING);
        assertThat(mac(ShortcutAction.NEW_HOST, KeyEvent.VK_V, CMD)).isEqualTo(Level.WARNING);
    }

    @Test
    void tombolModifierSajaBelumSelesai() {
        assertThat(ShortcutRules.isModifierOnly(KeyEvent.VK_SHIFT)).isTrue();
        assertThat(ShortcutRules.isModifierOnly(KeyEvent.VK_META)).isTrue();
        assertThat(ShortcutRules.isModifierOnly(KeyEvent.VK_A)).isFalse();
    }
}

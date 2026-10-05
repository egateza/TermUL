package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class ShortcutsTest {

    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int CMD = InputEvent.META_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    private static final int ALT = InputEvent.ALT_DOWN_MASK;

    private static KeyStroke ks(int key, int mods) {
        return KeyStroke.getKeyStroke(key, mods);
    }

    @Test
    void modifierUtamaCtrlAtauCmd() {
        assertThat(Shortcuts.menuMask(false)).isEqualTo(CTRL);
        assertThat(Shortcuts.menuMask(true)).isEqualTo(CMD);
    }

    @Test
    void windowsMengambilCtrlShiftDanCtrlF5Saja() {
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_P, CTRL | SHIFT), false)).isTrue();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_F5, CTRL), false)).isTrue();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_W, CTRL | SHIFT), false)).isTrue();
        // milik shell
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_W, CTRL), false)).isFalse();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_R, CTRL), false)).isFalse();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_F, CTRL), false)).isFalse();
        // AltGr = Ctrl+Alt
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_Q, CTRL | ALT | SHIFT), false)).isFalse();
        // Cmd tidak berarti apa-apa di Windows
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_P, CMD | SHIFT), false)).isFalse();
    }

    @Test
    void macMemakaiCmdDanCtrlTetapKeTerminal() {
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_P, CMD | SHIFT), true)).isTrue();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_R, CMD), true)).isTrue(); // reconnect
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_W, CMD), true)).isTrue(); // tutup tab
        // Ctrl di Mac selalu milik terminal (Ctrl+Shift+_ undo readline, Ctrl+R history search)
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_P, CTRL | SHIFT), true)).isFalse();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_R, CTRL), true)).isFalse();
        // Cmd+C/V/F milik JediTerm (copy/paste/find); Cmd+Ctrl/Option = shortcut sistem / karakter khusus
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_C, CMD), true)).isFalse();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_F, CMD | CTRL), true)).isFalse();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_P, CMD | ALT | SHIFT), true)).isFalse();
    }

    @Test
    void shortcutKhususPerOs() {
        assertThat(Shortcuts.reconnect(false)).isEqualTo(ks(KeyEvent.VK_F5, CTRL));
        assertThat(Shortcuts.reconnect(true)).isEqualTo(ks(KeyEvent.VK_R, CMD));
        assertThat(Shortcuts.closeTab(false)).isEqualTo(ks(KeyEvent.VK_W, CTRL | SHIFT));
        assertThat(Shortcuts.closeTab(true)).isEqualTo(ks(KeyEvent.VK_W, CMD));
    }

    @Test
    void teksShortcut() {
        assertThat(Shortcuts.text(ks(KeyEvent.VK_P, CTRL | SHIFT), false)).isEqualTo("Ctrl+Shift+P");
        assertThat(Shortcuts.text(ks(KeyEvent.VK_F5, CTRL), false)).isEqualTo("Ctrl+F5");
        assertThat(Shortcuts.text(ks(KeyEvent.VK_P, CMD | SHIFT), true)).isEqualTo("⇧⌘P");
        assertThat(Shortcuts.text(ks(KeyEvent.VK_EQUALS, CMD), true)).isEqualTo("⌘+");
        assertThat(Shortcuts.text(ks(KeyEvent.VK_N, CMD), true)).isEqualTo("⌘N");
    }
}

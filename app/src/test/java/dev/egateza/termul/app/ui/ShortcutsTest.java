package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class ShortcutsTest {

    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int CMD = InputEvent.META_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    private static final int ALT = InputEvent.ALT_DOWN_MASK;
    private static final KeyBindings WIN = KeyBindings.defaults(false);
    private static final KeyBindings MAC = KeyBindings.defaults(true);

    private static KeyStroke ks(int key, int mods) {
        return KeyStroke.getKeyStroke(key, mods);
    }

    @Test
    void altF4DariKeyEventCocokDenganShortcutKeluar() {
        org.junit.jupiter.api.Assumptions.assumeTrue(Shortcuts.exit() != null); // Mac: Cmd+Q dari menu aplikasi
        var e = new KeyEvent(new javax.swing.JPanel(), KeyEvent.KEY_PRESSED, 0, ALT, KeyEvent.VK_F4,
                KeyEvent.CHAR_UNDEFINED);

        assertThat(KeyStroke.getKeyStrokeForEvent(e)).isEqualTo(Shortcuts.exit());
        assertThat(Shortcuts.isAppCombo(KeyStroke.getKeyStrokeForEvent(e))).isFalse(); // makanya dicek terpisah di kunci
    }

    @Test
    void modifierUtamaCtrlAtauCmd() {
        assertThat(Shortcuts.menuMask(false)).isEqualTo(CTRL);
        assertThat(Shortcuts.menuMask(true)).isEqualTo(CMD);
    }

    @Test
    void windowsMengambilCtrlShiftDanCtrlF5Saja() {
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_P, CTRL | SHIFT), WIN)).isTrue();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_F5, CTRL), WIN)).isTrue();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_W, CTRL | SHIFT), WIN)).isTrue();
        // milik shell, termasuk shortcut menu default tanpa Shift (Ctrl+N host baru, Ctrl+F cari host)
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_W, CTRL), WIN)).isFalse();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_R, CTRL), WIN)).isFalse();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_F, CTRL), WIN)).isFalse();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_N, CTRL), WIN)).isFalse();
        // tidak dipakai aksi mana pun
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_Q, CTRL | SHIFT), WIN)).isFalse();
        // AltGr = Ctrl+Alt
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_P, CTRL | ALT | SHIFT), WIN)).isFalse();
        // Cmd tidak berarti apa-apa di Windows
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_P, CMD | SHIFT), WIN)).isFalse();
    }

    @Test
    void macMemakaiCmdDanCtrlTetapKeTerminal() {
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_P, CMD | SHIFT), MAC)).isTrue();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_R, CMD), MAC)).isTrue(); // reconnect
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_W, CMD), MAC)).isTrue(); // tutup tab
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_T, CMD), MAC)).isTrue(); // tab baru
        // Ctrl di Mac selalu milik terminal
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_P, CTRL | SHIFT), MAC)).isFalse();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_R, CTRL), MAC)).isFalse();
        // Cmd+C/V milik JediTerm; Cmd+Option = karakter khusus
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_C, CMD), MAC)).isFalse();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_P, CMD | ALT | SHIFT), MAC)).isFalse();
    }

    @Test
    void defaultPerOs() {
        assertThat(WIN.get(ShortcutAction.RECONNECT)).isEqualTo(ks(KeyEvent.VK_F5, CTRL));
        assertThat(MAC.get(ShortcutAction.RECONNECT)).isEqualTo(ks(KeyEvent.VK_R, CMD));
        assertThat(WIN.get(ShortcutAction.CLOSE_TAB)).isEqualTo(ks(KeyEvent.VK_W, CTRL | SHIFT));
        assertThat(MAC.get(ShortcutAction.CLOSE_TAB)).isEqualTo(ks(KeyEvent.VK_W, CMD));
        assertThat(MAC.get(ShortcutAction.NEW_TAB)).isEqualTo(ks(KeyEvent.VK_T, CMD));
        assertThat(WIN.get(ShortcutAction.NEW_TAB)).isEqualTo(ks(KeyEvent.VK_T, CTRL | SHIFT));
        assertThat(WIN.get(ShortcutAction.TOGGLE_HOST_LIST)).isEqualTo(ks(KeyEvent.VK_B, CTRL | SHIFT));
        assertThat(MAC.get(ShortcutAction.INJECT_SUDO)).isEqualTo(ks(KeyEvent.VK_P, CMD | SHIFT));
    }

    @Test
    void pindahTabCtrlTabDiKeduaOs() {
        for (var b : new KeyBindings[] {WIN, MAC}) {
            assertThat(b.get(ShortcutAction.NEXT_TAB)).isEqualTo(ks(KeyEvent.VK_TAB, CTRL));
            assertThat(b.get(ShortcutAction.PREVIOUS_TAB)).isEqualTo(ks(KeyEvent.VK_TAB, CTRL | SHIFT));
            assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_TAB, CTRL), b)).isTrue();
            assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_TAB, CTRL | SHIFT), b)).isTrue();
        }
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_TAB, CMD), MAC)).isFalse(); // ⌘Tab = app switcher macOS
    }

    @Test
    void nomorTab() {
        assertThat(WIN.actionFor(ks(KeyEvent.VK_1, CTRL))).contains(ShortcutAction.TAB_1);
        assertThat(MAC.actionFor(ks(KeyEvent.VK_9, CMD)).map(ShortcutAction::tabNumber)).contains(9);
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_5, CTRL), WIN)).isTrue();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_5, CMD), MAC)).isTrue();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_1, CTRL), MAC)).isFalse();
        assertThat(WIN.actionFor(ks(KeyEvent.VK_0, CTRL))).contains(ShortcutAction.ZOOM_RESET);
        assertThat(ShortcutAction.ZOOM_RESET.tabNumber()).isZero();
    }

    @Test
    void shortcutYangDigantiUserSelaluDiambil() {
        // Ctrl+K polos biasanya milik shell; kalau user sendiri memilihnya, TermUL mengambilnya
        var b = WIN.with(ShortcutAction.NEW_HOST, ks(KeyEvent.VK_K, CTRL));
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_K, CTRL), b)).isTrue();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_N, CTRL), b)).isFalse(); // lama: tidak dipakai lagi
        // reconnect dipindah: Ctrl+F5 kembali ke terminal
        b = WIN.with(ShortcutAction.RECONNECT, ks(KeyEvent.VK_F6, CTRL));
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_F5, CTRL), b)).isFalse();
        assertThat(Shortcuts.isAppCombo(ks(KeyEvent.VK_F6, CTRL), b)).isTrue();
    }

    @Test
    void teksShortcut() {
        assertThat(Shortcuts.text(ks(KeyEvent.VK_TAB, CTRL), false)).isEqualTo("Ctrl+Tab");
        assertThat(Shortcuts.text(ks(KeyEvent.VK_TAB, CTRL | SHIFT), true)).isEqualTo("⌃⇧Tab");
        assertThat(Shortcuts.text(ks(KeyEvent.VK_P, CTRL | SHIFT), false)).isEqualTo("Ctrl+Shift+P");
        assertThat(Shortcuts.text(ks(KeyEvent.VK_F5, CTRL), false)).isEqualTo("Ctrl+F5");
        assertThat(Shortcuts.text(ks(KeyEvent.VK_P, CMD | SHIFT), true)).isEqualTo("⇧⌘P");
        assertThat(Shortcuts.text(ks(KeyEvent.VK_EQUALS, CMD), true)).isEqualTo("⌘+");
        assertThat(Shortcuts.text(ks(KeyEvent.VK_N, CMD), true)).isEqualTo("⌘N");
    }

    @Test
    void bindingAktifBisaDiganti() {
        var before = Shortcuts.bindings();
        try {
            Shortcuts.use(KeyBindings.of(Map.of("inject.root", ""), before.mac()));
            assertThat(Shortcuts.get(ShortcutAction.INJECT_ROOT)).isNull();
            assertThat(Shortcuts.text(ShortcutAction.INJECT_ROOT)).isEqualTo("—");
        } finally {
            Shortcuts.use(before);
        }
    }
}

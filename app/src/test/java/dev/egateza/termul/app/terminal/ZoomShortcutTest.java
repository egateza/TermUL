package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.ui.Shortcuts;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class ZoomShortcutTest {

    private static final JPanel SOURCE = new JPanel();
    private static final int MENU = Shortcuts.MENU; // Ctrl, atau Cmd di Mac

    private static Integer zoom(int keyCode, int mods) {
        return TerminalTab.zoomDirection(new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, mods, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    @Test
    void zoomIn() {
        assertThat(zoom(KeyEvent.VK_EQUALS, MENU)).isEqualTo(1);
        assertThat(zoom(KeyEvent.VK_EQUALS, MENU | InputEvent.SHIFT_DOWN_MASK)).isEqualTo(1);
        assertThat(zoom(KeyEvent.VK_PLUS, MENU)).isEqualTo(1);
        assertThat(zoom(KeyEvent.VK_ADD, MENU)).isEqualTo(1);
    }

    @Test
    void zoomOutDanReset() {
        assertThat(zoom(KeyEvent.VK_MINUS, MENU)).isEqualTo(-1);
        assertThat(zoom(KeyEvent.VK_SUBTRACT, MENU)).isEqualTo(-1);
        assertThat(zoom(KeyEvent.VK_0, MENU)).isEqualTo(0);
    }

    @Test
    void kombinasiLainTidakDitangkap() {
        assertThat(zoom(KeyEvent.VK_MINUS, MENU | InputEvent.SHIFT_DOWN_MASK)).isNull(); // Ctrl+_
        assertThat(zoom(KeyEvent.VK_EQUALS, 0)).isNull();
        assertThat(zoom(KeyEvent.VK_EQUALS, MENU | InputEvent.ALT_DOWN_MASK)).isNull();
        assertThat(zoom(KeyEvent.VK_C, MENU)).isNull();
    }

    @Test
    void zoomMengikutiShortcutCustom() {
        var mac = Shortcuts.bindings().mac();
        var custom = dev.egateza.termul.app.ui.KeyBindings.defaults(mac)
                .with(dev.egateza.termul.app.ui.ShortcutAction.ZOOM_IN,
                        javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_UP, MENU | InputEvent.SHIFT_DOWN_MASK));
        assertThat(TerminalTab.zoomDirection(event(KeyEvent.VK_UP, MENU | InputEvent.SHIFT_DOWN_MASK), custom)).isEqualTo(1);
        // varian bawaan perbesar tidak berlaku lagi; perkecil masih bawaan
        assertThat(TerminalTab.zoomDirection(event(KeyEvent.VK_EQUALS, MENU), custom)).isNull();
        assertThat(TerminalTab.zoomDirection(event(KeyEvent.VK_ADD, MENU), custom)).isNull();
        assertThat(TerminalTab.zoomDirection(event(KeyEvent.VK_SUBTRACT, MENU), custom)).isEqualTo(-1);
    }

    private static KeyEvent event(int keyCode, int mods) {
        return new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, mods, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    void ukuranFontDibatasi() {
        var s = new TerminalSettings(14f).copy();
        assertThat(s.setFontSize(100f)).isEqualTo(TerminalSettings.MAX_SIZE);
        assertThat(s.setFontSize(2f)).isEqualTo(TerminalSettings.MIN_SIZE);
        assertThat(s.defaultSize()).isEqualTo(14f);
        assertThat(s.getTerminalFont().getSize()).isEqualTo(8);
    }
}

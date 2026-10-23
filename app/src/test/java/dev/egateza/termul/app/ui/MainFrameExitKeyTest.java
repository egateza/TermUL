package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JPanel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MainFrameExitKeyTest {

    private static KeyEvent key(int id, int mods, int code) {
        return new KeyEvent(new JPanel(), id, 0, mods, code, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    void hanyaAltF4DitekanYangDianggapKeluar() {
        Assumptions.assumeTrue(Shortcuts.exit() != null); // Mac: Cmd+Q lewat menu aplikasi

        assertThat(MainFrame.isExitKey(key(KeyEvent.KEY_PRESSED, InputEvent.ALT_DOWN_MASK, KeyEvent.VK_F4))).isTrue();
        assertThat(MainFrame.isExitKey(key(KeyEvent.KEY_RELEASED, InputEvent.ALT_DOWN_MASK, KeyEvent.VK_F4))).isFalse();
        assertThat(MainFrame.isExitKey(key(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_F4))).isFalse();
        assertThat(MainFrame.isExitKey(key(KeyEvent.KEY_PRESSED, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_F4))).isFalse();
        assertThat(MainFrame.isExitKey(key(KeyEvent.KEY_PRESSED, InputEvent.ALT_DOWN_MASK, KeyEvent.VK_ALT))).isFalse();
    }
}

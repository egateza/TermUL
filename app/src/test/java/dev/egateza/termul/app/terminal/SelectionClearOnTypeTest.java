package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SelectionClearOnTypeTest {

    @Test
    void mengetikMenghapusSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters("user@host:~$ ls");
            panel.selectAll();
            assertThat(panel.getSelection()).isNotNull();

            panel.processKeyEvent(typed(panel, 'a', 0));

            assertThat(panel.getSelection()).isNull();
        });
    }

    @Test
    void shortcutCmdTidakMenghapusSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters("user@host:~$ ls");
            panel.selectAll();

            panel.processKeyEvent(typed(panel, 'c', InputEvent.META_DOWN_MASK));

            assertThat(panel.getSelection()).isNotNull();
        });
    }

    private static KeyEvent typed(java.awt.Component source, char c, int modifiers) {
        return new KeyEvent(source, KeyEvent.KEY_TYPED, System.currentTimeMillis(), modifiers,
                KeyEvent.VK_UNDEFINED, c);
    }
}

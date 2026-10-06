package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jediterm.terminal.ui.TerminalPanel;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ShiftClickSelectionTest {

    @Test
    void shiftKlikMemperluasSelectionDariTitikAwalnya() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters("user@host:~$ ls -la /var/log");
            press(panel, x(widget, 2), 0);
            drag(panel, x(widget, 6), 0, 0);
            release(panel, x(widget, 6), 0);
            var start = panel.getSelection().getStart();

            press(panel, x(widget, 20), InputEvent.SHIFT_DOWN_MASK);

            var selection = panel.getSelection();
            assertThat(selection.getStart()).isEqualTo(start);
            assertThat(selection.getEnd().x).isEqualTo(20);
        });
    }

    @Test
    void shiftKlikTanpaSelectionMemilihDariKlikTerakhir() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters("user@host:~$ ls -la /var/log");
            press(panel, x(widget, 3), 0);
            release(panel, x(widget, 3), 0);
            assertThat(panel.getSelection()).isNull();

            press(panel, x(widget, 12), InputEvent.SHIFT_DOWN_MASK);

            assertThat(panel.getSelection().getStart().x).isEqualTo(3);
            assertThat(panel.getSelection().getEnd().x).isEqualTo(12);
        });
    }

    @Test
    void shiftDragMelanjutkanSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters("user@host:~$ ls -la /var/log");
            press(panel, x(widget, 1), 0);
            drag(panel, x(widget, 4), 0, 0);

            drag(panel, x(widget, 15), 0, InputEvent.SHIFT_DOWN_MASK);

            assertThat(panel.getSelection().getStart().x).isEqualTo(1);
            assertThat(panel.getSelection().getEnd().x).isEqualTo(15);
        });
    }

    /** Tengah kolom {@code column} (inset kiri JediTerm 4px). */
    private static int x(ZoomableTermWidget widget, int column) {
        int w = widget.charSize().width;
        return 4 + column * w + w / 2;
    }

    private static void press(TerminalPanel panel, int x, int modifiers) {
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                modifiers | InputEvent.BUTTON1_DOWN_MASK, x, 5, 1, false, MouseEvent.BUTTON1));
    }

    private static void drag(TerminalPanel panel, int x, int y, int modifiers) {
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                modifiers | InputEvent.BUTTON1_DOWN_MASK, x, y + 5, 0, false, MouseEvent.NOBUTTON));
    }

    private static void release(TerminalPanel panel, int x, int modifiers) {
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                modifiers, x, 5, 1, false, MouseEvent.BUTTON1));
    }
}

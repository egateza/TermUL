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

    @Test
    void klikDenganMouseBergeserSedikitTidakMembuatSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters("user@host:~$ ls -la /var/log");
            press(panel, x(widget, 5), 0);
            drag(panel, x(widget, 5) + 1, 1, 0); // jitter 1px, masih di sel yang sama
            release(panel, x(widget, 5) + 1, 0);

            assertThat(panel.getSelection()).isNull();
        });
    }

    @Test
    void dragKeSelLainTetapMemilihDariTitikKlik() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters("user@host:~$ ls -la /var/log");
            press(panel, x(widget, 5), 0);
            drag(panel, x(widget, 5) + 1, 0, 0);
            drag(panel, x(widget, 9), 0, 0);

            assertThat(panel.getSelection().getStart().x).isEqualTo(5);
            assertThat(panel.getSelection().getEnd().x).isEqualTo(9);
        });
    }

    @Test
    void dragKlikKananTidakMembuatSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters("user@host:~$ ls -la /var/log");
            press(panel, x(widget, 2), 0);
            release(panel, x(widget, 2), 0);

            panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                    InputEvent.BUTTON3_DOWN_MASK, x(widget, 12), 5, 0, false, MouseEvent.NOBUTTON));

            assertThat(panel.getSelection()).isNull();
        });
    }

    // "ls " 0-2, "dashboard" 3-11, "." 12, "pelangi" 13-19, "." 20, "co" 21-22, "." 23, "id" 24-25, "-" 26, "le" 27-28
    private static final String FILE_LINE = "ls dashboard.pelangi.co.id-le-ssl.conf";

    @Test
    void doubleClickHanyaMemilihSatuKata() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters(FILE_LINE);

            doubleClick(panel, x(widget, 5));

            assertThat(panel.getSelection().getStart().x).isEqualTo(3);
            assertThat(panel.getSelection().getEnd().x).isEqualTo(11);
        });
    }

    @Test
    void dragSetelahDoubleClickMemperluasPerKata() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters(FILE_LINE);
            clickSequence(panel, x(widget, 5), 1);
            press(panel, x(widget, 5), 0, 2);

            drag(panel, x(widget, 15), 0, 0); // di tengah "pelangi"

            assertThat(panel.getSelection().getStart().x).isEqualTo(3);
            assertThat(panel.getSelection().getEnd().x).isEqualTo(19);

            drag(panel, x(widget, 1), 0, 0); // mundur ke "ls": "dashboard" tetap terpilih utuh
            assertThat(panel.getSelection().getStart().x).isEqualTo(11);
            assertThat(panel.getSelection().getEnd().x).isEqualTo(0);
        });
    }

    @Test
    void shiftKlikSetelahDoubleClickMemperluasPerKata() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters(FILE_LINE);
            doubleClick(panel, x(widget, 5));

            press(panel, x(widget, 21), InputEvent.SHIFT_DOWN_MASK);

            assertThat(panel.getSelection().getStart().x).isEqualTo(3);
            assertThat(panel.getSelection().getEnd().x).isEqualTo(22);
        });
    }

    private static void doubleClick(TerminalPanel panel, int x) {
        clickSequence(panel, x, 1);
        clickSequence(panel, x, 2);
    }

    private static void clickSequence(TerminalPanel panel, int x, int count) {
        press(panel, x, 0, count);
        for (int id : new int[] {MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED}) {
            panel.dispatchEvent(new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, 5, count, false,
                    MouseEvent.BUTTON1));
        }
    }

    private static void press(TerminalPanel panel, int x, int modifiers, int count) {
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                modifiers | InputEvent.BUTTON1_DOWN_MASK, x, 5, count, false, MouseEvent.BUTTON1));
    }

    /** Tengah kolom {@code column} (inset kiri JediTerm 4px). */
    private static int x(ZoomableTermWidget widget, int column) {
        int w = widget.charSize().width;
        return 4 + column * w + w / 2;
    }

    private static void press(TerminalPanel panel, int x, int modifiers) {
        press(panel, x, modifiers, 1);
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

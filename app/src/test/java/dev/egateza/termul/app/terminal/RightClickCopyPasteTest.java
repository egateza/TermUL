package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jediterm.terminal.TerminalCopyPasteHandler;
import com.jediterm.terminal.ui.TerminalPanel;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class RightClickCopyPasteTest {

    /** Clipboard palsu supaya test tidak menyentuh clipboard sistem (dan bisa headless). */
    private static final class FakeClipboard implements TerminalCopyPasteHandler {
        String contents;

        @Override
        public void setContents(String text, boolean useSystemSelectionClipboardIfAvailable) {
            contents = text;
        }

        @Override
        public String getContents(boolean useSystemSelectionClipboardIfAvailable) {
            return contents;
        }
    }

    @Test
    void klikKananDenganSelectionMengcopyLaluMenghapusSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var clipboard = new FakeClipboard();
            var widget = widget(true, clipboard);
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters("user@host:~$ ls -la");
            select(panel, x(widget, 0), x(widget, 4));
            assertThat(panel.getSelection()).isNotNull();

            rightClick(panel, x(widget, 2), 0);

            assertThat(clipboard.contents).isEqualTo("user@");
            assertThat(panel.getSelection()).isNull();
        });
    }

    @Test
    void klikKananTanpaSelectionMelakukanPasteLewatFilter() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var clipboard = new FakeClipboard();
            clipboard.contents = "echo hai";
            var widget = widget(true, clipboard);
            List<String> pasted = new ArrayList<>();
            widget.setPasteFilter(text -> {
                pasted.add(text);
                return null; // dibatalkan: test tidak punya koneksi
            });

            rightClick(widget.getTerminalPanel(), x(widget, 2), 0);

            assertThat(pasted).containsExactly("echo hai");
        });
    }

    @Test
    void modeMenuTidakMengcopyAtauPaste() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var clipboard = new FakeClipboard();
            var widget = widget(false, clipboard);
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters("user@host:~$ ls -la");
            select(panel, x(widget, 0), x(widget, 4));

            // tanpa MOUSE_CLICKED: menu JediTerm butuh panel yang tampil di layar
            dispatch(panel, MouseEvent.MOUSE_PRESSED, x(widget, 2), 0);
            dispatch(panel, MouseEvent.MOUSE_RELEASED, x(widget, 2), 0);

            assertThat(clipboard.contents).isNull();
            assertThat(panel.getSelection()).isNotNull();
        });
    }

    @Test
    void shiftKlikKananTidakMengcopy() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var clipboard = new FakeClipboard();
            var widget = widget(true, clipboard);
            var panel = widget.getTerminalPanel();
            widget.getTerminal().writeCharacters("user@host:~$ ls -la");
            select(panel, x(widget, 0), x(widget, 4));

            dispatch(panel, MouseEvent.MOUSE_PRESSED, x(widget, 2), InputEvent.SHIFT_DOWN_MASK);

            assertThat(clipboard.contents).isNull();
            assertThat(panel.getSelection()).isNotNull();
        });
    }

    @Test
    void pengaturanBerlakuUntukSemuaSalinanTab() {
        var settings = new TerminalSettings(14f);
        var tab = settings.copy();

        settings.setRightClickCopyPaste(true);

        assertThat(tab.rightClickCopyPaste()).isTrue();
    }

    private static ZoomableTermWidget widget(boolean copyPaste, FakeClipboard clipboard) {
        var settings = new TerminalSettings(14f);
        settings.setRightClickCopyPaste(copyPaste);
        var widget = new ZoomableTermWidget(settings);
        widget.useClipboard(clipboard);
        return widget;
    }

    /** Tengah kolom {@code column} (inset kiri JediTerm 4px). */
    private static int x(ZoomableTermWidget widget, int column) {
        int w = widget.charSize().width;
        return 4 + column * w + w / 2;
    }

    /** Selection dari titik {@code fromX} sampai {@code toX} (kolom keduanya ikut), lewat press + drag kiri. */
    private static void select(TerminalPanel panel, int fromX, int toX) {
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                InputEvent.BUTTON1_DOWN_MASK, fromX, 5, 1, false, MouseEvent.BUTTON1));
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                InputEvent.BUTTON1_DOWN_MASK, toX, 5, 0, false, MouseEvent.NOBUTTON));
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, toX, 5, 1, false, MouseEvent.BUTTON1));
    }

    private static void rightClick(TerminalPanel panel, int x, int modifiers) {
        dispatch(panel, MouseEvent.MOUSE_PRESSED, x, modifiers);
        dispatch(panel, MouseEvent.MOUSE_RELEASED, x, modifiers);
        dispatch(panel, MouseEvent.MOUSE_CLICKED, x, modifiers);
    }

    private static void dispatch(TerminalPanel panel, int id, int x, int modifiers) {
        int down = id == MouseEvent.MOUSE_PRESSED ? InputEvent.BUTTON3_DOWN_MASK : 0;
        panel.dispatchEvent(new MouseEvent(panel, id, System.currentTimeMillis(), modifiers | down, x, 5, 1, false,
                MouseEvent.BUTTON3));
    }
}

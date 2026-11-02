package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jediterm.terminal.ArrayTerminalDataStream;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.model.TerminalTextBuffer;
import org.junit.jupiter.api.Test;

class XtermCursorTerminalTest {

    private final TerminalTextBuffer buffer = new TerminalTextBuffer(40, 10, new StyleState(), 100);
    private final JediTerminal terminal = new XtermCursorTerminal(new NoDisplay(), buffer, new StyleState());

    private void feed(String data) throws Exception {
        var emulator = new JediEmulator(new ArrayTerminalDataStream(data.toCharArray()), terminal);
        while (emulator.hasNext()) {
            emulator.next();
        }
    }

    @Test
    void fieldJediTermMasihAda() {
        assertThat(XtermCursorTerminal.supported()).isTrue();
    }

    /** Urutan dari nano (ncurses): simpan kursor di area edit (baris 3) selama layar alternate aktif. */
    @Test
    void keluarDariNanoKembaliKePrompt() throws Exception {
        feed("\u001b[8;12H");                       // prompt di baris 8, kolom 12
        feed("\u001b[?1049h\u001b[H\u001b[2J");    // smcup + clear
        feed("\u001b[3;1H\u001b7\u001b[3;9r\u001b8"); // sc; csr; rc
        feed("\u001b[?1049l");                     // rmcup

        assertThat(buffer.isUsingAlternateBuffer()).isFalse();
        assertThat(terminal.getCursorY()).isEqualTo(8);
        assertThat(terminal.getCursorX()).isEqualTo(12);
    }

    @Test
    void decrcDiLayarUtamaTidakMemakaiSlotLayarAlternate() throws Exception {
        feed("\u001b[2;5H\u001b7");                 // DECSC di layar utama
        feed("\u001b[6;1H\u001b[?1049h\u001b[3;1H\u001b7\u001b[?1049l");
        feed("\u001b8");                            // DECRC: slot layar utama hasil ?1049h (baris 6)

        assertThat(terminal.getCursorY()).isEqualTo(6);
        assertThat(terminal.getCursorX()).isEqualTo(1);
    }

    @Test
    void decrcDiLayarAlternateTetapBekerja() throws Exception {
        feed("\u001b[?1049h\u001b[4;7H\u001b7\u001b[1;1H\u001b8");

        assertThat(terminal.getCursorY()).isEqualTo(4);
        assertThat(terminal.getCursorX()).isEqualTo(7);
    }

    @Test
    void layarAlternateTanpaRestoreKursorTidakMemindahkanKursor() throws Exception {
        feed("\u001b[8;3H\u001b[?1047h\u001b[2;2H\u001b[?1047l");

        assertThat(terminal.getCursorY()).isEqualTo(2);
        assertThat(terminal.getCursorX()).isEqualTo(2);
    }

    private static final class NoDisplay implements TerminalDisplay {
        @Override public void setCursor(int x, int y) { }
        @Override public void setCursorShape(CursorShape cursorShape) { }
        @Override public void beep() { }
        @Override public void scrollArea(int scrollRegionTop, int scrollRegionSize, int dy) { }
        @Override public void setCursorVisible(boolean isCursorVisible) { }
        @Override public void useAlternateScreenBuffer(boolean useAlternateScreenBuffer) { }
        @Override public String getWindowTitle() { return ""; }
        @Override public void setWindowTitle(String windowTitle) { }
        @Override public TerminalSelection getSelection() { return null; }
        @Override public void terminalMouseModeSet(MouseMode mouseMode) { }
        @Override public void setMouseFormat(MouseFormat mouseFormat) { }
        @Override public boolean ambiguousCharsAreDoubleWidth() { return false; }
    }
}

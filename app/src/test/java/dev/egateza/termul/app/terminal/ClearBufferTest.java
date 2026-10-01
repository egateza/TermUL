package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jediterm.core.TerminalCoordinates;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ClearBufferTest {

    @Test
    void clearBufferMempertahankanBarisPromptDenganCursorDiBarisPertama() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var terminal = widget.getTerminal();
            for (int i = 0; i < 5; i++) {
                terminal.writeCharacters("baris " + i);
                terminal.carriageReturn();
                terminal.newLine();
            }
            terminal.writeCharacters("ega@host:~$ ");
            var coords = (TerminalCoordinates) terminal;
            assertThat(coords.getY()).isEqualTo(6);

            widget.getTerminalPanel().clearBuffer();

            assertThat(coords.getY()).isEqualTo(1); // JediTerm 3.76 tanpa perbaikan: 0 → getLine(-1)
            assertThat(widget.getTerminalTextBuffer().getLine(0).getText()).startsWith("ega@host:~$");
            terminal.writeCharacters("ls");
            assertThat(widget.getTerminalTextBuffer().getLine(0).getText()).startsWith("ega@host:~$ ls");
        });
    }
}

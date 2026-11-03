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
            terminal.writeCharacters("user@host:~$ ");
            var coords = (TerminalCoordinates) terminal;
            assertThat(coords.getY()).isEqualTo(6);

            widget.getTerminalPanel().clearBuffer();

            assertThat(coords.getY()).isEqualTo(1); // JediTerm 3.76 tanpa perbaikan: 0 → getLine(-1)
            assertThat(widget.getTerminalTextBuffer().getLine(0).getText()).startsWith("user@host:~$");
            terminal.writeCharacters("ls");
            assertThat(widget.getTerminalTextBuffer().getLine(0).getText()).startsWith("user@host:~$ ls");
        });
    }

    /**
     * Clear Buffer mengubah storage layar ({@code clearScreenBuffer}, {@code addLine}); itu harus terjadi selagi lock
     * buffer dipegang, supaya thread emulator tidak menulis ke storage (ArrayDeque) yang sama pada saat bersamaan.
     * Tanpa itu storage bisa rusak dan setiap paint gagal dengan NPE.
     */
    @Test
    void storageLayarDiubahSelagiLockBufferDipegang() throws Exception {
        var freeDuringChange = new java.util.concurrent.atomic.AtomicBoolean();
        var changes = new java.util.concurrent.atomic.AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var terminal = widget.getTerminal();
            for (int i = 0; i < 3; i++) {
                terminal.writeCharacters("baris " + i);
                terminal.carriageReturn();
                terminal.newLine();
            }
            terminal.writeCharacters("user@host:~$ ");
            var buffer = widget.getTerminalTextBuffer();
            buffer.addModelListener(() -> { // dipanggil saat storage layar berubah
                changes.incrementAndGet();
                var other = new Thread(() -> { // seperti thread emulator yang mau menulis
                    if (buffer.tryLock()) {
                        freeDuringChange.set(true);
                        buffer.unlock();
                    }
                });
                other.start();
                try {
                    other.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            widget.getTerminalPanel().clearBuffer();
        });

        assertThat(changes).hasPositiveValue();
        assertThat(freeDuringChange).as("thread lain bisa masuk selagi storage diubah").isFalse();
    }
}

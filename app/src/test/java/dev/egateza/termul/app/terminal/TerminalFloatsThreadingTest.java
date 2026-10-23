package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.ui.fab.FloatingOverlay;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TerminalFloatsThreadingTest {

    /**
     * Output yang menggulung layar memanggil listener seleksi JediTerm dari thread emulator sambil memegang lock buffer.
     * Toolbar tidak boleh diubah di thread itu (deadlock dengan EDT yang sedang paint), tapi diantrekan ke EDT.
     */
    @Test
    void scrollDariThreadEmulatorMemperbaruiToolbarDiEdt() throws Exception {
        var ref = new AtomicReference<TerminalFloats>();
        var widgetRef = new AtomicReference<ZoomableTermWidget>();
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            widget.getTerminal().writeCharacters("user@host:~$ ls");
            var floats = new TerminalFloats(widget, new FloatingOverlay(), () -> { }, null);
            widget.getTerminalPanel().selectAll();
            assertThat(floats.toolbar.isVisible()).isTrue(); // Cari/Salin tampil untuk seleksi
            ref.set(floats);
            widgetRef.set(widget);
        });
        var floats = ref.get();
        var terminal = widgetRef.get().getTerminal();

        var edtBusy = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            edtBusy.countDown();
            await(release);
        });
        assertThat(edtBusy.await(5, TimeUnit.SECONDS)).isTrue();

        // seperti thread emulator: baris baru sampai layar menggulung (scrollArea -> seleksi dihapus)
        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < terminal.getTerminalHeight() + 2; i++) {
                terminal.newLine();
            }
        }).get(5, TimeUnit.SECONDS);

        assertThat(floats.toolbar.isVisible()).as("belum diubah selama EDT sibuk").isTrue();
        release.countDown();
        SwingUtilities.invokeAndWait(() -> assertThat(floats.toolbar.isVisible()).isFalse());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

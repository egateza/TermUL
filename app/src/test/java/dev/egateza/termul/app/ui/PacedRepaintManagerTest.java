package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JPanel;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class PacedRepaintManagerTest {

    private final AtomicLong now = new AtomicLong(1_000);

    @Test
    void repaintSegeraSetelahGambarDitahanDanDigabung() throws Exception {
        var manager = new PacedRepaintManager(now::get, c -> true);
        var panel = new JPanel();
        SwingUtilities.invokeAndWait(() -> {
            manager.addDirtyRegion(panel, 0, 0, 10, 10); // belum pernah menggambar: langsung
            assertThat(manager.heldCount()).isZero();

            manager.paintDirtyRegions();
            now.addAndGet(5);
            manager.addDirtyRegion(panel, 0, 0, 10, 10);
            manager.addDirtyRegion(panel, 20, 20, 10, 10);
            assertThat(manager.heldCount()).as("satu komponen, area digabung").isEqualTo(1);

            now.addAndGet(PacedRepaintManager.INTERVAL_MS);
            manager.addDirtyRegion(new JPanel(), 0, 0, 5, 5); // jeda sudah lewat: langsung
            assertThat(manager.heldCount()).isEqualTo(1);
        });

        Thread.sleep(PacedRepaintManager.INTERVAL_MS * 4);
        SwingUtilities.invokeAndWait(() -> assertThat(manager.heldCount()).as("diteruskan oleh timer").isZero());
    }

    @Test
    void windowSolidTidakDibatasi() throws Exception {
        var manager = new PacedRepaintManager(now::get, c -> false);
        SwingUtilities.invokeAndWait(() -> {
            manager.paintDirtyRegions();
            manager.addDirtyRegion(new JPanel(), 0, 0, 10, 10);
            assertThat(manager.heldCount()).isZero();
        });
    }

    @Test
    void paintNowTidakDitahan() throws Exception {
        var manager = new PacedRepaintManager(now::get, c -> true);
        var panel = new JPanel();
        SwingUtilities.invokeAndWait(() -> {
            var previous = RepaintManager.currentManager(panel);
            RepaintManager.setCurrentManager(manager);
            try {
                manager.paintDirtyRegions();
                manager.addDirtyRegion(panel, 0, 0, 10, 10);
                assertThat(manager.heldCount()).isEqualTo(1);

                PacedRepaintManager.paintNow(panel);

                assertThat(manager.heldCount()).as("yang ditahan ikut dikirim sekarang").isZero();
                manager.addDirtyRegion(panel, 0, 0, 10, 10);
                assertThat(manager.heldCount()).as("hanya selama paintNow").isEqualTo(1);
            } finally {
                RepaintManager.setCurrentManager(previous);
            }
        });
    }
}

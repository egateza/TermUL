package dev.egateza.termul.app.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ActivityBarTest {

    @Test
    void reportShowsLatestMessageWithLevelSymbol() throws Exception {
        var bar = new ActivityBar();

        bar.report(ActivityBar.Level.INFO, "Mengupload a.txt ...");
        bar.report(ActivityBar.Level.SUCCESS, "a.txt berhasil diupload");
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(bar.text()).contains("a.txt berhasil diupload").contains("\u2714");
    }

    @Test
    void progressReplacesTextButIsNotKeptInHistory() throws Exception {
        var bar = new ActivityBar();

        bar.report(ActivityBar.Level.ERROR, "Upload gagal");
        bar.progress("Mencoba lagi dalam 15 dtk");
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(bar.text()).contains("Mencoba lagi dalam 15 dtk");
        assertThat(bar.historySize()).isEqualTo(1);
    }

    @Test
    void longMessageDoesNotWidenMinimumOrPreferredSize() throws Exception {
        var bar = new ActivityBar();
        SwingUtilities.invokeAndWait(() -> { });
        int minBefore = bar.getMinimumSize().width;
        int prefBefore = bar.getPreferredSize().width;

        bar.report(ActivityBar.Level.ERROR, "Gagal menghapus: " + "/sangat/panjang".repeat(40) + " (akses ditolak)");
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(bar.getMinimumSize().width).isLessThanOrEqualTo(minBefore + 1);
        assertThat(bar.getPreferredSize().width).isLessThan(200).isGreaterThanOrEqualTo(prefBefore);
    }

    @Test
    void longMessageWrapsToPanelWidth() throws Exception {
        var bar = new ActivityBar();
        bar.report(ActivityBar.Level.INFO, "ok");
        SwingUtilities.invokeAndWait(() -> { });
        int oneLine = bar.getPreferredSize().height;

        bar.report(ActivityBar.Level.ERROR, "Gagal menghapus file yang sangat panjang ".repeat(20));
        SwingUtilities.invokeAndWait(() -> {
            bar.setSize(300, 100);
            bar.doLayout();
        });

        assertThat(bar.getPreferredSize().height).isGreaterThan(oneLine);
    }
}

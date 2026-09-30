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
}

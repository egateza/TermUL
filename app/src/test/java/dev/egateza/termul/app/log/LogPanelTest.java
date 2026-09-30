package dev.egateza.termul.app.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LogPanelTest {

    private final LogBuffer buffer = new LogBuffer(3);
    private final LogPanel panel = new LogPanel(buffer, Path.of("logs"), Runnable::run, () -> { });

    @Test
    void pollAppendsOnlyNewEntries() {
        buffer.append(false, "satu");
        panel.poll();
        buffer.append(true, "dua");
        panel.poll();
        panel.poll();

        assertThat(panel.displayedText()).isEqualTo("satu\ndua\n");
    }

    @Test
    void reportsEntriesDroppedBetweenPolls() {
        buffer.append(false, "a");
        panel.poll();
        for (int i = 0; i < 5; i++) {
            buffer.append(false, "x" + i);
        }
        panel.poll();

        assertThat(panel.displayedText())
                .isEqualTo("a\n… 2 baris log terlewat (lihat file log)\nx2\nx3\nx4\n");
    }
}

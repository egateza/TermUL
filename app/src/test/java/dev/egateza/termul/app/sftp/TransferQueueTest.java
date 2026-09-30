package dev.egateza.termul.app.sftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.egateza.termul.sftp.RemoteFileException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TransferQueueTest {

    private final TransferQueue queue = new TransferQueue("test");
    private final List<String> events = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {
        queue.shutdown();
    }

    @Test
    void jobBerjalanBerurutan() {
        for (int i = 1; i <= 3; i++) {
            int n = i;
            queue.submit("job" + n, l -> {
                events.add("start" + n);
                l.progress(10, 10);
                events.add("end" + n);
            }, () -> events.add("done" + n), e -> events.add("error" + n));
        }

        await().atMost(Duration.ofSeconds(5)).until(() -> events.contains("done3"));
        assertThat(events.stream().filter(e -> e.startsWith("start") || e.startsWith("end")))
                .containsExactly("start1", "end1", "start2", "end2", "start3", "end3");
    }

    @Test
    void errorDiteruskanDanCancelDiam() throws Exception {
        queue.submit("gagal", l -> {
            throw new RemoteFileException("rusak");
        }, () -> events.add("done-gagal"), e -> events.add("error:" + e.getMessage()));
        queue.submit("batal", l -> {
            throw new RemoteFileException.Cancelled();
        }, () -> events.add("done-batal"), e -> events.add("error-batal"));
        var latch = new CountDownLatch(1);
        queue.submit("penanda", l -> { }, latch::countDown, e -> { });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(2)).until(() -> events.contains("error:rusak"));
        assertThat(events).containsExactly("error:rusak");
    }
}

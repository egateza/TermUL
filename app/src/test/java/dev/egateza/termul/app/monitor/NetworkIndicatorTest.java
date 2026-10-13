package dev.egateza.termul.app.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NetworkIndicatorTest {

    private static final Instant AT = Instant.parse("2026-10-13T07:00:00Z");

    private static NetworkProbe.Status online(long ms) {
        return new NetworkProbe.Status.Online("1.1.1.1:443", Duration.ofMillis(ms), AT);
    }

    @Test
    void warnaMengikutiStatusDanLatensi() {
        assertThat(NetworkIndicator.color(null)).isEqualTo(NetworkIndicator.GRAY);
        assertThat(NetworkIndicator.color(online(25))).isEqualTo(NetworkIndicator.GREEN);
        assertThat(NetworkIndicator.color(online(NetworkIndicator.SLOW_MS))).isEqualTo(NetworkIndicator.YELLOW);
        assertThat(NetworkIndicator.color(new NetworkProbe.Status.Offline("1.1.1.1:443", AT)))
                .isEqualTo(NetworkIndicator.RED);
    }

    @Test
    void teksDanTooltip() {
        assertThat(NetworkIndicator.text(online(25))).isEqualTo("25 ms");
        assertThat(NetworkIndicator.text(new NetworkProbe.Status.Offline("x", AT))).isEqualTo("Offline");
        assertThat(NetworkIndicator.tooltip(online(25))).startsWith("<html>").contains("1.1.1.1:443", "25 ms");
        assertThat(NetworkIndicator.tooltip(online(800))).contains("<br>", "800 ms");
        assertThat(NetworkIndicator.tooltip(new NetworkProbe.Status.Offline("1.1.1.1:443, 8.8.8.8:443", AT)))
                .contains("8.8.8.8:443", String.valueOf(NetworkIndicator.OFFLINE_PERIOD_MS / 1000));
    }

    @Test
    void tidakMemeriksaSelamaBelumTerpasang() {
        var calls = new AtomicInteger();
        var probe = new NetworkProbe(List.of(NetworkProbe.TARGETS.getFirst()), 100,
                (address, timeout) -> calls.incrementAndGet(), Clock.systemUTC());
        var indicator = new NetworkIndicator(probe);

        indicator.checkNow();

        assertThat(calls).hasValue(0);
        assertThat(indicator.status()).isNull();
        assertThat(indicator.getText()).isEqualTo(NetworkIndicator.text(null));
    }
}

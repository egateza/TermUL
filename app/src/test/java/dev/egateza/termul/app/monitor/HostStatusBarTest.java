package dev.egateza.termul.app.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class HostStatusBarTest {

    private static final ZonedDateTime TIME = ZonedDateTime.of(2026, 10, 1, 14, 23, 5, 0, ZoneId.of("Asia/Jakarta"));

    @Test
    void jamBahasaIndonesia() {
        assertThat(HostStatusBar.clock(TIME, Locale.forLanguageTag("id")))
                .startsWith("Kamis, 01 Okt 2026  14:23:05");
    }

    @Test
    void jamBahasaInggris() {
        assertThat(HostStatusBar.clock(TIME, Locale.ENGLISH)).startsWith("Thursday, 01 Oct 2026  14:23:05");
    }

    @Test
    void zonaDenganOffset() {
        assertThat(HostStatusBar.zone(TIME)).isEqualTo("Asia/Jakarta (UTC+07:00)");
        assertThat(HostStatusBar.zone(TIME.withZoneSameInstant(ZoneId.of("UTC")))).isEqualTo("UTC (UTC+00:00)");
    }
}

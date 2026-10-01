package dev.egateza.termul.app.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.i18n.I18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResourceMonitorTest {

    @AfterEach
    void reset() {
        I18n.use(I18n.FALLBACK);
    }

    @Test
    void formatPersen() {
        assertThat(ResourceMonitor.percent(0.074)).isEqualTo("7%");
        assertThat(ResourceMonitor.percent(1.0)).isEqualTo("100%");
        assertThat(ResourceMonitor.percent(Double.NaN)).isEqualTo("–");
    }

    @Test
    void formatUkuran() {
        assertThat(ResourceMonitor.size(123L * 1024 * 1024)).isEqualTo("123 MB");
        assertThat(ResourceMonitor.size(16L * 1024 * 1024 * 1024)).isEqualTo("16.0 GB");
        assertThat(ResourceMonitor.size(-1)).isEqualTo("–");
    }

    @Test
    void tooltipMenyebutBatasXmx() {
        I18n.use("id");
        var s = new JvmSample(0.05, 0.2, 100L << 20, 200L << 20, 512L << 20, 40L << 20, 42, 7, 30,
                16L << 30, 8L << 30);

        String tip = ResourceMonitor.tooltip(s);

        assertThat(tip).startsWith("<html>").contains("5%", "100 MB / 512 MB", "-Xmx", "42", "RAM fisik");
        assertThat(ResourceMonitor.tooltip(null)).isEqualTo(I18n.t("monitor.tooltip.waiting"));
    }

    @Test
    void tooltipTanpaDataCpuTetapAman() {
        var s = new JvmSample(Double.NaN, Double.NaN, 10L << 20, 20L << 20, -1, 0, 3, 0, 0, -1, -1);

        assertThat(ResourceMonitor.tooltip(s)).contains("–").doesNotContain("RAM fisik").doesNotContain("Physical");
    }
}

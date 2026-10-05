package dev.egateza.termul.app.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.monitor.HostStatusPanel.Mode;
import dev.egateza.termul.core.config.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HostStatusPanelTest {

    private ResourceMonitor monitor;
    private HostStatusPanel panel;

    @BeforeEach
    void setUp() {
        monitor = new ResourceMonitor(Runnable::run);
        panel = new HostStatusPanel(monitor, () -> { });
    }

    @AfterEach
    void tearDown() {
        monitor.close();
    }

    @Test
    void defaultSemua() {
        assertThat(panel.mode()).isEqualTo(Mode.ALL);
        assertThat(panel.showsGraph()).isTrue();
        assertThat(panel.showsThreads()).isTrue();
        assertThat(panel.showsPercentText()).isFalse(); // persen sudah tertulis di dalam grafik
    }

    @Test
    void grafikSaja() {
        panel.setMode(Mode.GRAPH);
        assertThat(panel.showsGraph()).isTrue();
        assertThat(panel.showsPercentText()).isFalse();
        assertThat(panel.showsThreads()).isFalse();
    }

    @Test
    void teksSaja() {
        panel.setMode(Mode.TEXT);
        assertThat(panel.showsGraph()).isFalse();
        assertThat(panel.showsPercentText()).isTrue();
        assertThat(panel.showsThreads()).isTrue();
    }

    @Test
    void pemisahDiUjungKananDiSemuaMode() {
        for (var mode : Mode.values()) {
            panel.setMode(mode);
            var parts = panel.getComponents();
            assertThat(parts[parts.length - 2]).as("mode %s", mode).isInstanceOf(javax.swing.JSeparator.class);
            assertThat(((javax.swing.JSeparator) parts[parts.length - 2]).getOrientation())
                    .isEqualTo(javax.swing.SwingConstants.VERTICAL);
        }
    }

    @Test
    void teksSajaLebihSempitDariSemua() {
        int all = panel.getPreferredSize().width;
        panel.setMode(Mode.TEXT);
        assertThat(panel.getPreferredSize().width).isLessThan(all);
        assertThat(panel.getMaximumSize()).isEqualTo(panel.getPreferredSize());
    }

    @Test
    void idCocokDenganConfigDanPunyaLabel() {
        assertThat(Mode.fromId(AppConfig.HOST_STATUS_ALL)).isEqualTo(Mode.ALL);
        assertThat(Mode.fromId(AppConfig.HOST_STATUS_GRAPH)).isEqualTo(Mode.GRAPH);
        assertThat(Mode.fromId(AppConfig.HOST_STATUS_TEXT)).isEqualTo(Mode.TEXT);
        assertThat(Mode.fromId("aneh")).isEqualTo(Mode.ALL);
        for (var mode : Mode.values()) {
            for (var language : I18n.SUPPORTED) {
                String key = "main.menu.settings.hostStatus.mode." + mode.id();
                assertThat(I18n.tIn(language.tag(), key)).isNotEqualTo(key);
            }
        }
    }
}

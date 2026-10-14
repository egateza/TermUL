package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigStreakWidgetTest {

    @TempDir
    Path tmp;

    @Test
    void defaultDanConfigLamaTidakTampilTanpaPosisi() throws Exception {
        Path file = tmp.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16, \"tray\": {\"enabled\": true}}");
        var store = new ConfigStore(file);
        store.load();

        assertThat(store.current().tray().enabled()).as("config lama tetap terbaca utuh").isTrue();
        assertThat(store.current().streakWidget()).isEqualTo(StreakWidgetConfig.defaults());
        assertThat(store.current().streakWidget().hasPosition()).isFalse();
    }

    @Test
    void posisiSetengahTerisiDianggapBelumAda() {
        var config = new StreakWidgetConfig(true, 10, null);
        assertThat(config.hasPosition()).isFalse();
        assertThat(config.x()).isNull();
    }

    @Test
    void tampilDanPosisiTersimpanDanTidakHilangSaatFieldLainDiubah() {
        Path file = tmp.resolve("config.json");
        var store = new ConfigStore(file);
        store.load();
        var widget = StreakWidgetConfig.defaults().withVisible(true).withPosition(-1200, 40);
        store.save(store.current().withStreakWidget(widget).withTray(TrayConfig.defaults().withEnabled(true)));

        var reloaded = new ConfigStore(file);
        reloaded.load();
        assertThat(reloaded.current().streakWidget()).isEqualTo(widget);
        assertThat(reloaded.current().tray().enabled()).isTrue();
    }
}

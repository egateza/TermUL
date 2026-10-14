package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigTrayTest {

    @TempDir
    Path tmp;

    @Test
    void defaultDanConfigLamaSemuaMati() throws Exception {
        Path file = tmp.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16, \"streak\": false}");
        var store = new ConfigStore(file);
        store.load();

        assertThat(store.current().streak()).as("config lama tetap terbaca utuh").isFalse();
        assertThat(store.current().tray()).isEqualTo(TrayConfig.defaults());
        assertThat(AppConfig.defaults().tray().enabled()).isFalse();
        assertThat(AppConfig.defaults().tray().hidesOnClose()).isFalse();
    }

    @Test
    void fieldTrayYangTidakLengkapTerbacaMati() throws Exception {
        Path file = tmp.resolve("config.json");
        Files.writeString(file, "{\"tray\": {\"enabled\": true}}");
        var store = new ConfigStore(file);
        store.load();

        assertThat(store.current().tray().enabled()).isTrue();
        assertThat(store.current().tray().closeToTray()).isFalse();
        assertThat(store.current().tray().minimizeToTray()).isFalse();
    }

    @Test
    void pilihanTersimpanDanTidakHilangSaatFieldLainDiubah() {
        Path file = tmp.resolve("config.json");
        var store = new ConfigStore(file);
        store.load();
        var tray = TrayConfig.defaults().withEnabled(true).withCloseToTray(true);
        store.save(store.current().withTray(tray).withStreak(false).withTheme("x"));

        var reloaded = new ConfigStore(file);
        reloaded.load();
        assertThat(reloaded.current().tray()).isEqualTo(tray);
        assertThat(reloaded.current().streak()).isFalse();
    }

    @Test
    void sembunyiKeTrayHanyaKalauIkonTrayAktif() {
        var off = new TrayConfig(false, true, true);
        assertThat(off.hidesOnClose()).isFalse();
        assertThat(off.hidesOnMinimize()).isFalse();

        var on = off.withEnabled(true);
        assertThat(on.hidesOnClose()).isTrue();
        assertThat(on.hidesOnMinimize()).isTrue();
        assertThat(on.withCloseToTray(false).hidesOnClose()).isFalse();
    }
}

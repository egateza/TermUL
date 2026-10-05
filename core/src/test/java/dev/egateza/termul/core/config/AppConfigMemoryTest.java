package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigMemoryTest {

    @Test
    void defaultHematTermasukConfigLamaDanNilaiTidakDikenal(@TempDir Path dir) throws Exception {
        assertThat(AppConfig.defaults().memoryMode()).isEqualTo(AppConfig.MEMORY_SAVER);
        assertThat(AppConfig.defaults().withMemoryMode("aneh").memoryMode()).isEqualTo(AppConfig.MEMORY_SAVER);
        var file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16.0}");
        assertThat(new ConfigStore(file).load().memoryMode()).isEqualTo(AppConfig.MEMORY_SAVER);
    }

    @Test
    void normalDisimpanDanDipertahankanWitherLain(@TempDir Path dir) {
        var store = new ConfigStore(dir.resolve("config.json"));
        store.save(AppConfig.defaults().withMemoryMode(AppConfig.MEMORY_NORMAL).withHostStatusMode(AppConfig.HOST_STATUS_TEXT));

        var loaded = new ConfigStore(dir.resolve("config.json")).load();

        assertThat(loaded.memoryMode()).isEqualTo(AppConfig.MEMORY_NORMAL);
        assertThat(loaded.hostStatusMode()).isEqualTo(AppConfig.HOST_STATUS_TEXT);
        assertThat(loaded.withTheme("x").withStatusAnimation("ekg").memoryMode()).isEqualTo(AppConfig.MEMORY_NORMAL);
    }
}

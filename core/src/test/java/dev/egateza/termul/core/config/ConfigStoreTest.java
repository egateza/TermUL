package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStoreTest {

    @TempDir
    Path dir;

    @Test
    void oldConfigWithoutIconSetUsesDefault() throws Exception {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16}");

        var config = new ConfigStore(file).load();

        assertThat(config.iconSet()).isEqualTo(AppConfig.DEFAULT_ICON_SET);
        assertThat(config.terminalFontSize()).isEqualTo(16f);
    }

    @Test
    void iconSetSurvivesSaveAndLoad() {
        Path file = dir.resolve("config.json");
        var store = new ConfigStore(file);
        store.save(AppConfig.defaults().withIconSet("material"));

        assertThat(new ConfigStore(file).load().iconSet()).isEqualTo("material");
    }
}

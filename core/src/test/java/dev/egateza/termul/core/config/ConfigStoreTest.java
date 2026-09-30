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
    void oldConfigWithoutNewFieldsUsesDefaults() throws Exception {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16}");

        var config = new ConfigStore(file).load();

        assertThat(config.iconSet()).isEqualTo(AppConfig.DEFAULT_ICON_SET);
        assertThat(config.hostButtonOpacity()).isEqualTo(AppConfig.DEFAULT_OPACITY);
        assertThat(config.hostPanelMode()).isEqualTo(AppConfig.HOST_DOCKED);
        assertThat(config.terminalFontSize()).isEqualTo(16f);
        assertThat(config.theme()).isEqualTo(AppConfig.DEFAULT_THEME);
        assertThat(config.themeMode()).isEqualTo(AppConfig.MODE_DARK);
        assertThat(config.language()).isEqualTo(AppConfig.DEFAULT_LANGUAGE);
    }

    @Test
    void settingsSurviveSaveAndLoad() {
        Path file = dir.resolve("config.json");
        new ConfigStore(file).save(AppConfig.defaults().withIconSet("material").withHostButtonOpacity(40)
                .withHostPanelMode(AppConfig.HOST_FLOATING).withTheme("default")
                .withThemeMode(AppConfig.MODE_LIGHT).withLanguage("en"));

        var loaded = new ConfigStore(file).load();

        assertThat(loaded.iconSet()).isEqualTo("material");
        assertThat(loaded.hostButtonOpacity()).isEqualTo(40);
        assertThat(loaded.hostPanelMode()).isEqualTo(AppConfig.HOST_FLOATING);
        assertThat(loaded.themeMode()).isEqualTo(AppConfig.MODE_LIGHT);
        assertThat(loaded.language()).isEqualTo("en");
    }

    @Test
    void invalidValuesFallBackToDefaults() {
        var config = AppConfig.defaults().withHostButtonOpacity(3).withHostPanelMode("aneh");

        assertThat(config.hostButtonOpacity()).isEqualTo(AppConfig.DEFAULT_OPACITY);
        assertThat(config.hostPanelMode()).isEqualTo(AppConfig.HOST_DOCKED);
        assertThat(config.withHostButtonOpacity(500).hostButtonOpacity()).isEqualTo(AppConfig.DEFAULT_OPACITY);
    }
}

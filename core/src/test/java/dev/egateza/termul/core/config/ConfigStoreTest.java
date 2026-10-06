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
        assertThat(config.bellSound()).isTrue();
        assertThat(config.bellShake()).isTrue();
        assertThat(config.hostToggleStyle()).isEqualTo(AppConfig.DEFAULT_HOST_TOGGLE);
        assertThat(config.panelCorners()).isEqualTo(AppConfig.PANEL_ROUNDED);
        assertThat(config.tabStyle()).isEqualTo(AppConfig.TAB_CARD);
    }

    @Test
    void settingsSurviveSaveAndLoad() {
        Path file = dir.resolve("config.json");
        new ConfigStore(file).save(AppConfig.defaults().withIconSet("material").withHostButtonOpacity(40)
                .withHostPanelMode(AppConfig.HOST_FLOATING).withTheme("default")
                .withThemeMode(AppConfig.MODE_LIGHT).withLanguage("en").withBellSound(false)
                .withBellShake(false).withHostToggleStyle("grabber").withPanelCorners(AppConfig.PANEL_SQUARE)
                .withTabStyle(AppConfig.TAB_UNDERLINED));

        var loaded = new ConfigStore(file).load();

        assertThat(loaded.iconSet()).isEqualTo("material");
        assertThat(loaded.hostButtonOpacity()).isEqualTo(40);
        assertThat(loaded.hostPanelMode()).isEqualTo(AppConfig.HOST_FLOATING);
        assertThat(loaded.themeMode()).isEqualTo(AppConfig.MODE_LIGHT);
        assertThat(loaded.language()).isEqualTo("en");
        assertThat(loaded.bellSound()).isFalse();
        assertThat(loaded.bellShake()).isFalse();
        assertThat(loaded.hostToggleStyle()).isEqualTo("grabber");
        assertThat(loaded.panelCorners()).isEqualTo(AppConfig.PANEL_SQUARE);
        assertThat(loaded.tabStyle()).isEqualTo(AppConfig.TAB_UNDERLINED);
    }

    @Test
    void invalidValuesFallBackToDefaults() {
        var config = AppConfig.defaults().withHostButtonOpacity(3).withHostPanelMode("aneh");

        assertThat(config.hostButtonOpacity()).isEqualTo(AppConfig.DEFAULT_OPACITY);
        assertThat(config.hostPanelMode()).isEqualTo(AppConfig.HOST_DOCKED);
        assertThat(config.withPanelCorners("aneh").panelCorners()).isEqualTo(AppConfig.PANEL_ROUNDED);
        assertThat(config.withTabStyle("aneh").tabStyle()).isEqualTo(AppConfig.TAB_CARD);
        assertThat(config.withHostButtonOpacity(500).hostButtonOpacity()).isEqualTo(AppConfig.DEFAULT_OPACITY);
    }
}

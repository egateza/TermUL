package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigWslTest {

    @TempDir
    Path dir;

    @Test
    void defaultDanConfigLamaMati() throws Exception {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16}");

        assertThat(AppConfig.defaults().wslManager()).isFalse();
        assertThat(new ConfigStore(file).load().wslManager()).isFalse();
    }

    @Test
    void pilihanTersimpanDanTidakHilangSaatFieldLainDiubah() {
        Path file = dir.resolve("config.json");
        new ConfigStore(file).save(AppConfig.defaults().withWslManager(true));

        var loaded = new ConfigStore(file).load();

        assertThat(loaded.wslManager()).isTrue();
        assertThat(loaded.withTheme("x").withRightClick(AppConfig.RIGHT_CLICK_MENU).withTabStyle(AppConfig.TAB_UNDERLINED)
                .wslManager()).isTrue();
        assertThat(loaded.withWslManager(false).wslManager()).isFalse();
    }
}

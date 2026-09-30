package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigSplitModeTest {

    @Test
    void defaultMenyamping() {
        assertThat(AppConfig.defaults().splitMode()).isEqualTo(AppConfig.SPLIT_HORIZONTAL);
    }

    @Test
    void configLamaTanpaFieldTetapMenyamping(@TempDir Path dir) throws Exception {
        var file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16.0, \"theme\": \"default\"}");

        assertThat(new ConfigStore(file).load().splitMode()).isEqualTo(AppConfig.SPLIT_HORIZONTAL);
    }

    @Test
    void nilaiTidakDikenalJatuhKeMenyamping() {
        assertThat(AppConfig.defaults().withSplitMode("diagonal").splitMode()).isEqualTo(AppConfig.SPLIT_HORIZONTAL);
        assertThat(AppConfig.defaults().withSplitMode(null).splitMode()).isEqualTo(AppConfig.SPLIT_HORIZONTAL);
    }

    @Test
    void witherLainMempertahankanSplitMode() {
        var config = AppConfig.defaults().withSplitMode(AppConfig.SPLIT_VERTICAL);

        assertThat(config.withTheme("x").withWindowOpacity(70).withValidationHooks(ValidationHooks.defaults())
                .splitMode()).isEqualTo(AppConfig.SPLIT_VERTICAL);
    }

    @Test
    void roundTripLewatConfigStore(@TempDir Path dir) {
        var store = new ConfigStore(dir.resolve("config.json"));
        store.save(AppConfig.defaults().withSplitMode(AppConfig.SPLIT_VERTICAL));

        assertThat(new ConfigStore(dir.resolve("config.json")).load().splitMode()).isEqualTo(AppConfig.SPLIT_VERTICAL);
    }
}

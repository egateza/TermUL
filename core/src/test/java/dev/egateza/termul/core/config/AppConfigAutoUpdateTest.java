package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigAutoUpdateTest {

    @Test
    void defaultAktif() {
        assertThat(AppConfig.defaults().autoUpdateCheck()).isTrue();
    }

    @Test
    void configLamaTanpaFieldTetapAktif(@TempDir Path dir) throws Exception {
        var file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16.0, \"hostStatusBar\": false}");

        var config = new ConfigStore(file).load();

        assertThat(config.autoUpdateCheck()).isTrue();
        assertThat(config.hostStatusBar()).isFalse();
    }

    @Test
    void witherLainMempertahankanPilihan() {
        var other = AppConfig.defaults().withAutoUpdateCheck(false).withHostStatusBar(false).withTheme("x");

        assertThat(other.autoUpdateCheck()).isFalse();
    }

    @Test
    void roundTripLewatConfigStore(@TempDir Path dir) {
        new ConfigStore(dir.resolve("config.json")).save(AppConfig.defaults().withAutoUpdateCheck(false));

        assertThat(new ConfigStore(dir.resolve("config.json")).load().autoUpdateCheck()).isFalse();
    }
}

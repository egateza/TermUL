package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigBellTest {

    @Test
    void configLamaTanpaFieldBellTetapAktif(@TempDir Path dir) throws Exception {
        var file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16.0, \"theme\": \"default\"}");

        var loaded = new ConfigStore(file).load();

        assertThat(loaded.bellSound()).isTrue();
        assertThat(loaded.bellShake()).isTrue();
    }

    @Test
    void satuOpsiDimatikanTidakMemengaruhiYangLain() {
        var config = AppConfig.defaults().withBellSound(false);

        assertThat(config.bellSound()).isFalse();
        assertThat(config.bellShake()).isTrue();
        assertThat(config.withBellShake(false).bellSound()).isFalse();
    }
}

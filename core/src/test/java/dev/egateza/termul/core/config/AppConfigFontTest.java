package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigFontTest {

    @Test
    void configLamaTanpaFieldFontMemakaiDefault(@TempDir Path dir) throws Exception {
        var file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16.0}");

        var loaded = new ConfigStore(file).load();

        assertThat(loaded.uiFontFamily()).isNull();
        assertThat(loaded.terminalFontFamily()).isNull();
    }

    @Test
    void duaFontDiubahTerpisahDanNamaKosongJadiDefault() {
        var config = AppConfig.defaults().withUiFontFamily("Segoe UI").withTerminalFontFamily("Consolas");

        assertThat(config.uiFontFamily()).isEqualTo("Segoe UI");
        assertThat(config.terminalFontFamily()).isEqualTo("Consolas");
        assertThat(config.withUiFontFamily("  ").uiFontFamily()).isNull();
        assertThat(config.withUiFontFamily(null).terminalFontFamily()).isEqualTo("Consolas");
        assertThat(config.withBellSound(false).uiFontFamily()).isEqualTo("Segoe UI");
    }
}

package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigHostStatusTest {

    @Test
    void defaultBarHostStatusTampil() {
        assertThat(AppConfig.defaults().hostStatusBar()).isTrue();
    }

    @Test
    void configLamaTanpaField(@TempDir Path dir) throws Exception {
        var file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16.0, \"theme\": \"default\"}");

        assertThat(new ConfigStore(file).load().hostStatusBar()).isTrue();
    }

    @Test
    void witherLainMempertahankanPilihan() {
        var config = AppConfig.defaults().withHostStatusBar(false);

        var other = config.withTheme("x").withSplitMode(AppConfig.SPLIT_VERTICAL).withWindowOpacity(70);

        assertThat(other.hostStatusBar()).isFalse();
    }

    @Test
    void roundTripLewatConfigStore(@TempDir Path dir) {
        var store = new ConfigStore(dir.resolve("config.json"));
        store.save(AppConfig.defaults().withHostStatusBar(false));

        assertThat(new ConfigStore(dir.resolve("config.json")).load().hostStatusBar()).isFalse();
    }
}

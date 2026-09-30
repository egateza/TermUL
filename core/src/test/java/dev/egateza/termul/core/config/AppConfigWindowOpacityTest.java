package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigWindowOpacityTest {

    @Test
    void defaultIsSolid() {
        assertThat(AppConfig.defaults().windowOpacity()).isEqualTo(100);
    }

    @Test
    void configLamaTanpaFieldTetapSolid(@TempDir Path dir) throws Exception {
        var file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16.0, \"theme\": \"default\"}");

        assertThat(new ConfigStore(file).load().windowOpacity()).isEqualTo(100);
    }

    @Test
    void valueIsKeptWithinRangeAndResetOutsideIt() {
        assertThat(AppConfig.defaults().withWindowOpacity(70).windowOpacity()).isEqualTo(70);
        assertThat(AppConfig.defaults().withWindowOpacity(AppConfig.MIN_WINDOW_OPACITY).windowOpacity())
                .isEqualTo(AppConfig.MIN_WINDOW_OPACITY);
        // terlalu rendah membuat jendela nyaris tak terlihat: dikembalikan ke solid
        assertThat(AppConfig.defaults().withWindowOpacity(5).windowOpacity()).isEqualTo(100);
        assertThat(AppConfig.defaults().withWindowOpacity(250).windowOpacity()).isEqualTo(100);
    }

    @Test
    void otherWithersKeepWindowOpacityAndViceVersa() {
        var config = AppConfig.defaults().withWindowOpacity(60);

        assertThat(config.withTheme("x").withTerminalThemeLinked(false).withTerminalTheme("y")
                .withBellSound(false).windowOpacity()).isEqualTo(60);
        assertThat(config.withWindowOpacity(80).theme()).isEqualTo(config.theme());
        assertThat(config.withWindowOpacity(80).terminalThemeLinked()).isTrue();
    }

    @Test
    void roundTripsThroughConfigStore(@TempDir Path dir) {
        var store = new ConfigStore(dir.resolve("config.json"));
        store.save(AppConfig.defaults().withWindowOpacity(55));

        assertThat(new ConfigStore(dir.resolve("config.json")).load().windowOpacity()).isEqualTo(55);
    }
}

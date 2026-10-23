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
        store.save(AppConfig.defaults().withWindowOpacity(55).withTerminalOpacity(40));

        var loaded = new ConfigStore(dir.resolve("config.json")).load();
        assertThat(loaded.windowOpacity()).isEqualTo(55);
        assertThat(loaded.terminalOpacity()).isEqualTo(40);
    }

    @Test
    void terminalOpacityDefaultSolidDanConfigLamaTetapSolid(@TempDir Path dir) throws Exception {
        assertThat(AppConfig.defaults().terminalOpacity()).isEqualTo(100);
        var file = dir.resolve("config.json");
        Files.writeString(file, "{\"windowOpacity\": 76}");

        var loaded = new ConfigStore(file).load();
        assertThat(loaded.terminalOpacity()).isEqualTo(100);
        assertThat(loaded.windowOpacity()).isEqualTo(76);
    }

    @Test
    void terminalOpacityDibatasiDanTerpisahDariWindowOpacity() {
        var config = AppConfig.defaults().withTerminalOpacity(AppConfig.MIN_TERMINAL_OPACITY);
        assertThat(config.terminalOpacity()).isEqualTo(AppConfig.MIN_TERMINAL_OPACITY);
        assertThat(config.windowOpacity()).isEqualTo(100);
        assertThat(config.withWindowOpacity(50).terminalOpacity()).isEqualTo(AppConfig.MIN_TERMINAL_OPACITY);
        assertThat(config.withTheme("x").terminalOpacity()).isEqualTo(AppConfig.MIN_TERMINAL_OPACITY);
        assertThat(AppConfig.defaults().withTerminalOpacity(5).terminalOpacity()).isEqualTo(100);
        assertThat(AppConfig.defaults().withTerminalOpacity(101).terminalOpacity()).isEqualTo(100);
    }

    @Test
    void translucentKalauSalahSatuDiBawah100() {
        assertThat(AppConfig.defaults().translucent()).isFalse();
        assertThat(AppConfig.defaults().withWindowOpacity(90).translucent()).isTrue();
        assertThat(AppConfig.defaults().withTerminalOpacity(90).translucent()).isTrue();
    }
}

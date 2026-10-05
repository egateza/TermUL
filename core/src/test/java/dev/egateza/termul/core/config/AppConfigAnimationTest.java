package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigAnimationTest {

    @Test
    void defaultPacmanDiKeduaTempat() {
        var config = AppConfig.defaults();
        assertThat(config.loadingAnimation()).isEqualTo(AppConfig.DEFAULT_ANIMATION);
        assertThat(config.statusAnimation()).isEqualTo(AppConfig.DEFAULT_ANIMATION);
    }

    @Test
    void configLamaTanpaFieldMemakaiDefault(@TempDir Path dir) throws Exception {
        var file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16.0, \"hostStatusBar\": true}");

        var config = new ConfigStore(file).load();

        assertThat(config.loadingAnimation()).isEqualTo(AppConfig.DEFAULT_ANIMATION);
        assertThat(config.statusAnimation()).isEqualTo(AppConfig.DEFAULT_ANIMATION);
    }

    @Test
    void kosongMenjadiDefault() {
        var config = AppConfig.defaults().withLoadingAnimation(" ").withStatusAnimation(null);
        assertThat(config.loadingAnimation()).isEqualTo(AppConfig.DEFAULT_ANIMATION);
        assertThat(config.statusAnimation()).isEqualTo(AppConfig.DEFAULT_ANIMATION);
    }

    @Test
    void witherLainMempertahankanPilihan() {
        var config = AppConfig.defaults().withLoadingAnimation("ssh").withStatusAnimation(AppConfig.ANIMATION_OFF);

        var other = config.withTheme("x").withHostStatusBar(false).withAutoUpdateCheck(false);

        assertThat(other.loadingAnimation()).isEqualTo("ssh");
        assertThat(other.statusAnimation()).isEqualTo(AppConfig.ANIMATION_OFF);
    }

    @Test
    void roundTripLewatConfigStore(@TempDir Path dir) {
        var store = new ConfigStore(dir.resolve("config.json"));
        store.save(AppConfig.defaults().withLoadingAnimation(AppConfig.ANIMATION_RANDOM).withStatusAnimation("invaders"));

        var loaded = new ConfigStore(dir.resolve("config.json")).load();

        assertThat(loaded.loadingAnimation()).isEqualTo(AppConfig.ANIMATION_RANDOM);
        assertThat(loaded.statusAnimation()).isEqualTo("invaders");
    }
}

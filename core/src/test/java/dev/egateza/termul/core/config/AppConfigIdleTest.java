package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigIdleTest {

    @Test
    void defaultLimaBelasMenitDanAnimasiAcakTermasukConfigLama(@TempDir Path dir) throws Exception {
        assertThat(AppConfig.defaults().idleMinutes()).isEqualTo(AppConfig.DEFAULT_IDLE_MINUTES);
        assertThat(AppConfig.defaults().idleAnimation()).isEqualTo(AppConfig.ANIMATION_RANDOM);
        var file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16.0}");
        var loaded = new ConfigStore(file).load();
        assertThat(loaded.idleMinutes()).isEqualTo(AppConfig.DEFAULT_IDLE_MINUTES);
        assertThat(loaded.idleAnimation()).isEqualTo(AppConfig.ANIMATION_RANDOM);
    }

    @Test
    void nolBerartiMatiDanNilaiDiLuarBatasDijepit() {
        assertThat(AppConfig.defaults().withIdleMinutes(0).idleMinutes()).isZero();
        assertThat(AppConfig.defaults().withIdleMinutes(-5).idleMinutes()).isZero();
        assertThat(AppConfig.defaults().withIdleMinutes(100_000).idleMinutes()).isEqualTo(AppConfig.MAX_IDLE_MINUTES);
    }

    @Test
    void disimpanDanDipertahankanWitherLain(@TempDir Path dir) {
        var store = new ConfigStore(dir.resolve("config.json"));
        store.save(AppConfig.defaults().withIdleMinutes(0).withIdleAnimation("matrix"));

        var loaded = new ConfigStore(dir.resolve("config.json")).load();

        assertThat(loaded.idleMinutes()).isZero();
        assertThat(loaded.idleAnimation()).isEqualTo("matrix");
        var other = loaded.withTheme("x").withMemoryMode(AppConfig.MEMORY_NORMAL).withHostToggleStyle("edgeTab");
        assertThat(other.idleMinutes()).isZero();
        assertThat(other.idleAnimation()).isEqualTo("matrix");
    }
}

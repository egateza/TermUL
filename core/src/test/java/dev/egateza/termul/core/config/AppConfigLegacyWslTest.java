package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** WSL Manager dihapus (ADR 0011): config lama dengan {@code wslManager} tetap terbaca, field-nya hilang saat disimpan. */
class AppConfigLegacyWslTest {

    @Test
    void configLamaDenganWslManagerTetapTerbaca(@TempDir Path dir) throws Exception {
        var file = dir.resolve("config.json");
        Files.writeString(file, "{\"wslManager\": true, \"theme\": \"x\", \"windowOpacity\": 80}");

        var store = new ConfigStore(file);
        var loaded = store.load();
        assertThat(loaded.theme()).isEqualTo("x");
        assertThat(loaded.windowOpacity()).isEqualTo(80);

        store.save(loaded.withTabStyle(AppConfig.TAB_UNDERLINED));
        assertThat(Files.readString(file)).doesNotContain("wslManager");
    }
}

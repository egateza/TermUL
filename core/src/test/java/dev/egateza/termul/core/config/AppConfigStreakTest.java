package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigStreakTest {

    @TempDir
    Path tmp;

    @Test
    void defaultDanConfigLamaTampil() throws Exception {
        Path file = tmp.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16, \"snippetButton\": false}");
        var store = new ConfigStore(file);
        store.load();

        assertThat(store.current().snippetButton()).as("config lama tetap terbaca utuh").isFalse();
        assertThat(store.current().streak()).isTrue();
        assertThat(AppConfig.defaults().streak()).isTrue();
    }

    @Test
    void pilihanTersimpanDanTidakHilangSaatFieldLainDiubah() {
        Path file = tmp.resolve("config.json");
        var store = new ConfigStore(file);
        store.load();
        store.save(store.current().withStreak(false).withSnippetCorner(AppConfig.SNIPPET_BOTTOM_LEFT).withTheme("x"));

        var reloaded = new ConfigStore(file);
        reloaded.load();
        assertThat(reloaded.current().streak()).isFalse();
        assertThat(reloaded.current().snippetCorner()).isEqualTo(AppConfig.SNIPPET_BOTTOM_LEFT);
    }
}

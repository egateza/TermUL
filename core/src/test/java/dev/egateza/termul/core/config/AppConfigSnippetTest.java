package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigSnippetTest {

    @TempDir
    Path tmp;

    @Test
    void defaultDanConfigLamaTampilDiKananAtas() throws Exception {
        Path file = tmp.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16}");
        var store = new ConfigStore(file);
        store.load();

        assertThat(store.current().terminalFontSize()).as("config lama tetap terbaca utuh").isEqualTo(16f);
        for (var config : new AppConfig[] {AppConfig.defaults(), store.current()}) {
            assertThat(config.snippetButton()).isTrue();
            assertThat(config.snippetCorner()).isEqualTo(AppConfig.SNIPPET_TOP_RIGHT);
        }
    }

    @Test
    void pilihanTersimpanDanTidakHilangSaatFieldLainDiubah() {
        Path file = tmp.resolve("config.json");
        var store = new ConfigStore(file);
        store.load();
        store.save(store.current().withSnippetButton(false).withSnippetCorner(AppConfig.SNIPPET_BOTTOM_LEFT)
                .withWslManager(true).withTheme("neo-brutalism"));

        var reloaded = new ConfigStore(file);
        reloaded.load();
        assertThat(reloaded.current().snippetButton()).isFalse();
        assertThat(reloaded.current().snippetCorner()).isEqualTo(AppConfig.SNIPPET_BOTTOM_LEFT);
    }

    @Test
    void sudutTidakDikenalKembaliKeDefault() {
        assertThat(AppConfig.defaults().withSnippetCorner("tengah").snippetCorner())
                .isEqualTo(AppConfig.SNIPPET_TOP_RIGHT);
    }
}

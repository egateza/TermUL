package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigSftpColumnsTest {

    @TempDir
    Path tmp;

    @Test
    void configLamaSemuaKolomTampil() throws Exception {
        Path file = tmp.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16, \"streak\": false}");
        var store = new ConfigStore(file);
        store.load();

        assertThat(store.current().streak()).as("config lama tetap terbaca utuh").isFalse();
        assertThat(store.current().sftpHiddenColumns()).isEmpty();
        assertThat(AppConfig.defaults().sftpHiddenColumns()).isEmpty();
    }

    @Test
    void kolomNamaTidakBisaDisembunyikanDanNilaiDibersihkan() {
        var hidden = AppConfig.defaults()
                .withSftpHiddenColumns(Arrays.asList("owner", " group ", "name", null, "", "owner"))
                .sftpHiddenColumns();

        assertThat(hidden).containsExactly("owner", "group");
    }

    @Test
    void pilihanTersimpanDanTidakHilangSaatFieldLainDiubah() {
        Path file = tmp.resolve("config.json");
        var store = new ConfigStore(file);
        store.load();
        store.save(store.current().withSftpHiddenColumns(java.util.List.of("mode", "owner")).withStreak(false));

        var reloaded = new ConfigStore(file);
        reloaded.load();
        assertThat(reloaded.current().sftpHiddenColumns()).containsExactly("mode", "owner");
        assertThat(reloaded.current().streak()).isFalse();
    }
}

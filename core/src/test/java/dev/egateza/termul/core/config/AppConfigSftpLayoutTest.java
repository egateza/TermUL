package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigSftpLayoutTest {

    @TempDir
    Path tmp;

    @Test
    void configLamaDanNilaiTidakDikenalSatuPanel() throws Exception {
        Path file = tmp.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16, \"streak\": false}");
        var store = new ConfigStore(file);
        store.load();

        assertThat(store.current().streak()).as("config lama tetap terbaca utuh").isFalse();
        assertThat(store.current().sftpLayout()).isEqualTo(AppConfig.SFTP_SINGLE);
        assertThat(AppConfig.defaults().sftpLayout()).isEqualTo(AppConfig.SFTP_SINGLE);
        assertThat(AppConfig.defaults().withSftpLayout("tiga").sftpLayout()).isEqualTo(AppConfig.SFTP_SINGLE);
    }

    @Test
    void pilihanTersimpanDanTidakHilangSaatFieldLainDiubah() {
        Path file = tmp.resolve("config.json");
        var store = new ConfigStore(file);
        store.load();
        store.save(store.current().withSftpLayout(AppConfig.SFTP_DUAL).withStreak(false).withTheme("x"));

        var reloaded = new ConfigStore(file);
        reloaded.load();
        assertThat(reloaded.current().sftpLayout()).isEqualTo(AppConfig.SFTP_DUAL);
        assertThat(reloaded.current().streak()).isFalse();
    }
}

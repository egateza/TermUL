package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigSftpFollowTest {

    @TempDir
    Path tmp;

    @Test
    void configLamaDanDefaultMati() throws Exception {
        Path file = tmp.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16, \"sftpLayout\": \"dual\"}");
        var store = new ConfigStore(file);
        store.load();

        assertThat(store.current().sftpLayout()).as("config lama tetap terbaca utuh").isEqualTo(AppConfig.SFTP_DUAL);
        assertThat(store.current().sftpFollowTerminal()).isFalse();
        assertThat(AppConfig.defaults().sftpFollowTerminal()).isFalse();
    }

    @Test
    void pilihanTersimpanDanTidakHilangSaatFieldLainDiubah() {
        Path file = tmp.resolve("config.json");
        var store = new ConfigStore(file);
        store.load();
        store.save(store.current().withSftpFollowTerminal(true).withSftpLayout(AppConfig.SFTP_DUAL).withStreak(false));

        var reloaded = new ConfigStore(file);
        reloaded.load();
        assertThat(reloaded.current().sftpFollowTerminal()).isTrue();
        assertThat(reloaded.current().sftpLayout()).isEqualTo(AppConfig.SFTP_DUAL);
    }
}

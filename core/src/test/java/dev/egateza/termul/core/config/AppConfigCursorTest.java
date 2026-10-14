package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigCursorTest {

    @TempDir
    Path tmp;

    @Test
    void configLamaBlokBerkedip() throws Exception {
        Path file = tmp.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16, \"sftpLayout\": \"dual\"}");
        var store = new ConfigStore(file);
        store.load();

        assertThat(store.current().sftpLayout()).as("config lama tetap terbaca utuh").isEqualTo(AppConfig.SFTP_DUAL);
        assertThat(store.current().cursorShape()).isEqualTo(AppConfig.CURSOR_BLOCK);
        assertThat(store.current().cursorBlink()).isTrue();
        assertThat(AppConfig.defaults().withCursorShape("bintang").cursorShape()).isEqualTo(AppConfig.CURSOR_BLOCK);
    }

    @Test
    void pilihanTersimpanDanTidakHilangSaatFieldLainDiubah() {
        Path file = tmp.resolve("config.json");
        var store = new ConfigStore(file);
        store.load();
        store.save(store.current().withCursorShape(AppConfig.CURSOR_BAR).withCursorBlink(false)
                .withSftpLayout(AppConfig.SFTP_DUAL).withTheme("x"));

        var reloaded = new ConfigStore(file);
        reloaded.load();
        assertThat(reloaded.current().cursorShape()).isEqualTo(AppConfig.CURSOR_BAR);
        assertThat(reloaded.current().cursorBlink()).isFalse();
        assertThat(reloaded.current().sftpLayout()).isEqualTo(AppConfig.SFTP_DUAL);
    }
}

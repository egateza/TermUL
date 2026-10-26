package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigCollapsedGroupsTest {

    @TempDir
    Path tmp;

    @Test
    void configLamaDanDefaultSemuaGrupTerbuka() throws Exception {
        Path file = tmp.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16, \"sftpLayout\": \"dual\"}");
        var store = new ConfigStore(file);
        store.load();

        assertThat(store.current().sftpLayout()).as("config lama tetap terbaca utuh").isEqualTo(AppConfig.SFTP_DUAL);
        assertThat(store.current().collapsedHostGroups()).isEmpty();
        assertThat(AppConfig.defaults().collapsedHostGroups()).isEmpty();
    }

    @Test
    void grupTertutupTersimpanTerurutDanTidakHilangSaatFieldLainDiubah() {
        Path file = tmp.resolve("config.json");
        var store = new ConfigStore(file);
        store.load();
        store.save(store.current().withCollapsedHostGroups(Set.of("group:Staging", "builtin:recent", "group:Prod/Db"))
                .withStreak(false).withSftpFollowTerminal(true));

        var reloaded = new ConfigStore(file);
        reloaded.load();
        assertThat(reloaded.current().collapsedHostGroups())
                .containsExactly("builtin:recent", "group:Prod/Db", "group:Staging");
        assertThat(reloaded.current().sftpFollowTerminal()).isTrue();
    }
}

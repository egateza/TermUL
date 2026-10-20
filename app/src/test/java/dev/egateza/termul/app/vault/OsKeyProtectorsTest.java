package dev.egateza.termul.app.vault;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.Os;
import dev.egateza.termul.vault.DpapiKeyProtector;
import dev.egateza.termul.vault.KeyProtector;
import dev.egateza.termul.vault.KeychainKeyProtector;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OsKeyProtectorsTest {

    private final Path vault = Path.of("/Users/a/Library/Application Support/TermUL/vault.bin");

    @Test
    void windowsMemakaiDpapi() {
        assertThat(OsKeyProtectors.choose(Os.WINDOWS, Map.<String, String>of()::get, vault))
                .isInstanceOf(DpapiKeyProtector.class);
    }

    @Test
    void macHanyaUntukAppHasilInstalasi() {
        assertThat(OsKeyProtectors.choose(Os.MAC, Map.<String, String>of()::get, vault))
                .isSameAs(KeyProtector.UNAVAILABLE);
        var app = OsKeyProtectors.choose(Os.MAC,
                Map.of("jpackage.app-path", "/Applications/TermUL.app/Contents/MacOS/TermUL")::get, vault);
        assertThat(app).isInstanceOf(KeychainKeyProtector.class);
        assertThat(app.blobSuffix()).isEqualTo(".keychain");
    }

    @Test
    void linuxTidakAda() {
        assertThat(OsKeyProtectors.choose(Os.OTHER, Map.of("jpackage.app-path", "/opt/termul")::get, vault))
                .isSameAs(KeyProtector.UNAVAILABLE);
    }

    @Test
    void namaItemKeychainStabilDanBedaPerLokasi() {
        assertThat(OsKeyProtectors.account(vault)).isEqualTo(OsKeyProtectors.account(vault))
                .matches("vault-[0-9a-f]{16}");
        assertThat(OsKeyProtectors.account(Path.of("/tmp/lain/vault.bin"))).isNotEqualTo(OsKeyProtectors.account(vault));
    }
}

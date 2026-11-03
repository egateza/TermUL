package dev.egateza.termul.ssh;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectionTesterTest {

    @TempDir
    Path dir;

    @Test
    void portTertutupDilaporkanTidakTercapai() throws Exception {
        int port;
        try (var s = new ServerSocket(0)) {
            port = s.getLocalPort(); // ditutup lagi: koneksi ke sini ditolak
        }
        var profile = new HostProfile(UUID.randomUUID(), "mati", "", "127.0.0.1", port, "admin",
                AuthMethod.PASSWORD, null, null, EnvironmentTag.NONE, null, null, false);

        var result = ConnectionTester.test(profile, id -> Optional.empty(),
                new KnownHostsStore(dir.resolve("known_hosts")), CredentialProvider.NONE, Duration.ofSeconds(5));

        assertThat(result.hostKey()).isEqualTo(ConnectionTester.HostKey.UNREACHED);
        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isNotBlank();
    }
}

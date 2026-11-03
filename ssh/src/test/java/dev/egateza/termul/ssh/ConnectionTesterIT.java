package dev.egateza.termul.ssh;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import dev.egateza.termul.ssh.hostkey.HostKeyInfo;
import dev.egateza.termul.ssh.hostkey.HostKeyPrompt;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.ssh.testing.OpenSshContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Tombol Test di Profile manager terhadap OpenSSH asli (butuh Docker; otomatis skip kalau Docker tidak ada). */
@Testcontainers(disabledWithoutDocker = true)
class ConnectionTesterIT {

    @Container
    static final OpenSshContainer SSHD = new OpenSshContainer();

    @TempDir
    Path dir;

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private HostProfile profile() {
        return new HostProfile(UUID.randomUUID(), "it", "", SSHD.getHost(), SSHD.sshPort(), OpenSshContainer.USER,
                AuthMethod.PASSWORD, null, null, EnvironmentTag.DEV, null, null, false);
    }

    private static CredentialProvider password(String pw) {
        return new CredentialProvider() {
            @Override
            public char[] password(HostProfile profile, int attempt) {
                return pw == null ? null : pw.toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt) {
                return null;
            }
        };
    }

    /** Percayai host key container lewat koneksi biasa (seperti user menekan Trust di dialog). */
    private KnownHostsStore trusted() throws Exception {
        var store = new KnownHostsStore(dir.resolve("known_hosts"));
        var trust = new HostKeyPrompt() {
            @Override
            public boolean confirmUnknownHost(HostKeyInfo info) {
                return true;
            }

            @Override
            public void hostKeyChanged(HostKeyInfo presented, List<String> knownFingerprints) {
            }
        };
        try (var manager = new SessionManager(store, trust, password(OpenSshContainer.PASSWORD), SshSettings.defaults());
             var lease = manager.acquire(profile())) {
            assertThat(lease).isNotNull();
        }
        return store;
    }

    @Test
    void hostBaruTidakDipercayaDanPasswordTidakDikirim() {
        var knownHosts = new KnownHostsStore(dir.resolve("known_hosts"));
        var asked = new boolean[1];
        var creds = new CredentialProvider() {
            @Override
            public char[] password(HostProfile profile, int attempt) {
                asked[0] = true;
                return OpenSshContainer.PASSWORD.toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt) {
                return null;
            }
        };

        var result = ConnectionTester.test(profile(), id -> Optional.empty(), knownHosts, creds, TIMEOUT);

        assertThat(result.hostKey()).isEqualTo(ConnectionTester.HostKey.NEW);
        assertThat(result.signedIn()).isFalse();
        assertThat(result.ok()).isTrue();
        assertThat(asked[0]).as("password tidak boleh dikirim ke host yang belum dipercaya").isFalse();
        assertThat(dir.resolve("known_hosts")).satisfiesAnyOf(
                f -> assertThat(f).doesNotExist(), f -> assertThat(Files.readString(f)).isBlank());
    }

    @Test
    void hostDikenalDanPasswordBenarBerhasilLogin() throws Exception {
        var result = ConnectionTester.test(profile(), id -> Optional.empty(), trusted(),
                password(OpenSshContainer.PASSWORD), TIMEOUT);

        assertThat(result.hostKey()).isEqualTo(ConnectionTester.HostKey.KNOWN);
        assertThat(result.signedIn()).isTrue();
        assertThat(result.error()).isNull();
    }

    @Test
    void passwordSalahGagalTanpaMintaUlang() throws Exception {
        var result = ConnectionTester.test(profile(), id -> Optional.empty(), trusted(), password("salah"), TIMEOUT);

        assertThat(result.signedIn()).isFalse();
        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isNotBlank();
    }

    @Test
    void tanpaPasswordLoginTidakDites() throws Exception {
        var result = ConnectionTester.test(profile(), id -> Optional.empty(), trusted(), password(null), TIMEOUT);

        assertThat(result.passwordMissing()).isTrue();
        assertThat(result.signedIn()).isFalse();
        assertThat(result.ok()).isTrue();
    }
}

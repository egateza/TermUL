package dev.egateza.myterm.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.myterm.core.profile.AuthMethod;
import dev.egateza.myterm.core.profile.EnvironmentTag;
import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.ssh.auth.CredentialProvider;
import dev.egateza.myterm.ssh.hostkey.HostKeyInfo;
import dev.egateza.myterm.ssh.hostkey.HostKeyPrompt;
import dev.egateza.myterm.ssh.hostkey.KnownHostsStore;
import dev.egateza.myterm.ssh.testing.OpenSshContainer;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration test terhadap OpenSSH asli (butuh Docker; otomatis skip kalau Docker tidak ada). */
@Testcontainers(disabledWithoutDocker = true)
class SessionManagerIT {

    @Container
    static final OpenSshContainer SSHD = new OpenSshContainer();

    @TempDir
    Path dir;

    private SessionManager manager;
    private volatile String password = OpenSshContainer.PASSWORD;

    @BeforeEach
    void setUp() {
        var trust = new HostKeyPrompt() {
            @Override
            public boolean confirmUnknownHost(HostKeyInfo info) {
                return true;
            }

            @Override
            public void hostKeyChanged(HostKeyInfo presented, List<String> knownFingerprints) {
            }
        };
        var creds = new CredentialProvider() {
            @Override
            public char[] password(HostProfile profile, int attempt) {
                return password == null ? null : password.toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt) {
                return null;
            }
        };
        manager = new SessionManager(new KnownHostsStore(dir.resolve("known_hosts")), trust, creds,
                SshSettings.defaults());
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    private HostProfile profile() {
        return new HostProfile(UUID.randomUUID(), "it", "", SSHD.getHost(), SSHD.sshPort(), OpenSshContainer.USER,
                AuthMethod.PASSWORD, null, null, EnvironmentTag.DEV, null, null, false);
    }

    @Test
    void shellPtyDenganOpenSsh() throws Exception {
        try (SshLease lease = manager.acquire(profile())) {
            var shell = lease.connection().openShell(new PtySize(100, 30), Map.of());
            var in = shell.getInvertedIn();
            in.write("stty size; echo $TERM; exit\n".getBytes(StandardCharsets.UTF_8));
            in.flush();
            shell.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), Duration.ofSeconds(10));
            String out = new String(shell.getInvertedOut().readAllBytes(), StandardCharsets.UTF_8);

            assertThat(out).contains("30 100").contains("xterm-256color");
        }
    }

    @Test
    void heartbeatMendeteksiServerHang() throws Exception {
        var settings = new SshSettings(Duration.ofSeconds(15), Duration.ofSeconds(30), Duration.ofSeconds(15),
                Duration.ofSeconds(1), 2, Duration.ZERO);
        var creds = new CredentialProvider() {
            @Override
            public char[] password(HostProfile profile, int attempt) {
                return OpenSshContainer.PASSWORD.toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt) {
                return null;
            }
        };
        try (var fast = new SessionManager(new KnownHostsStore(dir.resolve("kh2")), new HostKeyPrompt() {
            @Override
            public boolean confirmUnknownHost(HostKeyInfo info) {
                return true;
            }

            @Override
            public void hostKeyChanged(HostKeyInfo presented, List<String> knownFingerprints) {
            }
        }, creds, settings)) {
            SshLease lease = fast.acquire(profile());
            var docker = SSHD.getDockerClient();
            docker.pauseContainerCmd(SSHD.getContainerId()).exec();
            try {
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20))
                        .until(() -> !lease.connection().isOpen());
            } finally {
                docker.unpauseContainerCmd(SSHD.getContainerId()).exec();
                lease.close();
            }
        }
    }

    @Test
    void deteksiOsAlpine() throws Exception {
        try (SshLease lease = manager.acquire(profile())) {
            assertThat(OsDetector.detect(lease.connection())).get()
                    .extracting(o -> o.id()).isEqualTo("alpine");
        }
    }

    @Test
    void execDanPasswordSalah() throws Exception {
        try (SshLease lease = manager.acquire(profile())) {
            var exec = lease.connection().createExec("id -un");
            var out = new ByteArrayOutputStream();
            exec.setOut(out);
            exec.open().verify(Duration.ofSeconds(10));
            exec.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), Duration.ofSeconds(10));
            assertThat(out.toString(StandardCharsets.UTF_8).strip()).isEqualTo(OpenSshContainer.USER);
        }

        password = "salah";
        assertThatThrownBy(() -> manager.acquire(profile()))
                .isInstanceOf(SshConnectException.class)
                .hasMessageContaining("gagal");
    }
}

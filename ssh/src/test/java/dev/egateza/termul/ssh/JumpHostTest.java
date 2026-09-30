package dev.egateza.termul.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import dev.egateza.termul.ssh.hostkey.HostKeyInfo;
import dev.egateza.termul.ssh.hostkey.HostKeyPrompt;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.ssh.testing.TestSshServer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.forward.RejectAllForwardingFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ProxyJump lewat server MINA in-process (tanpa Docker). */
class JumpHostTest {

    @TempDir
    Path dir;

    private TestSshServer jump;
    private TestSshServer target;
    private SessionManager sessions;
    private KnownHostsStore knownHosts;
    private final Map<UUID, HostProfile> profiles = new ConcurrentHashMap<>();
    private final List<String> trusted = new CopyOnWriteArrayList<>();

    private void start(boolean jumpAllowsForwarding) throws Exception {
        jump = new TestSshServer(dir.resolve("jump.ser"), s -> s.setForwardingFilter(jumpAllowsForwarding
                ? AcceptAllForwardingFilter.INSTANCE : RejectAllForwardingFilter.INSTANCE));
        target = new TestSshServer(dir.resolve("target.ser"));
        knownHosts = new KnownHostsStore(dir.resolve("known_hosts"));
        var trust = new HostKeyPrompt() {
            @Override
            public boolean confirmUnknownHost(HostKeyInfo info) {
                trusted.add(info.hostLabel());
                return true;
            }

            @Override
            public void hostKeyChanged(HostKeyInfo presented, List<String> knownFingerprints) {
            }
        };
        var creds = new CredentialProvider() {
            @Override
            public char[] password(HostProfile p, int attempt) {
                return TestSshServer.PASSWORD.toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile p, Path keyFile, int attempt) {
                return null;
            }
        };
        sessions = new SessionManager(knownHosts, trust, creds,
                new SshSettings(Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(10),
                        Duration.ZERO, 0, Duration.ZERO),
                id -> Optional.ofNullable(profiles.get(id)));
    }

    private HostProfile profile(String name, int port, UUID jumpId) {
        var p = new HostProfile(UUID.randomUUID(), name, "", "127.0.0.1", port, TestSshServer.USER,
                AuthMethod.PASSWORD, null, jumpId, EnvironmentTag.DEV, null, null, false);
        profiles.put(p.id(), p);
        return p;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (sessions != null) {
            sessions.close();
        }
        if (jump != null) {
            jump.close();
        }
        if (target != null) {
            target.close();
        }
    }

    @Test
    void connectLewatJumpHostDanHostKeyTujuanDicatatDenganAlamatAsli() throws Exception {
        start(true);
        var bastion = profile("bastion", jump.port(), null);
        var app = profile("app", target.port(), bastion.id());

        try (SshLease lease = sessions.acquire(app)) {
            assertThat(lease.connection().isOpen()).isTrue();
            assertThat(sessions.connectionCount()).isEqualTo(2); // jump host ikut tersambung
            assertThat(jump.server().getActiveSessions()).hasSize(1);
            assertThat(knownHosts.lookup("127.0.0.1", target.port())).hasSize(1); // bukan port tunnel
            assertThat(knownHosts.lookup("127.0.0.1", jump.port())).hasSize(1);
            assertThat(trusted).hasSize(2);
        }

        await().atMost(Duration.ofSeconds(10)).until(() -> sessions.connectionCount() == 0);
        await().atMost(Duration.ofSeconds(10)).until(() -> jump.server().getActiveSessions().isEmpty());
    }

    @Test
    void jumpHostYangSudahTersambungDipakaiBersama() throws Exception {
        start(true);
        var bastion = profile("bastion", jump.port(), null);
        var app = profile("app", target.port(), bastion.id());

        try (SshLease direct = sessions.acquire(bastion); SshLease viaJump = sessions.acquire(app)) {
            assertThat(viaJump.connection().isOpen()).isTrue();
            assertThat(jump.server().getActiveSessions()).hasSize(1);
        }
    }

    @Test
    void jumpHostMenolakForwardingMemberiPesanJelas() throws Exception {
        start(false);
        var bastion = profile("bastion", jump.port(), null);
        var app = profile("app", target.port(), bastion.id());

        assertThatThrownBy(() -> sessions.acquire(app))
                .isInstanceOf(SshConnectException.class)
                .hasMessageContaining("jump host");
        await().atMost(Duration.ofSeconds(10)).until(() -> sessions.connectionCount() == 0);
    }

    @Test
    void rantaiBerputarDanJumpHostHilangDitolak() throws Exception {
        start(true);
        var a = profile("a", jump.port(), null);
        var b = profile("b", target.port(), a.id());
        profiles.put(a.id(), new HostProfile(a.id(), "a", "", "127.0.0.1", jump.port(), TestSshServer.USER,
                AuthMethod.PASSWORD, null, b.id(), EnvironmentTag.DEV, null, null, false));
        var orphan = profile("orphan", target.port(), UUID.randomUUID());

        assertThatThrownBy(() -> sessions.acquire(b)).hasMessageContaining("berputar");
        assertThatThrownBy(() -> sessions.acquire(orphan)).hasMessageContaining("tidak ditemukan");
    }
}

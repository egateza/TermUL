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
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** ProxyJump ke OpenSSH asli: host tujuan hanya bisa dijangkau dari jump host (butuh Docker). */
@Testcontainers(disabledWithoutDocker = true)
class JumpHostIT {

    static final Network NETWORK = Network.newNetwork();

    @Container
    static final OpenSshContainer BASTION = new OpenSshContainer().withNetwork(NETWORK);

    /** Tanpa port yang di-expose ke host: hanya lewat jaringan Docker. */
    @Container
    static final OpenSshContainer INTERNAL = new OpenSshContainer().withNetwork(NETWORK)
            .withNetworkAliases("internal-app").withCreateContainerCmdModifier(cmd -> cmd.withHostName("internal-app"));

    @TempDir
    Path tmp;

    private SessionManager sessions;

    @BeforeAll
    static void allowForwardingOnBastion() throws Exception {
        // image linuxserver mematikan TCP forwarding secara default
        var result = BASTION.execInContainer("sh", "-c",
                "sed -i 's/^AllowTcpForwarding.*/AllowTcpForwarding yes/' /config/sshd/sshd_config"
                        + " && grep -q '^AllowTcpForwarding yes' /config/sshd/sshd_config"
                        + " || echo 'AllowTcpForwarding yes' >> /config/sshd/sshd_config;"
                        + " kill -HUP \"$(cat /config/sshd.pid)\"");
        assertThat(result.getExitCode()).isZero();
        Thread.sleep(1000);
    }

    @AfterEach
    void tearDown() {
        if (sessions != null) {
            sessions.close();
        }
    }

    @Test
    void shellDiHostInternalLewatBastion() throws Exception {
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
            public char[] password(HostProfile p, int attempt) {
                return OpenSshContainer.PASSWORD.toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile p, Path keyFile, int attempt) {
                return null;
            }
        };
        var bastion = new HostProfile(UUID.randomUUID(), "bastion", "", BASTION.getHost(), BASTION.sshPort(),
                OpenSshContainer.USER, AuthMethod.PASSWORD, null, null, EnvironmentTag.DEV, null, null, false);
        var internal = new HostProfile(UUID.randomUUID(), "internal", "", "internal-app", OpenSshContainer.PORT,
                OpenSshContainer.USER, AuthMethod.PASSWORD, null, bastion.id(), EnvironmentTag.DEV, null, null, false);
        var known = new KnownHostsStore(tmp.resolve("known_hosts"));
        sessions = new SessionManager(known, trust, creds, SshSettings.defaults(),
                id -> Optional.ofNullable(Map.of(bastion.id(), bastion).get(id)));

        try (SshLease lease = sessions.acquire(internal)) {
            var result = RemoteExec.run(lease.connection(), "hostname", null, Duration.ofSeconds(20));

            assertThat(result.stdout().strip()).isEqualTo("internal-app");
            assertThat(known.lookup("internal-app", OpenSshContainer.PORT)).hasSize(1);
        }
    }
}

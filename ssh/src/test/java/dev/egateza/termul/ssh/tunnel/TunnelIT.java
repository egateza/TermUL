package dev.egateza.termul.ssh.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.tunnel.Tunnel;
import dev.egateza.termul.core.tunnel.TunnelType;
import dev.egateza.termul.ssh.SessionManager;
import dev.egateza.termul.ssh.SshSettings;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import dev.egateza.termul.ssh.hostkey.HostKeyInfo;
import dev.egateza.termul.ssh.hostkey.HostKeyPrompt;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.ssh.testing.OpenSshContainer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Port forwarding lewat OpenSSH asli: local, dynamic (SOCKS5), dan remote (butuh Docker). */
@Testcontainers(disabledWithoutDocker = true)
class TunnelIT {

    @Container
    static final OpenSshContainer SERVER = new OpenSshContainer();

    @TempDir
    Path tmp;

    private SessionManager sessions;
    private TunnelManager manager;
    private ExecutorService executor;
    private HostProfile host;

    @BeforeAll
    static void allowForwarding() throws Exception {
        // image linuxserver mematikan TCP forwarding secara default
        var result = SERVER.execInContainer("sh", "-c",
                "sed -i 's/^AllowTcpForwarding.*/AllowTcpForwarding yes/' /config/sshd/sshd_config"
                        + " && grep -q '^AllowTcpForwarding yes' /config/sshd/sshd_config"
                        + " || echo 'AllowTcpForwarding yes' >> /config/sshd/sshd_config;"
                        + " kill -HUP \"$(cat /config/sshd.pid)\"");
        assertThat(result.getExitCode()).isZero();
        Thread.sleep(1000);
    }

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
            public char[] password(HostProfile p, int attempt) {
                return OpenSshContainer.PASSWORD.toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile p, Path keyFile, int attempt) {
                return null;
            }
        };
        host = new HostProfile(UUID.randomUUID(), "openssh", "", SERVER.getHost(), SERVER.sshPort(),
                OpenSshContainer.USER, AuthMethod.PASSWORD, null, null, EnvironmentTag.DEV, null, null, false);
        sessions = new SessionManager(new KnownHostsStore(tmp.resolve("known_hosts")), trust, creds,
                SshSettings.defaults(), id -> id.equals(host.id()) ? Optional.of(host) : Optional.empty());
        executor = Executors.newVirtualThreadPerTaskExecutor();
        manager = new TunnelManager(sessions, id -> id.equals(host.id()) ? Optional.of(host) : Optional.empty(),
                List::of, executor, Duration.ofSeconds(15));
    }

    @AfterEach
    void tearDown() {
        manager.close();
        sessions.close();
        executor.shutdownNow();
    }

    private void awaitActive(Tunnel t) {
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            var status = manager.state(t.id()).status();
            assertThat(status).isNotInstanceOf(TunnelStatus.Failed.class);
            return status instanceof TunnelStatus.Active;
        });
    }

    @Test
    void localKeSshdDiServer() throws Exception {
        // tujuan dilihat dari server: sshd-nya sendiri di localhost:2222
        var t = Tunnel.create("sshd", TunnelType.LOCAL, host.id(), freePort(), "localhost", OpenSshContainer.PORT,
                false, false);
        manager.start(t);
        awaitActive(t);

        try (var socket = new Socket(InetAddress.getLoopbackAddress(), t.sourcePort())) {
            socket.setSoTimeout(10_000);
            assertThat(new String(socket.getInputStream().readNBytes(8), StandardCharsets.US_ASCII))
                    .isEqualTo("SSH-2.0-");
        }
        await().until(() -> manager.state(t.id()).bytes() >= 8);
    }

    @Test
    void dynamicSocks5DenganNamaHostDariServer() throws Exception {
        var t = Tunnel.create("socks", TunnelType.DYNAMIC, host.id(), freePort(), "", 0, false, false);
        manager.start(t);
        awaitActive(t);

        try (var socket = new Socket(InetAddress.getLoopbackAddress(), t.sourcePort())) {
            socket.setSoTimeout(10_000);
            var out = socket.getOutputStream();
            var in = socket.getInputStream();
            out.write(new byte[] {5, 1, 0});
            assertThat(in.readNBytes(2)).containsExactly(5, 0);
            byte[] name = "localhost".getBytes(StandardCharsets.US_ASCII);
            out.write(new byte[] {5, 1, 0, 3, (byte) name.length});
            out.write(name);
            out.write(new byte[] {(byte) (OpenSshContainer.PORT >> 8), (byte) OpenSshContainer.PORT});
            assertThat(in.readNBytes(10)[1]).isZero();
            assertThat(new String(in.readNBytes(8), StandardCharsets.US_ASCII)).isEqualTo("SSH-2.0-");
        }
    }

    @Test
    void remoteDariServerKeLayananDiPcIni() throws Exception {
        try (var service = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            Thread.ofVirtual().start(() -> {
                // beberapa koneksi: yang pertama uji tujuan saat tunnel dinyalakan
                while (!service.isClosed()) {
                    try (Socket s = service.accept()) {
                        s.getOutputStream().write("halo dari PC\n".getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        return; // test selesai
                    }
                }
            });
            int remotePort = 19000;
            var t = Tunnel.create("webhook", TunnelType.REMOTE, host.id(), remotePort, "127.0.0.1",
                    service.getLocalPort(), false, false);
            manager.start(t);
            awaitActive(t);

            var result = SERVER.execInContainer("sh", "-c", "nc -w 5 127.0.0.1 " + remotePort + " </dev/null");

            assertThat(result.getStdout()).contains("halo dari PC");
            await().until(() -> manager.state(t.id()).bytes() > 0);
        }
    }

    private static int freePort() throws IOException {
        try (var s = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            return s.getLocalPort();
        }
    }
}

package dev.egateza.termul.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jediterm.core.util.TermSize;
import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.ssh.PtySize;
import dev.egateza.termul.ssh.SessionManager;
import dev.egateza.termul.ssh.SshSettings;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import dev.egateza.termul.ssh.hostkey.HostKeyInfo;
import dev.egateza.termul.ssh.hostkey.HostKeyPrompt;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.ssh.testing.TestSshServer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SshTtyConnectorTest {

    @TempDir
    Path dir;

    private TestSshServer server;
    private SessionManager sessions;
    private SshTerminalFactory factory;
    private HostProfile profile;

    @BeforeEach
    void setUp() throws Exception {
        server = new TestSshServer(dir.resolve("hostkey.ser"));
        var trustAll = new HostKeyPrompt() {
            @Override
            public boolean confirmUnknownHost(HostKeyInfo info) {
                return true;
            }

            @Override
            public void hostKeyChanged(HostKeyInfo presented, List<String> knownFingerprints) {
            }
        };
        var credentials = new CredentialProvider() {
            @Override
            public char[] password(HostProfile p, int attempt) {
                return TestSshServer.PASSWORD.toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile p, Path keyFile, int attempt) {
                return null;
            }
        };
        var settings = new SshSettings(Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(10),
                Duration.ZERO, 0, Duration.ZERO);
        sessions = new SessionManager(new KnownHostsStore(dir.resolve("known_hosts")), trustAll, credentials, settings);
        factory = new SshTerminalFactory(sessions);
        profile = new HostProfile(UUID.randomUUID(), "echo", "", "127.0.0.1", server.port(), TestSshServer.USER,
                AuthMethod.PASSWORD, null, null, EnvironmentTag.DEV, null, null, false);
    }

    @AfterEach
    void tearDown() throws Exception {
        sessions.close();
        server.close();
    }

    @Test
    void readWriteResizeDanOutputListener() throws Exception {
        SshTtyConnector tty = factory.open(profile, new PtySize(90, 30));
        var seen = new StringBuilder();
        tty.addOutputListener((buf, off, len) -> {
            synchronized (seen) {
                seen.append(buf, off, len);
            }
        });

        tty.write("halo dunia ü\r");
        String out = readUntil(tty, "echo:halo dunia ü");
        assertThat(out).contains("welcome");
        synchronized (seen) {
            assertThat(seen.toString()).contains("echo:halo dunia ü");
        }

        tty.resize(new TermSize(132, 43));
        await().atMost(Duration.ofSeconds(5)).until(() -> server.windowChanges.contains("132x43"));
        assertThat(tty.isConnected()).isTrue();

        tty.closeByUser();
        await().atMost(Duration.ofSeconds(5)).until(() -> sessions.connectionCount() == 0);
        assertThat(tty.isClosedByUser()).isTrue();
    }

    @Test
    void remoteExitMemicuCloseListenerDanMelepasKoneksi() throws Exception {
        SshTtyConnector tty = factory.open(profile, SshTerminalFactory.DEFAULT_SIZE);
        var closed = new AtomicReference<SshTtyConnector>();
        tty.addCloseListener(closed::set);

        readUntil(tty, "$ ");
        tty.write("exit\r");
        // JediTerm sendiri memanggil close() setelah stream EOF: tetap bukan penutupan oleh user
        var buf = new char[256];
        while (tty.read(buf, 0, buf.length) >= 0) {
            // habiskan output sampai EOF
        }
        tty.close();

        await().atMost(Duration.ofSeconds(5)).until(() -> closed.get() != null);
        assertThat(tty.isClosedByUser()).isFalse();
        assertThat(tty.isConnected()).isFalse();
        await().atMost(Duration.ofSeconds(5)).until(() -> sessions.connectionCount() == 0);

        // listener yang didaftarkan setelah tertutup tetap dipanggil
        var late = new AtomicReference<SshTtyConnector>();
        tty.addCloseListener(late::set);
        assertThat(late.get()).isSameAs(tty);
    }

    @Test
    void koneksiPutusDitandaiBedaDenganExit() throws Exception {
        SshTtyConnector tty = factory.open(profile, SshTerminalFactory.DEFAULT_SIZE);
        var closed = new AtomicReference<SshTtyConnector>();
        tty.addCloseListener(closed::set);
        readUntil(tty, "$ ");

        server.dropAllSessions(); // jaringan putus / server menutup koneksi

        await().atMost(Duration.ofSeconds(10)).until(() -> closed.get() != null);
        assertThat(tty.isConnectionLost()).isTrue();
        assertThat(tty.isClosedByUser()).isFalse();
    }

    @Test
    void exitBiasaBukanKoneksiPutus() throws Exception {
        SshTtyConnector tty = factory.open(profile, SshTerminalFactory.DEFAULT_SIZE);
        var closed = new AtomicReference<SshTtyConnector>();
        tty.addCloseListener(closed::set);
        readUntil(tty, "$ ");
        tty.write("exit\r");
        var buf = new char[256];
        while (tty.read(buf, 0, buf.length) >= 0) {
            // habiskan output sampai EOF
        }
        tty.close();

        await().atMost(Duration.ofSeconds(5)).until(() -> closed.get() != null);
        assertThat(tty.isConnectionLost()).isFalse();
    }

    @Test
    void writeSetelahCloseDiabaikanTanpaException() throws Exception {
        SshTtyConnector tty = factory.open(profile, SshTerminalFactory.DEFAULT_SIZE);
        tty.close();
        tty.write("ls\r");
        tty.writeSecret("rahasia".getBytes());
        tty.resize(new TermSize(10, 10));
    }

    @Test
    void writeSecretMenzeroArrayPemanggil() throws Exception {
        SshTtyConnector tty = factory.open(profile, SshTerminalFactory.DEFAULT_SIZE);
        readUntil(tty, "$ ");
        byte[] secret = "pw123\r".getBytes();

        tty.writeSecret(secret);

        assertThat(secret).containsOnly(0);
        readUntil(tty, "echo:pw123");
        tty.close();
    }

    private static String readUntil(SshTtyConnector tty, String marker) throws Exception {
        var sb = new StringBuilder();
        var buf = new char[512];
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            int n = tty.read(buf, 0, buf.length);
            if (n < 0) {
                break;
            }
            sb.append(buf, 0, n);
            if (sb.toString().contains(marker)) {
                return sb.toString();
            }
        }
        throw new AssertionError("Marker '" + marker + "' tidak muncul: " + sb);
    }
}

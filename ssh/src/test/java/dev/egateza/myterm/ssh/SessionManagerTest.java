package dev.egateza.myterm.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.egateza.myterm.core.profile.AuthMethod;
import dev.egateza.myterm.core.profile.EnvironmentTag;
import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.ssh.auth.CredentialProvider;
import dev.egateza.myterm.ssh.hostkey.HostKeyInfo;
import dev.egateza.myterm.ssh.hostkey.HostKeyPrompt;
import dev.egateza.myterm.ssh.hostkey.HostKeyRejectedException;
import dev.egateza.myterm.ssh.hostkey.HostKeyVerdict;
import dev.egateza.myterm.ssh.hostkey.KnownHostsStore;
import dev.egateza.myterm.ssh.testing.TestSshServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionManagerTest {

    @TempDir
    Path dir;

    private TestSshServer server;
    private KnownHostsStore knownHosts;
    private final List<HostKeyInfo> tofuPrompts = new CopyOnWriteArrayList<>();
    private final AtomicInteger passwordPrompts = new AtomicInteger();
    private volatile String passwordToGive = TestSshServer.PASSWORD;
    private volatile String passphraseToGive;
    private SessionManager manager;

    private final HostKeyPrompt acceptAll = new HostKeyPrompt() {
        @Override
        public boolean confirmUnknownHost(HostKeyInfo info) {
            tofuPrompts.add(info);
            return true;
        }

        @Override
        public void hostKeyChanged(HostKeyInfo presented, List<String> knownFingerprints) {
        }
    };

    private final CredentialProvider credentials = new CredentialProvider() {
        @Override
        public char[] password(HostProfile profile, int attempt) {
            passwordPrompts.incrementAndGet();
            return passwordToGive == null ? null : passwordToGive.toCharArray();
        }

        @Override
        public char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt) {
            return passphraseToGive == null ? null : passphraseToGive.toCharArray();
        }
    };

    @BeforeEach
    void setUp() throws Exception {
        server = new TestSshServer(dir.resolve("hostkey.ser"));
        knownHosts = new KnownHostsStore(dir.resolve("known_hosts"));
        manager = newManager(Duration.ZERO);
    }

    @AfterEach
    void tearDown() throws Exception {
        manager.close();
        server.close();
    }

    private SessionManager newManager(Duration grace) {
        var settings = new SshSettings(Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(10),
                Duration.ofSeconds(1), 2, grace);
        return new SessionManager(knownHosts, acceptAll, credentials, settings);
    }

    private HostProfile profile(AuthMethod auth, String keyPath) {
        return new HostProfile(UUID.randomUUID(), "test", "", "127.0.0.1", server.port(), TestSshServer.USER,
                auth, keyPath, null, EnvironmentTag.DEV, null, null, false);
    }

    @Test
    void connectPasswordTofuDanShellEcho() throws Exception {
        var profile = profile(AuthMethod.PASSWORD, null);

        try (SshLease lease = manager.acquire(profile)) {
            assertThat(tofuPrompts).hasSize(1);
            assertThat(passwordPrompts).hasValue(1);

            var shell = lease.connection().openShell(new PtySize(120, 40), Map.of());
            await().atMost(Duration.ofSeconds(5)).until(() -> server.lastShellEnv != null);
            assertThat(server.lastShellEnv.getEnv()).containsEntry("TERM", "xterm-256color")
                    .containsEntry("COLUMNS", "120").containsEntry("LINES", "40");

            OutputStream in = shell.getInvertedIn();
            in.write("hello\r".getBytes(StandardCharsets.UTF_8));
            in.flush();
            assertThat(readUntil(shell.getInvertedOut(), "echo:hello")).contains("welcome");

            shell.sendWindowChange(100, 30);
            await().atMost(Duration.ofSeconds(5)).until(() -> server.windowChanges.contains("100x30"));
            shell.close(false);
        }
        assertThat(Files.readString(knownHosts.file())).startsWith("[127.0.0.1]:" + server.port());
    }

    @Test
    void acquireKeduaMemakaiKoneksiYangSamaDanTutupSaatRefNol() throws Exception {
        var profile = profile(AuthMethod.PASSWORD, null);

        SshLease a = manager.acquire(profile);
        SshLease b = manager.acquire(profile);
        assertThat(b.connection()).isSameAs(a.connection());
        assertThat(passwordPrompts).hasValue(1);
        assertThat(server.server().getActiveSessions()).hasSize(1);

        a.close();
        a.close(); // idempotent
        assertThat(b.connection().isOpen()).isTrue();

        SshConnection conn = b.connection();
        b.close();
        await().atMost(Duration.ofSeconds(5)).until(() -> !conn.isOpen());
        assertThat(manager.connectionCount()).isZero();
    }

    @Test
    void graceMenahanKoneksiUntukReopenTab() throws Exception {
        manager.close();
        manager = newManager(Duration.ofSeconds(30));
        var profile = profile(AuthMethod.PASSWORD, null);

        SshLease a = manager.acquire(profile);
        SshConnection first = a.connection();
        a.close();
        SshLease b = manager.acquire(profile);

        assertThat(b.connection()).isSameAs(first);
        assertThat(passwordPrompts).hasValue(1);
        b.close();
    }

    @Test
    void passwordSalahDicobaMaksimalTigaKali() {
        passwordToGive = "salah";
        var profile = profile(AuthMethod.PASSWORD, null);

        assertThatThrownBy(() -> manager.acquire(profile))
                .isInstanceOf(SshConnectException.class)
                .hasMessageContaining("gagal");
        assertThat(passwordPrompts.get()).isBetween(1, 3);
        assertThat(manager.connectionCount()).isZero();
    }

    @Test
    void userMembatalkanPrompt() {
        passwordToGive = null;

        assertThatThrownBy(() -> manager.acquire(profile(AuthMethod.PASSWORD, null)))
                .isInstanceOf(SshConnectException.class)
                .hasMessageContaining("dibatalkan");
    }

    @Test
    void hostKeyBerubahDitolak() throws Exception {
        var gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(256);
        knownHosts.add("127.0.0.1", server.port(), gen.generateKeyPair().getPublic());

        assertThatThrownBy(() -> manager.acquire(profile(AuthMethod.PASSWORD, null)))
                .isInstanceOfSatisfying(HostKeyRejectedException.class,
                        e -> assertThat(e.verdict()).isInstanceOf(HostKeyVerdict.Changed.class))
                .hasMessageContaining("BERUBAH");
        assertThat(passwordPrompts).hasValue(0);
        assertThat(tofuPrompts).isEmpty();
    }

    @Test
    void authPrivateKeyTanpaDanDenganPassphrase() throws Exception {
        KeyPair plain = ecKeyPair();
        KeyPair encrypted = ecKeyPair();
        server.authorize(plain.getPublic());
        server.authorize(encrypted.getPublic());
        Path plainFile = writeKey(plain, null);
        Path encFile = writeKey(encrypted, "rahasia");

        try (var lease = manager.acquire(profile(AuthMethod.KEY, plainFile.toString()))) {
            assertThat(lease.connection().isOpen()).isTrue();
        }

        passphraseToGive = "rahasia";
        try (var lease = manager.acquire(profile(AuthMethod.KEY, encFile.toString()))) {
            assertThat(lease.connection().isOpen()).isTrue();
        }
        assertThat(passwordPrompts).hasValue(0);
    }

    @Test
    void authDefaultTanpaKeyJatuhKePassword() throws Exception {
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", dir.resolve("home-tanpa-key").toString());
        try (var lease = manager.acquire(profile(AuthMethod.AGENT, null))) {
            assertThat(lease.connection().isOpen()).isTrue();
            assertThat(passwordPrompts).hasValue(1);
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    @Test
    void keyFileTidakAda() {
        assertThatThrownBy(() -> manager.acquire(profile(AuthMethod.KEY, dir.resolve("nope").toString())))
                .isInstanceOf(SshConnectException.class)
                .hasMessageContaining("tidak ditemukan");
    }

    @Test
    void koneksiPutusMemicuListenerDanAcquireBerikutnyaReconnect() throws Exception {
        var profile = profile(AuthMethod.PASSWORD, null);
        SshLease lease = manager.acquire(profile);
        var lost = new AtomicBoolean();
        lease.connection().addCloseListener(c -> lost.set(!c.isClosedByManager()));

        server.dropAllSessions();

        await().atMost(Duration.ofSeconds(10)).untilTrue(lost);
        try (SshLease again = manager.acquire(profile)) {
            assertThat(again.connection()).isNotSameAs(lease.connection());
            assertThat(again.connection().isOpen()).isTrue();
        }
        lease.close();
    }

    @Test
    void hostTidakBisaDijangkau() throws Exception {
        int closedPort;
        try (var s = new java.net.ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        var profile = new HostProfile(UUID.randomUUID(), "x", "", "127.0.0.1", closedPort, "u",
                AuthMethod.PASSWORD, null, null, null, null, null, false);

        assertThatThrownBy(() -> manager.acquire(profile))
                .isInstanceOf(SshConnectException.class)
                .hasMessageContaining("127.0.0.1:" + closedPort);
    }

    private static KeyPair ecKeyPair() throws Exception {
        var gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(256);
        return gen.generateKeyPair();
    }

    private Path writeKey(KeyPair kp, String passphrase) throws Exception {
        Path file = dir.resolve("key-" + UUID.randomUUID());
        OpenSSHKeyEncryptionContext ctx = null;
        if (passphrase != null) {
            ctx = new OpenSSHKeyEncryptionContext();
            ctx.setCipherName("AES");
            ctx.setCipherMode("CTR");
            ctx.setCipherType("256");
            ctx.setPassword(passphrase);
        }
        try (var out = Files.newOutputStream(file)) {
            OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(kp, "test", ctx, out);
        }
        return file;
    }

    private static String readUntil(InputStream in, String marker) throws Exception {
        var buf = new ByteArrayOutputStream();
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        var chunk = new byte[1024];
        while (System.nanoTime() < deadline) {
            if (in.available() > 0 || buf.size() == 0) {
                int n = in.read(chunk);
                if (n < 0) {
                    break;
                }
                buf.write(chunk, 0, n);
                if (buf.toString(StandardCharsets.UTF_8).contains(marker)) {
                    return buf.toString(StandardCharsets.UTF_8);
                }
            } else {
                Thread.sleep(20);
            }
        }
        throw new AssertionError("Marker '" + marker + "' tidak muncul. Output: " + buf.toString(StandardCharsets.UTF_8));
    }
}

package dev.egateza.termul.ssh.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.tunnel.Tunnel;
import dev.egateza.termul.core.tunnel.TunnelType;
import dev.egateza.termul.ssh.SessionManager;
import dev.egateza.termul.ssh.SshLease;
import dev.egateza.termul.ssh.SshSettings;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import dev.egateza.termul.ssh.hostkey.HostKeyInfo;
import dev.egateza.termul.ssh.hostkey.HostKeyPrompt;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.ssh.testing.TestSshServer;
import dev.egateza.termul.ssh.tunnel.TunnelStatus.Failed;
import dev.egateza.termul.ssh.tunnel.TunnelStatus.Reason;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TunnelManagerTest {

    @TempDir
    Path dir;

    private TestSshServer server;
    private SessionManager sessions;
    private TunnelManager manager;
    private ExecutorService executor;
    private EchoServer echo;
    private HostProfile host;
    private final List<Tunnel> saved = new CopyOnWriteArrayList<>();

    private final HostKeyPrompt trust = new HostKeyPrompt() {
        @Override
        public boolean confirmUnknownHost(HostKeyInfo info) {
            return true;
        }

        @Override
        public void hostKeyChanged(HostKeyInfo presented, List<String> knownFingerprints) {
        }
    };

    private final CredentialProvider credentials = new CredentialProvider() {
        @Override
        public char[] password(HostProfile profile, int attempt) {
            return TestSshServer.PASSWORD.toCharArray();
        }

        @Override
        public char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt) {
            return null;
        }
    };

    @BeforeEach
    void setUp() throws Exception {
        server = new TestSshServer(dir.resolve("hostkey.ser"),
                s -> s.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE));
        host = new HostProfile(UUID.randomUUID(), "test", "", "127.0.0.1", server.port(), TestSshServer.USER,
                AuthMethod.PASSWORD, null, null, EnvironmentTag.DEV, null, null, false);
        var settings = new SshSettings(Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(10),
                Duration.ofSeconds(1), 2, Duration.ZERO);
        sessions = new SessionManager(new KnownHostsStore(dir.resolve("known_hosts")), trust, credentials, settings,
                id -> Optional.ofNullable(Map.of(host.id(), host).get(id)));
        executor = Executors.newVirtualThreadPerTaskExecutor();
        manager = new TunnelManager(sessions, id -> id.equals(host.id()) ? Optional.of(host) : Optional.empty(),
                () -> saved, executor, Duration.ofSeconds(10));
        echo = new EchoServer();
    }

    @AfterEach
    void tearDown() throws Exception {
        manager.close();
        sessions.close();
        executor.shutdownNow();
        echo.close();
        server.close();
    }

    private Tunnel tunnel(TunnelType type, int source, int destination, boolean auto) {
        var t = Tunnel.create("t-" + type, type, host.id(), source, "127.0.0.1", destination, false, auto);
        saved.add(t);
        return t;
    }

    private void awaitStatus(Tunnel t, Class<? extends TunnelStatus> type) {
        await().atMost(Duration.ofSeconds(15)).until(() -> type.isInstance(manager.state(t.id()).status()));
    }

    @Test
    void localMeneruskanKeTujuanDanMenghitungByte() throws Exception {
        var t = tunnel(TunnelType.LOCAL, freePort(), echo.port(), false);
        var changes = new CopyOnWriteArrayList<String>();
        manager.addListener(() -> changes.add(manager.state(t.id()).status().getClass().getSimpleName()));

        manager.start(t);
        awaitStatus(t, TunnelStatus.Active.class);

        assertThat(roundTrip(t.sourcePort(), "halo")).isEqualTo("halo");
        await().until(() -> manager.state(t.id()).bytes() >= 8); // 4 byte pergi + 4 byte pulang
        assertThat(manager.runningCount()).isEqualTo(1);
        assertThat(changes).contains("Starting", "Active");

        manager.stop(t.id());
        assertThat(manager.state(t.id())).isEqualTo(TunnelState.STOPPED);
        await().until(() -> !canConnect(t.sourcePort()));
    }

    @Test
    void koneksiYangSedangTerbukaTerhitung() throws Exception {
        var t = tunnel(TunnelType.LOCAL, freePort(), echo.port(), false);
        manager.start(t);
        awaitStatus(t, TunnelStatus.Active.class);

        try (var socket = new Socket(InetAddress.getLoopbackAddress(), t.sourcePort())) {
            write(socket.getOutputStream(), "a");
            assertThat(read(socket.getInputStream(), 1)).isEqualTo("a");
            assertThat(manager.state(t.id()).connections()).isEqualTo(1);
            assertThat(manager.state(t.id()).clients()).containsExactly("127.0.0.1");
            // port dipegang tunnel ini sendiri: form tidak menganggapnya "dipakai program lain"
            assertThat(manager.holdsPort(t.id(), "127.0.0.1", t.sourcePort())).isTrue();
            assertThat(manager.holdsPort(t.id(), "0.0.0.0", t.sourcePort())).isFalse();
            assertThat(TunnelManager.isPortFree("127.0.0.1", t.sourcePort())).isFalse();
        }
        await().until(() -> manager.state(t.id()).connections() == 0);
        assertThat(manager.state(t.id()).clients()).isEmpty();
    }

    @Test
    void dynamicSocks5DanSocks4a() throws Exception {
        var t = tunnel(TunnelType.DYNAMIC, freePort(), 0, false);
        manager.start(t);
        awaitStatus(t, TunnelStatus.Active.class);

        try (var socket = new Socket(InetAddress.getLoopbackAddress(), t.sourcePort())) {
            var out = socket.getOutputStream();
            var in = new DataInputStream(socket.getInputStream());
            out.write(new byte[] {5, 1, 0});
            assertThat(in.readNBytes(2)).containsExactly(5, 0);
            byte[] name = "localhost".getBytes(StandardCharsets.US_ASCII);
            out.write(new byte[] {5, 1, 0, 3, (byte) name.length});
            out.write(name);
            out.write(new byte[] {(byte) (echo.port() >> 8), (byte) echo.port()});
            assertThat(in.readNBytes(10)[1]).isZero(); // berhasil
            write(out, "socks5");
            assertThat(read(in, 6)).isEqualTo("socks5");
        }

        try (var socket = new Socket(InetAddress.getLoopbackAddress(), t.sourcePort())) {
            var out = socket.getOutputStream();
            var in = socket.getInputStream();
            out.write(new byte[] {4, 1, (byte) (echo.port() >> 8), (byte) echo.port(), 0, 0, 0, 1, 'u', 0});
            out.write("127.0.0.1".getBytes(StandardCharsets.US_ASCII));
            out.write(0);
            assertThat(in.readNBytes(8)[1]).isEqualTo((byte) 0x5A);
            write(out, "socks4a");
            assertThat(read(in, 7)).isEqualTo("socks4a");
        }
    }

    @Test
    void socksKeTujuanTertutupDibalasGagal() throws Exception {
        var t = tunnel(TunnelType.DYNAMIC, freePort(), 0, false);
        manager.start(t);
        awaitStatus(t, TunnelStatus.Active.class);
        int closedPort = freePort();

        try (var socket = new Socket(InetAddress.getLoopbackAddress(), t.sourcePort())) {
            var out = socket.getOutputStream();
            var in = socket.getInputStream();
            out.write(new byte[] {5, 1, 0});
            in.readNBytes(2);
            out.write(new byte[] {5, 1, 0, 1, 127, 0, 0, 1, (byte) (closedPort >> 8), (byte) closedPort});
            assertThat(in.readNBytes(10)[1]).isNotZero();
        }
        await().until(() -> manager.state(t.id()).issue() != null);
        assertThat(manager.state(t.id()).status()).isInstanceOf(TunnelStatus.Active.class);
    }

    @Test
    void remoteMembukaPortDiServerKeLayananDiPcIni() throws Exception {
        var t = tunnel(TunnelType.REMOTE, freePort(), echo.port(), false);
        manager.start(t);
        awaitStatus(t, TunnelStatus.Active.class);

        // server in-process: port remote-nya dibuka di loopback mesin ini
        assertThat(roundTrip(t.sourcePort(), "dari server")).isEqualTo("dari server");
        try (var socket = new Socket(InetAddress.getLoopbackAddress(), t.sourcePort())) {
            await().until(() -> !manager.state(t.id()).clients().isEmpty());
            assertThat(manager.state(t.id()).clients()).containsExactly(""); // datang dari server
        }
        await().until(() -> manager.state(t.id()).bytes() >= 22);

        manager.stop(t.id());
        await().until(() -> !canConnect(t.sourcePort()));
    }

    @Test
    void portDipakaiProgramLainGagalDenganSaranPort() throws Exception {
        try (var busy = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            var t = tunnel(TunnelType.LOCAL, busy.getLocalPort(), echo.port(), false);
            manager.start(t);
            awaitStatus(t, Failed.class);

            var failed = (Failed) manager.state(t.id()).status();
            assertThat(failed.reason()).isIn(Reason.PORT_IN_USE, Reason.PORT_DENIED);
            assertThat(failed.suggestedPort()).isGreaterThan(busy.getLocalPort());
            assertThat(manager.runningCount()).isZero();
            assertThat(sessions.connectionCount()).isZero(); // dicek sebelum connect: tidak login ke host
            assertThat(TunnelManager.isPortFree("127.0.0.1", busy.getLocalPort())).isFalse();

            // port saran tidak dipakai di sini: di test, port ephemeral bisa diambil socket keluar client SSH
            manager.start(t.withSourcePort(freePort()));
            awaitStatus(t, TunnelStatus.Active.class);
        }
    }

    @Test
    void hostTidakAdaGagalTanpaConnect() {
        var t = Tunnel.create("x", TunnelType.LOCAL, UUID.randomUUID(), 5433, "localhost", 5432, false, false);
        manager.start(t);
        awaitStatus(t, Failed.class);

        assertThat(((Failed) manager.state(t.id()).status()).reason()).isEqualTo(Reason.HOST_MISSING);
        assertThat(sessions.connectionCount()).isZero();
    }

    @Test
    void tujuanYangTidakBisaDijangkauTidakDinyalakan() throws Exception {
        int closed = freePort(); // tidak ada yang mendengarkan: server menolak membuka tujuan
        var t = tunnel(TunnelType.LOCAL, freePort(), closed, false);
        var seen = new CopyOnWriteArrayList<String>();
        manager.addListener(() -> seen.add(manager.state(t.id()).status().getClass().getSimpleName()));

        manager.start(t);
        awaitStatus(t, Failed.class);

        var failed = (Failed) manager.state(t.id()).status();
        assertThat(failed.reason()).isEqualTo(Reason.DESTINATION_UNREACHABLE);
        assertThat(failed.detail()).startsWith("127.0.0.1:" + closed + " — ");
        assertThat(seen).doesNotContain("Active"); // tidak pernah aktif
        await().until(() -> !canConnect(t.sourcePort())); // port lokal ditutup lagi
        assertThat(manager.runningCount()).isZero();
    }

    @Test
    void layananRemoteYangMatiTidakDinyalakan() throws Exception {
        var t = tunnel(TunnelType.REMOTE, freePort(), freePort(), false);
        manager.start(t);
        awaitStatus(t, Failed.class);

        assertThat(((Failed) manager.state(t.id()).status()).reason()).isEqualTo(Reason.SERVICE_UNREACHABLE);
        assertThat(canConnect(t.sourcePort())).isFalse(); // tidak ada port yang dibuka di server
    }

    private static java.util.Map<TunnelManager.Step, TunnelManager.TestStep> byStep(List<TunnelManager.TestStep> s) {
        var map = new java.util.LinkedHashMap<TunnelManager.Step, TunnelManager.TestStep>();
        s.forEach(x -> map.put(x.step(), x));
        return map;
    }

    @Test
    void testLocalSemuaLangkahLulusTanpaMembukaTunnel() throws Exception {
        var t = Tunnel.create("pg", TunnelType.LOCAL, host.id(), freePort(), "127.0.0.1", echo.port(), false, false);

        var steps = manager.test(t);

        assertThat(steps).extracting(TunnelManager.TestStep::step).containsExactly(TunnelManager.Step.LOCAL_PORT,
                TunnelManager.Step.CONNECT, TunnelManager.Step.DESTINATION);
        assertThat(steps).allMatch(s -> s.check() == TunnelManager.Check.OK);
        assertThat(manager.state(t.id())).isEqualTo(TunnelState.STOPPED);
        assertThat(canConnect(t.sourcePort())).isFalse(); // test tidak membuka port
    }

    @Test
    void testMenunjukkanLangkahYangGagal() throws Exception {
        try (var busy = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            int closed = freePort();
            var t = Tunnel.create("pg", TunnelType.LOCAL, host.id(), busy.getLocalPort(), "127.0.0.1", closed,
                    false, false);

            var steps = byStep(manager.test(t));

            assertThat(steps.get(TunnelManager.Step.LOCAL_PORT).check()).isEqualTo(TunnelManager.Check.PROBLEM);
            assertThat(steps.get(TunnelManager.Step.LOCAL_PORT).suggestedPort()).isGreaterThan(busy.getLocalPort());
            assertThat(steps.get(TunnelManager.Step.CONNECT).check()).isEqualTo(TunnelManager.Check.OK);
            assertThat(steps.get(TunnelManager.Step.DESTINATION).check()).isEqualTo(TunnelManager.Check.PROBLEM);
            assertThat(steps.get(TunnelManager.Step.DESTINATION).detail()).isNotBlank();
        }
    }

    @Test
    void testHostGagalConnectMelewatiLangkahServer() throws Exception {
        var unknown = Tunnel.create("x", TunnelType.LOCAL, UUID.randomUUID(), freePort(), "127.0.0.1", 1, false,
                false);
        assertThat(byStep(manager.test(unknown)).get(TunnelManager.Step.CONNECT).check())
                .isEqualTo(TunnelManager.Check.PROBLEM);

        server.close(); // server mati: connect gagal, tujuan tidak dicek
        var t = Tunnel.create("pg", TunnelType.LOCAL, host.id(), freePort(), "127.0.0.1", echo.port(), false, false);
        var steps = byStep(manager.test(t));
        assertThat(steps.get(TunnelManager.Step.CONNECT).check()).isEqualTo(TunnelManager.Check.PROBLEM);
        assertThat(steps).doesNotContainKey(TunnelManager.Step.DESTINATION);
        server = new TestSshServer(dir.resolve("hostkey.ser")); // untuk tearDown
    }

    @Test
    void testRemoteLayananDanPortServer() throws Exception {
        // server test tanpa exec: daftar port tidak bisa dibaca
        var t = Tunnel.create("hook", TunnelType.REMOTE, host.id(), freePort(), "127.0.0.1", echo.port(), false,
                false);
        var steps = byStep(manager.test(t));
        assertThat(steps.get(TunnelManager.Step.SERVICE).check()).isEqualTo(TunnelManager.Check.OK);
        assertThat(steps.get(TunnelManager.Step.SERVER_PORT).check()).isEqualTo(TunnelManager.Check.UNKNOWN);

        var down = Tunnel.create("hook", TunnelType.REMOTE, host.id(), freePort(), "127.0.0.1", freePort(), false,
                false);
        assertThat(byStep(manager.test(down)).get(TunnelManager.Step.SERVICE).check())
                .isEqualTo(TunnelManager.Check.PROBLEM);
    }

    @Test
    void portServerYangDipakaiDitolakSebelumForward() throws Exception {
        int used = freePort();
        server.server().setCommandFactory((channel, command) -> new Output(
                "LISTEN 0 128 0.0.0.0:22 0.0.0.0:*\nLISTEN 0 128 127.0.0.1:" + used + " 0.0.0.0:*\n"));
        var t = tunnel(TunnelType.REMOTE, used, echo.port(), false);

        manager.start(t);
        awaitStatus(t, Failed.class);

        var failed = (Failed) manager.state(t.id()).status();
        assertThat(failed.reason()).isEqualTo(Reason.REMOTE_PORT_IN_USE);
        assertThat(failed.suggestedPort()).isEqualTo(used + 1);
        var check = byStep(manager.test(t)).get(TunnelManager.Step.SERVER_PORT);
        assertThat(check.check()).isEqualTo(TunnelManager.Check.PROBLEM);
        assertThat(check.suggestedPort()).isEqualTo(used + 1);
        assertThat(byStep(manager.test(t.withSourcePort(used + 1))).get(TunnelManager.Step.SERVER_PORT).check())
                .isEqualTo(TunnelManager.Check.OK);
    }

    /** Command exec palsu: mencetak teks tetap (pengganti {@code ss -Hltn}) lalu selesai. */
    static final class Output implements org.apache.sshd.server.command.Command {
        private final String text;
        private OutputStream out;
        private org.apache.sshd.server.ExitCallback exit;

        Output(String text) {
            this.text = text;
        }

        @Override
        public void setInputStream(InputStream in) {
        }

        @Override
        public void setOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void setErrorStream(OutputStream err) {
        }

        @Override
        public void setExitCallback(org.apache.sshd.server.ExitCallback callback) {
            this.exit = callback;
        }

        @Override
        public void start(org.apache.sshd.server.channel.ChannelSession channel,
                          org.apache.sshd.server.Environment env) throws IOException {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
            exit.onExit(0);
        }

        @Override
        public void destroy(org.apache.sshd.server.channel.ChannelSession channel) {
        }
    }

    @Test
    void autoStartSaatHostTersambung() throws Exception {
        var auto = tunnel(TunnelType.LOCAL, freePort(), echo.port(), true);
        var manual = tunnel(TunnelType.LOCAL, freePort(), echo.port(), false);

        try (SshLease tab = sessions.acquire(host)) { // mis. tab terminal dibuka
            awaitStatus(auto, TunnelStatus.Active.class);
            assertThat(manager.state(manual.id()).status()).isInstanceOf(TunnelStatus.Stopped.class);
            assertThat(roundTrip(auto.sourcePort(), "x")).isEqualTo("x");
        }
        // tab ditutup: tunnel tetap jalan (memegang koneksinya sendiri)
        assertThat(roundTrip(auto.sourcePort(), "y")).isEqualTo("y");
    }

    @Test
    void putusLaluJalanLagiSaatHostTersambung() throws Exception {
        var t = tunnel(TunnelType.LOCAL, freePort(), echo.port(), false);
        manager.start(t);
        awaitStatus(t, TunnelStatus.Active.class);

        server.dropAllSessions();
        awaitStatus(t, Failed.class);
        assertThat(((Failed) manager.state(t.id()).status()).reason()).isEqualTo(Reason.DISCONNECTED);
        await().until(() -> !canConnect(t.sourcePort()));

        try (SshLease reconnect = sessions.acquire(host)) {
            awaitStatus(t, TunnelStatus.Active.class);
            assertThat(roundTrip(t.sourcePort(), "lagi")).isEqualTo("lagi");
        }
    }

    // --- helper ---

    private static int freePort() throws IOException {
        try (var s = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            return s.getLocalPort();
        }
    }

    private static boolean canConnect(int port) {
        try (var s = new Socket(InetAddress.getLoopbackAddress(), port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String roundTrip(int port, String text) throws IOException {
        try (var socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(10_000);
            write(socket.getOutputStream(), text);
            return read(socket.getInputStream(), text.length());
        }
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String read(InputStream in, int n) throws IOException {
        return new String(in.readNBytes(n), StandardCharsets.UTF_8);
    }

    /** Layanan tujuan: mengembalikan semua yang diterima. */
    static final class EchoServer implements AutoCloseable {
        private final ServerSocket socket;

        EchoServer() throws IOException {
            socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            Thread.ofVirtual().start(() -> {
                while (!socket.isClosed()) {
                    try {
                        Socket s = socket.accept();
                        Thread.ofVirtual().start(() -> {
                            try (s) {
                                s.getInputStream().transferTo(s.getOutputStream());
                            } catch (IOException e) {
                                // koneksi selesai
                            }
                        });
                    } catch (IOException e) {
                        return;
                    }
                }
            });
        }

        int port() {
            return socket.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}

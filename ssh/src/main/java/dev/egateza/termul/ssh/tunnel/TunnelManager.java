package dev.egateza.termul.ssh.tunnel;

import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.tunnel.Tunnel;
import dev.egateza.termul.core.tunnel.TunnelType;
import dev.egateza.termul.ssh.SessionManager;
import dev.egateza.termul.ssh.SshConnectException;
import dev.egateza.termul.ssh.SshConnection;
import dev.egateza.termul.ssh.SshLease;
import dev.egateza.termul.ssh.tunnel.TunnelStatus.Failed;
import dev.egateza.termul.ssh.tunnel.TunnelStatus.Reason;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.sshd.client.channel.ChannelDirectTcpip;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Menjalankan tunnel tersimpan lewat koneksi SSH bersama ({@link SessionManager}). Setiap tunnel yang jalan memegang
 * {@link SshLease} sendiri, jadi koneksinya tetap hidup walau tab terminal host itu ditutup.
 *
 * <ul>
 *   <li><b>Local</b>: socket di PC ini ({@link Tunnel#bindAddress()}), setiap koneksi dibuka sebagai channel
 *       {@code direct-tcpip} ke tujuan.</li>
 *   <li><b>Dynamic</b>: sama, tapi tujuannya dibaca dari permintaan SOCKS4/4a/5 aplikasi.</li>
 *   <li><b>Remote</b>: {@code tcpip-forward} di loopback server; koneksi dari server diteruskan MINA ke relay
 *       {@code 127.0.0.1:<acak>} milik TermUL, lalu ke layanan tujuan di PC ini (supaya byte dan koneksi terhitung).</li>
 * </ul>
 *
 * <p>Tunnel dengan {@link Tunnel#autoStart()} jalan sendiri setiap kali koneksi baru ke host-nya tersambung; tunnel
 * yang berhenti karena koneksi putus juga jalan lagi saat host tersambung. Semua I/O di {@code executor} dan virtual
 * thread; method publik tidak blocking dan boleh dipanggil dari EDT. Listener dipanggil dari thread mana saja.
 */
public final class TunnelManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TunnelManager.class);
    /** Batas tunggu connect ke layanan tujuan di PC ini (tunnel remote). */
    static final Duration LOCAL_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** Jumlah port setelah port yang dipakai yang dicoba saat mencari pengganti. */
    static final int SUGGEST_RANGE = 200;
    /** Batas tunggu membaca daftar port di server. */
    static final Duration CHECK_TIMEOUT = Duration.ofSeconds(10);
    private static final String SERVER_LOOPBACK = "localhost";

    private final SessionManager sessions;
    private final Function<UUID, Optional<HostProfile>> profiles;
    private final Supplier<List<Tunnel>> tunnels;
    private final Executor executor;
    private final Duration channelOpenTimeout;
    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Consumer<SshConnection> onConnected = this::hostConnected;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * @param profiles mencari profil host terbaru dari store
     * @param tunnels  daftar tunnel tersimpan terbaru (untuk auto-start dan pencarian port pengganti)
     * @param executor untuk connect dan menutup tunnel (blocking)
     */
    public TunnelManager(SessionManager sessions, Function<UUID, Optional<HostProfile>> profiles,
                         Supplier<List<Tunnel>> tunnels, Executor executor, Duration channelOpenTimeout) {
        this.sessions = Objects.requireNonNull(sessions);
        this.profiles = Objects.requireNonNull(profiles);
        this.tunnels = Objects.requireNonNull(tunnels);
        this.executor = Objects.requireNonNull(executor);
        this.channelOpenTimeout = Objects.requireNonNull(channelOpenTimeout);
        sessions.addConnectionListener(onConnected);
    }

    /** Satu kali start sebuah tunnel; diganti objek baru setiap start. */
    private final class Run {
        final Tunnel tunnel;
        final LongAdder bytes = new LongAdder();
        final AtomicInteger open = new AtomicInteger();
        /** Koneksi yang sedang terbuka → alamat asalnya ({@code ""} untuk tunnel remote: datang dari server). */
        final Map<Socket, String> clients = new ConcurrentHashMap<>();
        /** Ditutup dari belakang (terakhir dibuka, pertama ditutup); lease paling awal, jadi paling akhir dilepas. */
        private final ConcurrentLinkedDeque<AutoCloseable> resources = new ConcurrentLinkedDeque<>();
        volatile TunnelStatus status = TunnelStatus.STARTING;
        volatile String issue;
        volatile boolean closed;

        Run(Tunnel tunnel) {
            this.tunnel = tunnel;
        }

        /** Catat resource; kalau run sudah ditutup, resource langsung ditutup dan {@code false}. */
        boolean adopt(AutoCloseable resource) {
            resources.add(resource);
            if (closed) {
                if (resources.remove(resource)) {
                    Relay.closeQuietly(resource);
                }
                return false;
            }
            return true;
        }

        void forget(AutoCloseable resource) {
            resources.remove(resource);
        }

        void closeAll() {
            closed = true;
            AutoCloseable r;
            while ((r = resources.pollLast()) != null) {
                Relay.closeQuietly(r);
            }
        }

        TunnelState state() {
            return new TunnelState(status, open.get(), bytes.sum(), issue, List.copyOf(clients.values()));
        }
    }

    // --- API ---

    /** Jalankan (atau jalankan ulang dengan pengaturan baru) tunnel ini. */
    public void start(Tunnel tunnel) {
        Objects.requireNonNull(tunnel, "tunnel");
        if (closed.get()) {
            return;
        }
        var run = new Run(tunnel);
        Run previous = runs.put(tunnel.id(), run);
        if (previous != null) {
            previous.closed = true;
        }
        log.info("Start tunnel {} ({} {})", tunnel.name(), tunnel.type(), tunnel.sourcePort());
        fire();
        executor.execute(() -> {
            if (previous != null) {
                previous.closeAll(); // lepas port lama dulu (lokal dan di server), baru buka lagi
            }
            open(run);
        });
    }

    /** Hentikan tunnel (koneksi yang lewat ikut ditutup). Tidak melakukan apa-apa kalau tidak jalan. */
    public void stop(UUID id) {
        Run run = runs.remove(id);
        if (run != null) {
            run.closed = true;
            log.info("Stop tunnel {}", run.tunnel.name());
            executor.execute(run::closeAll);
            fire();
        }
    }

    public TunnelState state(UUID id) {
        Run run = runs.get(id);
        return run == null ? TunnelState.STOPPED : run.state();
    }

    /** Jumlah tunnel yang Active atau Starting. */
    public int runningCount() {
        return (int) runs.values().stream().filter(r -> r.status.isRunning()).count();
    }

    /** @param listener dipanggil saat status tunnel berubah (bukan saat statistik byte berubah) */
    public void addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        sessions.removeConnectionListener(onConnected);
        for (UUID id : List.copyOf(runs.keySet())) {
            Run run = runs.remove(id);
            if (run != null) {
                run.closeAll();
            }
        }
    }

    // --- start ---

    private boolean current(Run run) {
        return !run.closed && runs.get(run.tunnel.id()) == run;
    }

    private void open(Run run) {
        Tunnel t = run.tunnel;
        HostProfile host = profiles.apply(t.hostId()).filter(p -> !p.isFtp()).orElse(null);
        if (host == null) {
            fail(run, new Failed(Reason.HOST_MISSING, t.sourcePort(), "", 0));
            return;
        }
        // local/dynamic: buka (cadangkan) port di PC ini dulu. Kalau dipakai program lain, gagal sekarang dengan saran
        // port lain, tanpa connect/login ke host yang sia-sia.
        ServerSocket server = null;
        if (t.type().listensOnThisPc()) {
            try {
                server = bind(t.bindAddress(), t.sourcePort());
            } catch (BindException e) {
                fail(run, bindFailure(t, e));
                return;
            } catch (IOException e) {
                fail(run, new Failed(Reason.OTHER, t.sourcePort(), message(e), 0));
                return;
            }
            if (!run.adopt(server)) {
                return;
            }
        }
        SshLease lease;
        try {
            lease = sessions.acquire(host);
        } catch (SshConnectException e) {
            fail(run, new Failed(Reason.CONNECT_FAILED, t.sourcePort(), e.getMessage(), 0));
            return;
        } catch (RuntimeException e) {
            log.warn("Tunnel {}: connect gagal", t.name(), e);
            fail(run, new Failed(Reason.CONNECT_FAILED, t.sourcePort(), e.toString(), 0));
            return;
        }
        if (!run.adopt(lease)) {
            return;
        }
        SshConnection connection = lease.connection();
        connection.addCloseListener(c -> disconnected(run, c));
        if (!connection.isOpen()) {
            disconnected(run, connection);
            return;
        }
        // tujuan harus bisa dijangkau dulu; kalau tidak, tunnel tidak dinyalakan (port lokal ditutup lagi)
        if (t.type().hasDestination()) {
            var problem = t.type() == TunnelType.LOCAL
                    ? ServerChecks.destinationProblem(connection, t.destinationHost(), t.destinationPort(),
                            channelOpenTimeout)
                    : localServiceProblem(t.destinationHost(), t.destinationPort());
            if (problem.isPresent()) {
                if (current(run)) {
                    fail(run, new Failed(t.type() == TunnelType.LOCAL ? Reason.DESTINATION_UNREACHABLE
                            : Reason.SERVICE_UNREACHABLE, t.sourcePort(),
                            t.destinationHost() + ":" + t.destinationPort() + " — " + problem.get(), 0));
                }
                return;
            }
        }
        try {
            switch (t.type()) {
                case LOCAL, DYNAMIC -> listen(run, connection, server);
                case REMOTE -> openRemote(run, connection);
            }
        } catch (IOException | RuntimeException e) {
            if (current(run)) {
                log.warn("Tunnel {} gagal dibuka: {}", t.name(), e.toString());
                fail(run, t.type() == TunnelType.REMOTE ? new Failed(Reason.REMOTE_REFUSED, t.sourcePort(),
                        message(e), 0) : new Failed(Reason.OTHER, t.sourcePort(), message(e), 0));
            }
            return;
        }
        if (current(run)) {
            run.status = TunnelStatus.ACTIVE;
            log.info("Tunnel {} aktif lewat {}", t.name(), host.address());
            fire();
        }
    }

    private void openRemote(Run run, SshConnection connection) throws IOException {
        Tunnel t = run.tunnel;
        // port di server sudah dipakai program lain? Dicek dulu supaya pesannya jelas (server hanya bilang "gagal")
        var used = ServerChecks.listeningPorts(connection, CHECK_TIMEOUT);
        if (used.isPresent() && used.get().contains(t.sourcePort())) {
            fail(run, new Failed(Reason.REMOTE_PORT_IN_USE, t.sourcePort(), "",
                    suggestServerPort(t.id(), t.hostId(), t.sourcePort(), used.get())));
            return;
        }
        ServerSocket relay = bind(Tunnel.LOOPBACK, 0);
        if (!run.adopt(relay)) {
            return;
        }
        accept(run, relay, socket -> relayToService(run, socket));
        var tracker = connection.session().createRemotePortForwardingTracker(
                new SshdSocketAddress(SERVER_LOOPBACK, t.sourcePort()),
                new SshdSocketAddress(Tunnel.LOOPBACK, relay.getLocalPort()));
        run.adopt(tracker);
    }

    /** @param server port yang sudah dibuka dan dicatat di {@code run} */
    private void listen(Run run, SshConnection connection, ServerSocket server) {
        if (run.tunnel.type() == TunnelType.DYNAMIC) {
            accept(run, server, socket -> socks(run, connection, socket));
        } else {
            accept(run, server, socket -> direct(run, connection, socket, run.tunnel.destinationHost(),
                    run.tunnel.destinationPort(), null));
        }
    }

    /**
     * Apakah port ini bisa dibuka sekarang di alamat {@code bind} (pengecekan form sebelum tunnel dinyalakan). Port
     * langsung ditutup lagi. Panggil di luar EDT.
     */
    public static boolean isPortFree(String bind, int port) {
        try (var _ = bind(bind, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Uji koneksi singkat ke layanan di PC ini (tujuan tunnel remote). Blocking: panggil di luar EDT.
     *
     * @return pesan kenapa tidak bisa dihubungi, atau kosong kalau bisa
     */
    public static Optional<String> localServiceProblem(String host, int port) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) LOCAL_CONNECT_TIMEOUT.toMillis());
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.of(message(e));
        }
    }

    /** Langkah Test connection, urut sesuai jalannya. */
    public enum Step {
        /** Port sumber di PC ini (local/dynamic) kosong. */
        LOCAL_PORT,
        /** Layanan tujuan tunnel remote bisa dihubungi dari PC ini. */
        SERVICE,
        /** Connect dan login ke host. */
        CONNECT,
        /** Tujuan tunnel local bisa dibuka dari server. */
        DESTINATION,
        /** Port tunnel remote kosong di server. */
        SERVER_PORT
    }

    /** {@code UNKNOWN}: tidak bisa dicek (mis. server tanpa {@code ss}/{@code netstat}); dicek lagi saat tunnel jalan. */
    public enum Check { OK, PROBLEM, UNKNOWN }

    /**
     * Hasil satu langkah Test connection.
     *
     * @param detail        pesan masalah (dari koneksi / server), boleh kosong
     * @param suggestedPort port lain yang kosong untuk langkah port, 0 kalau tidak ada
     */
    public record TestStep(Step step, Check check, String detail, int suggestedPort) {
    }

    /**
     * Test connection dari form: jalankan pengecekan yang sama dengan saat tunnel dinyalakan, tanpa membuka tunnel.
     * Boleh connect dan meminta login ke host (dipicu user). Langkah server dilewati kalau connect gagal. Blocking:
     * panggil di luar EDT.
     *
     * @param tunnel isian form; {@link Tunnel#id()} = tunnel yang diedit (port yang dipegangnya sendiri bukan masalah)
     */
    public List<TestStep> test(Tunnel tunnel) {
        var steps = new java.util.ArrayList<TestStep>();
        Tunnel t = tunnel;
        if (t.type().listensOnThisPc()) {
            boolean free = holdsPort(t.id(), t.bindAddress(), t.sourcePort())
                    || isPortFree(t.bindAddress(), t.sourcePort());
            steps.add(free ? new TestStep(Step.LOCAL_PORT, Check.OK, "", 0)
                    : new TestStep(Step.LOCAL_PORT, Check.PROBLEM, "", suggestPort(t)));
        }
        if (t.type() == TunnelType.REMOTE) {
            var problem = localServiceProblem(t.destinationHost(), t.destinationPort());
            steps.add(new TestStep(Step.SERVICE, problem.isPresent() ? Check.PROBLEM : Check.OK,
                    problem.orElse(""), 0));
        }
        var host = profiles.apply(t.hostId()).filter(p -> !p.isFtp());
        if (host.isEmpty()) {
            steps.add(new TestStep(Step.CONNECT, Check.PROBLEM, "", 0));
            return steps;
        }
        try (SshLease lease = sessions.acquire(host.get())) {
            steps.add(new TestStep(Step.CONNECT, Check.OK, host.get().address(), 0));
            SshConnection connection = lease.connection();
            if (t.type() == TunnelType.LOCAL) {
                var problem = ServerChecks.destinationProblem(connection, t.destinationHost(), t.destinationPort(),
                        channelOpenTimeout);
                steps.add(new TestStep(Step.DESTINATION, problem.isPresent() ? Check.PROBLEM : Check.OK,
                        problem.orElse(""), 0));
            }
            if (t.type() == TunnelType.REMOTE) {
                if (holdsServerPort(t.id(), t.hostId(), t.sourcePort())) {
                    steps.add(new TestStep(Step.SERVER_PORT, Check.OK, "", 0));
                } else {
                    steps.add(ServerChecks.listeningPorts(connection, CHECK_TIMEOUT)
                            .map(used -> used.contains(t.sourcePort())
                                    ? new TestStep(Step.SERVER_PORT, Check.PROBLEM, "",
                                            suggestServerPort(t.id(), t.hostId(), t.sourcePort(), used))
                                    : new TestStep(Step.SERVER_PORT, Check.OK, "", 0))
                            .orElse(new TestStep(Step.SERVER_PORT, Check.UNKNOWN, "", 0)));
                }
            }
        } catch (SshConnectException e) {
            steps.add(new TestStep(Step.CONNECT, Check.PROBLEM, e.getMessage(), 0));
        } catch (RuntimeException e) {
            log.warn("Test tunnel {} gagal", t.name(), e);
            steps.add(new TestStep(Step.CONNECT, Check.PROBLEM, message(e), 0));
        }
        return steps;
    }

    private boolean holdsServerPort(UUID id, UUID hostId, int port) {
        Run run = runs.get(id);
        return run != null && run.status.isRunning() && run.tunnel.type() == TunnelType.REMOTE
                && run.tunnel.hostId().equals(hostId) && run.tunnel.sourcePort() == port;
    }

    /** Port server kosong terdekat: tidak didengarkan di server dan tidak dipakai tunnel remote lain ke host itu. */
    int suggestServerPort(UUID selfId, UUID hostId, int port, java.util.Set<Integer> used) {
        var taken = new java.util.HashSet<>(used);
        for (Tunnel other : tunnels.get()) {
            if (!other.id().equals(selfId) && other.type() == TunnelType.REMOTE && other.hostId().equals(hostId)) {
                taken.add(other.sourcePort());
            }
        }
        for (int p = port + 1; p <= Math.min(65535, port + SUGGEST_RANGE); p++) {
            if (!taken.contains(p)) {
                return p;
            }
        }
        return 0;
    }

    /** Port lokal tunnel ini sedang dipegang tunnel itu sendiri (Active/Starting dengan alamat dan port yang sama). */
    public boolean holdsPort(UUID id, String bind, int port) {
        Run run = runs.get(id);
        return run != null && run.status.isRunning() && run.tunnel.type().listensOnThisPc()
                && run.tunnel.sourcePort() == port && run.tunnel.bindAddress().equals(bind);
    }

    private static ServerSocket bind(String address, int port) throws IOException {
        var server = new ServerSocket();
        try {
            server.bind(new InetSocketAddress(InetAddress.getByName(address), port), 50);
            return server;
        } catch (IOException e) {
            Relay.closeQuietly(server);
            throw e;
        }
    }

    /** Terima koneksi di virtual thread sampai socket ditutup; setiap koneksi ditangani di virtual thread sendiri. */
    private void accept(Run run, ServerSocket server, Consumer<Socket> handler) {
        Thread.ofVirtual().name("tunnel-accept-" + server.getLocalPort()).start(() -> {
            while (!run.closed) {
                Socket socket;
                try {
                    socket = server.accept();
                } catch (IOException e) {
                    if (current(run)) {
                        log.warn("Tunnel {}: menerima koneksi gagal: {}", run.tunnel.name(), e.toString());
                        fail(run, new Failed(Reason.OTHER, run.tunnel.sourcePort(), message(e), 0));
                    }
                    return;
                }
                if (!run.adopt(socket)) {
                    return;
                }
                String from = run.tunnel.type() == TunnelType.REMOTE ? "" : socket.getInetAddress().getHostAddress();
                Thread.ofVirtual().name("tunnel-conn").start(() -> {
                    run.open.incrementAndGet();
                    run.clients.put(socket, from);
                    try {
                        handler.accept(socket);
                    } finally {
                        run.clients.remove(socket);
                        run.open.decrementAndGet();
                        run.forget(socket);
                        Relay.closeQuietly(socket);
                    }
                });
            }
        });
    }

    /** Local / dynamic: buka channel {@code direct-tcpip} ke tujuan, lalu salurkan. */
    private void direct(Run run, SshConnection connection, Socket socket, String host, int port,
                        Socks.Request socks) {
        ChannelDirectTcpip channel = null;
        try {
            channel = connection.session().createDirectTcpipChannel(
                    new SshdSocketAddress(socket.getInetAddress().getHostAddress(), socket.getPort()),
                    new SshdSocketAddress(host, port));
            if (!run.adopt(channel)) {
                return;
            }
            channel.open().verify(channelOpenTimeout);
        } catch (IOException | RuntimeException e) {
            run.issue = host + ":" + port + " — " + message(e);
            log.info("Tunnel {}: tujuan {}:{} tidak bisa dibuka lewat server: {}", run.tunnel.name(), host, port,
                    e.toString());
            if (socks != null) {
                replyQuietly(socket, socks, false);
            }
            if (channel != null) {
                run.forget(channel);
                channel.close(true);
            }
            return;
        }
        try {
            if (socks != null) {
                Socks.reply(socket.getOutputStream(), socks, true);
            }
        } catch (IOException e) {
            run.forget(channel);
            channel.close(true);
            return;
        }
        run.issue = null;
        ChannelDirectTcpip opened = channel;
        Relay.run(socket, new Relay.Peer() {
            @Override
            public InputStream in() {
                return opened.getInvertedOut();
            }

            @Override
            public OutputStream out() {
                return opened.getInvertedIn();
            }

            @Override
            public void shutdownOutput() throws IOException {
                opened.getInvertedIn().close(); // kirim EOF ke tujuan
            }

            @Override
            public void close() {
                run.forget(opened);
                opened.close(false);
            }
        }, run.bytes);
    }

    private void socks(Run run, SshConnection connection, Socket socket) {
        Socks.Request request;
        try {
            socket.setSoTimeout((int) channelOpenTimeout.toMillis()); // aplikasi yang diam saat handshake
            request = Socks.accept(socket.getInputStream(), socket.getOutputStream());
            socket.setSoTimeout(0);
        } catch (IOException e) {
            log.debug("Tunnel {}: permintaan SOCKS ditolak: {}", run.tunnel.name(), e.toString());
            return;
        }
        direct(run, connection, socket, request.host(), request.port(), request);
    }

    /** Remote: koneksi dari server (lewat relay) ke layanan tujuan di PC ini. */
    private void relayToService(Run run, Socket socket) {
        Tunnel t = run.tunnel;
        var service = new Socket();
        if (!run.adopt(service)) {
            return;
        }
        try {
            service.connect(new InetSocketAddress(t.destinationHost(), t.destinationPort()),
                    (int) LOCAL_CONNECT_TIMEOUT.toMillis());
        } catch (IOException e) {
            run.issue = t.destinationHost() + ":" + t.destinationPort() + " — " + message(e);
            log.info("Tunnel {}: layanan {}:{} di PC ini tidak bisa dihubungi: {}", t.name(), t.destinationHost(),
                    t.destinationPort(), e.toString());
            run.forget(service);
            Relay.closeQuietly(service);
            return;
        }
        run.issue = null;
        var peer = Relay.socket(service);
        Relay.run(socket, new Relay.Peer() {
            @Override
            public InputStream in() throws IOException {
                return peer.in();
            }

            @Override
            public OutputStream out() throws IOException {
                return peer.out();
            }

            @Override
            public void shutdownOutput() throws IOException {
                peer.shutdownOutput();
            }

            @Override
            public void close() {
                run.forget(service);
                peer.close();
            }
        }, run.bytes);
    }

    private static void replyQuietly(Socket socket, Socks.Request request, boolean ok) {
        try {
            Socks.reply(socket.getOutputStream(), request, ok);
        } catch (IOException e) {
            log.debug("Balasan SOCKS gagal: {}", e.toString());
        }
    }

    // --- gagal / putus / auto-start ---

    private void fail(Run run, Failed failure) {
        run.closeAll();
        if (runs.get(run.tunnel.id()) != run) {
            return; // sudah dihentikan / diganti
        }
        run.status = failure;
        log.info("Tunnel {}: {} {}", run.tunnel.name(), failure.reason(), failure.detail());
        fire();
    }

    private Failed bindFailure(Tunnel t, BindException e) {
        String text = Objects.toString(e.getMessage(), "").toLowerCase(Locale.ROOT);
        boolean denied = text.contains("denied") || text.contains("permission") || text.contains("forbidden");
        return new Failed(denied ? Reason.PORT_DENIED : Reason.PORT_IN_USE, t.sourcePort(), message(e),
                suggestPort(t));
    }

    /**
     * Port kosong terdekat setelah port tunnel ini: tidak dipakai tunnel lain yang mendengarkan di PC ini, dan bisa
     * dibuka sekarang.
     *
     * @return port, atau 0 kalau tidak ada dalam {@value #SUGGEST_RANGE} port berikutnya
     */
    int suggestPort(Tunnel t) {
        var taken = new java.util.HashSet<Integer>();
        for (Tunnel other : tunnels.get()) {
            if (!other.id().equals(t.id()) && other.type().listensOnThisPc()) {
                taken.add(other.sourcePort());
            }
        }
        for (int p = t.sourcePort() + 1; p <= Math.min(65535, t.sourcePort() + SUGGEST_RANGE); p++) {
            if (taken.contains(p)) {
                continue;
            }
            try (var probe = bind(t.bindAddress(), p)) {
                return probe.getLocalPort();
            } catch (IOException e) {
                // dipakai: coba berikutnya
            }
        }
        return 0;
    }

    private void disconnected(Run run, SshConnection connection) {
        if (!current(run) || closed.get()) {
            return;
        }
        log.info("Tunnel {}: koneksi ke {} terputus", run.tunnel.name(), connection.profile().address());
        executor.execute(() -> fail(run, new Failed(Reason.DISCONNECTED, run.tunnel.sourcePort(), "", 0)));
    }

    /** Koneksi baru ke sebuah host: jalankan tunnel auto-start-nya dan tunnel yang tadi berhenti karena putus. */
    private void hostConnected(SshConnection connection) {
        if (closed.get()) {
            return;
        }
        UUID hostId = connection.profile().id();
        executor.execute(() -> {
            for (Tunnel t : tunnels.get()) {
                if (!t.hostId().equals(hostId)) {
                    continue;
                }
                Run run = runs.get(t.id());
                boolean idle = run == null || run.status instanceof Failed;
                boolean resume = run != null && run.status instanceof Failed f && f.reason() == Reason.DISCONNECTED;
                if (idle && (t.autoStart() || resume)) {
                    start(t);
                }
            }
        });
    }

    private void fire() {
        for (Runnable l : listeners) {
            try {
                l.run();
            } catch (RuntimeException e) {
                log.warn("Listener tunnel gagal", e);
            }
        }
    }

    private static String message(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String m = root.getMessage();
        return m == null || m.isBlank() ? root.getClass().getSimpleName() : m;
    }
}

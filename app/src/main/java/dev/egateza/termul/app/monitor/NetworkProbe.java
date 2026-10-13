package dev.egateza.termul.app.monitor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pemeriksa koneksi internet: TCP connect (tanpa mengirim data) ke beberapa alamat IP publik secara berurutan; online
 * kalau salah satu tersambung. Alamat berupa IP literal supaya hasilnya tidak bergantung pada DNS. Blocking, jadi
 * jangan dipanggil di EDT. Thread-safe (tanpa state yang berubah).
 */
public final class NetworkProbe {

    private static final Logger log = LoggerFactory.getLogger(NetworkProbe.class);

    /** Cloudflare, Google, Quad9 — port HTTPS, yang hampir selalu diizinkan firewall. */
    static final List<InetSocketAddress> TARGETS = List.of(
            new InetSocketAddress(InetAddress.ofLiteral("1.1.1.1"), 443),
            new InetSocketAddress(InetAddress.ofLiteral("8.8.8.8"), 443),
            new InetSocketAddress(InetAddress.ofLiteral("9.9.9.9"), 443));
    static final int TIMEOUT_MS = 3000;

    /** Hasil satu pemeriksaan. */
    public sealed interface Status {

        Instant at();

        /** @param target alamat yang tersambung, mis. {@code 1.1.1.1:443}; latency = waktu TCP handshake */
        record Online(String target, Duration latency, Instant at) implements Status {
        }

        /** @param tried alamat yang dicoba, dipisah koma */
        record Offline(String tried, Instant at) implements Status {
        }
    }

    @FunctionalInterface
    interface Connector {
        void connect(InetSocketAddress address, int timeoutMs) throws IOException;
    }

    private final List<InetSocketAddress> targets;
    private final int timeoutMs;
    private final Connector connector;
    private final Clock clock;

    public NetworkProbe() {
        this(TARGETS, TIMEOUT_MS, NetworkProbe::tcpConnect, Clock.systemDefaultZone());
    }

    NetworkProbe(List<InetSocketAddress> targets, int timeoutMs, Connector connector, Clock clock) {
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("targets kosong");
        }
        this.targets = List.copyOf(targets);
        this.timeoutMs = timeoutMs;
        this.connector = connector;
        this.clock = clock;
    }

    /** Coba alamat satu per satu; berhenti di yang pertama tersambung. */
    public Status probe() {
        for (var target : targets) {
            long start = System.nanoTime();
            try {
                connector.connect(target, timeoutMs);
                return new Status.Online(label(target), Duration.ofNanos(System.nanoTime() - start), clock.instant());
            } catch (IOException | RuntimeException e) {
                log.debug("Probe internet ke {} gagal: {}", label(target), e.toString());
            }
        }
        return new Status.Offline(String.join(", ", targets.stream().map(NetworkProbe::label).toList()),
                clock.instant());
    }

    static String label(InetSocketAddress address) {
        return address.getAddress().getHostAddress() + ":" + address.getPort();
    }

    private static void tcpConnect(InetSocketAddress address, int timeoutMs) throws IOException {
        try (var socket = new Socket()) {
            socket.connect(address, timeoutMs);
        }
    }
}

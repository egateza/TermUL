package dev.egateza.termul.ssh.tunnel;

import dev.egateza.termul.ssh.RemoteExec;
import dev.egateza.termul.ssh.SshConnection;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.apache.sshd.client.channel.ChannelDirectTcpip;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pengecekan di sisi server lewat koneksi SSH yang sudah ada. Blocking: panggil di luar EDT.
 *
 * <ul>
 *   <li>{@link #listeningPorts}: port TCP yang sedang didengarkan di loopback/semua interface server (untuk port
 *       tunnel remote), dari {@code ss -Hltn} atau {@code netstat -ltn}. Hanya membaca daftar port.</li>
 *   <li>{@link #destinationProblem}: membuka lalu langsung menutup channel {@code direct-tcpip} ke tujuan tunnel local.
 *       OpenSSH baru mengonfirmasi channel setelah koneksi ke tujuan berhasil, jadi ini sama dengan uji koneksi dari
 *       server. Layanan tujuan melihat satu koneksi singkat tanpa data (mis. Postgres mencatat "incomplete startup
 *       packet").</li>
 * </ul>
 */
final class ServerChecks {

    private static final Logger log = LoggerFactory.getLogger(ServerChecks.class);
    static final String LISTEN_COMMAND = "ss -Hltn 2>/dev/null || netstat -ltn 2>/dev/null";

    private ServerChecks() {
    }

    /** @return port yang dipakai di server, atau kosong kalau tidak bisa dicek (exec ditolak, tanpa ss/netstat) */
    static Optional<Set<Integer>> listeningPorts(SshConnection connection, Duration timeout) {
        try {
            var result = RemoteExec.run(connection, LISTEN_COMMAND, null, timeout);
            if (!result.ok() || result.stdout().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parseListening(result.stdout()));
        } catch (IOException | RuntimeException e) {
            log.debug("Daftar port server tidak bisa dibaca: {}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * Port dari output {@code ss -Hltn} / {@code netstat -ltn} yang didengarkan di alamat yang bentrok dengan forward
     * di loopback server: semua interface ({@code 0.0.0.0}, {@code *}, {@code ::}) atau loopback.
     */
    static Set<Integer> parseListening(String output) {
        var ports = new HashSet<Integer>();
        for (String line : output.split("\\R")) {
            String[] cols = line.strip().split("\\s+");
            if (cols.length < 4) {
                continue;
            }
            String first = cols[0].toLowerCase(Locale.ROOT);
            if (!first.equals("listen") && !first.startsWith("tcp")) {
                continue;
            }
            if (first.startsWith("tcp") && !line.contains("LISTEN")) {
                continue;
            }
            String local = cols[3];
            int colon = local.lastIndexOf(':');
            if (colon < 0) {
                continue;
            }
            String address = local.substring(0, colon).replace("[", "").replace("]", "");
            int pct = address.indexOf('%');
            if (pct >= 0) {
                address = address.substring(0, pct); // 127.0.0.53%lo
            }
            try {
                int port = Integer.parseInt(local.substring(colon + 1));
                if (clashesWithLoopback(address)) {
                    ports.add(port);
                }
            } catch (NumberFormatException e) {
                // bukan baris port
            }
        }
        return ports;
    }

    private static boolean clashesWithLoopback(String address) {
        return address.isEmpty() || address.equals("*") || address.equals("0.0.0.0") || address.equals("::")
                || address.equals("::1") || address.startsWith("127.") || address.equals("::ffff:127.0.0.1");
    }

    /** @return pesan kenapa tujuan tidak bisa dibuka dari server, atau kosong kalau bisa */
    static Optional<String> destinationProblem(SshConnection connection, String host, int port, Duration timeout) {
        ChannelDirectTcpip channel = null;
        try {
            channel = connection.session().createDirectTcpipChannel(new SshdSocketAddress("127.0.0.1", 0),
                    new SshdSocketAddress(host, port));
            channel.open().verify(timeout);
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.of(rootMessage(e));
        } finally {
            if (channel != null) {
                channel.close(true);
            }
        }
    }

    static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String m = root.getMessage();
        return m == null || m.isBlank() ? root.getClass().getSimpleName() : m;
    }
}

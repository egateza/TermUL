package dev.egateza.myterm.ssh;

import dev.egateza.myterm.core.profile.OsInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mendeteksi OS server dengan membaca {@code /etc/os-release} lewat exec channel
 * (koneksi yang sama dengan terminal, tidak mengganggu shell). Blocking: jangan di EDT.
 */
public final class OsDetector {

    private static final Logger log = LoggerFactory.getLogger(OsDetector.class);

    /** Perintah tetap (tanpa input dari user/profil). */
    static final String COMMAND = "cat /etc/os-release 2>/dev/null || cat /usr/lib/os-release 2>/dev/null";
    private static final int MAX_OUTPUT = 16 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private OsDetector() {
    }

    /** @return OS terdeteksi, atau kosong kalau gagal/bukan Linux (tidak pernah melempar exception). */
    public static Optional<OsInfo> detect(SshConnection connection) {
        try (ChannelExec exec = connection.createExec(COMMAND)) {
            var out = new LimitedOutput(MAX_OUTPUT);
            exec.setOut(out);
            exec.setErr(OutputStream.nullOutputStream());
            exec.open().verify(TIMEOUT);
            exec.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), TIMEOUT);
            Optional<OsInfo> os = OsInfo.parseOsRelease(out.toString(StandardCharsets.UTF_8));
            log.info("OS {}: {}", connection.profile().address(), os.map(OsInfo::label).orElse("tidak terdeteksi"));
            return os;
        } catch (IOException | RuntimeException e) {
            log.info("Deteksi OS {} gagal: {}", connection.profile().address(), e.toString());
            return Optional.empty();
        }
    }

    /** Membuang output di atas batas (server tidak bisa membanjiri memory). */
    private static final class LimitedOutput extends ByteArrayOutputStream {
        private final int limit;

        LimitedOutput(int limit) {
            this.limit = limit;
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            int room = limit - count;
            if (room > 0) {
                super.write(b, off, Math.min(room, len));
            }
        }

        @Override
        public synchronized void write(int b) {
            if (count < limit) {
                super.write(b);
            }
        }
    }
}

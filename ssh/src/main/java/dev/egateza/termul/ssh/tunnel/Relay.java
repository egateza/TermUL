package dev.egateza.termul.ssh.tunnel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Menyalurkan data dua arah antara socket lokal dan sisi lain (channel SSH atau socket tujuan), sambil menghitung byte.
 * EOF diteruskan sebagai half-close; selesai saat kedua arah selesai atau salah satu error. Blocking: jalankan di
 * virtual thread.
 */
final class Relay {

    private static final Logger log = LoggerFactory.getLogger(Relay.class);
    private static final int BUFFER = 32 * 1024;
    static final java.time.Duration HALF_CLOSE_GRACE = java.time.Duration.ofSeconds(60);

    /** Sisi lain koneksi: data masuk, data keluar, half-close keluar, dan tutup penuh. */
    interface Peer extends AutoCloseable {
        InputStream in() throws IOException;

        OutputStream out() throws IOException;

        /** Tidak ada data lagi dari socket lokal (EOF ke sisi lain). */
        void shutdownOutput() throws IOException;

        @Override
        void close();
    }

    private Relay() {
    }

    /** Sisi lain berupa socket biasa (tunnel remote: relay lokal → layanan di PC ini). */
    static Peer socket(Socket s) {
        return new Peer() {
            @Override
            public InputStream in() throws IOException {
                return s.getInputStream();
            }

            @Override
            public OutputStream out() throws IOException {
                return s.getOutputStream();
            }

            @Override
            public void shutdownOutput() throws IOException {
                if (!s.isClosed() && !s.isOutputShutdown()) {
                    s.shutdownOutput();
                }
            }

            @Override
            public void close() {
                closeQuietly(s);
            }
        };
    }

    /**
     * Salurkan sampai selesai. {@code local} dan {@code peer} selalu tertutup saat method ini kembali.
     *
     * @param bytes ditambah setiap ada data yang lewat, dua arah
     */
    static void run(Socket local, Peer peer, LongAdder bytes) {
        Thread upstream = null;
        try {
            InputStream fromLocal = local.getInputStream();
            OutputStream toLocal = local.getOutputStream();
            InputStream fromPeer = peer.in();
            OutputStream toPeer = peer.out();
            upstream = Thread.ofVirtual().name("tunnel-up").start(() -> {
                try {
                    copy(fromLocal, toPeer, bytes);
                    peer.shutdownOutput();
                } catch (IOException e) {
                    log.debug("Tunnel: arah ke tujuan selesai: {}", e.toString());
                    peer.close();
                    closeQuietly(local);
                }
            });
            copy(fromPeer, toLocal, bytes);
            if (!local.isClosed()) {
                local.shutdownOutput();
            }
            // tujuan sudah selesai: beri program lokal waktu menyelesaikan kirimannya, lalu tutup
            upstream.join(HALF_CLOSE_GRACE);
        } catch (IOException e) {
            log.debug("Tunnel: arah ke lokal selesai: {}", e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            peer.close();
            closeQuietly(local);
            if (upstream != null) {
                upstream.interrupt();
            }
        }
    }

    static void copy(InputStream in, OutputStream out, LongAdder bytes) throws IOException {
        byte[] buffer = new byte[BUFFER];
        int n;
        while ((n = in.read(buffer)) >= 0) {
            if (n > 0) {
                out.write(buffer, 0, n);
                out.flush();
                bytes.add(n);
            }
        }
    }

    static void closeQuietly(AutoCloseable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Exception e) {
            log.debug("Menutup {}: {}", c, e.toString());
        }
    }
}

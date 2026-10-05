package dev.egateza.termul.sftp;

import dev.egateza.termul.ssh.ConnectCancel;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Koneksi SFTP yang mengikuti sesi terminal host-nya. SFTP tidak menyambung ulang sendiri dengan hitungan mundur:
 * kalau koneksi mati, {@link #ensureLive} melihat status terminal ({@link SessionGate}):
 * <ul>
 *   <li>terminal sedang menyambung/menyambung ulang: operasi <b>menunggu</b> sampai terminal tersambung;</li>
 *   <li>terminal tersambung (atau tidak ada tab terminal untuk host ini): kanal SFTP dibuka lagi sekali;</li>
 *   <li>terminal terputus dan berhenti mencoba: operasi ditolak, user harus Reconnect dari terminal.</li>
 * </ul>
 * Begitu terminal tersambung kembali, kanal SFTP dibuka lagi otomatis ({@link #sessionChanged}).
 *
 * <p>Semua method blocking: jangan dipanggil di EDT. Listener dipanggil di thread sembarang.
 */
public final class SftpConnection implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SftpConnection.class);

    static final String TERMINAL_DOWN =
            "Sesi terputus. Klik Reconnect di bagian atas tab, lalu SFTP bisa dipakai lagi.";

    public enum State { IDLE, CONNECTED, WAITING, DISCONNECTED, CLOSED }

    /** @param message keterangan untuk user (Bahasa Indonesia) */
    public record Status(State state, String message) {
    }

    /** Membuka koneksi SFTP baru (boleh memunculkan prompt login). */
    @FunctionalInterface
    public interface Opener {
        /** @param cancel dibatalkan saat koneksi ini ditutup: connect yang masih berjalan dihentikan */
        RemoteFileService open(ConnectCancel cancel) throws Exception;
    }

    /** Status sesi terminal untuk host ini. */
    public interface SessionGate {
        /** @return status terminal, atau null kalau tidak ada tab terminal untuk host ini */
        TerminalState state();

        /** Menunggu sampai status berubah dari {@code seen}, paling lama {@code max}. */
        void awaitChange(TerminalState seen, Duration max) throws InterruptedException;
    }

    private static final Duration POLL = Duration.ofSeconds(1);

    private final Opener opener;
    private final SessionGate gate;
    private final List<Consumer<Status>> listeners = new CopyOnWriteArrayList<>();
    private final Object openLock = new Object(); // hanya satu kanal SFTP dibuka pada satu waktu
    private volatile RemoteFileService current; // null sampai pertama kali dibuka
    private volatile Status status = new Status(State.IDLE, "Belum tersambung");
    private volatile boolean closed;
    private final ConnectCancel closing = new ConnectCancel(); // dibatalkan di close()

    public SftpConnection(Opener opener, SessionGate gate) {
        this.opener = opener;
        this.gate = gate;
    }

    public Status status() {
        return status;
    }

    /** Berlangganan perubahan status. @return pemanggil untuk berhenti berlangganan */
    public Runnable addListener(Consumer<Status> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Service saat ini (tanpa mengecek koneksi). Dipakai di dalam operasi yang dijalankan lewat {@link #execute}. */
    public RemoteFileService service() {
        var svc = current;
        if (svc == null) {
            throw new IllegalStateException("SFTP belum dibuka");
        }
        return svc;
    }

    /**
     * Pastikan kanal SFTP hidup; menunggu kalau terminal sedang menyambung.
     *
     * @throws RemoteFileException kalau terminal terputus dan berhenti mencoba, atau kanal tidak bisa dibuka
     */
    public RemoteFileService ensureLive() throws RemoteFileException {
        while (true) {
            var svc = current;
            if (svc != null && svc.isOpen()) {
                return svc;
            }
            if (closed) {
                throw new RemoteFileException("Koneksi SFTP sudah ditutup.");
            }
            TerminalState terminal = gate.state();
            if (terminal != null && terminal.isPending()) {
                publish(State.WAITING, "Menunggu sesi tersambung ...");
                try {
                    gate.awaitChange(terminal, POLL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RemoteFileException("Menunggu sesi terminal dibatalkan.", e);
                }
                continue;
            }
            if (terminal == TerminalState.DOWN) {
                publish(State.DISCONNECTED, TERMINAL_DOWN);
                throw new RemoteFileException(TERMINAL_DOWN);
            }
            return reopen(); // terminal tersambung, atau tidak ada terminal untuk host ini
        }
    }

    private RemoteFileService reopen() throws RemoteFileException {
        synchronized (openLock) {
            var svc = current;
            if (svc != null && svc.isOpen()) {
                return svc; // thread lain sudah membukanya
            }
            boolean again = svc != null;
            if (svc != null) {
                svc.close(); // lepaskan lease koneksi lama
            }
            if (closed) {
                throw new RemoteFileException("Koneksi SFTP sudah ditutup.");
            }
            try {
                current = opener.open(closing);
            } catch (Exception e) {
                if (closed) { // dibatalkan oleh close(): status CLOSED jangan ditimpa
                    throw new RemoteFileException("Koneksi SFTP sudah ditutup.", e);
                }
                publish(State.DISCONNECTED, e.getMessage());
                throw e instanceof RemoteFileException rfe ? rfe : new RemoteFileException(e.getMessage(), e);
            }
            if (closed) { // ditutup selagi membuka: hasilnya tidak dipakai lagi
                current.close();
                throw new RemoteFileException("Koneksi SFTP sudah ditutup.");
            }
            publish(State.CONNECTED, again ? "Tersambung kembali" : "Tersambung");
            return current;
        }
    }

    /**
     * Menjalankan operasi setelah kanal dipastikan hidup. Kalau koneksi putus di tengah operasi, menunggu terminal
     * tersambung kembali lalu operasi diulang satu kali. Operasi mengambil service lewat {@link #service()}.
     */
    public <T> T execute(Callable<T> operation) throws Exception {
        ensureLive();
        try {
            return operation.call();
        } catch (RemoteFileException.Cancelled e) {
            throw e;
        } catch (Exception e) {
            var svc = current;
            if (svc != null && svc.isOpen()) {
                throw e;
            }
            ensureLive();
            return operation.call();
        }
    }

    /**
     * Membuka lagi kanal SFTP yang mati <b>tanpa menunggu terminal</b>. Dipanggil oleh terminal sebagai bagian dari
     * satu percobaan reconnect, supaya terminal dan SFTP tersambung bersama (atau percobaan dianggap gagal).
     * Tidak melakukan apa-apa kalau SFTP belum pernah dibuka atau masih hidup.
     */
    public void restore() throws RemoteFileException {
        var svc = current;
        if (closed || svc == null || svc.isOpen()) {
            return;
        }
        reopen();
    }

    /**
     * Dipanggil ketika status terminal host ini berubah. Kalau kanal SFTP sedang mati: menampilkan status menunggu,
     * atau membukanya lagi di background begitu terminal tersambung.
     */
    void sessionChanged() {
        var svc = current;
        if (closed || svc == null || svc.isOpen()) {
            return; // belum pernah dibuka (panel yang membukanya) atau masih hidup
        }
        TerminalState terminal = gate.state();
        if (terminal != null && terminal.isPending()) {
            publish(State.WAITING, "Menunggu sesi tersambung ...");
        } else if (terminal == TerminalState.DOWN) {
            publish(State.DISCONNECTED, TERMINAL_DOWN);
        } else {
            Thread.ofVirtual().name("sftp-restore").start(() -> {
                try {
                    ensureLive();
                } catch (RemoteFileException e) {
                    log.info("SFTP belum bisa dibuka lagi setelah terminal tersambung: {}", e.getMessage());
                }
            });
        }
    }

    @Override
    public void close() {
        closed = true;
        closing.cancel(); // hentikan connect yang mungkin masih berjalan
        var svc = current;
        if (svc != null) {
            svc.close();
        }
        publish(State.CLOSED, "Ditutup");
    }

    private void publish(State state, String message) {
        var s = new Status(state, message);
        synchronized (this) {
            if (s.equals(status)) {
                return; // hindari banjir pesan yang sama (mis. saat polling menunggu terminal)
            }
            status = s;
        }
        for (var l : listeners) {
            try {
                l.accept(s);
            } catch (RuntimeException e) {
                log.warn("Listener status SFTP gagal", e);
            }
        }
    }
}

package dev.egateza.termul.terminal;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Menyambung ulang sesi yang terputus, bertahap: percobaan pertama langsung; kalau gagal menunggu 15 detik lalu
 * mencoba lagi; kalau gagal lagi menunggu 30 detik lalu mencoba lagi; kalau tetap gagal berhenti (user harus
 * memilih Reconnect sendiri).
 *
 * <p>{@link #run} blocking dan berjalan di thread pemanggil (bukan EDT); semua callback {@link Listener} dipanggil
 * di thread itu juga. {@link #skipWait} dan {@link #cancel} boleh dipanggil dari thread mana pun.
 *
 * @param <T> hasil sambungan yang berhasil (mis. {@link SshTtyConnector})
 */
public final class Reconnector<T> {

    private static final Logger log = LoggerFactory.getLogger(Reconnector.class);

    /** Jeda antar percobaan setelah percobaan langsung pertama. */
    public static final List<Duration> DEFAULT_DELAYS = List.of(Duration.ofSeconds(15), Duration.ofSeconds(30));

    @FunctionalInterface
    public interface Attempt<T> {
        T open() throws Exception;
    }

    public interface Listener<T> {
        /** Percobaan ke-{@code attempt} (mulai 1) dimulai. */
        void attempting(int attempt);

        /** Hitung mundur menuju percobaan berikutnya; dipanggil tiap detik. */
        void waiting(int secondsLeft, Exception lastError);

        void connected(T value);

        /** Sambungan berhasil tetapi sudah dibatalkan; pemilik wajib menutupnya (mis. shell yang baru dibuka). */
        default void discarded(T value) {
        }

        /** Semua percobaan gagal. */
        void gaveUp(Exception lastError);
    }

    /** Jeda satu detik antar hitungan; diganti di test. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final List<Duration> delays;
    private final Sleeper sleeper;
    private volatile boolean skipWait;
    private volatile boolean cancelled;

    public Reconnector() {
        this(DEFAULT_DELAYS, d -> Thread.sleep(d));
    }

    public Reconnector(List<Duration> delays, Sleeper sleeper) {
        this.delays = List.copyOf(delays);
        this.sleeper = sleeper;
    }

    /** Lewati hitung mundur yang sedang berjalan: percobaan berikutnya dilakukan sekarang. */
    public void skipWait() {
        skipWait = true;
    }

    /** Hentikan; {@link #run} kembali tanpa memanggil {@code connected}/{@code gaveUp} lagi. */
    public void cancel() {
        cancelled = true;
        skipWait = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void run(Attempt<T> attempt, Listener<T> listener) {
        Exception last = null;
        for (int n = 1; !cancelled; n++) {
            listener.attempting(n);
            try {
                T value = attempt.open();
                if (cancelled) { // dibatalkan selagi menyambung: hasilnya tidak dipakai
                    listener.discarded(value);
                    return;
                }
                listener.connected(value);
                return;
            } catch (Exception e) {
                last = e;
                log.info("Reconnect percobaan {} gagal: {}", n, e.getMessage());
            }
            if (n > delays.size()) {
                break;
            }
            if (!countdown(delays.get(n - 1), last, listener)) {
                return;
            }
        }
        if (!cancelled) {
            listener.gaveUp(last);
        }
    }

    /** @return false kalau dibatalkan atau thread diinterupsi */
    private boolean countdown(Duration delay, Exception last, Listener<T> listener) {
        try {
            for (long left = delay.toSeconds(); left > 0 && !skipWait && !cancelled; left--) {
                listener.waiting((int) left, last);
                sleeper.sleep(Duration.ofSeconds(1));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            skipWait = false;
        }
        return !cancelled;
    }
}

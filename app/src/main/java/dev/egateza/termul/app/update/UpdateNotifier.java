package dev.egateza.termul.app.update;

import dev.egateza.termul.core.AppPaths;
import dev.egateza.termul.update.ReleaseVersion;
import dev.egateza.termul.update.UpdateClient;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pemeriksaan rilis baru di background untuk badge di menu bar. <b>Tidak pernah memasang apa pun</b>: memasang dan
 * restart tetap lewat {@link UpdateDialog} atas keputusan user. Pemeriksaan jalan di thread scheduler (jaringan),
 * {@link #state} hanya diubah dan dibaca di EDT. Gagal (offline, GitHub tidak terjangkau) cukup di-log.
 */
public final class UpdateNotifier implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(UpdateNotifier.class);

    /** Jeda setelah start supaya tidak bersaing dengan startup dan koneksi pertama. */
    static final Duration FIRST_DELAY = Duration.ofSeconds(20);
    static final Duration INTERVAL = Duration.ofHours(12);

    /** Kondisi badge. */
    public sealed interface State {
    }

    /** Tidak ada yang perlu ditampilkan. */
    public record Idle() implements State {
    }

    /** Ada rilis lebih baru yang belum dipasang. */
    public record Available(ReleaseVersion version) implements State {
    }

    /** Update sudah dipasang di sesi ini; aktif setelah TermUL dibuka ulang. */
    public record Installed(ReleaseVersion version) implements State {
    }

    private final Callable<Optional<ReleaseVersion>> probe;
    private final BooleanSupplier enabled;
    private final Consumer<State> listener;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("update-notifier").daemon().factory());
    /** Hanya diakses di EDT. */
    private State state = new Idle();

    /**
     * @param probe    versi lebih baru dari yang berjalan (blocking, jaringan), atau kosong
     * @param enabled  dibaca di thread scheduler; false = pemeriksaan dilewati
     * @param listener dipanggil di EDT setiap state berubah
     */
    UpdateNotifier(Callable<Optional<ReleaseVersion>> probe, BooleanSupplier enabled, Consumer<State> listener) {
        this.probe = probe;
        this.enabled = enabled;
        this.listener = listener;
    }

    /** Pemeriksaan ke GitHub Releases resmi, dengan verifikasi tanda tangan yang sama seperti pemasangan. */
    public static UpdateNotifier forGitHub(AppPaths paths, BooleanSupplier enabled, Consumer<State> listener) {
        return new UpdateNotifier(() -> {
            try (var client = new UpdateClient()) {
                return AppUpdates.updater(paths, client).newerVersion();
            }
        }, enabled, listener);
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(this::checkQuietly, FIRST_DELAY.toSeconds(), INTERVAL.toSeconds(),
                TimeUnit.SECONDS);
    }

    /** Periksa secepatnya (mis. user baru menyalakan pemeriksaan otomatis). */
    public void checkSoon() {
        if (!scheduler.isShutdown()) {
            scheduler.execute(this::checkQuietly);
        }
    }

    void checkQuietly() {
        if (!enabled.getAsBoolean()) {
            return;
        }
        try {
            Optional<ReleaseVersion> newer = probe.call();
            SwingUtilities.invokeLater(() -> onResult(newer));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.info("Pemeriksaan update otomatis gagal: {}", e.getMessage());
        }
    }

    /** Hasil pemeriksaan (otomatis atau dari dialog). EDT. */
    void onResult(Optional<ReleaseVersion> newer) {
        if (state instanceof Installed) {
            return; // sudah terpasang: badge tetap "restart" sampai TermUL dibuka ulang
        }
        newer.ifPresent(v -> log.info("Update {} tersedia", v));
        set(newer.<State>map(Available::new).orElseGet(Idle::new));
    }

    /** Update dipasang lewat dialog. EDT. */
    void installed(ReleaseVersion version) {
        set(new Installed(version));
    }

    /** User mematikan pemeriksaan otomatis: badge "tersedia" disembunyikan, badge "restart" tetap. EDT. */
    public void disabled() {
        if (state instanceof Available) {
            set(new Idle());
        }
    }

    public State state() {
        return state;
    }

    private void set(State next) {
        if (!Objects.equals(next, state)) {
            state = next;
            listener.accept(next);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}

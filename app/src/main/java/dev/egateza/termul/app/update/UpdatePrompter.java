package dev.egateza.termul.app.update;

import dev.egateza.termul.update.ReleaseVersion;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Selain badge, buka dialog update (modal) saat pemeriksaan background menemukan versi baru. Sekali per versi per
 * sesi; versi yang sudah dilihat user lewat dialog yang dibuka sendiri tidak ditawarkan lagi. Selama ada dialog modal
 * lain (mis. konfirmasi host key, password), penawaran ditunda dan dicoba lagi setiap {@link #RETRY_MS}. Hanya EDT.
 */
public final class UpdatePrompter {

    static final int RETRY_MS = 30_000;

    private final BooleanSupplier busy;
    private final BooleanSupplier dialogOpen;
    private final Consumer<ReleaseVersion> open;
    private final Timer retry;
    /** Versi terakhir yang sudah ditawarkan atau dilihat user. */
    private ReleaseVersion prompted;
    /** Versi yang menunggu dialog modal lain ditutup. */
    private ReleaseVersion pending;

    /**
     * @param busy       true = ada dialog modal lain yang sedang tampil
     * @param dialogOpen true = dialog update sedang tampil (dibuka user)
     * @param open       buka dialog update; dipanggil lewat {@code invokeLater}, bukan di dalam listener badge
     */
    public UpdatePrompter(BooleanSupplier busy, BooleanSupplier dialogOpen, Consumer<ReleaseVersion> open) {
        this.busy = busy;
        this.dialogOpen = dialogOpen;
        this.open = open;
        this.retry = new Timer(RETRY_MS, e -> tryOpen());
        this.retry.setRepeats(false);
    }

    /** State badge berubah. */
    public void onState(UpdateNotifier.State state) {
        if (!(state instanceof UpdateNotifier.Available(var version))) {
            cancel();
            return;
        }
        if (version.equals(prompted)) {
            return;
        }
        if (dialogOpen.getAsBoolean()) {
            prompted = version; // user sedang melihatnya sendiri
            cancel();
            return;
        }
        pending = version;
        tryOpen();
    }

    void tryOpen() {
        if (pending == null) {
            return;
        }
        if (busy.getAsBoolean()) {
            retry.restart();
            return;
        }
        ReleaseVersion version = pending;
        prompted = version;
        pending = null;
        SwingUtilities.invokeLater(() -> open.accept(version));
    }

    private void cancel() {
        pending = null;
        retry.stop();
    }

    /** Untuk test: apakah penawaran sedang menunggu dialog lain ditutup. */
    boolean waiting() {
        return pending != null;
    }

    public void stop() {
        cancel();
    }
}

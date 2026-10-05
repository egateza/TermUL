package dev.egateza.termul.ssh;

import java.util.concurrent.CompletableFuture;

/**
 * Token pembatalan connect yang sedang berjalan (mis. tab ditutup selagi masih connect). Thread-safe;
 * {@link #cancel()} idempotent dan boleh dipanggil dari thread mana pun (termasuk EDT: tidak blocking).
 */
public final class ConnectCancel {

    private final CompletableFuture<Void> cancelled = new CompletableFuture<>();

    public void cancel() {
        cancelled.complete(null);
    }

    public boolean isCancelled() {
        return cancelled.isDone();
    }

    /** Menjalankan {@code action} saat dibatalkan (langsung kalau sudah), di thread yang membatalkan. */
    void onCancel(Runnable action) {
        cancelled.thenRun(action);
    }

    CompletableFuture<Void> future() {
        return cancelled;
    }
}

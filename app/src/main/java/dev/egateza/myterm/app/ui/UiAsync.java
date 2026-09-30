package dev.egateza.myterm.app.ui;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Menjalankan pekerjaan I/O di luar EDT lalu mengirim hasilnya kembali ke EDT. */
public final class UiAsync {

    private UiAsync() {
    }

    public static <T> CompletableFuture<T> run(Executor executor, Supplier<T> work,
                                               Consumer<? super T> onSuccessEdt,
                                               Consumer<? super Throwable> onErrorEdt) {
        var future = CompletableFuture.supplyAsync(work, executor);
        future.whenComplete((value, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                onErrorEdt.accept(unwrap(error));
            } else {
                onSuccessEdt.accept(value);
            }
        }));
        return future;
    }

    public static CompletableFuture<Void> run(Executor executor, Runnable work,
                                              Consumer<? super Throwable> onErrorEdt) {
        return run(executor, () -> {
            work.run();
            return null;
        }, ignored -> { }, onErrorEdt);
    }

    public static Throwable unwrap(Throwable t) {
        while (t instanceof CompletionException && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}

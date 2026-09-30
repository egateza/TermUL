package dev.egateza.myterm.app;

import com.formdev.flatlaf.FlatDarkLaf;
import dev.egateza.myterm.app.ui.Dialogs;
import dev.egateza.myterm.app.ui.MainFrame;
import dev.egateza.myterm.app.ui.UiAsync;
import dev.egateza.myterm.core.AppPaths;
import dev.egateza.myterm.core.profile.ProfileStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point dan wiring manual (constructor injection, tanpa framework DI). */
public final class MyTermApp {

    private MyTermApp() {
    }

    public static void main(String[] args) {
        AppPaths paths = AppPaths.detect();
        // Harus diset sebelum logger pertama dibuat (dipakai logback.xml)
        System.setProperty("myterm.logDir", paths.logDir().toString());
        Logger log = LoggerFactory.getLogger(MyTermApp.class);
        log.info("MyTerm start, config={}, cache={}", paths.configDir(), paths.cacheDir());

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> log.error("Uncaught exception di thread {}", t.getName(), e));

        ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("io").daemon().factory());
        var store = new ProfileStore(paths.profilesFile());

        SwingUtilities.invokeLater(() -> {
            FlatDarkLaf.setup();
            var frame = new MainFrame(store, io, () -> shutdown(io, log));
            frame.setVisible(true);
            UiAsync.run(io, store::load, frame::showSnapshot,
                    err -> Dialogs.error(frame, "Gagal memuat profil", err));
        });
    }

    private static void shutdown(ExecutorService io, Logger log) {
        log.info("MyTerm keluar");
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.exit(0);
    }
}

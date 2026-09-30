package dev.egateza.termul.app;

import com.formdev.flatlaf.FlatDarkLaf;
import dev.egateza.termul.app.edit.EditManager;
import dev.egateza.termul.app.ssh.SwingCredentialProvider;
import dev.egateza.termul.app.ssh.SwingHostKeyPrompt;
import dev.egateza.termul.app.terminal.TerminalSettings;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.MainFrame;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.app.vault.VaultCredentialProvider;
import dev.egateza.termul.app.vault.VaultGate;
import dev.egateza.termul.core.AppPaths;
import dev.egateza.termul.core.config.ConfigStore;
import dev.egateza.termul.core.profile.ProfileStore;
import dev.egateza.termul.ssh.SessionManager;
import dev.egateza.termul.ssh.SshSettings;
import dev.egateza.termul.sftp.edit.EditCache;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.terminal.SshTerminalFactory;
import dev.egateza.termul.vault.Argon2Params;
import dev.egateza.termul.vault.DpapiKeyProtector;
import dev.egateza.termul.vault.FileCredentialVault;
import java.awt.Component;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point dan wiring manual (constructor injection, tanpa framework DI). */
public final class TermULApp {

    private TermULApp() {
    }

    public static void main(String[] args) {
        AppPaths.Detected detected = AppPaths.detect();
        AppPaths paths = detected.paths();
        // Harus diset sebelum logger pertama dibuat (dipakai logback.xml)
        System.setProperty("termul.logDir", paths.logDir().toString());
        Logger log = LoggerFactory.getLogger(TermULApp.class);
        log.info("TermUL start, config={}, cache={}", paths.configDir(), paths.cacheDir());
        detected.notes().forEach(note -> log.warn("{}", note));

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> log.error("Uncaught exception di thread {}", t.getName(), e));

        ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("io").daemon().factory());
        ExecutorService sshOps = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ssh-ops-", 0).factory());
        var store = new ProfileStore(paths.profilesFile());
        var config = new ConfigStore(paths.configFile());
        config.load();

        var frameRef = new AtomicReference<Component>();
        var vault = new VaultGate(
                new FileCredentialVault(paths.vaultFile(), Argon2Params.defaults(), new DpapiKeyProtector()),
                frameRef::get, Duration.ofMinutes(15));
        var sessions = new SessionManager(
                new KnownHostsStore(paths.knownHostsFile()),
                new SwingHostKeyPrompt(frameRef::get),
                new VaultCredentialProvider(vault, new SwingCredentialProvider(frameRef::get)),
                SshSettings.defaults());
        EditManager edits;
        try {
            edits = new EditManager(sessions, new EditCache(paths.editCacheDir()), () -> config.current().editors(),
                    sshOps, frameRef::get);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("WatchService tidak tersedia", e);
        }

        SwingUtilities.invokeLater(() -> {
            FlatDarkLaf.setup();
            var ctx = new AppContext(paths, config, store, io, sshOps, sessions, new SshTerminalFactory(sessions),
                    new TerminalSettings(config.current().terminalFontSize()), vault, edits);
            var frame = new MainFrame(ctx, () -> shutdown(io, sshOps, sessions, vault, edits, log));
            frameRef.set(frame);
            frame.setVisible(true);
            UiAsync.run(io, store::load, frame::showSnapshot,
                    err -> Dialogs.error(frame, "Gagal memuat profil", err));
        });
    }

    private static void shutdown(ExecutorService io, ExecutorService sshOps, SessionManager sessions, VaultGate vault,
                                 EditManager edits,
                                 Logger log) {
        log.info("TermUL keluar");
        edits.close();
        sshOps.shutdownNow();
        sessions.close();
        vault.close();
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.exit(0);
    }
}

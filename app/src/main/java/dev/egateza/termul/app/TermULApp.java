package dev.egateza.termul.app;

import dev.egateza.termul.app.edit.EditManager;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ssh.SwingCredentialProvider;
import dev.egateza.termul.app.ssh.SwingHostKeyPrompt;
import dev.egateza.termul.app.terminal.BackgroundImages;
import dev.egateza.termul.app.terminal.TerminalSettings;
import dev.egateza.termul.app.update.AppUpdates;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.DialogSounds;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.IconSet;
import dev.egateza.termul.app.ui.MainFrame;
import dev.egateza.termul.app.ui.FontCatalog;
import dev.egateza.termul.app.ui.MacIntegration;
import dev.egateza.termul.app.ui.ThemeMode;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.app.ui.UiFont;
import dev.egateza.termul.app.ui.UiThemes;
import dev.egateza.termul.app.ui.anim.AnimationChoice;
import dev.egateza.termul.app.ui.anim.LoadingPanel;
import dev.egateza.termul.app.vault.VaultCredentialProvider;
import dev.egateza.termul.app.vault.VaultGate;
import dev.egateza.termul.app.vault.VaultSudoPassword;
import dev.egateza.termul.core.AppPaths;
import dev.egateza.termul.core.config.ConfigStore;
import dev.egateza.termul.core.theme.BundledThemes;
import dev.egateza.termul.core.theme.ThemeStore;
import dev.egateza.termul.core.profile.ProfileStore;
import dev.egateza.termul.sftp.SftpLinks;
import dev.egateza.termul.ssh.SessionManager;
import dev.egateza.termul.ssh.SshSettings;
import dev.egateza.termul.sftp.edit.EditCache;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.terminal.SshTerminalFactory;
import dev.egateza.termul.vault.Argon2Params;
import dev.egateza.termul.vault.DpapiKeyProtector;
import dev.egateza.termul.vault.FileCredentialVault;
import java.awt.Component;
import java.io.UncheckedIOException;
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
        MacIntegration.configureBeforeAwt();
        AppPaths.Detected detected = AppPaths.detect();
        AppPaths paths = detected.paths();
        // Harus diset sebelum logger pertama dibuat (dipakai logback.xml)
        System.setProperty("termul.logDir", paths.logDir().toString());
        Logger log = LoggerFactory.getLogger(TermULApp.class);
        log.info("TermUL start, config={}, cache={}", paths.configDir(), paths.cacheDir());
        detected.notes().forEach(note -> log.warn("{}", note));
        AppUpdates.logBootstrapNotes(log);

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> log.error("Uncaught exception di thread {}", t.getName(), e));

        ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("io").daemon().factory());
        ExecutorService sshOps = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ssh-ops-", 0).factory());
        var store = new ProfileStore(paths.profilesFile());
        var config = new ConfigStore(paths.configFile());
        config.load();
        var themes = new ThemeStore(paths.themesDir());
        try {
            int installed = themes.installBundled(BundledThemes.load());
            if (installed > 0) {
                log.info("{} template tema bawaan dipasang", installed);
            }
        } catch (UncheckedIOException e) {
            log.warn("Template tema bawaan tidak terpasang: {}", e.getMessage()); // tidak fatal, tema lain tetap jalan
        }
        var customThemes = themes.list(); // baca disk di sini, bukan di EDT
        var startupTheme = UiThemes.resolve(config.current().theme(), customThemes);
        var startupMode = ThemeMode.fromId(config.current().themeMode());
        var startupPalette = UiThemes.terminalPalette(config.current(), customThemes, startupTheme.effectiveMode(startupMode));
        var startupBackdrop = BackgroundImages.key(startupPalette) == null ? null
                : BackgroundImages.loadFor(startupPalette); // baca disk di sini, bukan di EDT
        I18n.use(config.current().language());
        FontCatalog.preload(); // menyaring font monospace lambat; selesai di background sebelum dialog font dibuka

        var frameRef = new AtomicReference<Component>();
        var vault = new VaultGate(
                new FileCredentialVault(paths.vaultFile(), Argon2Params.defaults(), new DpapiKeyProtector()),
                frameRef::get, Duration.ofMinutes(15));
        var knownHosts = new KnownHostsStore(paths.knownHostsFile());
        var sessions = new SessionManager(
                knownHosts,
                new SwingHostKeyPrompt(frameRef::get),
                new VaultCredentialProvider(vault, new SwingCredentialProvider(frameRef::get)),
                SshSettings.defaults(), id -> store.snapshot().find(id));
        var sftpLinks = new SftpLinks(sessions);
        EditManager edits;
        try {
            edits = new EditManager(sftpLinks, new EditCache(paths.editCacheDir()), () -> config.current().editors(),
                    sshOps, frameRef::get, p -> new VaultSudoPassword(vault, p, frameRef::get),
                    () -> config.current().validationHooks());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("WatchService tidak tersedia", e);
        }

        SwingUtilities.invokeLater(() -> {
            UiFont.apply(config.current().uiFontFamily()); // sebelum tema dipasang
            startupTheme.install(startupMode);
            AppIcon.use(IconSet.fromId(config.current().iconSet()));
            LoadingPanel.use(AnimationChoice.fromId(config.current().loadingAnimation()));
            DialogSounds.install();
            var terminalSettings = new TerminalSettings(config.current().terminalFontFamily(),
                    config.current().terminalFontSize());
            terminalSettings.setPalette(startupPalette);
            terminalSettings.setBackgroundImage(startupBackdrop, startupPalette.imageVisibility());
            terminalSettings.bell().setSound(config.current().bellSound());
            terminalSettings.bell().setShake(config.current().bellShake());
            var ctx = new AppContext(paths, config, store, io, sshOps, sessions, new SshTerminalFactory(sessions),
                    terminalSettings, vault, edits, sftpLinks, themes, knownHosts);
            var frame = new MainFrame(ctx, customThemes, () -> shutdown(io, sshOps, sessions, vault, edits, log));
            frameRef.set(frame);
            MacIntegration.install(frame);
            frame.setVisible(true);
            frame.startUpdateChecks();
            io.execute(() -> AppUpdates.markHealthy(paths, log)); // rollback otomatis tidak dipicu untuk versi ini
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

package dev.egateza.termul.app;

import dev.egateza.termul.app.edit.EditManager;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.monitor.MemoryTuning;
import dev.egateza.termul.app.ssh.SwingCredentialProvider;
import dev.egateza.termul.app.ssh.SwingFtpsCertPrompt;
import dev.egateza.termul.app.ssh.SwingHostKeyPrompt;
import dev.egateza.termul.app.startup.AutoStart;
import dev.egateza.termul.app.startup.ForegroundPermission;
import dev.egateza.termul.app.startup.OpenLocalRequest;
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
import dev.egateza.termul.app.vault.OsKeyProtectors;
import dev.egateza.termul.app.vault.VaultCredentialProvider;
import dev.egateza.termul.app.vault.VaultGate;
import dev.egateza.termul.app.vault.VaultSudoPassword;
import dev.egateza.termul.core.AppPaths;
import dev.egateza.termul.core.config.AppConfig;
import dev.egateza.termul.core.config.ConfigStore;
import dev.egateza.termul.core.instance.SingleInstance;
import dev.egateza.termul.core.snippet.SnippetStore;
import dev.egateza.termul.core.theme.BundledThemes;
import dev.egateza.termul.core.theme.ThemeStore;
import dev.egateza.termul.core.profile.ProfileStore;
import dev.egateza.termul.core.wsl.ProcessWslRunner;
import dev.egateza.termul.core.wsl.WslManager;
import dev.egateza.termul.sftp.SftpLinks;
import dev.egateza.termul.sftp.ftp.FtpConnector;
import dev.egateza.termul.sftp.ftp.FtpsCertStore;
import dev.egateza.termul.ssh.SessionManager;
import dev.egateza.termul.ssh.SshSettings;
import dev.egateza.termul.sftp.edit.EditCache;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.terminal.SshTerminalFactory;
import dev.egateza.termul.vault.Argon2Params;
import dev.egateza.termul.vault.FileCredentialVault;
import java.awt.Component;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JOptionPane;
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
        // Harus diset sebelum logger pertama dibuat (dipakai logback.xml), termasuk logger static class lain seperti
        // MacIntegration: logback hanya membaca konfigurasi sekali, sesudah itu log tertulis ke ./logs
        System.setProperty("termul.logDir", paths.logDir().toString());
        MacIntegration.configureBeforeAwt();
        Logger log = LoggerFactory.getLogger(TermULApp.class);
        log.info("TermUL start, config={}, cache={}", paths.configDir(), paths.cacheDir());
        detected.notes().forEach(note -> log.warn("{}", note));
        AppUpdates.logBootstrapNotes(log);

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> log.error("Uncaught exception di thread {}", t.getName(), e));

        // satu instance per folder data: membuka TermUL lagi (mis. saat di tray) menampilkan window yang sudah ada
        Optional<OpenLocalRequest> openLocal = OpenLocalRequest.parse(args); // menu klik kanan Explorer
        SingleInstance instance = acquireInstance(paths, log,
                openLocal.map(OpenLocalRequest::toRequest).orElseGet(SingleInstance.Request::show));
        boolean startHidden = openLocal.isEmpty() && List.of(args).contains(AutoStart.ARG_TRAY);

        ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("io").daemon().factory());
        ExecutorService sshOps = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ssh-ops-", 0).factory());
        var store = new ProfileStore(paths.profilesFile());
        var config = new ConfigStore(paths.configFile());
        config.load();
        MemoryTuning.platform().apply(MemoryTuning.Mode.fromId(config.current().memoryMode()));
        var themes = new ThemeStore(paths.themesDir());
        var snippets = new SnippetStore(paths.snippetsFile());
        snippets.load();
        var localStore = new dev.egateza.termul.core.localshell.LocalProfileStore(paths.localProfilesFile());
        localStore.load();
        var locals = new dev.egateza.termul.app.terminal.LocalProfiles(localStore);
        locals.detect(); // baca /etc/shells & cek file di sini, bukan di EDT
        var highlights = new dev.egateza.termul.core.highlight.HighlightStore(paths.highlightFile());
        highlights.load();
        var bookmarks = new dev.egateza.termul.core.bookmark.BookmarkStore(paths.bookmarksFile());
        bookmarks.load();
        var shortcuts = new dev.egateza.termul.core.shortcut.ShortcutStore(paths.shortcutsFile());
        dev.egateza.termul.app.ui.Shortcuts.use(dev.egateza.termul.app.ui.KeyBindings.of(shortcuts.load(),
                dev.egateza.termul.core.Os.current().isMac())); // sebelum menu dibuat
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
                new FileCredentialVault(paths.vaultFile(), Argon2Params.defaults(),
                        OsKeyProtectors.forCurrentOs(paths.vaultFile())),
                frameRef::get, Duration.ofMinutes(15));
        var knownHosts = new KnownHostsStore(paths.knownHostsFile());
        var credentials = new VaultCredentialProvider(vault, new SwingCredentialProvider(frameRef::get));
        var sessions = new SessionManager(
                knownHosts,
                new SwingHostKeyPrompt(frameRef::get),
                credentials,
                SshSettings.defaults(), id -> store.snapshot().find(id));
        var ftpsCerts = new FtpsCertStore(paths.ftpsCertsFile());
        var sftpLinks = new SftpLinks(sessions,
                new FtpConnector(credentials, ftpsCerts, new SwingFtpsCertPrompt(frameRef::get)));
        var wsl = new WslManager(new ProcessWslRunner());
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
            terminalSettings.setRightClickCopyPaste(
                    AppConfig.RIGHT_CLICK_COPY_PASTE.equals(config.current().rightClick()));
            terminalSettings.setCursorShape(TerminalSettings.cursorShapeOf(config.current().cursorShape(),
                    config.current().cursorBlink()));
            var ctx = new AppContext(paths, config, store, io, sshOps, sessions, new SshTerminalFactory(sessions),
                    terminalSettings, vault, edits, sftpLinks, themes, knownHosts, ftpsCerts, wsl, snippets, locals,
                    highlights, shortcuts, bookmarks);
            var frame = new MainFrame(ctx, customThemes,
                    () -> shutdown(io, sshOps, sessions, vault, edits, wsl, instance, log));
            frameRef.set(frame);
            MacIntegration.install(frame);
            if (startHidden) {
                frame.startHidden(); // run at startup: tetap di tray, tanpa prompt vault / koneksi
            } else {
                frame.setVisible(true);
                frame.startStreak();
            }
            frame.startUpdateChecks();
            openLocal.ifPresent(o -> frame.openLocalIn(o.shell(), o.directory()));
            if (instance != null) {
                instance.listen(request -> SwingUtilities.invokeLater(() -> OpenLocalRequest.from(request)
                        .ifPresentOrElse(o -> frame.openLocalIn(o.shell(), o.directory()), frame::showWindow)));
            }
            io.execute(() -> AppUpdates.markHealthy(paths, log)); // rollback otomatis tidak dipicu untuk versi ini
            dev.egateza.termul.app.startup.WindowsAppIcon.refreshInBackground(paths); // ikon shortcut ikut update
            UiAsync.run(io, store::load, frame::showSnapshot,
                    err -> Dialogs.error(frame, "Gagal memuat profil", err));
        });
    }

    /**
     * Ambil lock single instance. Kalau TermUL lain sudah berjalan, window-nya diminta tampil dan proses ini keluar.
     *
     * @return null kalau pemeriksaan gagal (lanjut tanpa single instance, seperti sebelum fitur ini ada)
     */
    private static SingleInstance acquireInstance(AppPaths paths, Logger log, SingleInstance.Request request) {
        ForegroundPermission.allowAny(); // instance kedua: izinkan instance pertama membawa window-nya ke depan
        SingleInstance.Result result;
        try {
            result = SingleInstance.acquire(paths.configDir(), request);
        } catch (IOException | RuntimeException e) {
            log.warn("Pemeriksaan single instance gagal, lanjut tanpa: {}", e.toString());
            return null;
        }
        switch (result) {
            case SingleInstance.Primary(var instance) -> {
                return instance;
            }
            case SingleInstance.Signaled() -> log.info("TermUL sudah berjalan: permintaan diteruskan ke instance itu");
            case SingleInstance.Unreachable(var reason) -> {
                log.warn("TermUL sudah berjalan tetapi tidak merespons: {}", reason);
                var config = new ConfigStore(paths.configFile());
                config.load();
                I18n.use(config.current().language());
                JOptionPane.showMessageDialog(null, I18n.t("instance.unreachable"), I18n.t("instance.unreachable.title"),
                        JOptionPane.WARNING_MESSAGE);
            }
        }
        System.exit(0);
        return null; // tidak tercapai
    }

    private static void shutdown(ExecutorService io, ExecutorService sshOps, SessionManager sessions, VaultGate vault,
                                 EditManager edits, WslManager wsl, SingleInstance instance, Logger log) {
        log.info("TermUL keluar");
        edits.close();
        wsl.close(); // lepas keep-alive; distro mati sendiri saat idle kecuali dipakai di luar TermUL
        sshOps.shutdownNow();
        sessions.close();
        vault.close();
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (instance != null) {
            instance.close(); // paling akhir: instance baru tidak boleh start selagi state masih disimpan
        }
        System.exit(0);
    }
}

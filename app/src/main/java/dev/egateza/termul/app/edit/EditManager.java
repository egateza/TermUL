package dev.egateza.termul.app.edit;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.Edt;
import dev.egateza.termul.app.sftp.ActivityBar.Level;
import dev.egateza.termul.core.config.EditorConfig;
import dev.egateza.termul.core.config.ValidationHooks;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.sftp.RemoteFileException;
import dev.egateza.termul.sftp.SftpConnection;
import dev.egateza.termul.sftp.SftpLinks;
import dev.egateza.termul.sftp.edit.EditCache;
import dev.egateza.termul.sftp.edit.EditWatcher;
import dev.egateza.termul.sftp.edit.RemoteEditSession;
import dev.egateza.termul.sftp.edit.RemoteEditSession.LineEndingPolicy;
import dev.egateza.termul.sftp.edit.RemoteEditSession.SyncOptions;
import dev.egateza.termul.sftp.edit.RemoteEditSession.SyncResult;
import dev.egateza.termul.sftp.edit.RemoteValidationException;
import dev.egateza.termul.sftp.edit.SudoAuthException;
import dev.egateza.termul.sftp.edit.SudoWriter;
import java.awt.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mengelola semua sesi edit remote: buka → editor lokal → auto-upload saat disimpan → tutup.
 * Method publik boleh dipanggil dari EDT; semua I/O berjalan di {@code sshOps}.
 */
public final class EditManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EditManager.class);
    /** Editor yang keluar lebih cepat dari ini dianggap "fork" (non-wait): sesi tidak ditutup otomatis. */
    private static final Duration WAIT_EDITOR_MIN = Duration.ofSeconds(3);

    /** Satu file yang sedang diedit. */
    public static final class Entry {
        private final HostProfile profile;
        private final RemoteEditSession session;
        private volatile Process editor;
        private volatile String template; // command editor pilihan user, null = sesuai pengaturan
        private final SftpConnection link; // koneksi bersama profil ini (dilepas saat sesi ditutup)
        private volatile SudoWriter sudo;   // null = upload SFTP biasa; bisa dinaikkan ke sudo (Edit sebagai root)
        private final boolean needsSudo;    // tidak bisa ditimpa user login: setiap "Edit" menanyakan sudo / lihat saja

        Entry(HostProfile profile, RemoteEditSession session, SftpConnection link, SudoWriter sudo, boolean needsSudo) {
            this.profile = profile;
            this.session = session;
            this.link = link;
            this.sudo = sudo;
            this.needsSudo = needsSudo;
        }

        public HostProfile profile() {
            return profile;
        }

        public RemoteEditSession session() {
            return session;
        }

        /** true kalau file dipasang lewat sudo (file root). */
        public boolean isSudo() {
            return sudo != null;
        }
    }

    private record Key(java.util.UUID profileId, String remotePath) {
    }

    /** Keterangan aktivitas edit untuk ditampilkan di panel SFTP profil yang sama. */
    private record Subscriber(java.util.UUID profileId, BiConsumer<Level, String> sink) {
    }

    private final SftpLinks links;
    private final EditCache cache;
    private final EditorLauncher launcher;
    private final Supplier<EditorConfig> editorConfig;
    private final ExecutorService sshOps;
    private final Supplier<Component> parent;
    private final EditWatcher watcher;
    private final Function<HostProfile, SudoWriter.SudoPassword> sudoPasswords;
    private final Supplier<ValidationHooks> validationHooks;
    /** Folder salinan "lihat saja" yang masih ada → profil pemiliknya; dihapus di {@link #closeProfile} / {@link #close()}. */
    private static final Map<Path, java.util.UUID> viewDirs = new ConcurrentHashMap<>();
    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();
    private final Map<Path, Entry> byLocal = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    /**
     * @param sudoPasswords   sumber password sudo per profil (edit file root)
     * @param validationHooks hook validasi yang berlaku saat upload (dibaca setiap upload)
     */
    public EditManager(SftpLinks links, EditCache cache, Supplier<EditorConfig> editorConfig,
                       ExecutorService sshOps, Supplier<Component> parent,
                       Function<HostProfile, SudoWriter.SudoPassword> sudoPasswords,
                       Supplier<ValidationHooks> validationHooks) throws IOException {
        this.links = links;
        this.cache = cache;
        this.editorConfig = editorConfig;
        this.launcher = new EditorLauncher(editorConfig);
        this.sshOps = sshOps;
        this.parent = parent;
        this.sudoPasswords = sudoPasswords;
        this.validationHooks = validationHooks;
        this.watcher = new EditWatcher(Duration.ofMillis(400), this::onLocalChanged);
    }

    /** Dipanggil (di thread sembarang) setiap daftar/status sesi berubah. */
    public void addListener(Runnable l) {
        listeners.add(l);
    }

    /**
     * Berlangganan keterangan aktivitas edit (buka, upload mulai/selesai, konflik, error) untuk satu profil.
     * Sink dipanggil di thread sembarang.
     *
     * @return pemanggil untuk berhenti berlangganan
     */
    public Runnable addActivityListener(java.util.UUID profileId, BiConsumer<Level, String> sink) {
        var subscriber = new Subscriber(profileId, sink);
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    private void activity(HostProfile profile, Level level, String message) {
        for (var s : subscribers) {
            if (s.profileId.equals(profile.id())) {
                s.sink.accept(level, message);
            }
        }
    }

    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    /** Buka file remote di editor lokal (atau fokuskan lagi kalau sudah dibuka). */
    public void open(HostProfile profile, String remotePath) {
        open(profile, remotePath, null);
    }

    /** @param template command editor pilihan user (menu "Edit dengan"); null = sesuai pengaturan */
    public void open(HostProfile profile, String remotePath, String template) {
        open(profile, remotePath, template, false);
    }

    /** Buka file untuk diedit sebagai root: perubahan dipasang lewat sudo (backup + validasi + rollback). */
    public void openAsRoot(HostProfile profile, String remotePath) {
        if (profile.isFtp()) { // sudo butuh exec lewat SSH
            showError(I18n.t("edit.error.openFailed", remotePath),
                    new IllegalStateException(I18n.t("edit.sudo.notForFtp")));
            return;
        }
        open(profile, remotePath, null, true);
    }

    /**
     * Lihat saja: salinan sementara (read-only) dibuka di editor lokal, tanpa sesi edit, watcher, atau upload.
     * Mengubah dan menyimpan salinan itu tidak pernah menyentuh file di server. Selalu dibuka dengan aplikasi default
     * file (association OS). Salinan dihapus di {@link #close()}.
     */
    public void view(HostProfile profile, String remotePath) {
        sshOps.execute(() -> {
            Path dir = null;
            boolean linked = false;
            try {
                activity(profile, Level.INFO, I18n.t("edit.activity.downloading", remotePath));
                SftpConnection link = links.acquire(profile);
                linked = true;
                try {
                    dir = Files.createTempDirectory("termul-view-");
                    viewDirs.put(dir, profile.id());
                    Path local = dir.resolve(dev.egateza.termul.sftp.RemotePaths.name(remotePath));
                    link.ensureLive().download(remotePath, local, dev.egateza.termul.sftp.TransferListener.NONE);
                    if (!local.toFile().setReadOnly()) {
                        log.debug("Salinan lihat-saja {} tidak bisa ditandai read-only", local);
                    }
                    launcher.launch(local, EditorLauncher.SYSTEM_DEFAULT);
                    activity(profile, Level.INFO, I18n.t("edit.activity.viewing", remotePath));
                } finally {
                    links.release(profile);
                    linked = false;
                }
            } catch (Exception e) {
                if (linked) {
                    links.release(profile);
                }
                deleteViewCopy(dir);
                activity(profile, Level.ERROR, I18n.t("edit.activity.openFailed", remotePath, e.getMessage()));
                showError(I18n.t("edit.error.openFailed", remotePath), e);
            }
        });
    }

    private static void deleteViewCopy(Path dir) {
        if (dir == null) {
            return;
        }
        try (var files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                f.toFile().setWritable(true);
                Files.deleteIfExists(f);
            }
            Files.deleteIfExists(dir);
            viewDirs.remove(dir);
        } catch (IOException e) { // mis. file masih dikunci aplikasi; tetap terdaftar, dicoba lagi saat close()
            log.warn("Salinan lihat-saja {} tidak bisa dihapus: {}", dir, e.toString());
        }
    }

    /**
     * @param asRoot true = pasti lewat sudo; false = SFTP biasa, kecuali file tidak bisa ditulis user login
     *               (user ditanya sudo / lihat saja setiap kali dibuka, juga kalau sesinya sudah ada)
     */
    private void open(HostProfile profile, String remotePath, String template, boolean asRoot) {
        var key = new Key(profile.id(), remotePath);
        sshOps.execute(() -> {
            try {
                Entry entry = entries.get(key);
                Entry created = null;
                if (entry == null) {
                    activity(profile, Level.INFO, I18n.t("edit.activity.downloading", remotePath));
                    SftpConnection link = links.acquire(profile);
                    RemoteEditSession session;
                    SudoWriter sudo = null;
                    boolean needsSudo;
                    try {
                        var files = link.ensureLive();
                        needsSudo = !files.canWrite(remotePath);
                        Boolean useSudo = asRoot ? Boolean.TRUE : needsSudo ? askSudo(profile, remotePath)
                                : Boolean.FALSE;
                        if (useSudo == null) {
                            links.release(profile);
                            return; // dibatalkan
                        }
                        if (needsSudo && !useSudo) {
                            links.release(profile);
                            view(profile, remotePath); // tanpa sudo = salinan lihat-saja, bukan sesi yang menolak setiap save
                            return;
                        }
                        if (useSudo) {
                            sudo = new SudoWriter(sudoPasswords.apply(profile), validationHooks);
                        }
                        session = RemoteEditSession.open(files, cache, profile.id(), remotePath,
                                sudo != null ? sudo : RemoteEditSession.SFTP_UPLOADER);
                    } catch (Exception e) {
                        links.release(profile);
                        throw e;
                    }
                    var fresh = new Entry(profile, session, link, sudo, needsSudo);
                    entry = created = fresh;
                    session.addListener(s -> {
                        fireChanged();
                        publishState(fresh, s);
                    });
                    entries.put(key, entry);
                    byLocal.put(session.localFile().toAbsolutePath().normalize(), entry);
                    watcher.watch(session.localFile());
                    fireChanged();
                } else if (asRoot) {
                    if (entry.sudo == null) {
                        upgradeToSudo(entry);
                    }
                } else if (entry.needsSudo) {
                    // pilihan sudo / lihat saja berlaku per buka, bukan per sesi: selalu ditanya lagi
                    Boolean useSudo = askSudo(profile, remotePath);
                    if (useSudo == null) {
                        return; // dibatalkan: sesi tetap ada (tracker), editor tidak dibuka
                    }
                    if (!useSudo) {
                        view(profile, remotePath); // sesi yang ada dibiarkan; yang dibuka salinan lihat-saja
                        return;
                    }
                    if (entry.sudo == null) {
                        upgradeToSudo(entry);
                    }
                }
                if (created == null && !refreshFromServer(entry)) {
                    return; // dibatalkan
                }
                entry.template = template;
                launchEditor(entry);
            } catch (Exception e) {
                activity(profile, Level.ERROR, I18n.t("edit.activity.openFailed", remotePath, e.getMessage()));
                showError(I18n.t("edit.error.openFailed", remotePath), e);
            }
        });
    }

    /**
     * File sudah dibuka tanpa sudo (mis. "lihat saja") lalu dipilih "Edit sebagai root": sesi yang sama dipakai
     * dengan upload lewat sudo. Perubahan lokal yang tadi ditolak ikut terpasang pada save berikutnya.
     */
    private void upgradeToSudo(Entry entry) {
        var sudo = new SudoWriter(sudoPasswords.apply(entry.profile), validationHooks);
        entry.session.switchUploader(sudo);
        entry.sudo = sudo;
        log.info("Edit {} dialihkan ke sudo", entry.session.remotePath());
        activity(entry.profile, Level.INFO, I18n.t("edit.activity.switchedToSudo", entry.session.remotePath()));
        fireChanged();
    }

    /** File tidak bisa ditulis user login. @return true = sudo, false = lihat saja (tanpa sudo), null = batal */
    private Boolean askSudo(HostProfile profile, String remotePath) {
        Object[] choices = {I18n.t("edit.sudo.useSudo"), I18n.t("edit.sudo.withoutSudo"), I18n.t("edit.common.cancel")};
        int choice = ask(I18n.t("edit.sudo.title", remotePath),
                I18n.t("edit.sudo.message", remotePath, profile.username()), choices);
        return switch (choice) {
            case 0 -> Boolean.TRUE;
            case 1 -> Boolean.FALSE;
            default -> null;
        };
    }

    /** Menjalankan editor untuk entry (juga dipakai "Buka lagi" dari EditTracker). */
    public void reopenEditor(Entry entry) {
        sshOps.execute(() -> {
            try {
                if (refreshFromServer(entry)) {
                    launchEditor(entry);
                }
            } catch (IOException e) {
                showError(I18n.t("edit.error.editorLaunch"), e);
            }
        });
    }

    /**
     * Sesi edit yang masih terbuka dibuka lagi: file lokal disamakan dulu dengan server, supaya isi yang ditimpa
     * dari luar (upload panel SFTP, sesi lain, orang lain) tidak tertutup cache lama. Tanpa perubahan lokal yang
     * tertunda, file selalu diunduh ulang (sama seperti membuka dari awal). Kalau ada perubahan tertunda dan file
     * server juga berubah, user memilih versi mana yang dipakai. Tidak tersambung = pakai salinan lokal apa adanya.
     *
     * @return false kalau dibatalkan user (editor tidak dibuka)
     */
    private boolean refreshFromServer(Entry entry) {
        var session = entry.session;
        try {
            var live = entry.link.ensureLive();
            if (session.files() != live) {
                session.rebind(live);
            }
            if (!session.hasPendingChanges()) {
                session.reload();
                return true;
            }
            if (!session.remoteChanged()) {
                return true; // perubahan lokal tetap, di-upload pada save berikutnya
            }
            Object[] choices = {I18n.t("edit.reload.server"), I18n.t("edit.reload.local"), I18n.t("edit.common.cancel")};
            int choice = ask(I18n.t("edit.reload.title", session.remotePath()), I18n.t("edit.reload.message"), choices);
            switch (choice) {
                case 0 -> session.reload();
                case 1 -> { } // save berikutnya memunculkan dialog konflik
                default -> {
                    return false;
                }
            }
            return true;
        } catch (RemoteFileException e) {
            log.warn("Edit {} tidak bisa disamakan dengan server, memakai salinan lokal: {}",
                    session.remotePath(), e.getMessage());
            activity(entry.profile, Level.WARN, I18n.t("edit.activity.reloadFailed", session.remotePath(), e.getMessage()));
            return true;
        }
    }

    private void launchEditor(Entry entry) throws IOException {
        Process p = launcher.launch(entry.session.localFile(), entry.template);
        entry.editor = p;
        if (p == null) {
            return; // aplikasi default Windows: tidak ada proses, sesi ditutup dari tracker / saat tab ditutup
        }
        Instant started = Instant.now();
        p.onExit().thenRun(() -> {
            boolean waited = Duration.between(started, Instant.now()).compareTo(WAIT_EDITOR_MIN) >= 0;
            if (waited && entry.editor == p) {
                log.info("Editor ditutup untuk {}", entry.session.remotePath());
                sshOps.execute(() -> finishAfterEditorExit(entry));
            }
        });
    }

    /** Editor (mode wait) ditutup: sync terakhir lalu tutup sesi kalau tidak ada yang tertunda. */
    private void finishAfterEditorExit(Entry entry) {
        syncInteractive(entry, SyncOptions.DEFAULT);
        if (!entry.session.hasPendingChanges()) {
            close(entry);
        }
    }

    private void onLocalChanged(Path local) {
        Entry entry = byLocal.get(local);
        if (entry != null) {
            sshOps.execute(() -> syncInteractive(entry, SyncOptions.DEFAULT));
        }
    }

    /** Terjemahkan perubahan state sesi edit menjadi keterangan aktivitas. */
    private void publishState(Entry entry, RemoteEditSession s) {
        HostProfile profile = entry.profile;
        String path = s.remotePath();
        switch (s.state()) {
            case UPLOADING -> activity(profile, Level.INFO, I18n.t("edit.activity.uploading", path));
            case EDITING -> {
                if ("Tersinkron".equals(s.lastMessage())) {
                    var installed = entry.sudo == null ? null : entry.sudo.lastInstalled();
                    activity(profile, Level.SUCCESS, installed == null
                            ? I18n.t("edit.activity.uploaded", path)
                            : I18n.t("edit.activity.uploadedSudo", path, installed.backupPath()));
                } else if ("Dibuka".equals(s.lastMessage())) {
                    activity(profile, Level.INFO, I18n.t("edit.activity.opened", path));
                } else if ("Dimuat ulang".equals(s.lastMessage())) {
                    activity(profile, Level.INFO, I18n.t("edit.activity.reloaded", path));
                }
            }
            case NEEDS_ATTENTION -> activity(profile, Level.WARN, I18n.t("edit.activity.attention", path, s.lastMessage()));
            case OPENING, CLOSED -> { }
        }
    }

    /** Upload manual / retry dari EditTracker. Butuh sesi terminal tersambung (Reconnect dilakukan dari terminal). */
    public void retry(Entry entry) {
        sshOps.execute(() -> {
            syncInteractive(entry, SyncOptions.DEFAULT); // menunggu / ditolak sesuai status sesi terminal
        });
    }

    /**
     * Pastikan koneksi SFTP hidup sebelum upload. Kalau terputus, upload menunggu terminal host ini menyambung
     * ulang (bertahap, dikerjakan tab terminal) dan berjalan setelah shell + SFTP tersambung; kalau terminal
     * menyerah, upload tidak dilakukan dan user diminta Reconnect dari terminal.
     *
     * @return false kalau tidak tersambung (user sudah diberi tahu)
     */
    private boolean ensureConnected(Entry entry) {
        try {
            var live = entry.link.ensureLive();
            if (entry.session.files() != live) {
                entry.session.rebind(live); // sesi edit yang sama tetap bisa menyimpan setelah reconnect
            }
            return true;
        } catch (RemoteFileException e) {
            log.warn("Upload {} ditunda, tidak tersambung: {}", entry.session.remotePath(), e.getMessage());
            activity(entry.profile, Level.ERROR, I18n.t("edit.activity.uploadDeferred", entry.session.remotePath(), e.getMessage()));
            showError(I18n.t("edit.error.uploadDeferred", entry.session.remotePath()), e);
            return false;
        }
    }

    /** Sync + dialog untuk konflik/CRLF. Berjalan di sshOps (dialog via invokeAndWait). */
    private void syncInteractive(Entry entry, SyncOptions options) {
        syncInteractive(entry, options, true);
    }

    /** @param retryOnDisconnect sekali saja: koneksi putus di tengah upload -> sambung ulang lalu ulangi */
    private void syncInteractive(Entry entry, SyncOptions options, boolean retryOnDisconnect) {
        if (!ensureConnected(entry)) {
            return;
        }
        var session = entry.session;
        try {
            SyncResult result = session.sync(options);
            switch (result) {
                case SyncResult.Conflict _ -> handleConflict(entry, options);
                case SyncResult.LineEndingChanged _ -> handleLineEnding(entry, options);
                case SyncResult.Uploaded _ -> { }
                case SyncResult.Unchanged _ -> { }
            }
        } catch (RemoteFileException e) {
            if (retryOnDisconnect && !(e instanceof RemoteFileException.Cancelled) && !session.files().isOpen()) {
                syncInteractive(entry, options, false);
                return;
            }
            log.warn("Upload {} gagal: {}", session.remotePath(), e.getMessage());
            activity(entry.profile, Level.ERROR, I18n.t("edit.activity.uploadFailed", session.remotePath(), e.getMessage()));
            String title = switch (e) {
                case RemoteValidationException v when v.rolledBack() ->
                        I18n.t("edit.error.validationFailed", session.remotePath());
                case RemoteValidationException _ -> I18n.t("edit.error.rollbackFailed", session.remotePath());
                case SudoAuthException _ -> I18n.t("edit.error.sudoAuth", session.remotePath());
                default -> I18n.t("edit.error.uploadFailed", session.remotePath());
            };
            showError(title, e);
        }
    }

    private void handleConflict(Entry entry, SyncOptions options) {
        var session = entry.session;
        while (true) {
            Object[] choices = {I18n.t("edit.conflict.overwrite"), I18n.t("edit.conflict.diff"), I18n.t("edit.common.cancel")};
            int choice = ask(I18n.t("edit.conflict.title", session.remotePath()),
                    I18n.t("edit.conflict.message"), choices);
            switch (choice) {
                case 0 -> {
                    syncInteractive(entry, new SyncOptions(true, options.lineEndings()));
                    return;
                }
                case 1 -> showDiff(entry);
                default -> {
                    return;
                }
            }
        }
    }

    private void handleLineEnding(Entry entry, SyncOptions options) {
        Object[] choices = {I18n.t("edit.lineEnding.convert"), I18n.t("edit.lineEnding.keep"), I18n.t("edit.common.cancel")};
        int choice = ask(I18n.t("edit.lineEnding.title", entry.session.remotePath()),
                I18n.t("edit.lineEnding.message"), choices);
        LineEndingPolicy policy = switch (choice) {
            case 0 -> LineEndingPolicy.CONVERT_TO_LF;
            case 1 -> LineEndingPolicy.KEEP;
            default -> null;
        };
        if (policy != null) {
            syncInteractive(entry, new SyncOptions(options.overwriteConflict(), policy));
        }
    }

    private void showDiff(Entry entry) {
        try {
            Path local = entry.session.localFile();
            Path remoteCopy = local.resolveSibling(local.getFileName() + ".server");
            entry.session.downloadRemoteCopy(remoteCopy);
            List<String> command = editorConfig.get().commandFor(local);
            if (command.getFirst().equalsIgnoreCase("code")) {
                new ProcessBuilder(EditorLauncher.resolve(List.of("code", "--diff",
                        remoteCopy.toString(), local.toString()))).start();
            } else {
                launcher.launch(remoteCopy);
            }
        } catch (IOException e) {
            showError(I18n.t("edit.error.diffFailed"), e);
        }
    }

    /** Jumlah file yang sedang diedit untuk profil ini. */
    public int openCount(java.util.UUID profileId) {
        return (int) entries.values().stream().filter(e -> e.profile.id().equals(profileId)).count();
    }

    /**
     * Tutup semua sesi edit profil ini (tab terminal terakhirnya ditutup), seperti WinSCP: save berikutnya di
     * editor tidak di-upload lagi. Cache yang tersinkron dihapus; yang belum ter-upload dipertahankan (N4).
     */
    public void closeProfile(java.util.UUID profileId) {
        sshOps.execute(() -> {
            for (var entry : entries.values()) {
                if (entry.profile.id().equals(profileId)) {
                    close(entry);
                }
            }
            viewDirs.forEach((dir, owner) -> {
                if (owner.equals(profileId)) {
                    deleteViewCopy(dir); // salinan "lihat saja" ikut hilang; editor yang masih terbuka tidak terpengaruh
                }
            });
        });
    }

    /** Tutup sesi edit (cache dihapus kalau tersinkron). Aman dipanggil lebih dari sekali. */
    public void close(Entry entry) {
        var session = entry.session;
        if (!entries.remove(new Key(entry.profile.id(), session.remotePath()), entry)) {
            return; // sudah ditutup (mis. dari tracker dan dari tab sekaligus)
        }
        watcher.unwatch(session.localFile());
        boolean removed = session.close();
        byLocal.remove(session.localFile().toAbsolutePath().normalize());
        cleanupDiffCopy(session.localFile());
        links.release(entry.profile);
        if (!removed) {
            showInfo(I18n.t("edit.info.unsynced", session.remotePath(), session.localFile().toString()));
        }
        fireChanged();
    }

    private static void cleanupDiffCopy(Path local) {
        try {
            Files.deleteIfExists(local.resolveSibling(local.getFileName() + ".server"));
        } catch (IOException e) {
            log.debug("Salinan diff tidak bisa dihapus: {}", e.toString());
        }
    }

    /** Dialog pilihan; @return index pilihan, -1 = ditutup. Diganti di test (tanpa UI). */
    @FunctionalInterface
    interface Asker {
        int ask(String title, String message, Object[] choices);
    }

    private volatile Asker asker = this::showOptions;

    void setAsker(Asker asker) {
        this.asker = asker;
    }

    private int ask(String title, String message, Object[] choices) {
        return asker.ask(title, message, choices);
    }

    private int showOptions(String title, String message, Object[] choices) {
        var result = new AtomicInteger(-1);
        Edt.runAndWait(() -> result.set(JOptionPane.showOptionDialog(parent.get(), message, title,
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, choices[choices.length - 1])));
        return result.get();
    }

    private void showError(String title, Exception e) {
        SwingUtilities.invokeLater(() -> Dialogs.error(parent.get(), title, e));
    }

    private void showInfo(String message) {
        SwingUtilities.invokeLater(() -> Dialogs.info(parent.get(), I18n.t("edit.info.title"), message));
    }

    private void fireChanged() {
        listeners.forEach(Runnable::run);
    }

    @Override
    public void close() {
        watcher.close();
        List.copyOf(viewDirs.keySet()).forEach(EditManager::deleteViewCopy);
        for (var item : entries.entrySet()) {
            Entry e = item.getValue();
            if (!entries.remove(item.getKey(), e)) {
                continue; // sedang/sudah ditutup lewat close(entry)
            }
            try {
                e.session.close();
                links.release(e.profile);
            } catch (RuntimeException ex) {
                log.warn("Menutup sesi edit gagal", ex);
            }
        }
    }
}

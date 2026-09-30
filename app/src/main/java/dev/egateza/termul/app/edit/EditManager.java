package dev.egateza.termul.app.edit;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.Edt;
import dev.egateza.termul.app.sftp.ActivityBar.Level;
import dev.egateza.termul.core.config.EditorConfig;
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

        Entry(HostProfile profile, RemoteEditSession session, SftpConnection link) {
            this.profile = profile;
            this.session = session;
            this.link = link;
        }

        public HostProfile profile() {
            return profile;
        }

        public RemoteEditSession session() {
            return session;
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
    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();
    private final Map<Path, Entry> byLocal = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    public EditManager(SftpLinks links, EditCache cache, Supplier<EditorConfig> editorConfig,
                       ExecutorService sshOps, Supplier<Component> parent) throws IOException {
        this.links = links;
        this.cache = cache;
        this.editorConfig = editorConfig;
        this.launcher = new EditorLauncher(editorConfig);
        this.sshOps = sshOps;
        this.parent = parent;
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
        var key = new Key(profile.id(), remotePath);
        sshOps.execute(() -> {
            try {
                Entry entry = entries.get(key);
                if (entry == null) {
                    activity(profile, Level.INFO, I18n.t("edit.activity.downloading", remotePath));
                    SftpConnection link = links.acquire(profile);
                    RemoteEditSession session;
                    try {
                        session = RemoteEditSession.open(link.ensureLive(), cache, profile.id(), remotePath,
                                RemoteEditSession.SFTP_UPLOADER);
                    } catch (Exception e) {
                        links.release(profile);
                        throw e;
                    }
                    entry = new Entry(profile, session, link);
                    session.addListener(s -> {
                        fireChanged();
                        publishState(profile, s);
                    });
                    entries.put(key, entry);
                    byLocal.put(session.localFile().toAbsolutePath().normalize(), entry);
                    watcher.watch(session.localFile());
                    fireChanged();
                }
                entry.template = template;
                launchEditor(entry);
            } catch (Exception e) {
                activity(profile, Level.ERROR, I18n.t("edit.activity.openFailed", remotePath, e.getMessage()));
                showError(I18n.t("edit.error.openFailed", remotePath), e);
            }
        });
    }

    /** Menjalankan editor untuk entry (juga dipakai "Buka lagi" dari EditTracker). */
    public void reopenEditor(Entry entry) {
        sshOps.execute(() -> {
            try {
                launchEditor(entry);
            } catch (IOException e) {
                showError(I18n.t("edit.error.editorLaunch"), e);
            }
        });
    }

    private void launchEditor(Entry entry) throws IOException {
        Process p = launcher.launch(entry.session.localFile(), entry.template);
        entry.editor = p;
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
    private void publishState(HostProfile profile, RemoteEditSession s) {
        String path = s.remotePath();
        switch (s.state()) {
            case UPLOADING -> activity(profile, Level.INFO, I18n.t("edit.activity.uploading", path));
            case EDITING -> {
                if ("Tersinkron".equals(s.lastMessage())) {
                    activity(profile, Level.SUCCESS, I18n.t("edit.activity.uploaded", path));
                } else if ("Dibuka".equals(s.lastMessage())) {
                    activity(profile, Level.INFO, I18n.t("edit.activity.opened", path));
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
            showError(I18n.t("edit.error.uploadFailed", session.remotePath()), e);
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

    /** Tutup sesi edit (cache dihapus kalau tersinkron). */
    public void close(Entry entry) {
        var session = entry.session;
        watcher.unwatch(session.localFile());
        boolean removed = session.close();
        entries.remove(new Key(entry.profile.id(), session.remotePath()));
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

    private int ask(String title, String message, Object[] choices) {
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
        for (Entry e : entries.values()) {
            try {
                e.session.close();
                links.release(e.profile);
            } catch (RuntimeException ex) {
                log.warn("Menutup sesi edit gagal", ex);
            }
        }
        entries.clear();
    }
}

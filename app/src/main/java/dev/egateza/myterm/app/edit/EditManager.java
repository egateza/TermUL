package dev.egateza.myterm.app.edit;

import dev.egateza.myterm.app.ui.Dialogs;
import dev.egateza.myterm.app.ui.Edt;
import dev.egateza.myterm.core.config.EditorConfig;
import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.sftp.RemoteFileException;
import dev.egateza.myterm.sftp.RemoteFileService;
import dev.egateza.myterm.sftp.edit.EditCache;
import dev.egateza.myterm.sftp.edit.EditWatcher;
import dev.egateza.myterm.sftp.edit.RemoteEditSession;
import dev.egateza.myterm.sftp.edit.RemoteEditSession.LineEndingPolicy;
import dev.egateza.myterm.sftp.edit.RemoteEditSession.SyncOptions;
import dev.egateza.myterm.sftp.edit.RemoteEditSession.SyncResult;
import dev.egateza.myterm.ssh.SessionManager;
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

        Entry(HostProfile profile, RemoteEditSession session) {
            this.profile = profile;
            this.session = session;
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

    private final SessionManager sessions;
    private final EditCache cache;
    private final EditorLauncher launcher;
    private final Supplier<EditorConfig> editorConfig;
    private final ExecutorService sshOps;
    private final Supplier<Component> parent;
    private final EditWatcher watcher;
    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();
    private final Map<Path, Entry> byLocal = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    public EditManager(SessionManager sessions, EditCache cache, Supplier<EditorConfig> editorConfig,
                       ExecutorService sshOps, Supplier<Component> parent) throws IOException {
        this.sessions = sessions;
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

    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    /** Buka file remote di editor lokal (atau fokuskan lagi kalau sudah dibuka). */
    public void open(HostProfile profile, String remotePath) {
        var key = new Key(profile.id(), remotePath);
        sshOps.execute(() -> {
            try {
                Entry entry = entries.get(key);
                if (entry == null) {
                    RemoteFileService files = RemoteFileService.open(sessions.acquire(profile));
                    RemoteEditSession session;
                    try {
                        session = RemoteEditSession.open(files, cache, profile.id(), remotePath,
                                RemoteEditSession.SFTP_UPLOADER);
                    } catch (RemoteFileException e) {
                        files.close();
                        throw e;
                    }
                    entry = new Entry(profile, session);
                    session.addListener(s -> fireChanged());
                    entries.put(key, entry);
                    byLocal.put(session.localFile().toAbsolutePath().normalize(), entry);
                    watcher.watch(session.localFile());
                    fireChanged();
                }
                launchEditor(entry);
            } catch (Exception e) {
                showError("Gagal membuka " + remotePath, e);
            }
        });
    }

    /** Menjalankan editor untuk entry (juga dipakai "Buka lagi" dari EditTracker). */
    public void reopenEditor(Entry entry) {
        sshOps.execute(() -> {
            try {
                launchEditor(entry);
            } catch (IOException e) {
                showError("Editor gagal dijalankan", e);
            }
        });
    }

    private void launchEditor(Entry entry) throws IOException {
        Process p = launcher.launch(entry.session.localFile());
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

    /** Upload manual / retry dari EditTracker (dengan reconnect kalau koneksi SFTP putus). */
    public void retry(Entry entry) {
        sshOps.execute(() -> {
            try {
                if (!entry.session.files().isOpen()) {
                    entry.session.rebind(RemoteFileService.open(sessions.acquire(entry.profile)));
                }
            } catch (Exception e) {
                showError("Reconnect SFTP gagal", e);
                return;
            }
            syncInteractive(entry, SyncOptions.DEFAULT);
        });
    }

    /** Sync + dialog untuk konflik/CRLF. Berjalan di sshOps (dialog via invokeAndWait). */
    private void syncInteractive(Entry entry, SyncOptions options) {
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
            log.warn("Upload {} gagal: {}", session.remotePath(), e.getMessage());
            showError("Upload " + session.remotePath() + " gagal (perubahan tetap tersimpan lokal, "
                    + "coba lagi dari daftar file yang diedit)", e);
        }
    }

    private void handleConflict(Entry entry, SyncOptions options) {
        var session = entry.session;
        while (true) {
            Object[] choices = {"Timpa file server", "Lihat diff", "Batal"};
            int choice = ask("Konflik: " + session.remotePath(),
                    "File di server sudah berubah sejak Anda membukanya.\n"
                            + "Menimpa akan menghapus perubahan orang lain di server.", choices);
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
        Object[] choices = {"Konversi ke LF & upload", "Upload apa adanya (CRLF)", "Batal"};
        int choice = ask("Line ending berubah: " + entry.session.remotePath(),
                "Editor menyimpan file dengan line ending Windows (CRLF), sedangkan file asli memakai LF.\n"
                        + "CRLF bisa merusak script shell/config di Linux.", choices);
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
            showError("Diff gagal", e);
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
        session.files().close();
        if (!removed) {
            showInfo("Perubahan " + session.remotePath() + " belum ter-upload dan disimpan di:\n" + session.localFile());
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
        SwingUtilities.invokeLater(() -> Dialogs.info(parent.get(), "Edit remote", message));
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
                e.session.files().close();
            } catch (RuntimeException ex) {
                log.warn("Menutup sesi edit gagal", ex);
            }
        }
        entries.clear();
    }
}

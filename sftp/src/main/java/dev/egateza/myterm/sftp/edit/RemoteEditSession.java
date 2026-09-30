package dev.egateza.myterm.sftp.edit;

import dev.egateza.myterm.sftp.RemoteEntry;
import dev.egateza.myterm.sftp.RemoteFileException;
import dev.egateza.myterm.sftp.RemoteFileService;
import dev.egateza.myterm.sftp.TransferListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Satu file remote yang sedang diedit dengan editor lokal.
 *
 * <pre>
 * OPENING ─download+stat─► EDITING ─sync()─► UPLOADING ─ok─► EDITING
 *                             │                  │ conflict/CRLF/error
 *                             │                  ▼
 *                             │            NEEDS_ATTENTION ─sync(opsi)─► ...
 *                             └─ close() ─► CLOSED (cache dihapus kalau sudah tersinkron)
 * </pre>
 *
 * <p>Thread-safe: {@link #sync} dan {@link #close} di-synchronize (satu upload pada satu waktu).
 * Semua method blocking (network/disk): jangan dipanggil di EDT.
 */
public final class RemoteEditSession {

    private static final Logger log = LoggerFactory.getLogger(RemoteEditSession.class);

    /** Batas ukuran file yang boleh diedit (editor lokal & upload berulang). */
    public static final long MAX_SIZE = 20L * 1024 * 1024;

    public enum State { OPENING, EDITING, UPLOADING, NEEDS_ATTENTION, CLOSED }

    /** Cara menangani perubahan line ending LF → CRLF oleh editor. */
    public enum LineEndingPolicy { ASK, CONVERT_TO_LF, KEEP }

    /** Opsi satu kali sync. */
    public record SyncOptions(boolean overwriteConflict, LineEndingPolicy lineEndings) {
        public static final SyncOptions DEFAULT = new SyncOptions(false, LineEndingPolicy.ASK);
    }

    /** Hasil sync. */
    public sealed interface SyncResult {
        record Unchanged() implements SyncResult {
        }

        record Uploaded(Instant at) implements SyncResult {
        }

        /** File remote berubah sejak baseline; upload ditahan. */
        record Conflict(RemoteEntry remote) implements SyncResult {
        }

        /** Editor mengubah LF menjadi CRLF; upload ditahan sampai user memilih. */
        record LineEndingChanged(LineEndings.Style original, LineEndings.Style now) implements SyncResult {
        }
    }

    /** Strategi upload (default: SFTP atomic; Fase 5: sudo). */
    @FunctionalInterface
    public interface Uploader {
        void upload(Path local, String remotePath) throws RemoteFileException;
    }

    private record Baseline(long size, Instant modified) {
        static Baseline of(RemoteEntry e) {
            return new Baseline(e.size(), e.modified());
        }

        boolean matches(RemoteEntry e) {
            return e.size() == size && e.modified().equals(modified);
        }
    }

    private final RemoteFileService files;
    private final String remotePath;
    private final Path localFile;
    private final EditCache cache;
    private final Uploader uploader;
    private final LineEndings.Style originalEnding;
    private final CopyOnWriteArrayList<Consumer<RemoteEditSession>> listeners = new CopyOnWriteArrayList<>();
    private Baseline baseline;                 // guarded by this
    private byte[] syncedHash;                 // guarded by this
    private volatile State state = State.OPENING;
    private volatile String lastMessage = "";
    private volatile Instant lastUpload;

    private RemoteEditSession(RemoteFileService files, String remotePath, Path localFile, EditCache cache,
                              Uploader uploader, Baseline baseline, byte[] syncedHash, LineEndings.Style ending) {
        this.files = files;
        this.remotePath = remotePath;
        this.localFile = localFile;
        this.cache = cache;
        this.uploader = uploader;
        this.baseline = baseline;
        this.syncedHash = syncedHash;
        this.originalEnding = ending;
    }

    /** Download file ke cache dan catat baseline. */
    public static RemoteEditSession open(RemoteFileService files, EditCache cache, java.util.UUID profileId,
                                         String remotePath, Uploader uploader) throws RemoteFileException {
        Objects.requireNonNull(uploader);
        RemoteEntry entry = files.stat(remotePath);
        if (entry.type() != RemoteEntry.Type.FILE) {
            throw new RemoteFileException("Bukan file biasa: " + remotePath);
        }
        if (entry.size() > MAX_SIZE) {
            throw new RemoteFileException("File terlalu besar untuk diedit (" + entry.size() / (1024 * 1024) + " MB): " + remotePath);
        }
        Path local = cache.pathFor(profileId, remotePath);
        try {
            cache.prepare(local);
            files.download(remotePath, local, TransferListener.NONE);
            byte[] content = Files.readAllBytes(local);
            var session = new RemoteEditSession(files, remotePath, local, cache, uploader, Baseline.of(entry),
                    sha256(content), LineEndings.detect(content));
            session.setState(State.EDITING, "Dibuka");
            log.info("Edit dibuka: {} → {}", remotePath, local);
            return session;
        } catch (IOException e) {
            throw e instanceof RemoteFileException rfe ? rfe
                    : new RemoteFileException("Gagal menyiapkan cache edit: " + e.getMessage(), e);
        }
    }

    /** Upload default: SFTP atomic, mode file dipertahankan. */
    public static Uploader sftpUploader(RemoteFileService files) {
        return (local, remote) -> files.upload(local, remote, null, TransferListener.NONE);
    }

    public String remotePath() {
        return remotePath;
    }

    public Path localFile() {
        return localFile;
    }

    public State state() {
        return state;
    }

    public String lastMessage() {
        return lastMessage;
    }

    public Instant lastUpload() {
        return lastUpload;
    }

    public void addListener(Consumer<RemoteEditSession> l) {
        listeners.add(l);
    }

    /**
     * Upload kalau konten lokal berubah sejak sync terakhir.
     * Konflik dan perubahan line ending menahan upload (state NEEDS_ATTENTION) sampai dipanggil lagi dengan opsi.
     */
    public synchronized SyncResult sync(SyncOptions options) throws RemoteFileException {
        if (state == State.CLOSED) {
            return new SyncResult.Unchanged();
        }
        byte[] content;
        try {
            content = Files.readAllBytes(localFile);
        } catch (IOException e) {
            setState(State.NEEDS_ATTENTION, "File lokal tidak bisa dibaca: " + e.getMessage());
            throw new RemoteFileException("File lokal tidak bisa dibaca: " + localFile, e);
        }
        if (Arrays.equals(sha256(content), syncedHash)) {
            if (state == State.NEEDS_ATTENTION) {
                setState(State.EDITING, "Tidak ada perubahan");
            }
            return new SyncResult.Unchanged();
        }

        LineEndings.Style now = LineEndings.detect(content);
        if (LineEndings.crlfIntroduced(originalEnding, now)) {
            switch (options.lineEndings()) {
                case ASK -> {
                    setState(State.NEEDS_ATTENTION, "Editor mengubah line ending menjadi CRLF");
                    return new SyncResult.LineEndingChanged(originalEnding, now);
                }
                case CONVERT_TO_LF -> {
                    content = LineEndings.toLf(content);
                    try {
                        Files.write(localFile, content);
                    } catch (IOException e) {
                        throw new RemoteFileException("Gagal menulis file lokal: " + e.getMessage(), e);
                    }
                }
                case KEEP -> {
                    // upload apa adanya
                }
            }
        }

        setState(State.UPLOADING, "Mengupload...");
        try {
            RemoteEntry current = files.stat(remotePath);
            if (!baseline.matches(current) && !options.overwriteConflict()) {
                setState(State.NEEDS_ATTENTION, "Konflik: file di server berubah");
                return new SyncResult.Conflict(current);
            }
            uploader.upload(localFile, remotePath);
            baseline = Baseline.of(files.stat(remotePath));
            syncedHash = sha256(content);
            lastUpload = Instant.now();
            setState(State.EDITING, "Tersinkron");
            log.info("Edit diupload: {}", remotePath);
            return new SyncResult.Uploaded(lastUpload);
        } catch (RemoteFileException e) {
            setState(State.NEEDS_ATTENTION, e.getMessage());
            throw e;
        }
    }

    /** true kalau file lokal belum ter-upload. */
    public synchronized boolean hasPendingChanges() {
        try {
            return !Arrays.equals(sha256(Files.readAllBytes(localFile)), syncedHash);
        } catch (IOException e) {
            return false;
        }
    }

    /** Download versi remote saat ini ke {@code target} (untuk diff). */
    public void downloadRemoteCopy(Path target) throws RemoteFileException {
        files.download(remotePath, target, TransferListener.NONE);
    }

    /**
     * Menutup sesi. Cache dihapus hanya kalau tidak ada perubahan yang belum ter-upload
     * (N4: edit pending tidak boleh hilang).
     *
     * @return true kalau cache dihapus
     */
    public synchronized boolean close() {
        if (state == State.CLOSED) {
            return true;
        }
        boolean pending = hasPendingChanges();
        setState(State.CLOSED, pending ? "Ditutup; perubahan belum ter-upload disimpan di " + localFile : "Ditutup");
        if (pending) {
            log.warn("Edit {} ditutup dengan perubahan belum ter-upload; cache dipertahankan di {}", remotePath, localFile);
            return false;
        }
        try {
            cache.remove(localFile);
            return true;
        } catch (IOException e) {
            log.warn("Cache {} tidak bisa dihapus: {}", localFile, e.toString());
            return false;
        }
    }

    private void setState(State s, String message) {
        state = s;
        lastMessage = message;
        for (var l : listeners) {
            try {
                l.accept(this);
            } catch (RuntimeException e) {
                log.warn("Listener edit gagal", e);
            }
        }
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

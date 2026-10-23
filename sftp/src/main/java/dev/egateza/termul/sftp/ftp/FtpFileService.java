package dev.egateza.termul.sftp.ftp;

import dev.egateza.termul.sftp.RemoteEntry;
import dev.egateza.termul.sftp.RemoteFileException;
import dev.egateza.termul.sftp.RemoteFileService;
import dev.egateza.termul.sftp.RemotePaths;
import dev.egateza.termul.sftp.TransferListener;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileFilters;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RemoteFileService} lewat FTP/FTPS (Apache Commons Net).
 *
 * <p>Satu koneksi FTP hanya bisa menjalankan satu perintah/transfer pada satu waktu, jadi:
 * <ul>
 *   <li>operasi metadata (list, stat, rename, ...) lewat <b>koneksi kontrol</b>, bergantian ({@code lock});</li>
 *   <li>setiap download/upload memakai <b>koneksi transfer</b> sendiri (login ulang, disimpan sebentar untuk dipakai
 *       lagi), sehingga pindah folder tetap lancar selama transfer berjalan.</li>
 * </ul>
 * Koneksi kontrol yang mati (mis. idle timeout server) dibuka lagi otomatis pada operasi berikutnya.
 *
 * <p>Keterbatasan dibanding SFTP: tidak ada pemeriksaan izin/owner sebelum upload ({@link #canWrite} selalu true), mode
 * file hanya dipertahankan kalau server mendukung {@code SITE CHMOD}, dan rename yang menimpa file bergantung pada
 * server (kalau ditolak: hapus lalu rename, tidak atomic). Semua method blocking: jangan di EDT.
 */
public final class FtpFileService implements RemoteFileService {

    private static final Logger log = LoggerFactory.getLogger(FtpFileService.class);
    private static final int BUFFER = 32 * 1024;
    private static final int MAX_IDLE_TRANSFER = 2;
    private static final int MAX_LINK_HOPS = 40;
    /** Koneksi yang menganggur lebih lama dari ini dicek dulu ({@code NOOP}) sebelum dipakai. */
    private static final Duration PROBE_AFTER = Duration.ofSeconds(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Membuka satu koneksi baru yang sudah login. */
    @FunctionalInterface
    interface ClientFactory {
        FTPClient open() throws IOException;
    }

    private final String label;
    private final ClientFactory factory;
    private final Runnable onClose;
    private final ReentrantLock lock = new ReentrantLock();
    private FTPClient control;        // guarded by lock; null = perlu dibuka lagi
    private Instant controlUsed;      // guarded by lock
    private final String home;
    private volatile boolean mlst;    // server mendukung MLST/MLSD (waktu presisi detik, fakta terstruktur)
    private final Deque<FTPClient> idle = new ArrayDeque<>(); // guarded by idle
    private volatile boolean closed;  // ditulis di bawah idle

    FtpFileService(String label, ClientFactory factory, FTPClient control, Runnable onClose) throws RemoteFileException {
        this.label = label;
        this.factory = factory;
        this.onClose = onClose;
        this.control = control;
        this.controlUsed = Instant.now();
        try {
            String pwd = control.printWorkingDirectory();
            this.home = pwd == null || !pwd.startsWith("/") ? "/" : pwd;
            this.mlst = control.hasFeature("MLST");
        } catch (IOException e) {
            disconnectQuietly(control);
            onClose.run();
            throw new RemoteFileException("Gagal membaca direktori awal FTP: " + e.getMessage(), e);
        }
        log.info("FTP {} tersambung (home {}, MLST {})", label, home, mlst);
    }

    /** Untuk test: pakai jalur LIST + MDTM seperti server tanpa MLST (mis. vsftpd). */
    void useListOnly() {
        mlst = false;
    }

    // --- koneksi ---

    @FunctionalInterface
    private interface Op<T> {
        T run(FTPClient c) throws IOException;
    }

    /** Menjalankan operasi di koneksi kontrol (bergantian). IOException = koneksi kontrol dianggap mati. */
    private <T> T onControl(String action, String path, Op<T> op) throws RemoteFileException {
        lock.lock();
        try {
            FTPClient c = liveControl();
            try {
                T result = op.run(c);
                controlUsed = Instant.now();
                return result;
            } catch (RemoteFileException e) {
                controlUsed = Instant.now();
                throw e;
            } catch (IOException e) {
                disconnectQuietly(c);
                control = null; // dibuka lagi pada operasi berikutnya
                throw new RemoteFileException(action + ": " + path + " (" + e.getMessage() + ")", e);
            }
        } finally {
            lock.unlock();
        }
    }

    private FTPClient liveControl() throws RemoteFileException {
        if (closed) {
            throw new RemoteFileException("Koneksi FTP sudah ditutup.");
        }
        FTPClient c = control;
        if (c != null && c.isConnected()
                && (Duration.between(controlUsed, Instant.now()).compareTo(PROBE_AFTER) < 0 || noop(c))) {
            return c;
        }
        disconnectQuietly(c);
        control = null;
        try {
            control = factory.open();
            controlUsed = Instant.now();
            log.info("Koneksi FTP {} dibuka lagi", label);
            return control;
        } catch (IOException e) {
            throw new RemoteFileException("Koneksi FTP " + label + " terputus dan gagal dibuka lagi: " + e.getMessage(), e);
        }
    }

    private static boolean noop(FTPClient c) {
        try {
            return FTPReply.isPositiveCompletion(c.noop());
        } catch (IOException e) {
            return false;
        }
    }

    /** Koneksi transfer: yang menganggur dipakai lagi kalau masih hidup, kalau tidak dibuka baru. */
    private FTPClient borrow() throws IOException {
        while (true) {
            FTPClient c;
            synchronized (idle) {
                if (closed) {
                    throw new RemoteFileException("Koneksi FTP sudah ditutup.");
                }
                c = idle.pollFirst();
            }
            if (c == null) {
                return factory.open();
            }
            if (c.isConnected() && noop(c)) {
                return c;
            }
            disconnectQuietly(c);
        }
    }

    private void giveBack(FTPClient c, boolean healthy) {
        if (healthy && c.isConnected()) {
            synchronized (idle) {
                if (!closed && idle.size() < MAX_IDLE_TRANSFER) {
                    idle.addFirst(c);
                    return;
                }
            }
        }
        disconnectQuietly(c);
    }

    @Override
    public boolean isOpen() {
        return !closed; // koneksi kontrol yang putus dibuka lagi otomatis
    }

    // --- metadata ---

    @Override
    public boolean canWrite(String path) {
        return true; // FTP tidak bisa memeriksa izin tanpa menulis; upload melaporkan errornya
    }

    @Override
    public String home() {
        return home;
    }

    @Override
    public String canonicalize(String path) throws RemoteFileException {
        String abs = normalize(path.startsWith("/") ? path : RemotePaths.join(home, path));
        return onControl("Path tidak valid", abs, c -> {
            if (c.changeWorkingDirectory(abs)) {
                String pwd = c.printWorkingDirectory();
                return pwd != null && pwd.startsWith("/") ? pwd : abs;
            }
            if (lstat(c, abs) != null) {
                return abs;
            }
            throw new RemoteFileException("Path tidak valid: " + abs + " (tidak ditemukan)");
        });
    }

    @Override
    public List<RemoteEntry> list(String dir) throws RemoteFileException {
        return onControl("Gagal membaca direktori", dir, c -> {
            FTPFile[] files = mlst ? c.mlistDir(dir, FTPFileFilters.NON_NULL) : c.listFiles(dir, FTPFileFilters.NON_NULL);
            if (!FTPReply.isPositiveCompletion(c.getReplyCode())) {
                throw replyError("Gagal membaca direktori", dir, c);
            }
            var result = new ArrayList<RemoteEntry>();
            for (FTPFile f : files) {
                String name = baseName(f.getName());
                if (name.isEmpty() || name.equals(".") || name.equals("..")) {
                    continue;
                }
                result.add(toEntry(RemotePaths.join(dir, name), name, f));
            }
            result.sort(RemoteEntry.DIRECTORIES_FIRST);
            return result;
        });
    }

    @Override
    public String readSmallText(String path, int maxBytes) {
        try (InputStream in = openRead(path)) {
            return new String(in.readNBytes(maxBytes), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            log.debug("Membaca {} gagal: {}", path, e.toString());
            return null;
        }
    }

    @Override
    public RemoteEntry stat(String path) throws RemoteFileException {
        RemoteEntry e = onControl("Gagal membaca info file", path, c -> stat(c, path));
        if (e == null) {
            throw new RemoteFileException("Gagal membaca info file: " + path + " (tidak ditemukan)");
        }
        return e;
    }

    @Override
    public boolean exists(String path) throws RemoteFileException {
        return onControl("Gagal membaca info file", path, c -> stat(c, path)) != null;
    }

    @Override
    public void mkdir(String path) throws RemoteFileException {
        onControl("Gagal membuat direktori", path, c -> {
            if (!c.makeDirectory(path)) {
                throw replyError("Gagal membuat direktori", path, c);
            }
            return null;
        });
    }

    @Override
    public void rename(String from, String to) throws RemoteFileException {
        onControl("Gagal rename", from, c -> {
            if (stat(c, to) != null) {
                throw new RemoteFileException("Tujuan sudah ada: " + to);
            }
            if (!c.rename(from, to)) {
                throw replyError("Gagal rename", from, c);
            }
            return null;
        });
    }

    @Override
    public void delete(String path, boolean recursive) throws RemoteFileException {
        onControl("Gagal menghapus", path, c -> {
            RemoteEntry e = lstat(c, path);
            if (e == null) {
                throw new RemoteFileException("Gagal menghapus: " + path + " (tidak ditemukan)");
            }
            delete(c, e, recursive);
            return null;
        });
    }

    private void delete(FTPClient c, RemoteEntry e, boolean recursive) throws IOException {
        if (e.type() == RemoteEntry.Type.DIRECTORY) {
            if (recursive) {
                FTPFile[] children = c.listFiles(e.path(), FTPFileFilters.NON_NULL);
                for (FTPFile f : children) {
                    String name = baseName(f.getName());
                    if (!name.isEmpty() && !name.equals(".") && !name.equals("..")) {
                        delete(c, toEntry(RemotePaths.join(e.path(), name), name, f), true);
                    }
                }
            }
            if (!c.removeDirectory(e.path())) {
                throw replyError("Gagal menghapus", e.path(), c);
            }
        } else if (!c.deleteFile(e.path())) { // file atau symlink (link-nya saja)
            throw replyError("Gagal menghapus", e.path(), c);
        }
    }

    @Override
    public void chmod(String path, int mode) throws RemoteFileException {
        onControl("Gagal chmod", path, c -> {
            if (!siteChmod(c, path, mode)) {
                throw replyError("Gagal chmod (server mungkin tidak mendukung SITE CHMOD)", path, c);
            }
            return null;
        });
    }

    private static boolean siteChmod(FTPClient c, String path, int mode) throws IOException {
        return c.sendSiteCommand("CHMOD " + Integer.toOctalString(mode & 07777) + " " + path);
    }

    // --- transfer ---

    @Override
    public void download(String remote, Path local, TransferListener listener) throws RemoteFileException {
        Objects.requireNonNull(listener);
        long total = stat(remote).size();
        Path dir = local.toAbsolutePath().getParent();
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            tmp = Files.createTempFile(dir, "." + local.getFileName(), ".part");
            try (InputStream in = openRead(remote); OutputStream out = Files.newOutputStream(tmp)) {
                copy(in, out, total, listener);
            }
            Files.move(tmp, local, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            tmp = null;
        } catch (RemoteFileException e) {
            throw e;
        } catch (IOException e) {
            throw new RemoteFileException("Gagal download: " + remote + " (" + e.getMessage() + ")", e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException e) {
                    log.warn("File temp {} tidak bisa dihapus", tmp);
                }
            }
        }
    }

    @Override
    public InputStream openRead(String remote) throws IOException {
        FTPClient c = borrow();
        InputStream in;
        try {
            in = c.retrieveFileStream(remote);
        } catch (IOException e) {
            disconnectQuietly(c);
            throw e;
        }
        if (in == null) {
            var error = replyError("Gagal membaca file", remote, c);
            giveBack(c, true);
            throw error;
        }
        return new FilterInputStream(in) {
            private boolean done;

            @Override
            public void close() throws IOException {
                if (done) {
                    return;
                }
                done = true;
                boolean ok = false;
                try {
                    super.close();
                    ok = c.completePendingCommand(); // dibaca sebagian: server membalas 426, koneksi dibuang
                } catch (IOException e) {
                    log.debug("Menutup download FTP {}: {}", remote, e.toString());
                } finally {
                    giveBack(c, ok);
                }
            }
        };
    }

    /** Stream tulis ke {@code remote} (STOR); close gagal kalau server tidak mengonfirmasi file tersimpan. */
    private OutputStream openWrite(String remote) throws IOException {
        FTPClient c = borrow();
        OutputStream out;
        try {
            out = c.storeFileStream(remote);
        } catch (IOException e) {
            disconnectQuietly(c);
            throw e;
        }
        if (out == null) {
            var error = replyError("Gagal upload", remote, c);
            giveBack(c, true);
            throw error;
        }
        return new FilterOutputStream(out) {
            private boolean done;

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len); // FilterOutputStream.write(byte[]) menulis per byte
            }

            @Override
            public void close() throws IOException {
                if (done) {
                    return;
                }
                done = true;
                boolean ok = false;
                try {
                    super.close();
                    ok = c.completePendingCommand();
                    if (!ok) {
                        throw replyError("Upload tidak dikonfirmasi server", remote, c);
                    }
                } finally {
                    giveBack(c, ok);
                }
            }
        };
    }

    @Override
    public void upload(Path local, String remoteOrLink, Integer newFileMode, TransferListener listener)
            throws RemoteFileException {
        long total;
        try {
            total = Files.size(local);
        } catch (IOException e) {
            throw new RemoteFileException("Gagal upload: " + remoteOrLink + " (" + e.getMessage() + ")", e);
        }
        write(() -> Files.newInputStream(local), total, remoteOrLink, newFileMode, listener);
    }

    @Override
    public void copyFrom(RemoteFileService source, String sourcePath, String targetOrLink, TransferListener listener)
            throws RemoteFileException {
        RemoteEntry from = source.stat(sourcePath);
        if (from.type() != RemoteEntry.Type.FILE) {
            throw new RemoteFileException("Bukan file biasa, tidak bisa disalin: " + sourcePath);
        }
        write(() -> source.openRead(sourcePath), from.size(), targetOrLink, from.mode() == 0 ? null : from.mode(),
                listener);
    }

    @FunctionalInterface
    private interface Source {
        InputStream open() throws IOException;
    }

    /**
     * Tulis ke {@code <dir>/.<name>.termul-<rand>.tmp}, set mode (kalau server mendukung), lalu rename ke tujuan.
     * Kalau tujuan symlink, yang diganti file targetnya.
     */
    private void write(Source source, long total, String remoteOrLink, Integer newFileMode, TransferListener listener)
            throws RemoteFileException {
        Objects.requireNonNull(listener);
        String remote = onControl("Gagal membaca symlink", remoteOrLink, c -> resolveLinks(c, remoteOrLink));
        String tmp = RemotePaths.join(RemotePaths.parent(remote),
                "." + RemotePaths.name(remote) + ".termul-" + randomSuffix() + ".tmp");
        RemoteEntry existing = onControl("Gagal membaca info file", remote, c -> {
            if (stat(c, tmp) != null) {
                throw new RemoteFileException("File sementara sudah ada: " + tmp);
            }
            return stat(c, remote);
        });
        if (existing != null && existing.type() == RemoteEntry.Type.DIRECTORY) {
            throw new RemoteFileException("Tujuan adalah direktori: " + remote);
        }
        Integer mode = existing != null ? (existing.mode() == 0 ? null : Integer.valueOf(existing.mode())) : newFileMode;
        boolean tmpCreated = false;
        try {
            try (OutputStream out = openWrite(tmp)) {
                tmpCreated = true;
                try (InputStream in = source.open()) {
                    copy(in, out, total, listener);
                }
            }
            onControl("Gagal upload", remote, c -> {
                if (mode != null && !siteChmod(c, tmp, mode)) {
                    log.debug("SITE CHMOD {} ditolak server: {}", tmp, FtpConnector.reply(c));
                }
                replace(c, tmp, remote);
                return null;
            });
            tmpCreated = false;
        } catch (RemoteFileException e) {
            throw e;
        } catch (IOException e) {
            throw new RemoteFileException("Gagal upload: " + remote + " (" + e.getMessage() + ")", e);
        } finally {
            if (tmpCreated) {
                try {
                    onControl("Gagal menghapus", tmp, c -> c.deleteFile(tmp));
                } catch (RemoteFileException e) {
                    log.warn("File temp remote {} tidak bisa dihapus: {}", tmp, e.getMessage());
                }
            }
        }
    }

    /** Rename temp ke tujuan; server yang menolak menimpa: hapus tujuan lalu rename (tidak atomic). */
    private void replace(FTPClient c, String tmp, String target) throws IOException {
        if (c.rename(tmp, target)) {
            return;
        }
        if (stat(c, target) == null) {
            throw replyError("Gagal upload", target, c);
        }
        log.debug("Rename menimpa ditolak server ({}), fallback hapus + rename", FtpConnector.reply(c));
        if (!c.deleteFile(target) || !c.rename(tmp, target)) {
            throw replyError("Gagal upload", target, c);
        }
    }

    private static void copy(InputStream in, OutputStream out, long total, TransferListener listener)
            throws IOException {
        byte[] buf = new byte[BUFFER];
        long done = 0;
        listener.progress(0, total);
        int n;
        while ((n = in.read(buf)) >= 0) {
            if (listener.isCancelled()) {
                throw new RemoteFileException.Cancelled();
            }
            out.write(buf, 0, n);
            done += n;
            listener.progress(done, total);
        }
    }

    // --- stat / listing ---

    /** Stat yang mengikuti symlink; null = tidak ada. */
    private RemoteEntry stat(FTPClient c, String path) throws IOException {
        RemoteEntry e = lstat(c, path);
        if (e == null || e.type() != RemoteEntry.Type.SYMLINK) {
            return e;
        }
        // jenis target symlink: direktori kalau bisa dimasuki
        if (c.changeWorkingDirectory(path)) {
            return new RemoteEntry(e.name(), e.path(), RemoteEntry.Type.DIRECTORY, 0, e.modified(), e.mode(),
                    e.owner(), e.group());
        }
        long size = parseSize(c.getSize(path), e.size());
        Instant modified = mdtm(c, path, e.modified());
        return new RemoteEntry(e.name(), e.path(), RemoteEntry.Type.FILE, size, modified, e.mode(), e.owner(), e.group());
    }

    /** Stat tanpa mengikuti symlink; null = tidak ada. */
    private RemoteEntry lstat(FTPClient c, String path) throws IOException {
        String abs = normalize(path);
        if (abs.equals("/")) {
            return new RemoteEntry("/", "/", RemoteEntry.Type.DIRECTORY, 0, Instant.EPOCH, 0, "", "");
        }
        String name = RemotePaths.name(abs);
        if (mlst) {
            FTPFile f = c.mlistFile(abs);
            if (f != null && !f.isSymbolicLink()) {
                return toEntry(abs, name, f);
            }
            if (f == null && FTPReply.isNegativePermanent(c.getReplyCode())) {
                return null;
            }
        }
        FTPFile f = listEntry(c, abs);
        if (f == null) {
            // mis. direktori root chroot yang tidak tampil di listing parent-nya
            return c.changeWorkingDirectory(abs)
                    ? new RemoteEntry(name, abs, RemoteEntry.Type.DIRECTORY, 0, Instant.EPOCH, 0, "", "")
                    : null;
        }
        RemoteEntry e = toEntry(abs, name, f);
        if (e.type() == RemoteEntry.Type.FILE && !mlst) {
            // LIST hanya presisi menit (atau tanggal saja): MDTM untuk deteksi konflik remote edit
            e = new RemoteEntry(e.name(), e.path(), e.type(), e.size(), mdtm(c, abs, e.modified()), e.mode(),
                    e.owner(), e.group());
        }
        return e;
    }

    /** Entry {@code path} dari listing direktori parent-nya (LIST memuat target symlink). */
    private static FTPFile listEntry(FTPClient c, String path) throws IOException {
        String name = RemotePaths.name(path);
        FTPFile[] files = c.listFiles(RemotePaths.parent(path), FTPFileFilters.NON_NULL);
        for (FTPFile f : files) {
            if (name.equals(baseName(f.getName()))) {
                return f;
            }
        }
        return null;
    }

    /**
     * Mengikuti rantai symlink sampai path yang bukan symlink. Path yang belum ada dikembalikan apa adanya.
     */
    private String resolveLinks(FTPClient c, String path) throws IOException {
        String current = normalize(path);
        for (int hop = 0; hop < MAX_LINK_HOPS; hop++) {
            FTPFile f = current.equals("/") ? null : listEntry(c, current);
            if (f == null || !f.isSymbolicLink() || f.getLink() == null || f.getLink().isBlank()) {
                return hop == 0 ? path : current;
            }
            current = normalize(RemotePaths.join(RemotePaths.parent(current), f.getLink()));
        }
        throw new RemoteFileException("Terlalu banyak symlink bertingkat: " + path);
    }

    private static Instant mdtm(FTPClient c, String path, Instant fallback) {
        try {
            Instant t = c.mdtmInstant(path);
            return t != null ? t : fallback;
        } catch (IOException e) {
            return fallback;
        }
    }

    private static long parseSize(String reply, long fallback) {
        if (reply == null) {
            return fallback;
        }
        try {
            return Long.parseLong(reply.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static RemoteEntry toEntry(String path, String name, FTPFile f) {
        RemoteEntry.Type type;
        if (f.isSymbolicLink()) {
            type = RemoteEntry.Type.SYMLINK;
        } else if (f.isDirectory()) {
            type = RemoteEntry.Type.DIRECTORY;
        } else if (f.isFile()) {
            type = RemoteEntry.Type.FILE;
        } else {
            type = RemoteEntry.Type.OTHER;
        }
        Instant modified = f.getTimestampInstant() == null ? Instant.EPOCH : f.getTimestampInstant();
        return new RemoteEntry(name, path, type, Math.max(0, f.getSize()), modified, mode(f),
                f.getUser() == null ? "" : f.getUser(), f.getGroup() == null ? "" : f.getGroup());
    }

    /** Permission bits dari listing; 0 kalau server tidak memberi info permission. */
    static int mode(FTPFile f) {
        int mode = 0;
        int[] access = {FTPFile.USER_ACCESS, FTPFile.GROUP_ACCESS, FTPFile.WORLD_ACCESS};
        int[] perms = {FTPFile.READ_PERMISSION, FTPFile.WRITE_PERMISSION, FTPFile.EXECUTE_PERMISSION};
        for (int a = 0; a < access.length; a++) {
            for (int p = 0; p < perms.length; p++) {
                if (f.hasPermission(access[a], perms[p])) {
                    mode |= 1 << (8 - (a * 3 + p));
                }
            }
        }
        return mode;
    }

    /** Sebagian server mengembalikan path penuh di listing; ambil nama terakhirnya. */
    private static String baseName(String name) {
        if (name == null) {
            return "";
        }
        String n = name.endsWith("/") && name.length() > 1 ? name.substring(0, name.length() - 1) : name;
        int idx = n.lastIndexOf('/');
        return idx < 0 ? n : n.substring(idx + 1);
    }

    /** Membersihkan {@code .}, {@code ..}, dan {@code //} dari path absolut (leksikal). */
    static String normalize(String path) {
        var parts = new ArrayDeque<String>();
        for (String p : path.split("/")) {
            if (p.isEmpty() || p.equals(".")) {
                continue;
            }
            if (p.equals("..")) {
                parts.pollLast();
            } else {
                parts.addLast(p);
            }
        }
        return "/" + String.join("/", parts);
    }

    private static RemoteFileException replyError(String action, String path, FTPClient c) {
        return new RemoteFileException(action + ": " + path + " (" + FtpConnector.reply(c) + ")");
    }

    private static String randomSuffix() {
        byte[] b = new byte[6];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    static void disconnectQuietly(FTPClient c) {
        if (c == null || !c.isConnected()) {
            return;
        }
        try {
            c.disconnect();
        } catch (IOException e) {
            log.debug("Menutup koneksi FTP: {}", e.toString());
        }
    }

    private static void logoutQuietly(FTPClient c) {
        if (c == null || !c.isConnected()) {
            return;
        }
        try {
            c.logout();
        } catch (IOException e) {
            log.debug("Logout FTP: {}", e.toString());
        } finally {
            disconnectQuietly(c);
        }
    }

    @Override
    public void close() {
        List<FTPClient> spare;
        synchronized (idle) {
            if (closed) {
                return;
            }
            closed = true;
            spare = new ArrayList<>(idle);
            idle.clear();
        }
        spare.forEach(FtpFileService::logoutQuietly);
        lock.lock();
        try {
            logoutQuietly(control);
            control = null;
        } finally {
            lock.unlock();
            onClose.run();
        }
        log.info("FTP {} ditutup", label);
    }
}

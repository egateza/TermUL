package dev.egateza.termul.sftp;

import dev.egateza.termul.ssh.RemoteExec;
import dev.egateza.termul.ssh.ShellQuote;
import dev.egateza.termul.ssh.SshConnection;
import dev.egateza.termul.ssh.SshLease;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClient.Attributes;
import org.apache.sshd.sftp.client.SftpClient.CopyMode;
import org.apache.sshd.sftp.client.SftpClient.DirEntry;
import org.apache.sshd.sftp.client.SftpClient.OpenMode;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.client.extensions.openssh.OpenSSHPosixRenameExtension;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RemoteFileService} lewat SFTP di atas koneksi SSH yang sama dengan terminal (satu {@link SshLease}).
 *
 * <p>Semua method blocking (network): panggil dari executor, jangan di EDT. Satu instance boleh
 * dipakai beberapa thread (SftpClient MINA thread-safe untuk request terpisah).
 */
public final class SftpFileService implements RemoteFileService {

    private static final Logger log = LoggerFactory.getLogger(SftpFileService.class);
    private static final int BUFFER = 32 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(15);
    /** Batas rantai symlink (SYMLOOP_MAX Linux). */
    private static final int MAX_LINK_HOPS = 40;

    private final SshLease lease;
    private final SftpClient sftp;

    /**
     * Channel SFTP khusus aliran data download/upload. Satu channel = satu proses sftp-server yang melayani request
     * berurutan, jadi listing folder (atau stat) yang lewat channel yang sama harus menunggu read/write besar yang
     * sudah antre; di link yang dibatasi, pindah folder saat transfer terasa macet. Dibuka lazy.
     */
    private SftpClient bulk; // guarded by this; null = belum dibuka
    private boolean bulkClosed; // guarded by this

    private SftpFileService(SshLease lease, SftpClient sftp) {
        this.lease = lease;
        this.sftp = sftp;
    }

    /** Channel untuk aliran data transfer; kalau server menolak channel kedua (mis. MaxSessions), pakai channel utama. */
    private synchronized SftpClient bulk() {
        if (bulk == null && !bulkClosed) {
            try {
                bulk = SftpClientFactory.instance().createSftpClient(lease.connection().session());
            } catch (IOException | RuntimeException e) {
                log.warn("Channel SFTP kedua untuk transfer tidak bisa dibuka, memakai channel utama: {}", e.toString());
                bulkClosed = true;
            }
        }
        return bulk != null && bulk.isOpen() ? bulk : sftp;
    }

    /** Membuka subsystem SFTP. Lease menjadi milik service (dilepas di {@link #close()}). */
    public static SftpFileService open(SshLease lease) throws RemoteFileException {
        try {
            return new SftpFileService(lease, SftpClientFactory.instance().createSftpClient(lease.connection().session()));
        } catch (IOException e) {
            lease.close();
            throw new RemoteFileException("Gagal membuka SFTP: " + e.getMessage(), e);
        }
    }

    /** true kalau channel SFTP dan koneksi SSH-nya masih hidup. */
    @Override
    public boolean isOpen() {
        return sftp.isOpen() && !lease.isReleased() && lease.connection().isOpen();
    }

    /** Koneksi SSH di bawah SFTP ini (untuk exec channel, mis. sudo). Jangan di-close. */
    public SshConnection connection() {
        return lease.connection();
    }

    /**
     * Apakah file ini bisa ditimpa user login tanpa sudo: bisa ditulis ({@code test -w}, memperhitungkan group
     * dan ACL) <b>dan</b> dimiliki user login ({@code test -O}; kalau tidak, {@link #upload} akan menolak).
     * Kalau pengecekan gagal dijalankan (mis. server menolak exec), dianggap bisa: upload biasa yang akan
     * melaporkan errornya.
     */
    @Override
    public boolean canWrite(String path) {
        String quoted = ShellQuote.quote(path);
        try {
            var result = RemoteExec.run(connection(), "test -w " + quoted + " && test -O " + quoted, null, CHECK_TIMEOUT);
            return result.exitStatus() != 1; // 1 = tidak bisa ditulis; -1 = exec ditolak server (tidak diketahui)
        } catch (IOException | RuntimeException e) {
            log.debug("test -w {} gagal: {}", path, e.toString());
            return true;
        }
    }

    /**
     * Membuat direktori baru {@code <parent>/termul-<acak>} dengan mode 700, <b>sebelum</b> ada file di dalamnya,
     * supaya user lain tidak bisa membuka file sementara (mis. salinan config sebelum dipasang dengan sudo).
     *
     * @return path direktori
     */
    public String createPrivateDir(String parent) throws RemoteFileException {
        String dir = RemotePaths.join(parent, "termul-" + randomSuffix());
        try {
            sftp.mkdir(dir); // gagal kalau sudah ada (termasuk symlink yang disiapkan orang lain)
            chmod(dir, 0700);
            Attributes attrs = sftp.lstat(dir);
            if (!attrs.isDirectory() || (attrs.getPermissions() & 0077) != 0) {
                throw new RemoteFileException("Direktori sementara " + dir + " tidak aman");
            }
            return dir;
        } catch (RemoteFileException e) {
            throw e;
        } catch (IOException e) {
            throw translate("Gagal membuat direktori sementara", dir, e);
        }
    }

    /** Direktori home user (path absolut). */
    @Override
    public String home() throws RemoteFileException {
        return canonicalize(".");
    }

    @Override
    public String canonicalize(String path) throws RemoteFileException {
        try {
            return sftp.canonicalPath(path);
        } catch (IOException e) {
            throw translate("Path tidak valid", path, e);
        }
    }

    /** Isi direktori (tanpa {@code .} dan {@code ..}), direktori dulu lalu nama. */
    @Override
    public List<RemoteEntry> list(String dir) throws RemoteFileException {
        var result = new ArrayList<RemoteEntry>();
        try {
            for (DirEntry e : sftp.readDir(dir)) {
                String name = e.getFilename();
                if (name.equals(".") || name.equals("..")) {
                    continue;
                }
                result.add(toEntry(RemotePaths.join(dir, name), name, e.getAttributes(), e.getLongFilename()));
            }
        } catch (IOException e) {
            throw translate("Gagal membaca direktori", dir, e);
        } catch (UncheckedIOException e) { // iterator readDir MINA melempar secara lazy
            throw translate("Gagal membaca direktori", dir, e.getCause());
        }
        result.sort(RemoteEntry.DIRECTORIES_FIRST);
        return result;
    }

    /**
     * Isi file teks kecil (UTF-8), dipotong di {@code maxBytes}. Untuk file info seperti {@code /etc/os-release};
     * tidak memakai exec, jadi juga jalan di akun yang hanya boleh SFTP.
     *
     * @return isi file, atau null kalau file tidak ada/tidak bisa dibaca (mis. akun SFTP di-chroot)
     */
    @Override
    public String readSmallText(String path, int maxBytes) {
        try (InputStream in = sftp.read(path)) {
            return new String(in.readNBytes(maxBytes), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            log.debug("Membaca {} gagal: {}", path, e.toString());
            return null;
        }
    }

    /** Stat (mengikuti symlink). */
    @Override
    public RemoteEntry stat(String path) throws RemoteFileException {
        try {
            return toEntry(path, RemotePaths.name(path), sftp.stat(path), null);
        } catch (IOException e) {
            throw translate("Gagal membaca info file", path, e);
        }
    }

    @Override
    public boolean exists(String path) throws RemoteFileException {
        try {
            sftp.stat(path);
            return true;
        } catch (SftpException e) {
            if (e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            throw translate("Gagal membaca info file", path, e);
        } catch (IOException e) {
            throw translate("Gagal membaca info file", path, e);
        }
    }

    @Override
    public void mkdir(String path) throws RemoteFileException {
        try {
            sftp.mkdir(path);
        } catch (IOException e) {
            throw translate("Gagal membuat direktori", path, e);
        }
    }

    /** Rename/move; gagal kalau tujuan sudah ada. */
    @Override
    public void rename(String from, String to) throws RemoteFileException {
        try {
            if (exists(to)) {
                throw new RemoteFileException("Tujuan sudah ada: " + to);
            }
            sftp.rename(from, to);
        } catch (RemoteFileException e) {
            throw e;
        } catch (IOException e) {
            throw translate("Gagal rename", from, e);
        }
    }

    /** Hapus file, symlink, atau direktori (rekursif kalau {@code recursive}). */
    @Override
    public void delete(String path, boolean recursive) throws RemoteFileException {
        try {
            Attributes attrs = sftp.lstat(path);
            if (attrs.isDirectory()) {
                if (recursive) {
                    for (RemoteEntry child : list(path)) {
                        delete(child.path(), true);
                    }
                }
                sftp.rmdir(path);
            } else {
                sftp.remove(path);
            }
        } catch (RemoteFileException e) {
            throw e;
        } catch (IOException e) {
            throw translate("Gagal menghapus", path, e);
        }
    }

    /** Mengubah permission ({@code mode} mis. {@code 0640}). */
    @Override
    public void chmod(String path, int mode) throws RemoteFileException {
        try {
            var attrs = new Attributes();
            attrs.setPermissions(mode & 07777);
            sftp.setStat(path, attrs);
        } catch (IOException e) {
            throw translate("Gagal chmod", path, e);
        }
    }

    /** Download ke file lokal (ditulis ke temp lalu di-rename; file lokal lama tidak rusak kalau gagal/batal). */
    @Override
    public void download(String remote, Path local, TransferListener listener) throws RemoteFileException {
        Objects.requireNonNull(listener);
        long total = stat(remote).size();
        Path dir = local.toAbsolutePath().getParent();
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            tmp = Files.createTempFile(dir, "." + local.getFileName(), ".part");
            try (InputStream in = bulk().read(remote, BUFFER); OutputStream out = Files.newOutputStream(tmp)) {
                copy(in, out, total, listener);
            }
            Files.move(tmp, local, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            tmp = null;
        } catch (RemoteFileException e) {
            throw e;
        } catch (IOException e) {
            throw translate("Gagal download", remote, e);
        } finally {
            deleteQuietly(tmp);
        }
    }

    /**
     * Upload atomic: tulis ke {@code <dir>/.<name>.termul-<rand>.tmp}, set mode, lalu rename ke tujuan
     * ({@code posix-rename@openssh.com} kalau tersedia). Mode file lama dipertahankan; untuk file baru
     * dipakai {@code newFileMode} (atau default server kalau null).
     *
     * <p>File lama ditolak kalau tidak bisa ditulis user login, atau kalau owner-nya bukan user login
     * (mis. root, walau group-writable). Rename cukup butuh izin tulis di direktori, jadi tanpa pengecekan ini
     * file tersebut tertimpa dan owner-nya berubah menjadi user login. File seperti itu diedit lewat sudo.
     *
     * <p>Kalau {@code remote} symlink (mis. {@code /etc/apache2/sites-enabled/*.conf}), yang diganti adalah file
     * targetnya: rename di path link akan mengganti link itu sendiri dengan file biasa.
     */
    @Override
    public void upload(Path local, String remoteOrLink, Integer newFileMode, TransferListener listener)
            throws RemoteFileException {
        long total;
        try {
            total = Files.size(local);
        } catch (IOException e) {
            throw translate("Gagal upload", remoteOrLink, e);
        }
        write(() -> Files.newInputStream(local), total, remoteOrLink, newFileMode, listener);
    }

    /** Sumber isi file untuk {@link #write}: dibuka setelah file temp di tujuan siap. */
    @FunctionalInterface
    private interface Source {
        InputStream open() throws IOException;
    }

    /**
     * Salin file dari server lain (atau server yang sama) ke {@code targetOrLink} di server ini, langsung dari stream
     * SFTP sumber ke stream SFTP tujuan: tidak ada salinan di disk lokal. Aturan tulisnya sama dengan
     * {@link #upload(Path, String, Integer, TransferListener)} (temp lalu rename, mode file lama dipertahankan); file
     * baru memakai mode file sumber.
     *
     * @param sourcePath file di {@code source}; symlink diikuti
     */
    @Override
    public void copyFrom(RemoteFileService source, String sourcePath, String targetOrLink, TransferListener listener)
            throws RemoteFileException {
        RemoteEntry from = source.stat(sourcePath);
        if (from.type() != RemoteEntry.Type.FILE) {
            throw new RemoteFileException("Bukan file biasa, tidak bisa disalin: " + sourcePath);
        }
        write(() -> source.openRead(sourcePath), from.size(), targetOrLink, from.mode(), listener);
    }

    @Override
    public InputStream openRead(String remote) throws IOException {
        return bulk().read(remote, BUFFER);
    }

    private void write(Source source, long total, String remoteOrLink, Integer newFileMode, TransferListener listener)
            throws RemoteFileException {
        Objects.requireNonNull(listener);
        String remote = resolveLinks(remoteOrLink);
        String tmp = RemotePaths.join(RemotePaths.parent(remote),
                "." + RemotePaths.name(remote) + ".termul-" + randomSuffix() + ".tmp");
        boolean tmpCreated = false;
        try {
            Attributes existing = exists(remote) ? sftp.stat(remote) : null;
            if (existing != null) {
                requireWritable(remote);
            }
            Integer mode = existing != null ? Integer.valueOf(existing.getPermissions() & 07777) : newFileMode;
            try (OutputStream out = bulk().write(tmp, BUFFER, OpenMode.Write, OpenMode.Create, OpenMode.Exclusive)) {
                tmpCreated = true;
                if (existing != null) {
                    requireSameOwner(existing, tmp, remote); // temp dibuat user login: uid-nya = uid user login
                }
                try (InputStream in = source.open()) {
                    copy(in, out, total, listener);
                }
            }
            if (mode != null) {
                chmod(tmp, mode);
            }
            atomicReplace(tmp, remote);
            tmpCreated = false;
        } catch (RemoteFileException e) {
            throw e;
        } catch (IOException e) {
            throw translate("Gagal upload", remote, e);
        } finally {
            if (tmpCreated) {
                try {
                    sftp.remove(tmp);
                } catch (IOException e) {
                    log.warn("File temp remote {} tidak bisa dihapus: {}", tmp, e.toString());
                }
            }
        }
    }

    /**
     * Mengikuti rantai symlink sampai path yang bukan symlink (seperti {@code readlink -f} untuk komponen terakhir).
     * Path yang belum ada, atau link yang target akhirnya belum ada, dikembalikan apa adanya / sampai link terakhir.
     */
    String resolveLinks(String path) throws RemoteFileException {
        String current = path;
        try {
            for (int hop = 0; hop < MAX_LINK_HOPS; hop++) {
                Attributes attrs;
                try {
                    attrs = sftp.lstat(current);
                } catch (SftpException e) {
                    if (e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE) {
                        return hop == 0 ? path : canonicalOrSelf(current);
                    }
                    throw e;
                }
                if (!attrs.isSymbolicLink()) {
                    return hop == 0 ? path : canonicalOrSelf(current);
                }
                current = RemotePaths.join(RemotePaths.parent(current), sftp.readLink(current));
            }
        } catch (IOException e) {
            throw translate("Gagal membaca symlink", path, e);
        }
        throw new RemoteFileException("Terlalu banyak symlink bertingkat: " + path);
    }

    /** Membersihkan {@code ..} dari target link relatif; path yang belum ada dibiarkan. */
    private String canonicalOrSelf(String path) {
        try {
            return sftp.canonicalPath(path);
        } catch (IOException e) {
            return path;
        }
    }

    /**
     * Buka file untuk write tanpa truncate lalu langsung tutup: isi dan mtime tidak berubah, tapi server
     * memeriksa izin (owner, group, ACL) seperti write biasa.
     */
    private void requireWritable(String remote) throws IOException {
        try (var _ = sftp.open(remote, OpenMode.Write)) {
            // hanya cek izin
        } catch (SftpException e) {
            if (e.getStatus() == SftpConstants.SSH_FX_PERMISSION_DENIED) {
                throw new RemoteFileException("File tidak bisa ditulis user login: " + remote
                        + " (akses ditolak; pakai \"Edit sebagai root\")", e);
            }
            throw e;
        }
    }

    private void requireSameOwner(Attributes existing, String tmp, String remote) throws IOException {
        int loginUid = sftp.stat(tmp).getUserId();
        if (existing.getUserId() != loginUid) {
            String owner = existing.getOwner() != null ? existing.getOwner() : "uid " + existing.getUserId();
            throw new RemoteFileException("File milik " + owner + ", bukan user login: " + remote
                    + " (menimpa tanpa sudo akan mengubah owner-nya; pakai \"Edit sebagai root\")");
        }
    }

    private void atomicReplace(String tmp, String target) throws IOException {
        OpenSSHPosixRenameExtension posix = sftp.getExtension(OpenSSHPosixRenameExtension.class);
        if (posix != null && posix.isSupported()) {
            posix.posixRename(tmp, target);
            return;
        }
        try {
            sftp.rename(tmp, target, CopyMode.Overwrite, CopyMode.Atomic);
        } catch (IOException e) {
            // SFTP v3 tanpa ekstensi: tidak ada rename-overwrite. Hapus lalu rename (tidak atomic).
            log.debug("Rename overwrite tidak didukung ({}), fallback remove + rename", e.toString());
            if (exists(target)) {
                sftp.remove(target);
            }
            sftp.rename(tmp, target);
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

    private RemoteEntry toEntry(String path, String name, Attributes a, String longName) {
        RemoteEntry.Type type;
        if (a.isSymbolicLink()) {
            type = RemoteEntry.Type.SYMLINK;
        } else if (a.isDirectory()) {
            type = RemoteEntry.Type.DIRECTORY;
        } else if (a.isRegularFile()) {
            type = RemoteEntry.Type.FILE;
        } else {
            type = RemoteEntry.Type.OTHER;
        }
        Instant mtime = a.getModifyTime() == null ? Instant.EPOCH : a.getModifyTime().toInstant();
        String[] ownerGroup = LongName.ownerGroup(longName);
        String owner = ownerGroup != null ? ownerGroup[0] : a.getOwner() != null ? a.getOwner() : String.valueOf(a.getUserId());
        String group = ownerGroup != null ? ownerGroup[1] : a.getGroup() != null ? a.getGroup() : String.valueOf(a.getGroupId());
        return new RemoteEntry(name, path, type, a.getSize(), mtime, a.getPermissions() & 07777, owner, group);
    }

    private static RemoteFileException translate(String action, String path, IOException e) {
        if (e instanceof RemoteFileException rfe) {
            return rfe;
        }
        if (e instanceof SftpException se) {
            String reason = switch (se.getStatus()) {
                case SftpConstants.SSH_FX_NO_SUCH_FILE -> "tidak ditemukan";
                case SftpConstants.SSH_FX_PERMISSION_DENIED -> "akses ditolak";
                case SftpConstants.SSH_FX_FILE_ALREADY_EXISTS -> "sudah ada";
                case SftpConstants.SSH_FX_DIR_NOT_EMPTY -> "direktori tidak kosong";
                default -> se.getMessage();
            };
            return new RemoteFileException(action + ": " + path + " (" + reason + ")", e);
        }
        return new RemoteFileException(action + ": " + path + " (" + e.getMessage() + ")", e);
    }

    private static String randomSuffix() {
        byte[] b = new byte[6];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static void deleteQuietly(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                log.warn("File temp {} tidak bisa dihapus", p);
            }
        }
    }

    @Override
    public void close() {
        SftpClient extra;
        synchronized (this) {
            extra = bulk;
            bulk = null;
            bulkClosed = true;
        }
        if (extra != null) {
            try {
                extra.close();
            } catch (IOException e) {
                log.debug("Menutup SFTP transfer: {}", e.toString());
            }
        }
        try {
            sftp.close();
        } catch (IOException e) {
            log.debug("Menutup SFTP: {}", e.toString());
        } finally {
            lease.close();
        }
    }
}

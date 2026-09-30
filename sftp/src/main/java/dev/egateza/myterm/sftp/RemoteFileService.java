package dev.egateza.myterm.sftp;

import dev.egateza.myterm.ssh.SshLease;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
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
 * Operasi file remote lewat SFTP di atas koneksi SSH yang sama dengan terminal (satu {@link SshLease}).
 *
 * <p>Semua method blocking (network): panggil dari executor, jangan di EDT. Satu instance boleh
 * dipakai beberapa thread (SftpClient MINA thread-safe untuk request terpisah).
 */
public final class RemoteFileService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RemoteFileService.class);
    private static final int BUFFER = 32 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SshLease lease;
    private final SftpClient sftp;

    private RemoteFileService(SshLease lease, SftpClient sftp) {
        this.lease = lease;
        this.sftp = sftp;
    }

    /** Membuka subsystem SFTP. Lease menjadi milik service (dilepas di {@link #close()}). */
    public static RemoteFileService open(SshLease lease) throws RemoteFileException {
        try {
            return new RemoteFileService(lease, SftpClientFactory.instance().createSftpClient(lease.connection().session()));
        } catch (IOException e) {
            lease.close();
            throw new RemoteFileException("Gagal membuka SFTP: " + e.getMessage(), e);
        }
    }

    /** true kalau channel SFTP dan koneksi SSH-nya masih hidup. */
    public boolean isOpen() {
        return sftp.isOpen() && !lease.isReleased() && lease.connection().isOpen();
    }

    /** Direktori home user (path absolut). */
    public String home() throws RemoteFileException {
        return canonicalize(".");
    }

    public String canonicalize(String path) throws RemoteFileException {
        try {
            return sftp.canonicalPath(path);
        } catch (IOException e) {
            throw translate("Path tidak valid", path, e);
        }
    }

    /** Isi direktori (tanpa {@code .} dan {@code ..}), direktori dulu lalu nama. */
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

    /** Stat (mengikuti symlink). */
    public RemoteEntry stat(String path) throws RemoteFileException {
        try {
            return toEntry(path, RemotePaths.name(path), sftp.stat(path), null);
        } catch (IOException e) {
            throw translate("Gagal membaca info file", path, e);
        }
    }

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

    public void mkdir(String path) throws RemoteFileException {
        try {
            sftp.mkdir(path);
        } catch (IOException e) {
            throw translate("Gagal membuat direktori", path, e);
        }
    }

    /** Rename/move; gagal kalau tujuan sudah ada. */
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
    public void download(String remote, Path local, TransferListener listener) throws RemoteFileException {
        Objects.requireNonNull(listener);
        long total = stat(remote).size();
        Path dir = local.toAbsolutePath().getParent();
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            tmp = Files.createTempFile(dir, "." + local.getFileName(), ".part");
            try (InputStream in = sftp.read(remote, BUFFER); OutputStream out = Files.newOutputStream(tmp)) {
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
     * Upload atomic: tulis ke {@code <dir>/.<name>.myterm-<rand>.tmp}, set mode, lalu rename ke tujuan
     * ({@code posix-rename@openssh.com} kalau tersedia). Mode file lama dipertahankan; untuk file baru
     * dipakai {@code newFileMode} (atau default server kalau null).
     */
    public void upload(Path local, String remote, Integer newFileMode, TransferListener listener)
            throws RemoteFileException {
        Objects.requireNonNull(listener);
        String tmp = RemotePaths.join(RemotePaths.parent(remote),
                "." + RemotePaths.name(remote) + ".myterm-" + randomSuffix() + ".tmp");
        boolean tmpCreated = false;
        try {
            Integer mode = exists(remote) ? Integer.valueOf(stat(remote).mode()) : newFileMode;
            long total = Files.size(local);
            try (InputStream in = Files.newInputStream(local);
                 OutputStream out = sftp.write(tmp, BUFFER, OpenMode.Write, OpenMode.Create, OpenMode.Exclusive)) {
                tmpCreated = true;
                copy(in, out, total, listener);
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
        try {
            sftp.close();
        } catch (IOException e) {
            log.debug("Menutup SFTP: {}", e.toString());
        } finally {
            lease.close();
        }
    }
}

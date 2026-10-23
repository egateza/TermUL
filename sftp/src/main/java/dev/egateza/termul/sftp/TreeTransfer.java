package dev.egateza.termul.sftp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Upload/download/salin antar-server satu direktori beserta isinya di atas {@link RemoteFileService}. Isi direktori dibaca dulu
 * (rencana), baru disalin, supaya progress menunjukkan total seluruh direktori. Dipanggil dari thread transfer.
 *
 * <p>Symlink ke direktori tidak diikuti (bisa membentuk loop) dan dilaporkan sebagai dilewati; symlink remote ke
 * file ikut diunduh. Symlink lokal dilewati. File yang sudah ada di tujuan ditimpa: konfirmasi dilakukan sekali
 * untuk direktori tujuan lewat {@link Conflict}.
 */
public final class TreeTransfer {

    private static final Logger log = LoggerFactory.getLogger(TreeTransfer.class);

    /** Hasil transfer direktori. */
    public record Result(int files, int directories, long bytes, List<String> skipped) {
        public Result {
            skipped = List.copyOf(skipped);
        }
    }

    /** Konfirmasi saat direktori tujuan sudah ada (isi digabung, file bernama sama ditimpa). */
    @FunctionalInterface
    public interface Conflict {
        /** @return true = lanjut menggabungkan, false = batal tanpa menyalin apa pun */
        boolean merge(String existingTarget);
    }

    private record FileJob(Path local, String remote, long size) {
    }

    private TreeTransfer() {
    }

    /**
     * Upload {@code localDir} ke {@code remoteParent/<nama localDir>}.
     *
     * @return null kalau user memilih tidak menggabungkan ke direktori yang sudah ada
     */
    public static Result upload(RemoteFileService svc, Path localDir, String remoteParent, Conflict conflict,
                                TransferListener listener) throws RemoteFileException {
        Objects.requireNonNull(listener);
        String target = RemotePaths.join(remoteParent, localDir.getFileName().toString());
        if (svc.exists(target) && !conflict.merge(target)) {
            return null;
        }
        var dirs = new ArrayList<String>();
        var files = new ArrayList<FileJob>();
        var skipped = new ArrayList<String>();
        try (Stream<Path> walk = Files.walk(localDir)) { // tidak mengikuti symlink
            for (Path p : (Iterable<Path>) walk::iterator) {
                String remote = remotePath(target, localDir.relativize(p));
                if (Files.isSymbolicLink(p)) {
                    skipped.add(localDir.relativize(p).toString());
                } else if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    dirs.add(remote);
                } else if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    files.add(new FileJob(p, remote, Files.size(p)));
                } else {
                    skipped.add(localDir.relativize(p).toString());
                }
            }
        } catch (IOException | UncheckedIOException e) {
            throw new RemoteFileException("Gagal membaca folder lokal " + localDir + ": " + e.getMessage(), e);
        }
        for (String dir : dirs) { // Files.walk: induk selalu sebelum isinya
            checkCancelled(listener);
            if (!svc.exists(dir)) {
                svc.mkdir(dir);
            }
        }
        long total = files.stream().mapToLong(FileJob::size).sum();
        long done = 0;
        for (FileJob f : files) {
            checkCancelled(listener);
            svc.upload(f.local(), f.remote(), null, offset(listener, done, total));
            done += f.size();
        }
        listener.progress(total, total);
        return new Result(files.size(), dirs.size(), total, skipped);
    }

    /**
     * Download {@code remoteDir} ke {@code localParent/<nama remoteDir>}.
     *
     * @return null kalau user memilih tidak menggabungkan ke direktori yang sudah ada
     */
    public static Result download(RemoteFileService svc, String remoteDir, Path localParent, Conflict conflict,
                                  TransferListener listener) throws RemoteFileException {
        Objects.requireNonNull(listener);
        Path target = localParent.resolve(safeLocalName(RemotePaths.name(remoteDir)));
        if (Files.exists(target) && !conflict.merge(target.toString())) {
            return null;
        }
        var dirs = new ArrayList<Path>();
        var files = new ArrayList<FileJob>();
        var skipped = new ArrayList<String>();
        plan(svc, remoteDir, target, dirs, files, skipped, listener);
        try {
            for (Path dir : dirs) {
                checkCancelled(listener);
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new RemoteFileException("Gagal membuat folder lokal: " + e.getMessage(), e);
        }
        long total = files.stream().mapToLong(FileJob::size).sum();
        long done = 0;
        for (FileJob f : files) {
            checkCancelled(listener);
            svc.download(f.remote(), f.local(), offset(listener, done, total));
            done += f.size();
        }
        listener.progress(total, total);
        return new Result(files.size(), dirs.size(), total, skipped);
    }

    /** File dan direktori yang disalin antar-server: sumber → tujuan. */
    private record CopyJob(String from, String to, long size, int mode) {
    }

    /**
     * Salin {@code fromDir} di {@code from} ke {@code toParent/<nama fromDir>} di {@code to} (server lain atau server
     * yang sama), file per file langsung dari stream ke stream lewat {@link RemoteFileService#copyFrom}. Direktori yang
     * baru dibuat mendapat mode direktori sumber setelah isinya selesai disalin (mode read-only tidak menghalangi
     * penyalinan isinya).
     *
     * @return null kalau user memilih tidak menggabungkan ke direktori yang sudah ada
     */
    public static Result copy(RemoteFileService from, String fromDir, RemoteFileService to, String toParent,
                              Conflict conflict, TransferListener listener) throws RemoteFileException {
        Objects.requireNonNull(listener);
        String target = RemotePaths.join(toParent, safeRemoteName(RemotePaths.name(fromDir)));
        if (to.exists(target) && !conflict.merge(target)) {
            return null;
        }
        var dirs = new ArrayList<CopyJob>();
        var files = new ArrayList<CopyJob>();
        var skipped = new ArrayList<String>();
        planCopy(from, from.stat(fromDir), target, dirs, files, skipped, listener);
        var created = new ArrayList<CopyJob>();
        for (CopyJob dir : dirs) { // induk selalu sebelum isinya
            checkCancelled(listener);
            if (!to.exists(dir.to())) {
                to.mkdir(dir.to());
                created.add(dir);
            }
        }
        long total = files.stream().mapToLong(CopyJob::size).sum();
        long done = 0;
        for (CopyJob f : files) {
            checkCancelled(listener);
            to.copyFrom(from, f.from(), f.to(), offset(listener, done, total));
            done += f.size();
        }
        for (CopyJob dir : created.reversed()) { // terdalam dulu
            if (dir.mode() == 0) {
                continue; // sumber tidak memberi info mode (mis. sebagian server FTP): pakai default server tujuan
            }
            try {
                to.chmod(dir.to(), dir.mode());
            } catch (RemoteFileException e) { // mis. server FTP tanpa SITE CHMOD; isinya sudah tersalin
                log.warn("Mode direktori {} tidak bisa disamakan dengan sumber: {}", dir.to(), e.getMessage());
            }
        }
        listener.progress(total, total);
        return new Result(files.size(), dirs.size(), total, skipped);
    }

    private static void planCopy(RemoteFileService svc, RemoteEntry dir, String targetDir, List<CopyJob> dirs,
                                 List<CopyJob> files, List<String> skipped, TransferListener listener)
            throws RemoteFileException {
        checkCancelled(listener);
        dirs.add(new CopyJob(dir.path(), targetDir, 0, dir.mode()));
        for (RemoteEntry e : svc.list(dir.path())) {
            String to;
            try {
                to = RemotePaths.join(targetDir, safeRemoteName(e.name()));
            } catch (RemoteFileException ex) {
                skipped.add(e.path());
                continue;
            }
            switch (e.type()) {
                case DIRECTORY -> planCopy(svc, e, to, dirs, files, skipped, listener);
                case FILE -> files.add(new CopyJob(e.path(), to, e.size(), e.mode()));
                case SYMLINK -> {
                    RemoteEntry target = svc.stat(e.path());
                    if (target.type() == RemoteEntry.Type.FILE) {
                        files.add(new CopyJob(e.path(), to, target.size(), target.mode()));
                    } else {
                        skipped.add(e.path()); // symlink ke direktori: tidak diikuti (bisa loop)
                    }
                }
                case OTHER -> skipped.add(e.path());
            }
        }
    }

    /** Nama entri remote sebagai nama di server tujuan: menolak nama yang bisa keluar dari folder tujuan. */
    static String safeRemoteName(String name) throws RemoteFileException {
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/")) {
            throw new RemoteFileException("Nama file remote tidak aman untuk disalin: " + name);
        }
        return name;
    }

    private static void plan(RemoteFileService svc, String remoteDir, Path localDir, List<Path> dirs,
                             List<FileJob> files, List<String> skipped, TransferListener listener)
            throws RemoteFileException {
        checkCancelled(listener);
        dirs.add(localDir);
        for (RemoteEntry e : svc.list(remoteDir)) {
            Path local;
            try {
                local = localDir.resolve(safeLocalName(e.name()));
            } catch (java.nio.file.InvalidPathException ex) {
                skipped.add(e.path()); // nama Linux yang tidak valid di Windows (mis. berisi ':' atau '?')
                continue;
            }
            switch (e.type()) {
                case DIRECTORY -> plan(svc, e.path(), local, dirs, files, skipped, listener);
                case FILE -> files.add(new FileJob(local, e.path(), e.size()));
                case SYMLINK -> {
                    RemoteEntry target = svc.stat(e.path());
                    if (target.type() == RemoteEntry.Type.FILE) {
                        files.add(new FileJob(local, e.path(), target.size()));
                    } else {
                        skipped.add(e.path()); // symlink ke direktori: tidak diikuti (bisa loop)
                    }
                }
                case OTHER -> skipped.add(e.path());
            }
        }
    }

    /** Nama file remote sebagai nama lokal; menolak nama yang bisa keluar dari folder tujuan. */
    static String safeLocalName(String name) throws RemoteFileException {
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\")) {
            throw new RemoteFileException("Nama file remote tidak aman untuk disimpan lokal: " + name);
        }
        return name;
    }

    static String remotePath(String base, Path relative) {
        String result = base;
        for (Path part : relative) {
            if (!part.toString().isEmpty()) {
                result = RemotePaths.join(result, part.toString());
            }
        }
        return result;
    }

    /** Progress per file diterjemahkan ke progress seluruh direktori. */
    private static TransferListener offset(TransferListener listener, long before, long total) {
        return new TransferListener() {
            @Override
            public void progress(long transferred, long fileTotal) {
                listener.progress(before + transferred, total);
            }

            @Override
            public boolean isCancelled() {
                return listener.isCancelled();
            }
        };
    }

    private static void checkCancelled(TransferListener listener) throws RemoteFileException.Cancelled {
        if (listener.isCancelled()) {
            throw new RemoteFileException.Cancelled();
        }
    }
}

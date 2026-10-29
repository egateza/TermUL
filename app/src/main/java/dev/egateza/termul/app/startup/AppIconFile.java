package dev.egateza.termul.app.startup;

import dev.egateza.termul.core.io.AtomicFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * File {@code .ico} aplikasi di luar jar, untuk shortcut dan menu klik kanan Explorer (ADR 0010). Namanya memuat hash
 * isinya ({@code termul-<hash>.ico}): ikon baru = nama baru, sehingga cache ikon Windows tidak menampilkan ikon lama.
 */
final class AppIconFile {

    static final String RESOURCE = "/dev/egateza/termul/app/icons/termul.ico";
    private static final String PREFIX = "termul-";
    private static final String SUFFIX = ".ico";

    /** File ikon yang dipakai di sesi ini; null sampai {@link #use} dipanggil (dibaca thread lain). */
    private static volatile Path installed;

    private AppIconFile() {
    }

    /** @return file ikon dari jar yang dipakai shortcut / menu Explorer di sesi ini, atau null */
    static Path installed() {
        return installed;
    }

    /** Tandai {@code file} sebagai ikon sesi ini (dipakai saat menu Explorer ditulis ulang). */
    static void use(Path file) {
        installed = file;
    }

    /** Isi {@code termul.ico} di jar. */
    static byte[] bundled() throws IOException {
        try (InputStream in = AppIconFile.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("Ikon " + RESOURCE + " tidak ada di jar");
            }
            return in.readAllBytes();
        }
    }

    static String fileName(byte[] ico) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(ico);
            return PREFIX + HexFormat.of().formatHex(hash, 0, 6) + SUFFIX;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Salin {@code ico} ke {@code dir} kalau belum ada (atau ukurannya berbeda). */
    static Path install(Path dir, byte[] ico) throws IOException {
        Path target = dir.resolve(fileName(ico));
        if (!Files.isRegularFile(target) || Files.size(target) != ico.length) {
            Files.createDirectories(dir);
            AtomicFiles.write(target, ico);
        }
        return target;
    }

    /** Hapus ikon versi lama di {@code dir} (setelah shortcut diarahkan ke {@code keep}); gagal hapus diabaikan. */
    static void deleteOthers(Path dir, Path keep) {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, PREFIX + "*" + SUFFIX)) {
            for (Path f : files) {
                if (!f.getFileName().equals(keep.getFileName())) {
                    Files.deleteIfExists(f);
                }
            }
        } catch (IOException ignored) {
            // file lama yang tertinggal hanya beberapa KB; dicoba lagi di start berikutnya
        }
    }
}

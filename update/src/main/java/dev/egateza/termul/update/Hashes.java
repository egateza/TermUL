package dev.egateza.termul.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 file (hex huruf kecil, sama dengan format manifest). */
public final class Hashes {

    private Hashes() {
    }

    public static String sha256(Path file) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // wajib ada di setiap JDK
        }
    }

    /** @return true kalau file ada dengan ukuran dan hash persis seperti entry */
    public static boolean matches(Path file, UpdateManifest.FileEntry entry) {
        try {
            return Files.isRegularFile(file) && Files.size(file) == entry.size()
                    && sha256(file).equals(entry.sha256());
        } catch (IOException e) {
            return false;
        }
    }
}

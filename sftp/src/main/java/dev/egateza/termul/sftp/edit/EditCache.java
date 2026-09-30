package dev.egateza.termul.sftp.edit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lokasi cache file yang sedang diedit: {@code <root>/<profileId>/<hash path remote>/<nama aman>}.
 *
 * <p>Nama file disanitasi (hanya {@code [A-Za-z0-9._-]}) supaya aman diteruskan ke editor
 * (termasuk lewat {@code cmd.exe}) dan valid di Windows. Ekstensi dipertahankan untuk syntax highlight.
 */
public final class EditCache {

    private static final Logger log = LoggerFactory.getLogger(EditCache.class);
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._-]");
    private static final Set<String> RESERVED = Set.of("CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");
    private static final int MAX_NAME = 100;

    private final Path root;

    public EditCache(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    /** Path lokal (belum dibuat) untuk file remote milik profil. */
    public Path pathFor(UUID profileId, String remotePath) {
        return root.resolve(profileId.toString()).resolve(shortHash(remotePath)).resolve(safeName(remotePath));
    }

    /** Membuat direktori cache (akses hanya untuk user saat ini, best effort). */
    public Path prepare(Path localFile) throws IOException {
        boolean rootIsNew = !Files.exists(root);
        Files.createDirectories(localFile.getParent());
        if (rootIsNew) {
            restrictToOwner(root);
        }
        return localFile;
    }

    /** Menghapus file cache dan direktori hash-nya kalau kosong. */
    public void remove(Path localFile) throws IOException {
        Files.deleteIfExists(localFile);
        Path dir = localFile.getParent();
        try (var s = Files.list(dir)) {
            if (s.findAny().isEmpty()) {
                Files.deleteIfExists(dir);
            }
        }
    }

    static String safeName(String remotePath) {
        String name = remotePath.substring(remotePath.lastIndexOf('/') + 1);
        String safe = UNSAFE.matcher(name).replaceAll("_");
        if (safe.isEmpty() || safe.chars().allMatch(c -> c == '.')) {
            safe = "file";
        }
        String base = safe.contains(".") ? safe.substring(0, safe.indexOf('.')) : safe;
        if (RESERVED.contains(base.toUpperCase(Locale.ROOT))) {
            safe = "_" + safe;
        }
        if (safe.endsWith(".")) {
            safe = safe + "_";
        }
        if (safe.length() > MAX_NAME) {
            int dot = safe.lastIndexOf('.');
            String ext = dot > 0 && safe.length() - dot <= 16 ? safe.substring(dot) : "";
            safe = safe.substring(0, MAX_NAME - ext.length()) + ext;
        }
        return safe;
    }

    private static String shortHash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** ACL: hanya owner (Windows) atau mode 700 (POSIX). Gagal = hanya warning. */
    static void restrictToOwner(Path dir) {
        try {
            var acl = Files.getFileAttributeView(dir, AclFileAttributeView.class);
            if (acl != null) {
                var owner = acl.getOwner();
                var entry = AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(owner)
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                        .setFlags(java.nio.file.attribute.AclEntryFlag.FILE_INHERIT,
                                java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT)
                        .build();
                acl.setAcl(List.of(entry));
            } else {
                Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
            }
        } catch (IOException | UnsupportedOperationException e) {
            log.warn("Tidak bisa membatasi akses folder cache {}: {}", dir, e.toString());
        }
    }
}

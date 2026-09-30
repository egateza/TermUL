package dev.egateza.termul.sftp;

import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;

/**
 * Satu entry di direktori remote.
 *
 * @param name        nama file (tanpa path)
 * @param path        path absolut remote (POSIX)
 * @param type        jenis entry
 * @param size        ukuran byte
 * @param modified    waktu modifikasi (resolusi detik)
 * @param mode        permission bits (mis. {@code 0644}), tanpa bit tipe
 * @param owner       nama owner (dari longname) atau uid
 * @param group       nama group (dari longname) atau gid
 */
public record RemoteEntry(String name, String path, Type type, long size, Instant modified, int mode,
                          String owner, String group) {

    public enum Type { FILE, DIRECTORY, SYMLINK, OTHER }

    /** Direktori dulu, lalu nama (case-insensitive). */
    public static final Comparator<RemoteEntry> DIRECTORIES_FIRST = Comparator
            .comparing((RemoteEntry e) -> e.type() != Type.DIRECTORY)
            .thenComparing(e -> e.name().toLowerCase(Locale.ROOT));

    public boolean isDirectory() {
        return type == Type.DIRECTORY;
    }

    /** Format {@code rwxr-xr-x}. */
    public String modeString() {
        return modeString(mode);
    }

    public static String modeString(int mode) {
        var sb = new StringBuilder(9);
        String chars = "rwxrwxrwx";
        for (int i = 0; i < 9; i++) {
            sb.append((mode & (1 << (8 - i))) != 0 ? chars.charAt(i) : '-');
        }
        if ((mode & 04000) != 0) {
            sb.setCharAt(2, sb.charAt(2) == 'x' ? 's' : 'S');
        }
        if ((mode & 02000) != 0) {
            sb.setCharAt(5, sb.charAt(5) == 'x' ? 's' : 'S');
        }
        if ((mode & 01000) != 0) {
            sb.setCharAt(8, sb.charAt(8) == 'x' ? 't' : 'T');
        }
        return sb.toString();
    }
}

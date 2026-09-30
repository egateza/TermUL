package dev.egateza.myterm.app.sftp;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Format tampilan ukuran dan waktu file. */
public final class Formats {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private Formats() {
    }

    public static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double v = bytes;
        int i = -1;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(Locale.ROOT, v < 10 ? "%.1f %s" : "%.0f %s", v, units[i]);
    }

    public static String time(Instant t) {
        return DATE.format(t);
    }

    /** Parse mode oktal ("644", "0755", "4755"). @throws IllegalArgumentException kalau tidak valid */
    public static int parseMode(String text) {
        String s = text.strip();
        if (!s.matches("[0-7]{3,4}")) {
            throw new IllegalArgumentException("Mode harus angka oktal 3–4 digit, mis. 644 atau 0755");
        }
        return Integer.parseInt(s, 8);
    }
}

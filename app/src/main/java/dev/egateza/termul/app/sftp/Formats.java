package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.i18n.I18n;
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

    /**
     * Teks yang terlalu lebar disingkat di tengah ({@code awal…akhir}), supaya awal nama dan ekstensinya tetap
     * terlihat. Bagian akhir diberi satu karakter lebih kalau ganjil.
     *
     * @param width lebar teks dalam px (mis. {@code FontMetrics::stringWidth})
     */
    public static String elideMiddle(String text, int maxWidth, java.util.function.ToIntFunction<String> width) {
        if (width.applyAsInt(text) <= maxWidth) {
            return text;
        }
        for (int keep = text.length() - 1; keep > 1; keep--) {
            int tail = (keep + 1) / 2;
            String candidate = text.substring(0, keep - tail) + "…" + text.substring(text.length() - tail);
            if (width.applyAsInt(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return "…";
    }

    /** Parse mode oktal ("644", "0755", "4755"). @throws IllegalArgumentException kalau tidak valid */
    public static int parseMode(String text) {
        String s = text.strip();
        if (!s.matches("[0-7]{3,4}")) {
            throw new IllegalArgumentException(I18n.t("sftp.chmod.invalidMode"));
        }
        return Integer.parseInt(s, 8);
    }
}

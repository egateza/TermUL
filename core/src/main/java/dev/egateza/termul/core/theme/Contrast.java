package dev.egateza.termul.core.theme;

/** Rasio kontras WCAG antara dua warna hex {@code #RRGGBB}; dipakai untuk memperingatkan tema yang sulit dibaca. */
public final class Contrast {

    /** Batas teks biasa (WCAG AA). */
    public static final double TEXT_MIN = 4.5;
    /** Batas teks berwarna ANSI: lebih longgar, karena warna seperti biru tua memang sengaja redup. */
    public static final double ANSI_MIN = 3.0;

    private Contrast() {
    }

    /** @return 1.0 (sama persis) sampai 21.0 (hitam di atas putih) */
    public static double ratio(String hexA, String hexB) {
        double a = luminance(hexA);
        double b = luminance(hexB);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    static double luminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        return 0.2126 * channel((rgb >> 16) & 0xFF) + 0.7152 * channel((rgb >> 8) & 0xFF) + 0.0722 * channel(rgb & 0xFF);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}

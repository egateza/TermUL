package dev.egateza.termul.app.ui.streak;

import java.awt.Color;

/**
 * Warna tiga lapis api (luar, tengah, inti) plus cahaya di sekitarnya.
 *
 * @param glow warna cahaya, atau null untuk api tanpa cahaya (padam, beku)
 */
record FlamePalette(Color outer, Color mid, Color core, Color glow) {

    static final FlamePalette GRAY = new FlamePalette(new Color(0x55585D), new Color(0x7E8288), new Color(0xAEB2B8), null);
    static final FlamePalette ICE = new FlamePalette(new Color(0x4F9FDC), new Color(0x86C9F7), new Color(0xE4F5FF), null);

    static FlamePalette of(int outer, int mid, int core, int glow) {
        return new FlamePalette(new Color(outer), new Color(mid), new Color(core), new Color(glow));
    }

    /** Campuran ke arah {@code to} sebesar {@code p} (0..1); cahaya ikut memudar. */
    FlamePalette mix(FlamePalette to, double p) {
        Color g = glow == null ? null : fade(glow, 1 - p);
        return new FlamePalette(mix(outer, to.outer, p), mix(mid, to.mid, p), mix(core, to.core, p), g);
    }

    static Color mix(Color a, Color b, double p) {
        double q = Math.clamp(p, 0, 1);
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * q),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * q),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * q));
    }

    static Color fade(Color c, double alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * Math.clamp(alpha, 0, 1)));
    }
}

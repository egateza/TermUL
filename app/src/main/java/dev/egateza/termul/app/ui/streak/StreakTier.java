package dev.egateza.termul.app.ui.streak;

import dev.egateza.termul.app.i18n.I18n;
import java.awt.Color;
import java.util.Locale;

/**
 * Level api menurut jumlah hari streak. Rentangnya makin panjang di level atas, dan level atas mendapat detail
 * tambahan (lidah api, percikan, bintang).
 */
enum StreakTier {

    BARA(1, FlamePalette.of(0x8A3A17, 0xD0601F, 0xFFC07A, 0xFF7A2A), 0, 0, false),
    JINGGA(3, FlamePalette.of(0xFF6A00, 0xFF9D1F, 0xFFE28A, 0xFF7A00), 0, 1, false),
    MERAH(10, FlamePalette.of(0xE01A2B, 0xFF4D2E, 0xFFC76B, 0xFF2A1A), 1, 2, false),
    MAGENTA(30, FlamePalette.of(0xC8106A, 0xFF3F9F, 0xFFD3EA, 0xFF2F8F), 1, 2, false),
    UNGU(60, FlamePalette.of(0x5B1FC4, 0x9A5CFF, 0xEADCFF, 0x8A4DFF), 2, 3, false),
    BIRU(100, FlamePalette.of(0x1149D8, 0x3C8CFF, 0xDCEFFF, 0x2F7DFF), 2, 3, false),
    ZAMRUD(180, FlamePalette.of(0x05875E, 0x1EDB98, 0xE3FFF3, 0x16C98A), 2, 4, false),
    EMAS(365, FlamePalette.of(0xC07D00, 0xFFC21A, 0xFFF7C9, 0xFFB800), 2, 4, true),
    PELANGI(730, FlamePalette.of(0xFF3D6E, 0xFFB000, 0xD9F7FF, 0xFF7A00), 2, 5, true);

    /** Hari pertama level ini. */
    final int min;
    private final FlamePalette palette;
    /** Lidah api tambahan di kiri (1) atau kiri-kanan (2). */
    final int tongues;
    /** Kepadatan percikan; 0 = tanpa percikan. */
    final int sparks;
    /** Bintang berkilau di sekitar api. */
    final boolean stars;

    StreakTier(int min, FlamePalette palette, int tongues, int sparks, boolean stars) {
        this.min = min;
        this.palette = palette;
        this.tongues = tongues;
        this.sparks = sparks;
        this.stars = stars;
    }

    static StreakTier of(int count) {
        var values = values();
        for (int i = values.length - 1; i > 0; i--) {
            if (count >= values[i].min) {
                return values[i];
            }
        }
        return BARA;
    }

    /** Warna di detik {@code t}; api pelangi berputar warna, level lain tetap. */
    FlamePalette palette(double t) {
        if (this != PELANGI) {
            return palette;
        }
        float h = (float) ((t * 70) % 360 / 360);
        return new FlamePalette(Color.getHSBColor(h, 0.88f, 0.98f), Color.getHSBColor((h + 0.125f) % 1, 0.75f, 1f),
                Color.getHSBColor((h + 0.25f) % 1, 0.18f, 1f), Color.getHSBColor(h, 0.85f, 1f));
    }

    String displayName() {
        return I18n.t("streak.tier." + name().toLowerCase(Locale.ROOT));
    }
}

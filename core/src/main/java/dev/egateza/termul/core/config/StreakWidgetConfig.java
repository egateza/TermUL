package dev.egateza.termul.core.config;

/**
 * Widget streak mengambang: window kecil yang selalu di depan, berisi api streak. Opt-in (config lama = tidak tampil).
 *
 * @param visible tampilkan widget
 * @param x       posisi kiri atas di layar (koordinat desktop virtual); null = belum pernah digeser (pojok kanan bawah
 *                layar utama)
 * @param y       lihat {@code x}; keduanya null atau keduanya terisi
 */
public record StreakWidgetConfig(Boolean visible, Integer x, Integer y) {

    public StreakWidgetConfig {
        visible = visible != null && visible;
        if (x == null || y == null) {
            x = null;
            y = null;
        }
    }

    public static StreakWidgetConfig defaults() {
        return new StreakWidgetConfig(false, null, null);
    }

    public boolean hasPosition() {
        return x != null;
    }

    public StreakWidgetConfig withVisible(boolean on) {
        return new StreakWidgetConfig(on, x, y);
    }

    public StreakWidgetConfig withPosition(int newX, int newY) {
        return new StreakWidgetConfig(visible, newX, newY);
    }
}

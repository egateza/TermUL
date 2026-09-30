package dev.egateza.termul.app.ui;

import dev.egateza.termul.core.config.AppConfig;

/** Mode terang/gelap sebuah {@link AppTheme}. Disimpan di {@code config.json} sebagai {@link #id}. */
public enum ThemeMode {
    LIGHT(AppConfig.MODE_LIGHT, "Terang"),
    DARK(AppConfig.MODE_DARK, "Gelap");

    private final String id;
    private final String label;

    ThemeMode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public ThemeMode other() {
        return this == LIGHT ? DARK : LIGHT;
    }

    /** @return mode dengan id tersebut, atau {@link #DARK} kalau kosong/tidak dikenal */
    public static ThemeMode fromId(String id) {
        return AppConfig.MODE_LIGHT.equals(id) ? LIGHT : DARK;
    }
}

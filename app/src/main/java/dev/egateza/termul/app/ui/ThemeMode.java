package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.config.AppConfig;

/** Mode terang/gelap sebuah {@link AppTheme}. Disimpan di {@code config.json} sebagai {@link #id}. */
public enum ThemeMode {
    LIGHT(AppConfig.MODE_LIGHT, "theme.mode.light"),
    DARK(AppConfig.MODE_DARK, "theme.mode.dark");

    private final String id;
    private final String labelKey;

    ThemeMode(String id, String labelKey) {
        this.id = id;
        this.labelKey = labelKey;
    }

    public String id() {
        return id;
    }

    public String label() {
        return I18n.t(labelKey);
    }

    public ThemeMode other() {
        return this == LIGHT ? DARK : LIGHT;
    }

    /** @return mode dengan id tersebut, atau {@link #DARK} kalau kosong/tidak dikenal */
    public static ThemeMode fromId(String id) {
        return AppConfig.MODE_LIGHT.equals(id) ? LIGHT : DARK;
    }
}

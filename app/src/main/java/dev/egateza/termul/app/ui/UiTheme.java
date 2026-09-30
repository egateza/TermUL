package dev.egateza.termul.app.ui;

import java.util.Set;

/** Tema UI yang bisa dipasang: bawaan ({@link AppTheme}) atau buatan user ({@link CustomUiTheme}). */
public interface UiTheme {

    /** Disimpan di {@code config.json}. */
    String id();

    String label();

    Set<ThemeMode> modes();

    default boolean supports(ThemeMode mode) {
        return modes().contains(mode);
    }

    /** @return mode yang benar-benar dipakai: {@code wanted} kalau didukung, kalau tidak mode satu-satunya tema ini */
    default ThemeMode effectiveMode(ThemeMode wanted) {
        return supports(wanted) ? wanted : wanted.other();
    }

    /** Pasang tema ini. Panggil di EDT; window yang sudah tampil perlu {@code FlatLaf.updateUI()}. */
    boolean install(ThemeMode wanted);
}

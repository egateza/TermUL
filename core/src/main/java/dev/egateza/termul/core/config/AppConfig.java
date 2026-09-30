package dev.egateza.termul.core.config;

/**
 * Preferensi aplikasi ({@code config.json}). Tidak berisi secret.
 *
 * @param editors           editor lokal per ekstensi
 * @param terminalFontSize  ukuran font terminal
 * @param iconSet           id set ikon UI (mis. {@code fontawesome}, {@code material}); diterjemahkan di modul app
 * @param hostButtonOpacity opasitas tombol melayang panel host, persen {@value #MIN_OPACITY}..100 (100 = solid)
 * @param hostPanelMode     {@value #HOST_DOCKED} = panel host di samping (tetap), {@value #HOST_FLOATING} = tombol
 *                          melayang yang menampilkan panel di atas terminal
 * @param theme             id tema UI (bawaan, mis. {@code default}, atau id tema custom); diterjemahkan di modul app
 * @param themeMode         {@value #MODE_LIGHT} atau {@value #MODE_DARK}; dipakai kalau tema mendukungnya
 * @param language          tag bahasa UI (mis. {@code id}, {@code en}); diterjemahkan di modul app
 * @param bellSound         bunyikan suara sistem saat terminal menerima BEL (null di config lama = aktif)
 * @param bellShake         getarkan layar saat terminal menerima BEL (null di config lama = aktif)
 * @param uiFontFamily      nama font aplikasi (menu, tabel, dialog); null = font bawaan tema
 * @param terminalFontFamily nama font terminal; null = pilihan otomatis (font monospace pertama yang terpasang)
 * @param terminalThemeLinked tema UI dan tema terminal satu paket: warna terminal mengikuti tema UI
 *                          (null di config lama = aktif)
 * @param terminalTheme     id tema custom untuk warna terminal, dipakai kalau {@code terminalThemeLinked} mati;
 *                          null = warna bawaan JediTerm
 * @param windowOpacity     opasitas seluruh window, persen {@value #MIN_WINDOW_OPACITY}..100 (100 = solid)
 */
public record AppConfig(EditorConfig editors, float terminalFontSize, String iconSet, int hostButtonOpacity,
                        String hostPanelMode, String theme, String themeMode, String language, Boolean bellSound,
                        Boolean bellShake, String uiFontFamily, String terminalFontFamily,
                        Boolean terminalThemeLinked, String terminalTheme, int windowOpacity) {

    public static final String HOST_DOCKED = "docked";
    public static final String HOST_FLOATING = "floating";

    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    public static final String DEFAULT_LANGUAGE = "id";
    public static final String DEFAULT_THEME = "default";
    public static final String DEFAULT_ICON_SET = "fontawesome";
    public static final int MIN_OPACITY = 10;
    public static final int DEFAULT_OPACITY = 100;
    public static final int MIN_WINDOW_OPACITY = 30;

    public AppConfig {
        if (editors == null) {
            editors = EditorConfig.defaults();
        }
        if (terminalFontSize < 6 || terminalFontSize > 48) {
            terminalFontSize = 14f;
        }
        if (iconSet == null || iconSet.isBlank()) {
            iconSet = DEFAULT_ICON_SET;
        }
        // config lama tanpa field ini terbaca 0 -> default
        if (hostButtonOpacity < MIN_OPACITY || hostButtonOpacity > 100) {
            hostButtonOpacity = DEFAULT_OPACITY;
        }
        if (!HOST_FLOATING.equals(hostPanelMode)) {
            hostPanelMode = HOST_DOCKED;
        }
        if (theme == null || theme.isBlank()) {
            theme = DEFAULT_THEME;
        }
        if (!MODE_LIGHT.equals(themeMode)) {
            themeMode = MODE_DARK; // tampilan sebelum ada pilihan tema
        }
        if (language == null || language.isBlank()) {
            language = DEFAULT_LANGUAGE; // config lama; UI sebelum ada pilihan bahasa berbahasa Indonesia
        }
        // Boolean (bukan boolean): field yang tidak ada di config lama terbaca null, bukan false
        bellSound = bellSound == null || bellSound;
        bellShake = bellShake == null || bellShake;
        uiFontFamily = uiFontFamily == null || uiFontFamily.isBlank() ? null : uiFontFamily.strip();
        terminalFontFamily = terminalFontFamily == null || terminalFontFamily.isBlank() ? null : terminalFontFamily.strip();
        terminalThemeLinked = terminalThemeLinked == null || terminalThemeLinked;
        terminalTheme = terminalTheme == null || terminalTheme.isBlank() ? null : terminalTheme.strip();
        // config lama tanpa field ini terbaca 0 -> solid
        if (windowOpacity < MIN_WINDOW_OPACITY || windowOpacity > 100) {
            windowOpacity = DEFAULT_OPACITY;
        }
    }

    public static AppConfig defaults() {
        return new AppConfig(EditorConfig.defaults(), 14f, DEFAULT_ICON_SET, DEFAULT_OPACITY, HOST_DOCKED,
                DEFAULT_THEME, MODE_DARK, DEFAULT_LANGUAGE, true, true, null, null, true, null, DEFAULT_OPACITY);
    }

    public AppConfig withEditors(EditorConfig e) {
        return new AppConfig(e, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity);
    }

    public AppConfig withIconSet(String id) {
        return new AppConfig(editors, terminalFontSize, id, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity);
    }

    public AppConfig withHostButtonOpacity(int percent) {
        return new AppConfig(editors, terminalFontSize, iconSet, percent, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity);
    }

    public AppConfig withHostPanelMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, mode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity);
    }

    public AppConfig withTheme(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, id, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity);
    }

    public AppConfig withThemeMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, mode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity);
    }

    public AppConfig withLanguage(String tag) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                tag, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity);
    }

    public AppConfig withBellSound(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, on, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity);
    }

    public AppConfig withBellShake(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, on, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity);
    }

    /** @param family nama font aplikasi, atau null untuk font bawaan tema */
    public AppConfig withUiFontFamily(String family) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, family, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity);
    }

    /** @param family nama font terminal, atau null untuk pilihan otomatis */
    public AppConfig withTerminalFontFamily(String family) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, family, terminalThemeLinked, terminalTheme, windowOpacity);
    }

    public AppConfig withTerminalThemeLinked(boolean linked) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, linked, terminalTheme, windowOpacity);
    }

    /** @param id id tema custom untuk warna terminal, atau null untuk warna bawaan */
    public AppConfig withTerminalTheme(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, id, windowOpacity);
    }

    public AppConfig withWindowOpacity(int percent) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                percent);
    }
}

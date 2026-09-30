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
 * @param theme             id tema UI (mis. {@code default}); diterjemahkan di modul app
 * @param themeMode         {@value #MODE_LIGHT} atau {@value #MODE_DARK}; dipakai kalau tema mendukungnya
 * @param language          tag bahasa UI (mis. {@code id}, {@code en}); diterjemahkan di modul app
 * @param bellSound         bunyikan suara sistem saat terminal menerima BEL (null di config lama = aktif)
 * @param bellShake         getarkan layar saat terminal menerima BEL (null di config lama = aktif)
 */
public record AppConfig(EditorConfig editors, float terminalFontSize, String iconSet, int hostButtonOpacity,
                        String hostPanelMode, String theme, String themeMode, String language, Boolean bellSound,
                        Boolean bellShake) {

    public static final String HOST_DOCKED = "docked";
    public static final String HOST_FLOATING = "floating";

    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    public static final String DEFAULT_LANGUAGE = "id";
    public static final String DEFAULT_THEME = "default";
    public static final String DEFAULT_ICON_SET = "fontawesome";
    public static final int MIN_OPACITY = 10;
    public static final int DEFAULT_OPACITY = 100;

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
    }

    public static AppConfig defaults() {
        return new AppConfig(EditorConfig.defaults(), 14f, DEFAULT_ICON_SET, DEFAULT_OPACITY, HOST_DOCKED,
                DEFAULT_THEME, MODE_DARK, DEFAULT_LANGUAGE, true, true);
    }

    public AppConfig withEditors(EditorConfig e) {
        return new AppConfig(e, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake);
    }

    public AppConfig withIconSet(String id) {
        return new AppConfig(editors, terminalFontSize, id, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake);
    }

    public AppConfig withHostButtonOpacity(int percent) {
        return new AppConfig(editors, terminalFontSize, iconSet, percent, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake);
    }

    public AppConfig withHostPanelMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, mode, theme, themeMode,
                language, bellSound, bellShake);
    }

    public AppConfig withTheme(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, id, themeMode,
                language, bellSound, bellShake);
    }

    public AppConfig withThemeMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, mode,
                language, bellSound, bellShake);
    }

    public AppConfig withLanguage(String tag) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                tag, bellSound, bellShake);
    }

    public AppConfig withBellSound(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, on, bellShake);
    }

    public AppConfig withBellShake(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, on);
    }
}

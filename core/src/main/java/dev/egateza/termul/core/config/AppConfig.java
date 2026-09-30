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
 */
public record AppConfig(EditorConfig editors, float terminalFontSize, String iconSet, int hostButtonOpacity,
                        String hostPanelMode) {

    public static final String HOST_DOCKED = "docked";
    public static final String HOST_FLOATING = "floating";

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
    }

    public static AppConfig defaults() {
        return new AppConfig(EditorConfig.defaults(), 14f, DEFAULT_ICON_SET, DEFAULT_OPACITY, HOST_DOCKED);
    }

    public AppConfig withEditors(EditorConfig e) {
        return new AppConfig(e, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode);
    }

    public AppConfig withIconSet(String id) {
        return new AppConfig(editors, terminalFontSize, id, hostButtonOpacity, hostPanelMode);
    }

    public AppConfig withHostButtonOpacity(int percent) {
        return new AppConfig(editors, terminalFontSize, iconSet, percent, hostPanelMode);
    }

    public AppConfig withHostPanelMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, mode);
    }
}

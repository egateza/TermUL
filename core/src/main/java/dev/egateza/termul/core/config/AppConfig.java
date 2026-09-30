package dev.egateza.termul.core.config;

/**
 * Preferensi aplikasi ({@code config.json}). Tidak berisi secret.
 *
 * @param editors          editor lokal per ekstensi
 * @param terminalFontSize ukuran font terminal
 * @param iconSet          id set ikon UI (mis. {@code fontawesome}, {@code material}); diterjemahkan di modul app
 */
public record AppConfig(EditorConfig editors, float terminalFontSize, String iconSet) {

    public static final String DEFAULT_ICON_SET = "fontawesome";

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
    }

    public static AppConfig defaults() {
        return new AppConfig(EditorConfig.defaults(), 14f, DEFAULT_ICON_SET);
    }

    public AppConfig withEditors(EditorConfig e) {
        return new AppConfig(e, terminalFontSize, iconSet);
    }

    public AppConfig withIconSet(String id) {
        return new AppConfig(editors, terminalFontSize, id);
    }
}

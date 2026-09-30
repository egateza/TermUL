package dev.egateza.termul.core.config;

/**
 * Preferensi aplikasi ({@code config.json}). Tidak berisi secret.
 *
 * @param editors          editor lokal per ekstensi
 * @param terminalFontSize ukuran font terminal
 */
public record AppConfig(EditorConfig editors, float terminalFontSize) {

    public AppConfig {
        if (editors == null) {
            editors = EditorConfig.defaults();
        }
        if (terminalFontSize < 6 || terminalFontSize > 48) {
            terminalFontSize = 14f;
        }
    }

    public static AppConfig defaults() {
        return new AppConfig(EditorConfig.defaults(), 14f);
    }

    public AppConfig withEditors(EditorConfig e) {
        return new AppConfig(e, terminalFontSize);
    }
}

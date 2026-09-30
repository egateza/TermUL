package dev.egateza.termul.app.ui;

import dev.egateza.termul.core.config.AppConfig;
import java.awt.IllegalComponentStateException;
import java.awt.Window;

/** Transparansi seluruh window (teks ikut pudar). Hanya bisa untuk window tanpa dekorasi native. */
final class WindowOpacity {

    private WindowOpacity() {
    }

    /** @return persen yang dibatasi ke {@link AppConfig#MIN_WINDOW_OPACITY}..100 */
    static int clamp(int percent) {
        return Math.max(AppConfig.MIN_WINDOW_OPACITY, Math.min(100, percent));
    }

    /**
     * @return true kalau diterapkan; false kalau window masih berdekorasi native (Java menolak transparansi di sana,
     *         kecuali 100%) atau sistem tidak mendukung jendela tembus pandang
     */
    static boolean apply(Window window, int percent) {
        try {
            window.setOpacity(clamp(percent) / 100f);
            return true;
        } catch (IllegalComponentStateException | UnsupportedOperationException e) {
            return false;
        }
    }
}

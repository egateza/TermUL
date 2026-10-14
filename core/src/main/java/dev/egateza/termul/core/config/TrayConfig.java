package dev.egateza.termul.core.config;

/**
 * Ikon tray (notification area Windows / menu bar macOS) dan perilaku window terhadapnya. Opt-in: config lama tanpa
 * field ini = semua mati, jadi tombol tutup tetap keluar dari aplikasi.
 *
 * <p>Run at startup tidak disimpan di sini: sumber kebenarannya entri OS (registry {@code Run} / LaunchAgent),
 * supaya tidak berbeda dengan yang dilihat user di pengaturan OS.
 *
 * @param enabled        tampilkan ikon tray
 * @param closeToTray    tombol tutup (X) menyembunyikan window ke tray, bukan keluar; hanya berlaku kalau
 *                       {@code enabled}
 * @param minimizeToTray minimize menyembunyikan window ke tray (hilang dari taskbar); hanya berlaku kalau
 *                       {@code enabled}
 */
public record TrayConfig(Boolean enabled, Boolean closeToTray, Boolean minimizeToTray) {

    public TrayConfig {
        // Boolean (bukan boolean): field yang tidak ada di config lama terbaca null -> mati
        enabled = enabled != null && enabled;
        closeToTray = closeToTray != null && closeToTray;
        minimizeToTray = minimizeToTray != null && minimizeToTray;
    }

    public static TrayConfig defaults() {
        return new TrayConfig(false, false, false);
    }

    /** Tombol tutup menyembunyikan ke tray (ikon tray aktif dan opsinya dicentang). */
    public boolean hidesOnClose() {
        return enabled && closeToTray;
    }

    /** Minimize menyembunyikan ke tray (ikon tray aktif dan opsinya dicentang). */
    public boolean hidesOnMinimize() {
        return enabled && minimizeToTray;
    }

    public TrayConfig withEnabled(boolean on) {
        return new TrayConfig(on, closeToTray, minimizeToTray);
    }

    public TrayConfig withCloseToTray(boolean on) {
        return new TrayConfig(enabled, on, minimizeToTray);
    }

    public TrayConfig withMinimizeToTray(boolean on) {
        return new TrayConfig(enabled, closeToTray, on);
    }
}

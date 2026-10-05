package dev.egateza.termul.app.ui;

import dev.egateza.termul.core.Os;
import java.awt.Desktop;
import java.awt.Taskbar;
import java.util.Comparator;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integrasi macOS: nama aplikasi di menu aplikasi, About/Quit di menu itu (Cmd+Q), dan ikon di Dock.
 * Di OS lain semua method tidak melakukan apa-apa.
 *
 * <p>Menu bar tetap di dalam window (bukan di bar atas layar), karena menu bar TermUL berisi komponen yang bukan
 * menu (tombol mode terang/gelap) yang tidak ditampilkan di menu bar layar macOS.
 */
public final class MacIntegration {

    private static final Logger log = LoggerFactory.getLogger(MacIntegration.class);

    private MacIntegration() {
    }

    /** Harus dipanggil di awal {@code main}, sebelum AWT/Swing dimuat (property dibaca saat AWT start). */
    public static void configureBeforeAwt() {
        if (!Os.current().isMac()) {
            return;
        }
        System.setProperty("apple.awt.application.name", "TermUL");
        System.setProperty("apple.awt.application.appearance", "system"); // title bar mengikuti mode terang/gelap
    }

    /** Pasang handler menu aplikasi dan ikon Dock. EDT. */
    public static void install(MainFrame frame) {
        if (!Os.current().isMac()) {
            return;
        }
        if (Desktop.isDesktopSupported()) {
            var desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
                desktop.setAboutHandler(e -> SwingUtilities.invokeLater(frame::showAbout));
            }
            if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                // Quit native selalu dibatalkan; alur keluar aplikasi (konfirmasi, tutup sesi) yang memanggil exit
                desktop.setQuitHandler((e, response) -> {
                    response.cancelQuit();
                    SwingUtilities.invokeLater(frame::exit);
                });
            }
        }
        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
            MainFrame.appIcons().stream()
                    .max(Comparator.comparingInt(img -> img.getWidth(null)))
                    .ifPresent(img -> {
                        try {
                            Taskbar.getTaskbar().setIconImage(img);
                        } catch (UnsupportedOperationException | SecurityException e) {
                            log.debug("Ikon Dock tidak bisa dipasang: {}", e.toString());
                        }
                    });
        }
    }
}

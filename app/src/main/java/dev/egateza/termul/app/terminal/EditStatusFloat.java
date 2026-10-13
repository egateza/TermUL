package dev.egateza.termul.app.terminal;

import dev.egateza.termul.app.sftp.ActivityBar;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.fab.FloatingButton;
import dev.egateza.termul.app.ui.fab.FloatingOverlay;
import dev.egateza.termul.app.ui.fab.FloatingStyle;
import javax.swing.Timer;

/**
 * Status remote edit host ini di atas terminal (kanan bawah): mengunduh/mengunggah, berhasil, atau perlu perhatian.
 * Status biasa hilang sendiri; konflik dan gagal tetap tampil sampai diklik, lalu membuka daftar file yang diedit.
 * Teks sama dengan baris aktivitas panel SFTP. EDT.
 */
final class EditStatusFloat {

    static final int MAX_TEXT = 48;
    static final int INFO_MS = 6_000;
    static final int SUCCESS_MS = 4_000;

    /** Cara menampilkan satu laporan aktivitas. */
    record Look(FloatingStyle.Kind kind, AppIcon icon, int hideAfterMs, boolean opensTracker) {

        static Look of(ActivityBar.Level level) {
            return switch (level) {
                case INFO -> new Look(FloatingStyle.Kind.SECONDARY, AppIcon.UPLOAD, INFO_MS, false);
                case SUCCESS -> new Look(FloatingStyle.Kind.SECONDARY, AppIcon.UPLOAD, SUCCESS_MS, false);
                case WARN -> new Look(FloatingStyle.Kind.WARNING, AppIcon.INFO, 0, true);
                case ERROR -> new Look(FloatingStyle.Kind.DANGER, AppIcon.INFO, 0, true);
            };
        }
    }

    final FloatingButton button = new FloatingButton(AppIcon.UPLOAD, "", FloatingStyle.Kind.SECONDARY,
            FloatingButton.Size.MINI);
    private final FloatingOverlay overlay;
    private final Timer hide = new Timer(INFO_MS, e -> setShown(false));
    private boolean opensTracker; // EDT

    /** @param openTracker membuka daftar file yang sedang diedit (Ctrl+Shift+E) */
    EditStatusFloat(FloatingOverlay overlay, Runnable openTracker) {
        this.overlay = overlay;
        hide.setRepeats(false);
        button.setVisible(false);
        button.addActionListener(e -> {
            setShown(false);
            if (opensTracker) {
                openTracker.run();
            }
        });
        overlay.addFloating(button, FloatingOverlay.Anchor.BOTTOM_RIGHT);
    }

    void report(ActivityBar.Level level, String text) {
        Look look = Look.of(level);
        opensTracker = look.opensTracker();
        button.setKind(look.kind());
        button.setAppIcon(look.icon());
        button.setText(shorten(text));
        button.setToolTipText(text);
        setShown(true);
        hide.stop();
        if (look.hideAfterMs() > 0) {
            hide.setInitialDelay(look.hideAfterMs());
            hide.restart();
        }
    }

    static String shorten(String text) {
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= MAX_TEXT ? oneLine : oneLine.substring(0, MAX_TEXT - 1) + "…";
    }

    private void setShown(boolean shown) {
        if (button.isVisible() != shown) {
            button.setVisible(shown);
            overlay.revalidate();
            overlay.repaint();
        }
    }

    void dispose() {
        hide.stop();
        overlay.removeFloating(button);
    }
}

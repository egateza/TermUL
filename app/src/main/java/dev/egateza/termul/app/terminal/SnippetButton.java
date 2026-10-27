package dev.egateza.termul.app.terminal;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.snippet.SnippetQuickPick;
import dev.egateza.termul.app.snippet.SnippetSender;
import dev.egateza.termul.app.snippet.SnippetTarget;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.ShortcutAction;
import dev.egateza.termul.app.ui.Shortcuts;
import dev.egateza.termul.app.ui.fab.FloatingButton;
import dev.egateza.termul.app.ui.fab.FloatingOverlay;
import dev.egateza.termul.app.ui.fab.FloatingStyle;
import dev.egateza.termul.core.config.AppConfig;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Tombol mengambang "Snippet" di atas terminal. Klik membuka menu snippet untuk host ini ({@link SnippetQuickPick});
 * hasil tempel/salin tampil sebentar di dekat tombol. Tombol bisa diseret dan menempel ke sudut terdekat (disimpan di
 * config untuk semua tab), supaya tidak menutupi bagian output yang sedang dibaca. EDT.
 */
final class SnippetButton {

    static final int TOAST_MS = 2600;

    /**
     * Geseran minimum (per sumbu) sebelum tekan-lepas dianggap seret, bukan klik. Ambang lama (5 px, dx+dy) terlalu
     * kecil: klik biasa dengan touchpad / layar ber-scaling sering bergeser sejauh itu, sehingga menu tidak terbuka.
     */
    static final int DRAG_THRESHOLD = 8;
    /** Seret yang berakhir di sudut asal dengan geseran sekecil ini dianggap klik (menu tetap dibuka). */
    static final int CLICK_SLOP = 24;

    /** Pil "⚡ Snippet" seperti prototipe; petir kuning, badan tombol mengikuti gaya tombol sekunder tema. */
    final FloatingButton button = new FloatingButton(AppIcon.BOLT, I18n.t("fab.snippet"),
            FloatingStyle.Kind.SECONDARY, FloatingButton.Size.MINI);
    private final FloatingOverlay overlay;
    private final SnippetTarget target;
    private final SnippetSender sender;
    private final Runnable manage;
    private final Consumer<String> cornerChanged;
    private Point pressOnScreen;  // EDT; null = tidak sedang ditekan
    private Point pressTopLeft;   // EDT; posisi tombol saat ditekan, koordinat overlay
    private boolean dragging;     // EDT; true sejak seret dimulai sampai setelah klik dilepas diproses
    private JLabel toast;         // EDT; pesan hasil yang sedang tampil
    private Timer toastTimer;     // EDT

    /**
     * @param target        terminal tab ini
     * @param manage        buka Snippet manager
     * @param cornerChanged sudut baru (nilai {@code AppConfig.SNIPPET_*}) setelah tombol diseret
     */
    SnippetButton(FloatingOverlay overlay, String corner, SnippetTarget target, SnippetSender sender, Runnable manage,
                  Consumer<String> cornerChanged) {
        this.overlay = overlay;
        this.target = target;
        this.sender = sender;
        this.manage = manage;
        this.cornerChanged = cornerChanged;
        button.setIcon(AppIcon.BOLT.icon(14, () -> new Color(0xE0AF68)));
        button.setToolTipText(I18n.t("fab.snippet.tooltip", Shortcuts.text(ShortcutAction.SNIPPET_QUICK)));
        button.getAccessibleContext().setAccessibleName(I18n.t("fab.snippet"));
        button.addActionListener(e -> {
            if (!dragging) {
                showMenu();
            }
        });
        var drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    pressOnScreen = e.getLocationOnScreen();
                    pressTopLeft = button.getLocation();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressOnScreen == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                int dx = now.x - pressOnScreen.x;
                int dy = now.y - pressOnScreen.y;
                if (!dragging && !isDrag(dx, dy, DRAG_THRESHOLD)) {
                    return;
                }
                dragging = true;
                overlay.setDragPosition(button, new Point(pressTopLeft.x + dx, pressTopLeft.y + dy));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Point press = pressOnScreen;
                pressOnScreen = null;
                if (!dragging) {
                    return;
                }
                var b = button.getBounds();
                var from = overlay.anchorOf(button);
                var anchor = overlay.nearestCorner(new Point(b.x + b.width / 2, b.y + b.height / 2));
                overlay.setAnchor(button, anchor);
                Point now = e.getLocationOnScreen();
                if (anchor == from && press != null && !isDrag(now.x - press.x, now.y - press.y, CLICK_SLOP)) {
                    // hanya tergeser sedikit dan kembali ke sudut yang sama: maksud user adalah klik
                    SwingUtilities.invokeLater(() -> {
                        dragging = false;
                        showMenu();
                    });
                    return;
                }
                if (anchor != from) {
                    SnippetButton.this.cornerChanged.accept(cornerOf(anchor));
                }
                // klik yang ikut terpicu oleh lepasnya tombol tidak boleh membuka menu (urutan listener bisa berubah
                // setelah ganti tema), jadi tanda seret baru dihapus setelah event ini selesai
                SwingUtilities.invokeLater(() -> dragging = false);
            }
        };
        button.addMouseListener(drag);
        button.addMouseMotionListener(drag);
        overlay.addFloating(button, anchorOf(corner));
    }

    /** Sudut dari config (mis. diubah dari tab lain). */
    void setCorner(String corner) {
        var anchor = anchorOf(corner);
        if (overlay.anchorOf(button) != anchor && !dragging) {
            overlay.setAnchor(button, anchor);
        }
    }

    void setShown(boolean shown) {
        if (button.isVisible() != shown) {
            button.setVisible(shown);
            overlay.revalidate();
            overlay.repaint();
        }
    }

    /** Buka menu snippet di dekat tombol; kalau tombol disembunyikan, di sudut kanan atas terminal. */
    void showMenu() {
        if (!overlay.isShowing()) {
            return;
        }
        if (!button.isVisible()) {
            SnippetQuickPick.show(overlay, overlay.getWidth() - 16, 8, true, false, target, sender, manage,
                    this::showToast);
            return;
        }
        var anchor = overlay.anchorOf(button);
        boolean top = anchor == FloatingOverlay.Anchor.TOP_LEFT || anchor == FloatingOverlay.Anchor.TOP_RIGHT;
        boolean right = anchor == FloatingOverlay.Anchor.TOP_RIGHT || anchor == FloatingOverlay.Anchor.BOTTOM_RIGHT;
        SnippetQuickPick.show(button, right ? button.getWidth() : 0, top ? button.getHeight() + 4 : -4, right, !top,
                target, sender, manage, this::showToast);
    }

    /** Pesan hasil di dekat tombol (atau sudut kanan atas), hilang sendiri setelah {@link #TOAST_MS}. */
    void showToast(String message) {
        hideToast();
        toast = new Toast(message);
        var anchor = button.isVisible() ? overlay.anchorOf(button) : FloatingOverlay.Anchor.TOP_RIGHT;
        overlay.addFloating(toast, anchor);
        overlay.revalidate();
        overlay.repaint();
        toastTimer = new Timer(TOAST_MS, e -> hideToast());
        toastTimer.setRepeats(false);
        toastTimer.start();
    }

    private void hideToast() {
        if (toastTimer != null) {
            toastTimer.stop();
            toastTimer = null;
        }
        if (toast != null) {
            overlay.removeFloating(toast);
            toast = null;
        }
    }

    /** Label membulat dengan tanda centang hijau. */
    private static final class Toast extends JLabel {
        private static final Color OK = new Color(0x3DD68C);

        Toast(String text) {
            super("✓ " + text);
            setForeground(OK);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = UIManager.getColor("Panel.background");
            g2.setColor(bg != null ? bg : Color.DARK_GRAY);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.setColor(OK.darker());
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** true kalau geseran (dx, dy) melewati {@code threshold} di salah satu sumbu. */
    static boolean isDrag(int dx, int dy, int threshold) {
        return Math.abs(dx) >= threshold || Math.abs(dy) >= threshold;
    }

    static FloatingOverlay.Anchor anchorOf(String corner) {
        return switch (corner == null ? "" : corner) {
            case AppConfig.SNIPPET_TOP_LEFT -> FloatingOverlay.Anchor.TOP_LEFT;
            case AppConfig.SNIPPET_BOTTOM_LEFT -> FloatingOverlay.Anchor.BOTTOM_LEFT;
            case AppConfig.SNIPPET_BOTTOM_RIGHT -> FloatingOverlay.Anchor.BOTTOM_RIGHT;
            default -> FloatingOverlay.Anchor.TOP_RIGHT;
        };
    }

    static String cornerOf(FloatingOverlay.Anchor anchor) {
        return switch (anchor) {
            case TOP_LEFT -> AppConfig.SNIPPET_TOP_LEFT;
            case BOTTOM_LEFT -> AppConfig.SNIPPET_BOTTOM_LEFT;
            case BOTTOM_RIGHT, BOTTOM_CENTER -> AppConfig.SNIPPET_BOTTOM_RIGHT;
            case TOP_RIGHT -> AppConfig.SNIPPET_TOP_RIGHT;
        };
    }

    void dispose() {
        hideToast();
        overlay.removeFloating(button);
    }
}

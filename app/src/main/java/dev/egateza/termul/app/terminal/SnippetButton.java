package dev.egateza.termul.app.terminal;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.fab.FloatingButton;
import dev.egateza.termul.app.ui.fab.FloatingOverlay;
import dev.egateza.termul.app.ui.fab.FloatingStyle;
import dev.egateza.termul.core.config.AppConfig;
import dev.egateza.termul.core.snippet.Snippet;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * Tombol mengambang "Snippet" di atas terminal. Klik membuka daftar snippet untuk host ini; snippet yang dipilih
 * diketik ke prompt <b>tanpa Enter</b>. Tombol bisa diseret dan menempel ke sudut terdekat (disimpan di config untuk
 * semua tab), supaya tidak menutupi bagian output yang sedang dibaca. EDT.
 */
final class SnippetButton {

    /** Jarak seret minimum sebelum dianggap seret, bukan klik. */
    static final int DRAG_THRESHOLD = 5;

    final FloatingButton button = new FloatingButton(AppIcon.BOLT, null, FloatingStyle.Kind.PRIMARY,
            FloatingButton.Size.MINI);
    private final FloatingOverlay overlay;
    private final Supplier<List<Snippet>> snippets;
    private final Consumer<String> type;
    private final Runnable manage;
    private final Consumer<String> cornerChanged;
    private Point pressOnScreen;  // EDT; null = tidak sedang ditekan
    private Point pressTopLeft;   // EDT; posisi tombol saat ditekan, koordinat overlay
    private boolean dragging;     // EDT; true sejak seret dimulai sampai setelah klik dilepas diproses

    /**
     * @param snippets      snippet untuk host tab ini (dibaca saat tombol diklik)
     * @param type          ketik teks ke terminal tanpa Enter
     * @param manage        buka dialog kelola snippet
     * @param cornerChanged sudut baru (nilai {@code AppConfig.SNIPPET_*}) setelah tombol diseret
     */
    SnippetButton(FloatingOverlay overlay, String corner, Supplier<List<Snippet>> snippets, Consumer<String> type,
                  Runnable manage, Consumer<String> cornerChanged) {
        this.overlay = overlay;
        this.snippets = snippets;
        this.type = type;
        this.manage = manage;
        this.cornerChanged = cornerChanged;
        button.setToolTipText(I18n.t("fab.snippet.tooltip"));
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
                if (!dragging && Math.abs(dx) + Math.abs(dy) < DRAG_THRESHOLD) {
                    return;
                }
                dragging = true;
                overlay.setDragPosition(button, new Point(pressTopLeft.x + dx, pressTopLeft.y + dy));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressOnScreen = null;
                if (!dragging) {
                    return;
                }
                var b = button.getBounds();
                var anchor = overlay.nearestCorner(new Point(b.x + b.width / 2, b.y + b.height / 2));
                overlay.setAnchor(button, anchor);
                SnippetButton.this.cornerChanged.accept(cornerOf(anchor));
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

    private void showMenu() {
        var menu = new JPopupMenu();
        menu.setFocusable(false);
        List<Snippet> list = snippets.get();
        if (list.isEmpty()) {
            var empty = new JMenuItem(I18n.t("fab.snippet.empty"));
            empty.setEnabled(false);
            menu.add(empty);
        }
        for (Snippet s : list) {
            var item = new JMenuItem(s.name());
            item.setToolTipText(s.command());
            item.addActionListener(e -> type.accept(s.command()));
            menu.add(item);
        }
        menu.addSeparator();
        var manageItem = new JMenuItem(I18n.t("fab.snippet.manage"));
        manageItem.addActionListener(e -> manage.run());
        menu.add(manageItem);
        var size = menu.getPreferredSize();
        boolean top = overlay.anchorOf(button) == FloatingOverlay.Anchor.TOP_LEFT
                || overlay.anchorOf(button) == FloatingOverlay.Anchor.TOP_RIGHT;
        boolean right = overlay.anchorOf(button) == FloatingOverlay.Anchor.TOP_RIGHT
                || overlay.anchorOf(button) == FloatingOverlay.Anchor.BOTTOM_RIGHT;
        int x = right ? button.getWidth() - size.width : 0;
        int y = top ? button.getHeight() + 4 : -size.height - 4;
        menu.show(button, x, y);
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
        overlay.removeFloating(button);
    }
}

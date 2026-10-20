package dev.egateza.termul.app.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Drag tab untuk mengubah urutan. Selama drag hanya garis penanda posisi yang bergerak; tab baru dipindah saat mouse
 * dilepas, supaya panel terminal tidak di-reparent berkali-kali. Ctrl/⌘+klik (tandai untuk digabung) dan klik kanan
 * tidak memulai drag. Semua di EDT.
 */
public final class TabDragSupport extends MouseAdapter {

    /** Memindahkan tab {@code from} ke indeks {@code to} (indeks setelah tab itu dilepas dari posisi lamanya). */
    public interface Mover {
        void move(int from, int to);
    }

    /** Jarak gerak minimal (px) sebelum dianggap drag, supaya klik biasa tidak tergeser. */
    static final int THRESHOLD = 6;
    private static final int MARKER_WIDTH = 2;

    private final JTabbedPane tabs;
    private final Mover mover;
    private final Marker marker = new Marker();
    private int from = -1;
    private Point pressPoint;
    private boolean dragging;

    private TabDragSupport(JTabbedPane tabs, Mover mover) {
        this.tabs = tabs;
        this.mover = mover;
    }

    /** Pasang di {@code tabs}: event area tab (ScrollableTabPanel) diteruskan Swing ke listener JTabbedPane. */
    public static void install(JTabbedPane tabs, Mover mover) {
        var support = new TabDragSupport(tabs, mover);
        tabs.addMouseListener(support);
        tabs.addMouseMotionListener(support);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        reset();
        if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger() && !Shortcuts.isMenuDown(e)
                && tabs.getTabCount() > 1) {
            from = tabs.indexAtLocation(e.getX(), e.getY());
            pressPoint = e.getPoint();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (from < 0) {
            return;
        }
        if (!dragging && e.getPoint().distance(pressPoint) < THRESHOLD) {
            return;
        }
        dragging = true;
        tabs.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        showMarker(insertionIndex(tabBounds(), e.getX()));
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        int source = from;
        boolean dropped = dragging;
        reset();
        if (!dropped || source >= tabs.getTabCount()) {
            return;
        }
        int to = destination(source, insertionIndex(tabBounds(), e.getX()));
        if (to >= 0) {
            mover.move(source, to);
        }
    }

    private void reset() {
        from = -1;
        pressPoint = null;
        dragging = false;
        tabs.setCursor(null);
        hideMarker();
    }

    /** Batas tab yang sedang terlihat, dalam koordinat {@code tabs}; null untuk tab yang tergulir keluar. */
    private List<Rectangle> tabBounds() {
        var result = new ArrayList<Rectangle>(tabs.getTabCount());
        var visible = tabs.getVisibleRect();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            var r = tabs.getBoundsAt(i);
            result.add(r != null && r.intersects(visible) ? r : null);
        }
        return result;
    }

    /**
     * Posisi sisip (0..n) untuk titik x: sebelum tab pertama yang titik tengahnya di kanan x. Tab yang tidak
     * terlihat ({@code null}) dilewati.
     */
    static int insertionIndex(List<Rectangle> bounds, int x) {
        int last = -1;
        for (int i = 0; i < bounds.size(); i++) {
            var r = bounds.get(i);
            if (r == null) {
                continue;
            }
            if (x < r.x + r.width / 2) {
                return i;
            }
            last = i;
        }
        return last + 1;
    }

    /**
     * Indeks tujuan setelah tab {@code from} dilepas dari posisinya, untuk posisi sisip {@code insertion}; -1 kalau
     * posisinya tidak berubah (dilepas tepat di kiri atau kanan dirinya sendiri).
     */
    static int destination(int from, int insertion) {
        if (insertion == from || insertion == from + 1) {
            return -1;
        }
        return insertion > from ? insertion - 1 : insertion;
    }

    private void showMarker(int insertion) {
        var bounds = tabBounds();
        Rectangle ref = null;
        boolean after = false;
        if (insertion < bounds.size()) {
            ref = bounds.get(insertion);
        }
        if (ref == null) { // di ujung kanan: pakai tepi kanan tab terlihat terakhir
            for (int i = Math.min(insertion, bounds.size()) - 1; i >= 0 && ref == null; i--) {
                ref = bounds.get(i);
            }
            after = true;
        }
        var layered = SwingUtilities.getRootPane(tabs) == null ? null : SwingUtilities.getRootPane(tabs).getLayeredPane();
        if (ref == null || layered == null) {
            hideMarker();
            return;
        }
        int x = (after ? ref.x + ref.width : ref.x) - MARKER_WIDTH / 2;
        var p = SwingUtilities.convertPoint(tabs, x, ref.y, layered);
        if (marker.getParent() != layered) {
            layered.add(marker, JLayeredPane.DRAG_LAYER);
        }
        marker.setBounds(p.x, p.y, MARKER_WIDTH, ref.height);
        marker.setVisible(true);
        marker.repaint();
    }

    private void hideMarker() {
        var parent = marker.getParent();
        if (parent != null) {
            var r = marker.getBounds();
            parent.remove(marker);
            parent.repaint(r.x, r.y, r.width, r.height);
        }
    }

    /** Garis vertikal posisi sisip, warna aksen tema. */
    private static final class Marker extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            Color accent = UIManager.getColor("Component.accentColor");
            g.setColor(accent != null ? accent : UIManager.getColor("Component.focusColor"));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}

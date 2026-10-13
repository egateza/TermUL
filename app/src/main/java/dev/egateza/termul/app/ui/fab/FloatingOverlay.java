package dev.egateza.termul.app.ui.fab;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Panel berisi satu konten ({@link #setBase}) yang memenuhi seluruh area, dengan tombol mengambang di atasnya pada
 * posisi tetap ({@link Anchor}). Tombol yang tersembunyi tidak memakan tempat; beberapa tombol di anchor yang sama
 * ditumpuk ke atas. Klik di luar badan tombol tetap sampai ke konten. EDT.
 */
public final class FloatingOverlay extends JPanel {

    public enum Anchor {
        BOTTOM_RIGHT,
        BOTTOM_CENTER,
        BOTTOM_LEFT,
        TOP_LEFT,
        TOP_RIGHT;

        boolean top() {
            return this == TOP_LEFT || this == TOP_RIGHT;
        }
    }

    static final int GAP = 10;

    private Component base;
    private final Map<JComponent, Anchor> floats = new LinkedHashMap<>();
    /** Posisi sementara selama tombol diseret (kiri atas, koordinat panel ini). */
    private final Map<JComponent, java.awt.Point> dragged = new java.util.HashMap<>();
    private int marginRight = 16;
    private int marginBottom = 16;
    private int marginLeft = 16;
    private int marginTop = 16;

    public FloatingOverlay() {
        super(null);
        setOpaque(false);
    }

    /** Ganti konten di bawah tombol (mis. layar loading → terminal). */
    public void setBase(Component c) {
        if (base == c) {
            return;
        }
        if (base != null) {
            remove(base);
        }
        base = c;
        if (c != null) {
            add(c); // indeks terakhir = digambar paling bawah, tombol tetap di atasnya
        }
        revalidate();
        repaint();
    }

    public Component base() {
        return base;
    }

    public void addFloating(JComponent c, Anchor anchor) {
        floats.put(c, anchor);
        add(c, 0);
        revalidate();
    }

    /** Pindahkan tombol ke anchor lain (mis. setelah diseret ke sudut lain). */
    public void setAnchor(JComponent c, Anchor anchor) {
        if (floats.containsKey(c)) {
            floats.put(c, anchor);
            dragged.remove(c);
            revalidate();
            repaint();
        }
    }

    public Anchor anchorOf(JComponent c) {
        return floats.get(c);
    }

    /** Selama diseret: tombol mengikuti pointer (kiri atas); null = kembali ke anchor-nya. */
    public void setDragPosition(JComponent c, java.awt.Point topLeft) {
        if (topLeft == null) {
            dragged.remove(c);
        } else {
            dragged.put(c, topLeft);
        }
        revalidate();
        repaint();
    }

    /** Sudut terdekat dari titik tengah tombol (untuk menempelkan tombol setelah diseret). */
    public Anchor nearestCorner(java.awt.Point center) {
        boolean left = center.x < getWidth() / 2;
        boolean top = center.y < getHeight() / 2;
        return top ? (left ? Anchor.TOP_LEFT : Anchor.TOP_RIGHT) : (left ? Anchor.BOTTOM_LEFT : Anchor.BOTTOM_RIGHT);
    }

    public void removeFloating(JComponent c) {
        dragged.remove(c);
        if (floats.remove(c) != null) {
            remove(c);
            revalidate();
            repaint();
        }
    }

    /** Jarak tombol dari tepi kanan dan bawah (mis. lebih lebar di terminal supaya tidak menutupi scrollbar). */
    public void setMargins(int right, int bottom) {
        marginRight = right;
        marginBottom = bottom;
        revalidate();
    }

    /** Jarak tombol dari tepi kiri dan atas. */
    public void setLeadingMargins(int left, int top) {
        marginLeft = left;
        marginTop = top;
        revalidate();
    }

    /** Tombol bisa bertumpuk dengan konten: jangan pakai optimasi gambar yang mengandaikan anak tidak tumpang tindih. */
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    @Override
    public void doLayout() {
        int w = getWidth();
        int h = getHeight();
        if (base != null) {
            base.setBounds(0, 0, w, h);
        }
        floats.forEach((c, anchor) -> c.setBounds(place(c, anchor, w, h)));
    }

    /** Posisi tombol; yang lebih dulu ditambahkan ada paling bawah di tumpukan anchor-nya. */
    Rectangle place(JComponent c, Anchor anchor, int w, int h) {
        if (!c.isVisible()) {
            return new Rectangle();
        }
        Dimension size = c.getPreferredSize();
        var drag = dragged.get(c);
        if (drag != null) {
            int x = Math.max(0, Math.min(w - size.width, drag.x));
            int y = Math.max(0, Math.min(h - size.height, drag.y));
            return new Rectangle(x, y, size.width, size.height);
        }
        int stacked = 0; // tinggi tombol lain di anchor yang sama yang ditambahkan lebih dulu
        for (var e : floats.entrySet()) {
            if (e.getKey() == c) {
                break;
            }
            if (e.getValue() == anchor && e.getKey().isVisible() && !dragged.containsKey(e.getKey())) {
                stacked += e.getKey().getPreferredSize().height + GAP;
            }
        }
        int x = switch (anchor) {
            case BOTTOM_RIGHT, TOP_RIGHT -> w - marginRight - size.width;
            case BOTTOM_LEFT, TOP_LEFT -> marginLeft;
            case BOTTOM_CENTER -> (w - size.width) / 2;
        };
        int y = anchor.top() ? marginTop + stacked : h - marginBottom - stacked - size.height;
        return new Rectangle(Math.max(0, x), Math.max(0, y), size.width, size.height);
    }

    @Override
    public Dimension getPreferredSize() {
        return base == null ? super.getPreferredSize() : base.getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        return base == null ? super.getMinimumSize() : base.getMinimumSize();
    }
}

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
        BOTTOM_CENTER
    }

    static final int GAP = 10;

    private Component base;
    private final Map<JComponent, Anchor> floats = new LinkedHashMap<>();
    private int marginRight = 16;
    private int marginBottom = 16;

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

    public void removeFloating(JComponent c) {
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
        int bottom = h - marginBottom;
        for (var e : floats.entrySet()) {
            if (e.getKey() == c) {
                break;
            }
            if (e.getValue() == anchor && e.getKey().isVisible()) {
                bottom -= e.getKey().getPreferredSize().height + GAP;
            }
        }
        int x = anchor == Anchor.BOTTOM_RIGHT ? w - marginRight - size.width : (w - size.width) / 2;
        return new Rectangle(Math.max(0, x), Math.max(0, bottom - size.height), size.width, size.height);
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

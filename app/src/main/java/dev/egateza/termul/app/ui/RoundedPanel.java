package dev.egateza.termul.app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * Panel pembungkus yang bisa tampil sebagai "kartu" bersudut membulat: ada jarak di sekeliling isi, lalu sudut isi
 * ditimpa warna latar panel (antialias) dan diberi garis tepi tipis. Swing tidak bisa meng-clip anak komponen dengan
 * bentuk antialias, jadi sudutnya ditutup setelah anak-anaknya digambar.
 *
 * <p>Saat membulat, panel ini menjadi painting origin: repaint dari anak (mis. JediTerm yang menggambar ulang kursor
 * atau output) selalu dimulai dari panel ini, sehingga penutup sudut tidak tertimpa. Dalam mode kotak panel ini
 * tidak menambah apa pun. Semua method dipanggil di EDT.
 */
public final class RoundedPanel extends JPanel {

    /** Diameter lengkungan sudut, px. */
    static final int ARC = 14;

    private boolean rounded;
    private Insets gaps = new Insets(0, 0, 0, 0);
    private Area mask; // cache penutup sudut untuk ukuran terakhir; null = hitung ulang
    private int maskW = -1;
    private int maskH = -1;

    public RoundedPanel() {
        super(new BorderLayout());
        applyGaps();
    }

    public boolean isRounded() {
        return rounded;
    }

    public void setRounded(boolean rounded) {
        if (this.rounded == rounded) {
            return;
        }
        this.rounded = rounded;
        applyGaps();
    }

    /** Jarak di tiap sisi saat membulat (mode kotak selalu tanpa jarak). */
    public void setGaps(Insets gaps) {
        if (this.gaps.equals(gaps)) {
            return;
        }
        this.gaps = (Insets) gaps.clone();
        applyGaps();
    }

    private void applyGaps() {
        var in = rounded ? gaps : new Insets(0, 0, 0, 0);
        setBorder(BorderFactory.createEmptyBorder(in.top, in.left, in.bottom, in.right));
        mask = null;
        revalidate();
        repaint();
    }

    @Override
    public boolean isPaintingOrigin() {
        return rounded;
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (!rounded) {
            return;
        }
        var in = getInsets();
        int w = getWidth() - in.left - in.right;
        int h = getHeight() - in.top - in.bottom;
        if (w <= ARC || h <= ARC) {
            return;
        }
        var g2 = (Graphics2D) g.create();
        try {
            g2.translate(in.left, in.top);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(getBackground());
            g2.fill(cornerMask(w, h));
            Color line = UIManager.getColor("Component.borderColor");
            if (line != null) {
                g2.setColor(line);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, ARC, ARC));
            }
        } finally {
            g2.dispose();
        }
    }

    /** Bagian persegi {@code w x h} di luar persegi bersudut membulat: empat potongan sudut yang ditutup. */
    Area cornerMask(int w, int h) {
        if (mask == null || w != maskW || h != maskH) {
            var area = new Area(new Rectangle2D.Float(0, 0, w, h));
            area.subtract(new Area(new RoundRectangle2D.Float(0, 0, w, h, ARC, ARC)));
            mask = area;
            maskW = w;
            maskH = h;
        }
        return mask;
    }
}

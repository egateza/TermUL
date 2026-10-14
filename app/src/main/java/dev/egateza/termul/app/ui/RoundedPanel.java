package dev.egateza.termul.app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.UIManager;

/**
 * Panel pembungkus yang bisa tampil sebagai "kartu" bersudut membulat: ada jarak di sekeliling isi, lalu sudut isi
 * ditimpa warna latar panel (antialias) dan diberi garis tepi tipis. Swing tidak bisa meng-clip anak komponen dengan
 * bentuk antialias, jadi sudutnya ditutup setelah anak-anaknya digambar.
 *
 * <p>Saat membulat, panel ini menjadi painting origin: repaint dari anak (mis. JediTerm yang menggambar ulang kursor
 * atau output) selalu dimulai dari panel ini, sehingga penutup sudut tidak tertimpa. Dalam mode kotak panel ini
 * tidak menambah apa pun. Semua method dipanggil di EDT.
 *
 * <p>Viewport scroll pane di dalamnya (mis. log, daftar host) tidak boleh memakai blit scrolling selama membulat:
 * blit menyalin pixel layar yang sudah ada, termasuk garis tepi dan penutup sudut yang digambar panel ini di atas
 * viewport, lalu hanya menggambar ulang bagian yang baru. Akibatnya garis tepi bawah dan lengkungan sudut
 * "tertinggal" di tengah isi setiap kali di-scroll (mis. auto-scroll log). Lihat {@link #updateScrollModes}.
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
        updateScrollModes(this, rounded);
        revalidate();
        repaint();
    }

    /** Layout ulang juga terjadi saat isi panel diganti (mis. scroll pane baru): mode scroll-nya ikut disesuaikan. */
    @Override
    public void doLayout() {
        super.doLayout();
        updateScrollModes(this, rounded);
    }

    /** Penanda viewport yang mode scroll-nya diubah panel ini (dikembalikan ke blit saat mode kotak). */
    private static final String SIMPLE_SCROLL_KEY = RoundedPanel.class.getName() + ".simpleScroll";

    /**
     * Membulat: semua viewport di dalam {@code root} memakai {@link JViewport#SIMPLE_SCROLL_MODE} (gambar ulang, bukan
     * salin pixel). Kotak: viewport yang tadi diubah kembali ke {@link JViewport#BLIT_SCROLL_MODE}.
     */
    static void updateScrollModes(Container root, boolean rounded) {
        for (Component c : root.getComponents()) {
            if (c instanceof JViewport viewport) {
                if (rounded && viewport.getScrollMode() == JViewport.BLIT_SCROLL_MODE) {
                    viewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
                    viewport.putClientProperty(SIMPLE_SCROLL_KEY, Boolean.TRUE);
                } else if (!rounded && Boolean.TRUE.equals(viewport.getClientProperty(SIMPLE_SCROLL_KEY))) {
                    viewport.setScrollMode(JViewport.BLIT_SCROLL_MODE);
                    viewport.putClientProperty(SIMPLE_SCROLL_KEY, null);
                }
            }
            if (c instanceof Container container) {
                updateScrollModes(container, rounded);
            }
        }
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

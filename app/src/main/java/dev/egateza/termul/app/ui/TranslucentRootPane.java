package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.terminal.TerminalTranslucency;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * Root pane window utama yang bisa memudarkan semua bagian selain terminal (slider "Jendela"): title bar, tab, panel,
 * status bar, termasuk teksnya. Area terminal (yang mengatur alpha latarnya sendiri, lihat {@link TerminalTranslucency})
 * dan popup ringan tidak ikut pudar. Hanya bekerja di window beralpha per piksel.
 *
 * <p>Saat aktif, root pane menjadi "painting origin": repaint komponen mana pun di dalamnya digambar dari sini, ke
 * buffer, lalu ditempel dengan alpha (mengganti piksel, bukan menumpuk). Opasitas 100% = gambar biasa tanpa buffer.
 * EDT.
 */
final class TranslucentRootPane extends JRootPane {

    private float chromeAlpha = 1f;
    private boolean solidText; // teks tidak ikut dipudarkan, lihat setSolidText; EDT
    private BufferedImage buffer; // dipakai ulang antar-paint; EDT
    private BufferedImage mask; // mask teks untuk solidText; EDT
    private BufferedImage faded; // hasil pudar dengan teks solid; EDT
    private BufferedImage cover; // tangkapan isi window selama berganti mode, lihat setCover; EDT

    /** Teks tetap pekat walau latar bagian selain terminal dipudarkan (seperti Windows Terminal). */
    void setSolidText(boolean on) {
        if (on != solidText) {
            solidText = on;
            repaint();
        }
    }

    /** @param percent opasitas bagian selain terminal; 100 = tidak dipudarkan */
    void setChromeOpacity(int percent) {
        float alpha = Math.clamp(percent, 0, 100) / 100f;
        if (alpha != chromeAlpha) {
            chromeAlpha = alpha;
            if (alpha >= 1f) {
                buffer = null;
            }
            repaint();
        }
    }

    private boolean fading() {
        return chromeAlpha < 1f;
    }

    @Override
    protected boolean isPaintingOrigin() {
        return fading() || super.isPaintingOrigin();
    }

    /**
     * Isi root pane saat ini sebagai gambar seukuran piksel layar (skala HiDPI ikut), untuk {@link #setCover}.
     */
    BufferedImage capture() {
        var gc = getGraphicsConfiguration();
        AffineTransform tx = gc == null ? new AffineTransform() : gc.getDefaultTransform();
        var img = new BufferedImage(Math.max(1, (int) Math.ceil(getWidth() * tx.getScaleX())),
                Math.max(1, (int) Math.ceil(getHeight() * tx.getScaleY())), BufferedImage.TYPE_INT_ARGB_PRE);
        var g = img.createGraphics();
        try {
            g.scale(tx.getScaleX(), tx.getScaleY());
            g.setClip(0, 0, getWidth(), getHeight());
            paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * Selama {@code image} tidak null, paint hanya menempel gambar itu apa adanya (cepat), bukan menggambar isi window.
     * Dipakai saat window berganti mode solid/beralpha, ketika Windows membuang isi window dan butuh gambar baru segera.
     */
    void setCover(BufferedImage image) {
        cover = image;
    }

    @Override
    public void paint(Graphics g) {
        if (cover != null) {
            var out = (Graphics2D) g.create();
            try {
                out.setComposite(AlphaComposite.Src);
                out.drawImage(cover, 0, 0, getWidth(), getHeight(), null);
            } finally {
                out.dispose();
            }
            return;
        }
        if (!fading()) {
            super.paint(g);
            return;
        }
        Rectangle clip = g.getClipBounds();
        if (clip == null) {
            clip = new Rectangle(0, 0, getWidth(), getHeight());
        }
        clip = clip.intersection(new Rectangle(0, 0, getWidth(), getHeight()));
        if (clip.isEmpty()) {
            return;
        }
        AffineTransform tx = ((Graphics2D) g).getTransform();
        double sx = tx.getScaleX();
        double sy = tx.getScaleY();
        // Skala pecahan (mis. 125%): tepi area logis jatuh di tengah piksel perangkat. Buffer digambar 1 px logis lebih
        // luas supaya setiap piksel yang ditempel benar-benar terisi; tanpa ini piksel tepi tetap transparan, lalu
        // menimpa isi window sebagai garis tipis di batas area yang digambar ulang (mis. di bawah jam).
        boolean fractional = sx != Math.rint(sx) || sy != Math.rint(sy);
        Rectangle painted = fractional
                ? new Rectangle(clip.x - 1, clip.y - 1, clip.width + 2, clip.height + 2)
                        .intersection(new Rectangle(0, 0, getWidth(), getHeight()))
                : clip;
        int bx = (int) Math.floor(painted.x * sx);
        int by = (int) Math.floor(painted.y * sy);
        int w = (int) Math.ceil((painted.x + painted.width) * sx) - bx;
        int h = (int) Math.ceil((painted.y + painted.height) * sy) - by;

        BufferedImage img = buffer(w, h);
        var bg = img.createGraphics();
        try {
            bg.setComposite(AlphaComposite.Clear);
            bg.fillRect(0, 0, w, h);
            bg.setComposite(AlphaComposite.SrcOver);
            bg.translate(-bx, -by);
            bg.scale(sx, sy);
            bg.setClip(painted);
            super.paint(bg);
        } finally {
            bg.dispose();
        }

        var out = (Graphics2D) g.create();
        try {
            // tempel dalam piksel perangkat (tanpa skala), supaya batasnya bulat dan sama dengan buffer
            out.setTransform(AffineTransform.getTranslateInstance(tx.getTranslateX(), tx.getTranslateY()));
            Rectangle device = deviceBounds(clip, sx, sy);
            out.setClip(device);
            if (solidText) {
                out.setComposite(AlphaComposite.Src);
                blit(out, fadeKeepingText(img, w, h, bx, by, sx, sy, painted), device, bx, by);
            } else {
                out.setComposite(AlphaComposite.Src.derive(chromeAlpha));
                blit(out, img, device, bx, by);
            }
            out.setComposite(AlphaComposite.Src); // terminal & popup: alpha apa adanya (terminal mengatur sendiri)
            for (Rectangle area : solidAreas()) {
                Rectangle part = area.intersection(clip);
                if (!part.isEmpty()) {
                    blit(out, img, deviceBounds(part, sx, sy).intersection(device), bx, by);
                }
            }
        } finally {
            out.dispose();
        }
    }

    /**
     * Isi {@code img} (w×h, pojok di piksel perangkat bx, by) yang dipudarkan dengan alpha jendela, kecuali piksel
     * teks: alpha tiap piksel = max(alpha jendela, cakupan teks). Cakupan teks dari gambar ulang area yang sama lewat
     * {@link TextMaskGraphics2D}.
     */
    private BufferedImage fadeKeepingText(BufferedImage img, int w, int h, int bx, int by, double sx, double sy,
                                          Rectangle painted) {
        mask = sized(mask, w, h);
        var mg = mask.createGraphics();
        try {
            mg.setComposite(AlphaComposite.Clear);
            mg.fillRect(0, 0, w, h);
            mg.setComposite(AlphaComposite.SrcOver);
            mg.translate(-bx, -by);
            mg.scale(sx, sy);
            mg.setClip(painted);
            super.paint(new TextMaskGraphics2D(mg));
        } finally {
            mg.dispose();
        }
        faded = sized(faded, w, h);
        int[] src = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        int[] text = ((DataBufferInt) mask.getRaster().getDataBuffer()).getData();
        int[] dst = ((DataBufferInt) faded.getRaster().getDataBuffer()).getData();
        int srcStride = img.getWidth();
        int textStride = mask.getWidth();
        int dstStride = faded.getWidth();
        int base = Math.round(chromeAlpha * 255);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src[y * srcStride + x];
                int k = Math.max(base, text[y * textStride + x] >>> 24); // 0..255
                dst[y * dstStride + x] = scale(p >>> 24, k) << 24 | scale(p >> 16 & 0xFF, k) << 16
                        | scale(p >> 8 & 0xFF, k) << 8 | scale(p & 0xFF, k);
            }
        }
        return faded;
    }

    /** {@code v × k / 255}, dibulatkan (piksel premultiplied). */
    private static int scale(int v, int k) {
        return (v * k + 127) / 255;
    }

    private static BufferedImage sized(BufferedImage img, int w, int h) {
        if (img != null && img.getWidth() >= w && img.getHeight() >= h) {
            return img;
        }
        int bw = Math.max(w, img == null ? 0 : img.getWidth());
        int bh = Math.max(h, img == null ? 0 : img.getHeight());
        return new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB_PRE);
    }

    /** Piksel perangkat (relatif ke root pane) yang tersentuh {@code r}: dibulatkan keluar. */
    static Rectangle deviceBounds(Rectangle r, double sx, double sy) {
        int x1 = (int) Math.floor(r.x * sx);
        int y1 = (int) Math.floor(r.y * sy);
        int x2 = (int) Math.ceil((r.x + r.width) * sx);
        int y2 = (int) Math.ceil((r.y + r.height) * sy);
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    /** Tempel piksel perangkat {@code d} dari buffer yang pojok kiri atasnya di piksel perangkat ({@code bx}, {@code by}). */
    private static void blit(Graphics2D out, BufferedImage img, Rectangle d, int bx, int by) {
        if (d.isEmpty()) {
            return;
        }
        out.drawImage(img, d.x, d.y, d.x + d.width, d.y + d.height,
                d.x - bx, d.y - by, d.x + d.width - bx, d.y + d.height - by, null);
    }

    private BufferedImage buffer(int w, int h) {
        if (buffer == null || buffer.getWidth() < w || buffer.getHeight() < h) {
            int bw = Math.max(w, buffer == null ? 0 : buffer.getWidth());
            int bh = Math.max(h, buffer == null ? 0 : buffer.getHeight());
            buffer = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB_PRE);
        }
        return buffer;
    }

    /** Area yang tidak dipudarkan: panel terminal yang terlihat dan popup ringan (menu, tooltip). */
    private List<Rectangle> solidAreas() {
        var areas = new ArrayList<>(TerminalTranslucency.visibleAreasIn(this));
        JLayeredPane layers = getLayeredPane();
        for (Component c : layers.getComponentsInLayer(JLayeredPane.POPUP_LAYER)) {
            if (c.isVisible()) {
                areas.add(SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), this));
            }
        }
        return areas;
    }
}

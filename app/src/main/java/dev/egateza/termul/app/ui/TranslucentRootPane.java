package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.terminal.TerminalTranslucency;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
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
    private BufferedImage buffer; // dipakai ulang antar-paint; EDT
    private BufferedImage cover; // tangkapan isi window selama berganti mode, lihat setCover; EDT

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
        var target = (Graphics2D) g;
        double sx = target.getTransform().getScaleX();
        double sy = target.getTransform().getScaleY();
        int w = (int) Math.ceil(clip.width * sx);
        int h = (int) Math.ceil(clip.height * sy);

        BufferedImage img = buffer(w, h);
        var bg = img.createGraphics();
        try {
            bg.setComposite(AlphaComposite.Clear);
            bg.fillRect(0, 0, w, h);
            bg.setComposite(AlphaComposite.SrcOver);
            bg.scale(sx, sy);
            bg.translate(-clip.x, -clip.y);
            bg.setClip(clip);
            super.paint(bg);
        } finally {
            bg.dispose();
        }

        var out = (Graphics2D) g.create();
        try {
            out.setComposite(AlphaComposite.Src.derive(chromeAlpha));
            blit(out, img, clip, clip, sx, sy);
            out.setComposite(AlphaComposite.Src); // terminal & popup: alpha apa adanya (terminal mengatur sendiri)
            for (Rectangle area : solidAreas()) {
                Rectangle part = area.intersection(clip);
                if (!part.isEmpty()) {
                    blit(out, img, part, clip, sx, sy);
                }
            }
        } finally {
            out.dispose();
        }
    }

    /** Tempel bagian {@code part} (koordinat root) dari buffer yang mewakili {@code clip}. */
    private static void blit(Graphics2D out, BufferedImage img, Rectangle part, Rectangle clip, double sx, double sy) {
        int srcX1 = (int) Math.floor((part.x - clip.x) * sx);
        int srcY1 = (int) Math.floor((part.y - clip.y) * sy);
        int srcX2 = Math.min(img.getWidth(), (int) Math.ceil((part.x + part.width - clip.x) * sx));
        int srcY2 = Math.min(img.getHeight(), (int) Math.ceil((part.y + part.height - clip.y) * sy));
        out.drawImage(img, part.x, part.y, part.x + part.width, part.y + part.height,
                srcX1, srcY1, srcX2, srcY2, null);
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

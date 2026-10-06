package dev.egateza.termul.app.update;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Titik notifikasi (badge) untuk tombol/menu update, digambar seolah menyala: inti bergradasi radial
 * (terang di kiri atas) dengan halo lembut di sekelilingnya. Warna mengikuti tema (FlatLaf {@code Actions.Red}).
 *
 * <p>Versi {@code pulsing} menghitung intensitas halo dari waktu saat dicat; pemilik komponen cukup
 * memanggil {@code repaint()} berkala (mis. {@link javax.swing.Timer}) supaya halo berdenyut.
 */
public final class BadgeDotIcon implements Icon {

    /** Interval repaint yang disarankan untuk animasi denyut. */
    public static final int FRAME_MS = 50;

    private static final int CORE = 8;
    private static final int SIZE = 18;
    private static final long PERIOD_NANOS = 1_400_000_000L;
    private static final Color FALLBACK = new Color(0xE5, 0x53, 0x53);

    private final boolean pulsing;

    /** Badge statis (halo tetap), mis. untuk ikon item menu. */
    public BadgeDotIcon() {
        this(false);
    }

    public BadgeDotIcon(boolean pulsing) {
        this.pulsing = pulsing;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        var g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = UIManager.getColor("Actions.Red");
            if (base == null) {
                base = FALLBACK;
            }
            float cx = x + SIZE / 2f;
            float cy = y + SIZE / 2f;

            // Halo: radial dari warna dasar (semi transparan) ke transparan; jari-jari dan opasitas ikut denyut.
            float glow = intensity();
            float r = CORE / 2f;
            float halo = r + (SIZE / 2f - r) * (0.4f + 0.6f * glow);
            g2.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), halo,
                    new float[] {0f, 0.5f, 1f},
                    new Color[] {withAlpha(base, 0.35f + 0.55f * glow), withAlpha(base, 0.15f + 0.35f * glow),
                            withAlpha(base, 0f)}));
            g2.fill(new Ellipse2D.Float(cx - halo, cy - halo, halo * 2, halo * 2));

            // Inti: fokus gradasi di kiri atas supaya tampak seperti lampu yang menyala; lebih terang di puncak denyut.
            g2.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r,
                    new Point2D.Float(cx - r * 0.35f, cy - r * 0.35f),
                    new float[] {0f, 0.6f, 1f},
                    new Color[] {mix(base, Color.WHITE, 0.3f + 0.45f * glow), mix(base, Color.WHITE, 0.15f * glow),
                            mix(base, Color.BLACK, 0.25f)},
                    CycleMethod.NO_CYCLE));
            g2.fill(new Ellipse2D.Float(cx - r, cy - r, CORE, CORE));
        } finally {
            g2.dispose();
        }
    }

    /** 0..1 mengikuti gelombang cosinus (halus di puncak dan lembah) untuk badge berdenyut; 0.6 untuk badge statis. */
    private float intensity() {
        if (!pulsing) {
            return 0.6f;
        }
        double phase = (System.nanoTime() % PERIOD_NANOS) / (double) PERIOD_NANOS;
        return (float) ((1 - Math.cos(phase * 2 * Math.PI)) / 2);
    }

    private static Color withAlpha(Color c, float alpha) {
        int a = Math.round(Math.clamp(alpha, 0f, 1f) * 255);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static Color mix(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }
}

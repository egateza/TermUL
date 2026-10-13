package dev.egateza.termul.app.ui.streak;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.SplittableRandom;

/**
 * Bentuk api dari kurva Bezier (tiga lapis + lidah api), bongkah es, dan bintang. Semua posisi dalam double supaya
 * mulus saat diskalakan; {@code by} = dasar api, {@code size} = tinggi api.
 */
final class FlamePainter {

    /** Retakan es dalam koordinat 0..1 bongkah, urutan muncul menyebar (bukan berurutan sudut). */
    static final double[][][] CRACKS = cracks();

    private FlamePainter() {
    }

    static Path2D flamePath(double cx, double by, double h, double sway) {
        double w = h * .62, r = w / 2, top = by - h * .97, cy = top + h * .66, tx = cx + sway;
        var p = new Path2D.Double();
        p.moveTo(tx, top);
        p.curveTo(tx + r * .35, top + h * .22, cx + r * 1.08, cy - h * .22, cx + r, cy);
        p.append(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 0, -180, Arc2D.OPEN), true);
        p.curveTo(cx - r * 1.08, cy - h * .22, tx - r * .35, top + h * .22, tx, top);
        p.closePath();
        return p;
    }

    /**
     * @param flick seberapa kuat api bergoyang (0 = diam)
     * @param tongues lidah api tambahan (0..2)
     */
    static void flame(Graphics2D g, double cx, double by, double size, double t, FlamePalette pal, double flick,
                      int tongues) {
        if (size < 0.5) {
            return;
        }
        if (pal.glow() != null) {
            float radius = (float) (size * .75);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, by - size * .42), radius, new float[] {0f, 1f},
                    new Color[] {FlamePalette.fade(pal.glow(), pal.glow().getAlpha() / 255.0 * .45),
                            FlamePalette.fade(pal.glow(), 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
            g.fill(new Ellipse2D.Double(cx - radius, by - size * .42 - radius, radius * 2, radius * 2));
        }
        g.setColor(pal.outer());
        double h;
        if (tongues >= 1) {
            h = size * .5 * height(t, 2, flick);
            g.fill(flamePath(cx - size * .2, by - size * .06, h, sway(t, 2.2, h, flick) - h * .14));
        }
        if (tongues >= 2) {
            h = size * .42 * height(t, 4, flick);
            g.fill(flamePath(cx + size * .2, by - size * .07, h, sway(t, 4.1, h, flick) + h * .14));
        }
        h = size * height(t, 0, flick);
        g.fill(flamePath(cx, by, h, sway(t, 0, h, flick)));
        g.setColor(pal.mid());
        h = size * .7 * height(t, 1.3, flick);
        g.fill(flamePath(cx, by - size * .02, h, sway(t, 1.1, h, flick)));
        g.setColor(pal.core());
        h = size * .4 * height(t, 2.6, flick);
        g.fill(flamePath(cx, by - size * .05, h, sway(t, 2.4, h, flick)));
    }

    private static double sway(double t, double phase, double h, double flick) {
        return (Math.sin(t * 6.3 + phase) * .10 + Math.sin(t * 11.7 + phase * 2) * .05) * h * .62 * flick;
    }

    private static double height(double t, double phase, double flick) {
        return 1 + (Math.sin(t * 8.7 + phase) * .035 + Math.sin(t * 15.3 + phase) * .02) * flick;
    }

    /** Arang di bawah bara. */
    static void coal(Graphics2D g, double cx, double by, double size) {
        g.setColor(new Color(0x3D1B0C));
        g.fill(new Ellipse2D.Double(cx - size * .3, by - size * .13, size * .6, size * .16));
    }

    static Shape iceBlock(double cx, double by, double size) {
        double w = size * .98, h = size * 1.06;
        return new RoundRectangle2D.Double(cx - w / 2, by - h + size * .03, w, h, size * .36, size * .36);
    }

    /**
     * Bongkah es menutupi api beku; retakan bertambah sebanding {@code cracks}/{@code clicks}.
     *
     * @param t detik animasi untuk kilau; negatif = tanpa kilau
     */
    static void ice(Graphics2D g, double cx, double by, double size, int cracks, int clicks, double t) {
        var block = iceBlock(cx, by, size);
        var b = block.getBounds2D();
        g.setPaint(new GradientPaint((float) b.getX(), (float) b.getY(), new Color(220, 243, 255, 158),
                (float) b.getMaxX(), (float) b.getMaxY(), new Color(110, 182, 236, 117)));
        g.fill(block);
        g.setColor(new Color(236, 250, 255, 242));
        g.setStroke(new BasicStroke((float) Math.max(1, size * .03)));
        g.draw(block);
        g.setColor(new Color(255, 255, 255, 230));
        double x = b.getX() + b.getWidth() * .2;
        g.setStroke(new BasicStroke((float) (size * .045), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(x, b.getY() + b.getHeight() * .15, x, b.getY() + b.getHeight() * .36));
        g.draw(new java.awt.geom.Line2D.Double(x, b.getY() + b.getHeight() * .45, x, b.getY() + b.getHeight() * .5));
        int shown = clicks <= 0 ? 0 : (int) Math.round((double) Math.min(cracks, clicks) / clicks * CRACKS.length);
        if (shown > 0) {
            var clip = g.getClip();
            g.clip(block);
            g.setColor(new Color(255, 255, 255, 242));
            g.setStroke(new BasicStroke((float) Math.max(1, size * .022), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < shown; i++) {
                var path = new Path2D.Double();
                double[][] crack = CRACKS[i];
                path.moveTo(b.getX() + crack[0][0] * b.getWidth(), b.getY() + crack[0][1] * b.getHeight());
                for (int j = 1; j < crack.length; j++) {
                    path.lineTo(b.getX() + crack[j][0] * b.getWidth(), b.getY() + crack[j][1] * b.getHeight());
                }
                g.draw(path);
            }
            g.setClip(clip);
        }
        if (t >= 0 && size >= 40) {
            for (int i = 0; i < 3; i++) {
                double s = Math.max(0, Math.sin(t * 1.8 + i * 2.4)) * size * .05;
                star(g, b.getX() + b.getWidth() * (.3 + i * .25), b.getY() + b.getHeight() * (.22 + (i * 37 % 5) * .12),
                        s, new Color(255, 255, 255, 242));
            }
        }
    }

    /** Bintang empat sudut. */
    static void star(Graphics2D g, double x, double y, double s, Color color) {
        if (s <= 0.2) {
            return;
        }
        var p = new Path2D.Double();
        p.moveTo(x, y - s);
        p.quadTo(x, y, x + s, y);
        p.quadTo(x, y, x, y + s);
        p.quadTo(x, y, x - s, y);
        p.quadTo(x, y, x, y - s);
        g.setColor(color);
        g.fill(p);
    }

    private static double[][][] cracks() {
        var random = new SplittableRandom(42);
        double[][][] all = new double[10][][];
        for (int i = 0; i < all.length; i++) {
            double a = i / 10.0 * Math.PI * 2 + (random.nextDouble() - .5) * .5;
            double x = .52, y = .46;
            int n = 3 + random.nextInt(2);
            double[][] points = new double[n + 1][];
            points[0] = new double[] {x, y};
            for (int j = 1; j <= n; j++) {
                a += (random.nextDouble() - .5) * .8;
                double len = .08 + random.nextDouble() * .11;
                x += Math.cos(a) * len;
                y += Math.sin(a) * len;
                points[j] = new double[] {x, y};
            }
            all[i] = points;
        }
        int[] order = {0, 3, 6, 9, 2, 5, 8, 1, 4, 7};
        double[][][] spread = new double[order.length][][];
        for (int i = 0; i < order.length; i++) {
            spread[i] = all[order[i]];
        }
        return spread;
    }
}

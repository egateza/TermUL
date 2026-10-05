package dev.egateza.termul.app.ui.anim;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;

/** Garis detak jantung yang menyapu dari kiri ke kanan, seperti monitor rumah sakit (ada celah di depan kepala). */
final class EkgAnimation implements Animation {

    private final boolean large;
    private final int period;  // sampel (px) per detak
    private final int advance; // px per frame
    private final int gap;
    private final float[] samples;
    private int head;
    private int t;

    EkgAnimation(Size size) {
        large = size.large();
        period = large ? 90 : 70;
        advance = large ? 3 : 2;
        gap = large ? 14 : 10;
        samples = new float[size.width()];
    }

    /** Bentuk satu detak (P, QRS, T) untuk fase {@code t} di [0, 1); puncak R = 1, lembah S ≈ -0.3. */
    static double beat(double t) {
        return gauss(t, 0.18, 0.025, 0.12) - gauss(t, 0.29, 0.008, 0.15) + gauss(t, 0.32, 0.01, 1)
                - gauss(t, 0.35, 0.01, 0.3) + gauss(t, 0.55, 0.04, 0.25);
    }

    private static double gauss(double t, double mu, double sigma, double a) {
        return a * Math.exp(-((t - mu) * (t - mu)) / (2 * sigma * sigma));
    }

    @Override
    public void step() {
        for (int i = 0; i < advance; i++) {
            samples[head] = (float) beat((t % period) / (double) period);
            head = (head + 1) % samples.length;
            t++;
        }
    }

    @Override
    public void paint(Graphics2D g, int w, int h, Palette c) {
        g.setColor(Palette.alpha(c.line(), 0.6));
        g.fillRect(0, (int) y(0, h), w, 1);
        g.setStroke(new BasicStroke(large ? 1.6f : 1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int n = samples.length;
        for (int i = 1; i < n; i++) {
            if (Math.floorMod(i - 1 - head, n) < gap) {
                continue; // celah di depan kepala
            }
            int age = Math.floorMod(head - i, n);
            g.setColor(Palette.alpha(c.green(), Math.max(0.15, 1 - age / (double) n)));
            g.draw(new Line2D.Double(i - 1, y(samples[i - 1], h), i, y(samples[i], h)));
        }
        int hx = Math.floorMod(head - 1, n);
        g.setColor(c.green());
        Pixels.dot(g, hx, y(samples[hx], h), large ? 2.4 : 1.6);
    }

    private static double y(double v, int h) {
        return (h - 2) - (v + 0.3) / 1.3 * (h - 4);
    }
}

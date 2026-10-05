package dev.egateza.termul.app.ui.anim;

import java.awt.Graphics2D;

/** Tiga titik yang membesar-mengecil bergantian. */
final class PulseAnimation implements Animation {

    private final double radius;
    private final int gap;
    private int frame;

    PulseAnimation(Size size) {
        radius = size.large() ? 5 : 3;
        gap = size.large() ? 18 : 10;
    }

    /** Skala titik ke-{@code i} (0..1) pada frame ini. */
    static double pulse(int frame, int i) {
        return Math.max(0, Math.sin(frame / 5.0 - i * 0.9));
    }

    @Override
    public void step() {
        frame++;
    }

    @Override
    public void paint(Graphics2D g, int w, int h, Palette c) {
        for (int i = 0; i < 3; i++) {
            double k = pulse(frame, i);
            g.setColor(Palette.alpha(c.accent(), 0.35 + 0.65 * k));
            Pixels.dot(g, w / 2.0 + (i - 1) * gap, h / 2.0, radius * (0.55 + 0.45 * k));
        }
    }
}

package dev.egateza.termul.app.ui.anim;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.SplittableRandom;

/** Deretan rak server dengan LED aktivitas yang berkedip acak. */
final class RackAnimation implements Animation {

    private final boolean large;
    private final int racks;
    private final int rackWidth;
    private final int gap;
    private final int units;
    private final boolean[] green;
    private final boolean[] amber;
    private final SplittableRandom random = new SplittableRandom(11);

    RackAnimation(Size size) {
        large = size.large();
        racks = large ? 5 : 6;
        rackWidth = large ? 26 : 13;
        gap = large ? 12 : 5;
        units = large ? 5 : 3;
        green = new boolean[racks * units];
        amber = new boolean[racks * units];
        for (int i = 0; i < green.length; i++) {
            green[i] = random.nextBoolean();
        }
    }

    @Override
    public void step() {
        for (int i = 0; i < green.length; i++) {
            if (random.nextDouble() < 0.18) {
                green[i] = !green[i];
            }
            amber[i] = amber[i] ? random.nextDouble() < 0.7 : random.nextDouble() < 0.01;
        }
    }

    @Override
    public void paint(Graphics2D g, int w, int h, Palette c) {
        int total = racks * rackWidth + (racks - 1) * gap;
        int x0 = (w - total) / 2;
        int top = large ? 2 : 0;
        int rackHeight = h - top * 2;
        double uh = rackHeight / (double) units;
        double led = large ? 1.8 : 1.1;
        for (int r = 0; r < racks; r++) {
            int x = x0 + r * (rackWidth + gap);
            g.setColor(c.fill());
            g.fillRect(x, top, rackWidth, rackHeight);
            g.setColor(c.line());
            g.drawRect(x, top, rackWidth - 1, rackHeight - 1);
            for (int u = 0; u < units; u++) {
                double uy = top + u * uh;
                int i = r * units + u;
                if (u > 0) {
                    g.setColor(c.line());
                    g.fillRect(x + 1, (int) Math.round(uy), rackWidth - 2, 1);
                }
                g.setColor(Palette.alpha(c.muted(), 0.45));
                g.fill(new Rectangle2D.Double(x + 3, uy + uh / 2 - 0.5, rackWidth * (large ? 0.45 : 0.35), 1));
                g.setColor(green[i] ? c.green() : c.line());
                Pixels.dot(g, x + rackWidth - (large ? 5 : 3), uy + uh / 2, led);
                if (large) {
                    g.setColor(amber[i] ? c.amber() : c.line());
                    Pixels.dot(g, x + rackWidth - 10, uy + uh / 2, led);
                }
            }
        }
    }
}

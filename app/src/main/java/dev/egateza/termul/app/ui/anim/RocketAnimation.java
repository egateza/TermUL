package dev.egateza.termul.app.ui.anim;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.SplittableRandom;

/** Roket kecil terbang melintas dengan jejak pelangi di antara bintang yang berkelip. */
final class RocketAnimation implements Animation {

    private static final Color[] RAINBOW = {new Color(0xFF4F5E), new Color(0xFF9F43), new Color(0xFFCC00),
            new Color(0x3DDC84), new Color(0x29D6F0), new Color(0x7B6CFF)};
    private static final Color BODY = new Color(0xE6E8EE);
    private static final Color NOSE = new Color(0xFF4F5E);
    private static final Color WINDOW = new Color(0x29D6F0);
    private static final Color FLAME_A = new Color(0xFFCC00);
    private static final Color FLAME_B = new Color(0xFF9F43);

    private final boolean large;
    private final int length;
    private final int stripe;
    private final int trail;
    private final int segment;
    private final int speed;
    private final int width;
    private final double[][] stars; // x, y, fase
    private int frame;

    RocketAnimation(Size size) {
        large = size.large();
        length = large ? 26 : 14;
        stripe = large ? 2 : 1;
        trail = large ? 110 : 70;
        segment = large ? 8 : 5;
        speed = large ? 3 : 2;
        width = size.width();
        var random = new SplittableRandom(17);
        stars = new double[large ? 14 : 10][];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new double[] {random.nextDouble() * width, random.nextDouble() * size.height(), random.nextDouble() * 6};
        }
    }

    /** Tepi kiri badan roket; satu putaran = masuk dari kiri sampai jejak keluar di kanan. */
    int rocketX() {
        return Math.floorMod(frame * speed, width + trail + length) - length;
    }

    @Override
    public void step() {
        frame++;
        for (double[] s : stars) {
            s[0] -= 0.4;
            if (s[0] < 0) {
                s[0] += width;
            }
        }
    }

    @Override
    public void paint(Graphics2D g, int w, int h, Palette c) {
        for (double[] s : stars) {
            g.setColor(Palette.alpha(c.muted(), 0.3 + 0.7 * Math.abs(Math.sin(frame / 9.0 + s[2]))));
            g.fillRect((int) s[0], (int) s[1], 1, 1);
        }
        int x = rocketX();
        double cy = h / 2.0 + Math.sin(frame / 6.0) * (large ? 3 : 1);
        double bandTop = cy - 3 * stripe;
        for (int sx = x - trail; sx < x + 2; sx += segment) {
            int k = (x - sx) / segment;
            int offset = ((k + frame / 4) % 2) * stripe; // jejak bergelombang bertangga
            double fade = 1 - (x - sx) / (double) trail;
            if (fade <= 0) {
                continue;
            }
            for (int i = 0; i < RAINBOW.length; i++) {
                g.setColor(Palette.alpha(RAINBOW[i], fade));
                g.fillRect(sx, (int) Math.round(bandTop) + i * stripe + offset, segment, stripe);
            }
        }
        double rh = large ? 12 : 7;
        double ry = cy - rh / 2;
        g.setColor(frame % 4 < 2 ? FLAME_A : FLAME_B);
        g.fill(triangle(x, ry + rh * 0.25, x - (large ? 7 : 4) - frame % 3, cy, x, ry + rh * 0.75));
        g.setColor(BODY);
        g.fill(new Rectangle2D.Double(x, ry, length * 0.72, rh));
        g.setColor(NOSE);
        g.fill(triangle(x + length * 0.72, ry, x + length, cy, x + length * 0.72, ry + rh));
        int fin = large ? 3 : 1;
        g.fill(new Rectangle2D.Double(x, ry - fin, length * 0.2, fin));
        g.fill(new Rectangle2D.Double(x, ry + rh, length * 0.2, fin));
        g.setColor(WINDOW);
        Pixels.dot(g, x + length * 0.5, cy, rh * 0.22);
    }

    private static Path2D triangle(double x1, double y1, double x2, double y2, double x3, double y3) {
        var p = new Path2D.Double();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        p.lineTo(x3, y3);
        p.closePath();
        return p;
    }
}

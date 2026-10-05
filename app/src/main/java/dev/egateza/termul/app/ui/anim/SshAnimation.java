package dev.egateza.termul.app.ui.anim;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;

/** Laptop dan server dengan paket data bolak-balik di antaranya. */
final class SshAnimation implements Animation {

    private static final BasicStroke DASHED =
            new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {2f, 3f}, 0f);

    private static final class Packet {
        int x;
        final boolean outbound; // laptop -> server

        Packet(boolean outbound) {
            this.outbound = outbound;
        }
    }

    private final boolean large;
    private final int s;       // ukuran ikon
    private final int every;   // frame antar paket
    private final int speed;
    private final int span;    // panjang jalur antar ikon
    private final ArrayDeque<Packet> packets = new ArrayDeque<>();
    private int frame;
    private int sent;
    private int flash;         // LED server menyala sebentar saat paket tiba

    SshAnimation(Size size) {
        large = size.large();
        s = large ? 28 : 14;
        every = large ? 14 : 16;
        speed = large ? 3 : 2;
        span = size.width() - 2 * (s + 4);
    }

    @Override
    public void step() {
        frame++;
        if (flash > 0) {
            flash--;
        }
        if (frame % every == 0) {
            packets.addLast(new Packet(sent++ % 3 != 2)); // dua keluar, satu balasan
        }
        for (var p : packets) {
            p.x += speed;
        }
        while (!packets.isEmpty() && packets.peekFirst().x > span) {
            if (packets.pollFirst().outbound) {
                flash = 6;
            }
        }
    }

    @Override
    public void paint(Graphics2D g, int w, int h, Palette c) {
        double y0 = (h - s) / 2.0;
        int a = s + 4;
        int b = w - s - 4;
        double cy = Math.round(h / 2.0) + 0.5;
        g.setStroke(new BasicStroke(1f));

        // laptop
        var screen = new Rectangle2D.Double(s * 0.12, y0 + s * 0.2, s * 0.76, s * 0.48);
        g.setColor(c.fill());
        g.fill(screen);
        g.setColor(c.fg());
        g.draw(screen);
        g.setColor(c.green());
        g.fill(new Rectangle2D.Double(s * 0.22, y0 + s * 0.32, s * 0.2, Math.max(1, s * 0.06)));
        g.setColor(c.fg());
        g.fill(new Rectangle2D.Double(0, y0 + s * 0.72, s, Math.max(1, s * 0.08)));

        // server, 3 unit
        double sx = w - s * 0.85;
        double uh = s * 0.84 / 3;
        for (int i = 0; i < 3; i++) {
            double uy = y0 + s * 0.08 + i * uh;
            var unit = new Rectangle2D.Double(sx, uy, s * 0.78, uh - 1);
            g.setColor(c.fill());
            g.fill(unit);
            g.setColor(c.fg());
            g.draw(unit);
            boolean on = (i == 1 && flash > 0) || (i == 0 && frame % 20 < 10);
            g.setColor(on ? c.green() : c.muted());
            Pixels.dot(g, sx + s * 0.62, uy + uh / 2 - 0.5, Math.max(1, s * 0.05));
        }

        // jalur + paket
        g.setColor(c.line());
        g.setStroke(DASHED);
        g.draw(new Line2D.Double(a, cy, b, cy));
        g.setStroke(new BasicStroke(1f));
        int pw = large ? 7 : 4;
        int ph = large ? 4 : 3;
        for (var p : packets) {
            g.setColor(p.outbound ? c.accent() : c.green());
            double x = p.outbound ? a + p.x : b - p.x - pw;
            g.fill(new Rectangle2D.Double(x, cy - ph / 2.0, pw, ph));
        }
    }
}

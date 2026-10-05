package dev.egateza.termul.app.ui.anim;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/** Dinosaurus berlari dan melompati kaktus. */
final class DinoAnimation implements Animation {

    private static final String[] BODY = {".....XXXXX", "....XX.XXX", "....XXXXXX", "....XXX...", "X..XXXXX..",
            "XXXXXXXX..", ".XXXXXXX..", "..XXXXX..."};
    private static final String[][] LEGS = {{"...X..X...", "...XX.XX.."}, {"...X..XX..", "...XX....."}};
    private static final String[] CACTUS = {"..X..", "X.X..", "X.X.X", "XXX.X", "..XXX", "..X..", "..X.."};

    private static final class Thing {
        double x;
        final int w;
        final int y;

        Thing(double x, int w, int y) {
            this.x = x;
            this.w = w;
            this.y = y;
        }
    }

    private final boolean large;
    private final int px;
    private final int width;
    private final int dinoX;
    private final int speed;
    private final int jumpFrames;
    private final int jumpHeight;
    private final String[] cactus;
    private final List<Thing> cacti = new ArrayList<>();
    private final List<Thing> pebbles = new ArrayList<>();
    private final SplittableRandom random = new SplittableRandom(9);
    private int frame;
    private int jump = -1; // frame ke berapa dalam lompatan; -1 = di tanah

    DinoAnimation(Size size) {
        large = size.large();
        px = large ? 2 : 1;
        width = size.width();
        dinoX = large ? 14 : 8;
        speed = large ? 3 : 2;
        jumpFrames = large ? 22 : 18;
        jumpHeight = large ? 20 : 8;
        cactus = large ? CACTUS : Arrays.copyOfRange(CACTUS, 2, CACTUS.length);
    }

    boolean jumping() {
        return jump >= 0;
    }

    @Override
    public void step() {
        frame++;
        int cw = 5 * px;
        for (var k : cacti) {
            k.x -= speed;
        }
        cacti.removeIf(k -> k.x < -cw);
        for (var p : pebbles) {
            p.x -= speed;
        }
        pebbles.removeIf(p -> p.x < 0);
        if (random.nextDouble() < 0.25) {
            pebbles.add(new Thing(width, 1 + random.nextInt(3), random.nextBoolean() ? 1 : 2));
        }
        Thing last = cacti.isEmpty() ? null : cacti.getLast();
        double minGap = (large ? 90 : 60) + random.nextDouble() * (large ? 70 : 50);
        if (last == null || (last.x < width - minGap && random.nextDouble() < 0.08)) {
            cacti.add(new Thing(width, cw, 0));
        }
        double dinoCenter = dinoX + 5 * px;
        if (jump < 0) {
            for (var k : cacti) {
                double center = k.x + cw / 2.0;
                if (center > dinoCenter) {
                    if (center - dinoCenter <= speed * jumpFrames / 2.0) {
                        jump = 0;
                    }
                    break;
                }
            }
        } else if (++jump > jumpFrames) {
            jump = -1;
        }
    }

    @Override
    public void paint(Graphics2D g, int w, int h, Palette c) {
        int ground = h - (large ? 3 : 1);
        g.setColor(c.muted());
        g.fillRect(0, ground, w, 1);
        for (var p : pebbles) {
            g.fillRect((int) p.x, ground + p.y, p.w, 1);
        }
        for (var k : cacti) {
            Pixels.sprite(g, cactus, k.x, ground - cactus.length * px, px, c.green());
        }
        double t = jump < 0 ? 0 : jump / (double) jumpFrames;
        int lift = (int) Math.round(jumpHeight * 4 * t * (1 - t));
        int y = ground - 10 * px - lift;
        Pixels.sprite(g, BODY, dinoX, y, px, c.fg());
        Pixels.sprite(g, jump < 0 ? LEGS[(frame / 4) % 2] : LEGS[0], dinoX, y + 8 * px, px, c.fg());
    }
}

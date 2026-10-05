package dev.egateza.termul.app.ui.anim;

import java.awt.Graphics2D;
import java.util.Arrays;
import java.util.SplittableRandom;

/** Barisan alien bergerak kiri-kanan dan ditembak satu per satu oleh meriam di bawah, lalu ulang. */
final class InvadersAnimation implements Animation {

    private static final String[][] ALIEN = {
            {"..X.....X..", "...X...X...", "..XXXXXXX..", ".XX.XXX.XX.", "XXXXXXXXXXX", "X.XXXXXXX.X", "X.X.....X.X", "...XX.XX..."},
            {"..X.....X..", "X..X...X..X", "X.XXXXXXX.X", "XXX.XXX.XXX", "XXXXXXXXXXX", ".XXXXXXXXX.", "..X.....X..", ".X.......X."}};
    private static final String[] CANNON = {".....X.....", "....XXX....", ".XXXXXXXXX.", "XXXXXXXXXXX", "XXXXXXXXXXX", "XXXXXXXXXXX"};
    private static final String[] BOOM = {"X...X...X", ".X.X.X.X.", "..X...X..", "XX.....XX", "..X...X..", ".X.X.X.X.", "X...X...X"};
    static final int COLUMNS = 6;
    private static final int BOOM_FRAMES = 8;

    private final boolean large;
    private final int px;
    private final int alienWidth;
    private final int pitch;
    private final int width;
    private final int height;
    private final SplittableRandom random = new SplittableRandom(5);

    private int frame;
    private int fleetX;
    private int dir;
    private int legs;
    private final boolean[] alive = new boolean[COLUMNS];
    private final int[] boom = new int[COLUMNS];
    private double cannonX;
    private int target;
    private boolean shooting;
    private double bulletX;
    private double bulletY;
    private int pause;

    InvadersAnimation(Size size) {
        large = size.large();
        px = large ? 2 : 1;
        alienWidth = 11 * px;
        pitch = alienWidth + (large ? 9 : 6);
        width = size.width();
        height = size.height();
        reset();
    }

    private void reset() {
        frame = 0;
        fleetX = 0;
        dir = 1;
        legs = 0;
        Arrays.fill(alive, true);
        Arrays.fill(boom, 0);
        target = -1;
        shooting = false;
        pause = 0;
    }

    int aliveCount() {
        int n = 0;
        for (boolean a : alive) {
            if (a) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void step() {
        frame++;
        if (pause > 0) {
            if (--pause == 0) {
                reset();
            }
            return;
        }
        int fleet = COLUMNS * pitch - (pitch - alienWidth);
        if (frame % (large ? 5 : 6) == 0) {
            fleetX += dir * (large ? 3 : 2);
            legs ^= 1;
            if (fleetX < 0 || fleetX + fleet > width) {
                dir = -dir;
                fleetX += dir * (large ? 6 : 4);
            }
        }
        for (int i = 0; i < COLUMNS; i++) {
            boom[i] = Math.max(0, boom[i] - 1);
        }
        if (target < 0 || !alive[target]) {
            target = pickTarget();
        }
        if (target < 0) {
            if (Arrays.stream(boom).allMatch(b -> b == 0)) {
                pause = 20;
            }
            return;
        }
        double tx = fleetX + target * pitch + alienWidth / 2.0 - 11 * px / 2.0;
        int speed = large ? 3 : 2;
        if (Math.abs(tx - cannonX) > speed) {
            cannonX += Math.signum(tx - cannonX) * speed;
        } else if (!shooting) {
            shooting = true;
            bulletX = cannonX + 5 * px;
            bulletY = height - 6 * px;
        }
        if (shooting) {
            bulletY -= large ? 4 : 2;
            if (bulletY <= 8 * px + (large ? 2 : 0)) {
                for (int i = 0; i < COLUMNS; i++) {
                    double ax = fleetX + i * pitch;
                    if (alive[i] && bulletX >= ax && bulletX <= ax + alienWidth) {
                        alive[i] = false;
                        boom[i] = BOOM_FRAMES;
                        break;
                    }
                }
                shooting = false;
            }
        }
    }

    private int pickTarget() {
        int n = aliveCount();
        if (n == 0) {
            return -1;
        }
        int k = random.nextInt(n);
        for (int i = 0; i < COLUMNS; i++) {
            if (alive[i] && k-- == 0) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void paint(Graphics2D g, int w, int h, Palette c) {
        int ay = large ? 2 : 0;
        for (int i = 0; i < COLUMNS; i++) {
            int ax = fleetX + i * pitch;
            if (alive[i]) {
                Pixels.sprite(g, ALIEN[legs], ax, ay, px, c.fg());
            } else if (boom[i] > 0) {
                Pixels.sprite(g, BOOM, ax + px, ay, px, c.amber());
            }
        }
        Pixels.sprite(g, CANNON, cannonX, h - 6 * px, px, c.green());
        if (shooting) {
            g.setColor(c.fg());
            g.fillRect((int) bulletX, (int) bulletY, px, large ? 6 : 3);
        }
    }
}

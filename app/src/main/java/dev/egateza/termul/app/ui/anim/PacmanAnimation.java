package dev.egateza.termul.app.ui.anim;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.List;

/** Pac-Man berjalan memakan deretan titik, dikejar tiga hantu. */
final class PacmanAnimation implements Animation {

    /**
     * Geometri satu ukuran (px).
     *
     * @param track    lebar lintasan
     * @param size     diameter Pac-Man / lebar hantu
     * @param dotGap   jarak antar titik
     * @param speed    px per frame
     * @param ghostLag jarak hantu pertama di belakang Pac-Man (tepi kiri ke tepi kiri)
     * @param ghostGap jarak antar hantu (tepi kiri ke tepi kiri)
     */
    record Geometry(int track, int size, int dotGap, int speed, int ghostLag, int ghostGap) {

        Geometry {
            if (ghostGap < size) {
                throw new IllegalArgumentException("hantu tidak boleh bertumpuk");
            }
        }

        /** Satu putaran: Pac-Man masuk dari kiri sampai hantu terakhir keluar di kanan. */
        int loop() {
            return track + size - ghostX(GHOSTS.size() - 1, 0) + size;
        }

        /** Tepi kiri Pac-Man pada frame ini; mulai di luar kiri track. */
        int pacX(int frame) {
            return Math.floorMod(frame * speed, loop()) - size;
        }

        /** Tepi kiri hantu ke-{@code i} (0 = paling dekat) untuk Pac-Man di {@code pacX}. */
        int ghostX(int i, int pacX) {
            return pacX - ghostLag - i * ghostGap;
        }

        /** Posisi tengah titik ke-{@code i}. */
        int dotX(int i) {
            return dotGap / 2 + i * dotGap;
        }

        int dotCount() {
            return (track - dotGap / 2) / dotGap + 1;
        }

        int dotSize() {
            return Math.max(2, Math.round(size * 0.21f));
        }

        /** Titik sudah dimakan kalau tengah Pac-Man sudah melewatinya. Muncul lagi di putaran berikutnya. */
        boolean eaten(int dotX, int pacX) {
            return pacX + size / 2 >= dotX;
        }
    }

    static final Geometry LARGE = new Geometry(264, 28, 24, 3, 64, 36);
    static final Geometry COMPACT = new Geometry(200, 14, 12, 2, 30, 18);

    static final int MAX_MOUTH_DEG = 45;
    private static final int MOUTH_PERIOD = 10;

    private static final Color PACMAN = new Color(0xFFCC00);
    /** Blinky, Pinky, Inky — urutan dari yang paling dekat dengan Pac-Man. */
    static final List<Color> GHOSTS = List.of(new Color(0xFF4F5E), new Color(0xFFA0D2), new Color(0x29D6F0));
    private static final Color PUPIL = new Color(0x2B4BFF);

    private final Geometry geo;
    private int frame;

    PacmanAnimation(Size size) {
        this.geo = size.large() ? LARGE : COMPACT;
    }

    /** Bukaan mulut (derajat), naik-turun 0..{@link #MAX_MOUTH_DEG}. */
    static int mouthDeg(int frame) {
        int phase = Math.floorMod(frame, MOUTH_PERIOD);
        int half = MOUTH_PERIOD / 2;
        int tri = phase <= half ? phase : MOUTH_PERIOD - phase;
        return MAX_MOUTH_DEG * tri / half;
    }

    @Override
    public void step() {
        frame++;
    }

    @Override
    public void paint(Graphics2D g, int w, int h, Palette c) {
        int size = geo.size();
        g.translate((w - geo.track()) / 2, (h - size) / 2);
        int pac = geo.pacX(frame);
        g.setColor(c.dot());
        int dot = geo.dotSize();
        for (int i = 0; i < geo.dotCount(); i++) {
            int x = geo.dotX(i);
            if (!geo.eaten(x, pac)) {
                g.fill(new Ellipse2D.Double(x - dot / 2.0, (size - dot) / 2.0, dot, dot));
            }
        }
        for (int i = GHOSTS.size() - 1; i >= 0; i--) {
            // fase rok digeser per hantu supaya tidak bergoyang serempak
            paintGhost(g, geo.ghostX(i, pac), size, GHOSTS.get(i), frame + i * 2);
        }
        int mouth = mouthDeg(frame);
        g.setColor(PACMAN);
        g.fill(new Arc2D.Double(pac, 0, size, size, mouth, 360 - 2 * mouth, Arc2D.PIE));
        g.setColor(Color.BLACK);
        double eye = size * 0.12;
        g.fill(new Ellipse2D.Double(pac + size * 0.5, size * 0.18, eye, eye));
    }

    private static void paintGhost(Graphics2D g, int x, int size, Color color, int frame) {
        double w = size;
        double h = size;
        var body = new Path2D.Double();
        body.moveTo(x, h / 2);
        body.append(new Arc2D.Double(x, 0, w, w, 180, -180, Arc2D.OPEN), true);
        body.lineTo(x + w, h);
        // rok bergelombang, bergantian tiap beberapa frame supaya terlihat "melayang"
        int bumps = 3;
        double step = w / bumps;
        boolean alt = (frame / 4) % 2 == 0;
        for (int i = bumps - 1; i >= 0; i--) {
            double left = x + i * step;
            body.lineTo(left + step / 2, alt ? h * 0.82 : h * 0.93);
            body.lineTo(left, h);
        }
        body.closePath();
        g.setColor(color);
        g.fill(body);

        double eyeW = w * 0.26;
        double eyeH = w * 0.32;
        double eyeY = h * 0.25;
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(x + w * 0.18, eyeY, eyeW, eyeH));
        g.fill(new Ellipse2D.Double(x + w * 0.56, eyeY, eyeW, eyeH));
        g.setColor(PUPIL); // melirik ke kanan, ke arah Pac-Man
        double p = eyeW * 0.55;
        g.fill(new Ellipse2D.Double(x + w * 0.18 + eyeW - p, eyeY + eyeH * 0.35, p, p));
        g.fill(new Ellipse2D.Double(x + w * 0.56 + eyeW - p, eyeY + eyeH * 0.35, p, p));
    }
}

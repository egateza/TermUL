package dev.egateza.termul.app.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Animasi loading: Pac-Man berjalan memakan deretan titik, dikejar hantu. Timer hanya berjalan selama komponen
 * tampil ({@link #addNotify}/{@link #removeNotify}), jadi tidak ada yang tertinggal setelah layar connect diganti.
 */
public final class PacmanLoader extends JComponent {

    static final int TRACK = 264;
    static final int HEIGHT = 44;
    static final int SIZE = 28;
    static final int DOT_GAP = 24;
    static final int SPEED = 3;
    /** Jarak hantu di belakang Pac-Man (px, tepi kiri ke tepi kiri). */
    static final int GHOST_LAG = 64;
    static final int MAX_MOUTH_DEG = 45;
    private static final int MOUTH_PERIOD = 10;
    private static final int FRAME_MS = 33;
    private static final int DOT = 6;
    /** Satu putaran: Pac-Man masuk dari kiri sampai hantu keluar di kanan. */
    static final int LOOP = TRACK + SIZE + GHOST_LAG + SIZE;

    private static final Color PACMAN = new Color(0xFFCC00);
    private static final Color GHOST = new Color(0xFF4F5E);
    private static final Color PUPIL = new Color(0x2B4BFF);

    private int frame; // EDT
    private final Timer timer = new Timer(FRAME_MS, e -> {
        frame++;
        repaint();
    });

    public PacmanLoader() {
        setOpaque(false);
        Dimension size = new Dimension(TRACK, HEIGHT);
        setPreferredSize(size);
        setMinimumSize(size);
    }

    /** Animasi + pesan di bawahnya, di tengah area yang tersedia. */
    public static JPanel withMessage(String message) {
        var panel = new JPanel(new GridBagLayout());
        var c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        var loader = new PacmanLoader();
        loader.getAccessibleContext().setAccessibleName(message);
        panel.add(loader, c);
        c.gridy = 1;
        c.insets = new Insets(12, 0, 0, 0);
        panel.add(new JLabel(message, SwingConstants.CENTER), c);
        return panel;
    }

    /** {@link JComponent} polos tidak punya AccessibleContext (null): sediakan, dengan peran progress bar. */
    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.PROGRESS_BAR;
                }
            };
        }
        return accessibleContext;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    /** Tepi kiri Pac-Man pada frame ini; mulai di luar kiri track. */
    static int pacX(int frame) {
        return Math.floorMod(frame * SPEED, LOOP) - SIZE;
    }

    /** Bukaan mulut (derajat), naik-turun 0..{@link #MAX_MOUTH_DEG}. */
    static int mouthDeg(int frame) {
        int phase = Math.floorMod(frame, MOUTH_PERIOD);
        int half = MOUTH_PERIOD / 2;
        int tri = phase <= half ? phase : MOUTH_PERIOD - phase;
        return MAX_MOUTH_DEG * tri / half;
    }

    /** Posisi tengah titik ke-{@code i}. */
    static int dotX(int i) {
        return DOT_GAP / 2 + i * DOT_GAP;
    }

    static int dotCount() {
        return (TRACK - DOT_GAP / 2) / DOT_GAP + 1;
    }

    /** Titik sudah dimakan kalau tengah Pac-Man sudah melewatinya. Muncul lagi di putaran berikutnya. */
    static boolean eaten(int dotX, int pacX) {
        return pacX + SIZE / 2 >= dotX;
    }

    @Override
    protected void paintComponent(Graphics g) {
        var g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x0 = (getWidth() - TRACK) / 2;
            int y0 = (getHeight() - SIZE) / 2;
            g2.clipRect(x0, 0, TRACK, getHeight());
            g2.translate(x0, y0);

            int pac = pacX(frame);
            Color fg = UIManager.getColor("Label.foreground");
            g2.setColor(fg == null ? Color.GRAY : new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 150));
            for (int i = 0; i < dotCount(); i++) {
                int x = dotX(i);
                if (!eaten(x, pac)) {
                    g2.fill(new Ellipse2D.Double(x - DOT / 2.0, (SIZE - DOT) / 2.0, DOT, DOT));
                }
            }
            paintGhost(g2, pac - GHOST_LAG, frame);
            paintPacman(g2, pac, mouthDeg(frame));
        } finally {
            g2.dispose();
        }
    }

    private static void paintPacman(Graphics2D g2, int x, int mouth) {
        g2.setColor(PACMAN);
        g2.fill(new Arc2D.Double(x, 0, SIZE, SIZE, mouth, 360 - 2 * mouth, Arc2D.PIE));
        g2.setColor(Color.BLACK);
        double eye = SIZE * 0.12;
        g2.fill(new Ellipse2D.Double(x + SIZE * 0.5, SIZE * 0.18, eye, eye));
    }

    private static void paintGhost(Graphics2D g2, int x, int frame) {
        double w = SIZE;
        double h = SIZE;
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
            double dip = alt ? h - 5 : h - 2;
            body.lineTo(left + step / 2, dip);
            body.lineTo(left, h);
        }
        body.closePath();
        g2.setColor(GHOST);
        g2.fill(body);

        double eyeW = w * 0.26;
        double eyeH = w * 0.32;
        double eyeY = h * 0.25;
        g2.setColor(Color.WHITE);
        g2.fill(new Ellipse2D.Double(x + w * 0.18, eyeY, eyeW, eyeH));
        g2.fill(new Ellipse2D.Double(x + w * 0.56, eyeY, eyeW, eyeH));
        g2.setColor(PUPIL); // melirik ke kanan, ke arah Pac-Man
        double p = eyeW * 0.55;
        g2.fill(new Ellipse2D.Double(x + w * 0.18 + eyeW - p, eyeY + eyeH * 0.35, p, p));
        g2.fill(new Ellipse2D.Double(x + w * 0.56 + eyeW - p, eyeY + eyeH * 0.35, p, p));
    }
}

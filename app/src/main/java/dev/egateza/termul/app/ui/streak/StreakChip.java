package dev.egateza.termul.app.ui.streak;

import dev.egateza.termul.app.i18n.I18n;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Ikon api streak di pojok kiri panel bawah: api kecil + jumlah hari ({@code 23x}, atau {@code 2/3} saat masih bara)
 * dan petunjuk singkat saat padam/beku. Klik kiri diteruskan ke controller. Animasi hanya berjalan selama terpasang
 * dan terlihat. EDT.
 */
final class StreakChip extends JComponent {

    static final int ICON = 22;
    /** Tinggi api di dalam kotak ikon. */
    static final double FLAME = 18;
    private static final double FLAME_BASE = 20.5;
    private static final int GAP = 3;
    private static final double ARRIVE_SECONDS = .5;

    private final FlameActor actor = new FlameActor();
    private final Timer timer = new Timer(40, e -> repaint());
    private FlameLook look = FlameLook.active(0);
    private boolean iconHidden;
    private long lastPaint = System.nanoTime();
    private double arriveT = -1;

    StreakChip(Runnable onClick) {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    onClick.run();
                }
            }
        });
        getAccessibleContext().setAccessibleName(I18n.t("streak.name"));
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return javax.accessibility.AccessibleRole.PUSH_BUTTON;
                }
            };
        }
        return accessibleContext;
    }

    FlameActor actor() {
        return actor;
    }

    FlameLook look() {
        return look;
    }

    void setLook(FlameLook look) {
        this.look = look;
        revalidate();
        repaint();
    }

    /** Disembunyikan selama animasi besar terbang ke sini. */
    void setIconHidden(boolean hidden) {
        iconHidden = hidden;
        repaint();
    }

    /** Api mendarat dari animasi besar: membesar sebentar lalu kembali. */
    void arrive() {
        arriveT = 0;
        iconHidden = false;
    }

    /** Dasar tengah api, dalam koordinat komponen ini. */
    Point2D.Double flameAnchor() {
        return new Point2D.Double(ICON / 2.0, (getHeight() - ICON) / 2 + FLAME_BASE);
    }

    String countText() {
        return look.kindling() ? look.count() + "/3" : look.count() + "x";
    }

    String hintText() {
        return switch (look.mode()) {
            case GRAY -> I18n.t("streak.chip.gray");
            case FROZEN -> I18n.t("streak.chip.frozen", look.cracks(), look.clicks());
            case ACTIVE -> "";
        };
    }

    /** Warna angka: dari level api, versi gelap di panel terang supaya tetap terbaca. */
    static Color textColor(FlameLook look, boolean darkBackground) {
        return switch (look.mode()) {
            case GRAY -> new Color(darkBackground ? 0x8D9299 : 0x6F747B);
            case FROZEN -> new Color(darkBackground ? 0x9FD6FF : 0x2B7FC0);
            case ACTIVE -> {
                var pal = look.tier().palette(0);
                yield darkBackground ? pal.mid() : pal.outer();
            }
        };
    }

    private boolean darkBackground() {
        var bg = getParent() != null ? getParent().getBackground() : UIManager.getColor("Panel.background");
        if (bg == null) {
            return true;
        }
        return (bg.getRed() * 299 + bg.getGreen() * 587 + bg.getBlue() * 114) / 1000 < 128;
    }

    private Font countFont() {
        return getFont().deriveFont(Font.BOLD);
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        FontMetrics count = getFontMetrics(countFont());
        int width = ICON + GAP + count.stringWidth(countText()) + 4;
        String hint = hintText();
        if (!hint.isEmpty()) {
            width += 5 + getFontMetrics(getFont()).stringWidth(hint);
        }
        return new Dimension(width, Math.max(ICON, count.getHeight()));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        long now = System.nanoTime();
        double dt = Math.min(.1, (now - lastPaint) / 1e9);
        lastPaint = now;
        double t = now / 1e9;
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int top = (getHeight() - ICON) / 2;
            if (!iconHidden) {
                var icon = (Graphics2D) g.create();
                if (arriveT >= 0) {
                    arriveT += dt;
                    double p = Math.min(1, arriveT / ARRIVE_SECONDS);
                    double s = 1 + .5 * (1 - Easing.outBack(p));
                    icon.transform(AffineTransform.getTranslateInstance(ICON / 2.0, top + FLAME_BASE));
                    icon.scale(s, s);
                    icon.transform(AffineTransform.getTranslateInstance(-ICON / 2.0, -(top + FLAME_BASE)));
                    if (p >= 1) {
                        arriveT = -1;
                    }
                }
                actor.paint(icon, ICON / 2.0, top + FLAME_BASE, FLAME, t, dt, look);
                icon.dispose();
            }
            FontMetrics fm = g.getFontMetrics(countFont());
            int x = ICON + GAP;
            int baseline = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            String count = countText();
            boolean dark = darkBackground();
            g.setFont(countFont());
            if (look.mode() == FlameLook.Mode.ACTIVE && look.tier() == StreakTier.PELANGI) {
                int w = Math.max(1, fm.stringWidth(count));
                float shift = (float) ((t * 40) % (w * 2));
                g.setPaint(new LinearGradientPaint(x - shift, 0, x - shift + w * 2, 0,
                        new float[] {0f, .2f, .4f, .6f, .8f, 1f},
                        new Color[] {new Color(0xFF4D4D), new Color(0xFFB000), new Color(0x3DDC84), new Color(0x3D8BFF),
                                new Color(0xB05CFF), new Color(0xFF4D4D)},
                        java.awt.MultipleGradientPaint.CycleMethod.REPEAT));
            } else {
                g.setColor(textColor(look, dark));
            }
            g.drawString(count, x, baseline);
            String hint = hintText();
            if (!hint.isEmpty()) {
                g.setFont(getFont());
                g.setColor(FlamePalette.fade(textColor(look, dark), .85));
                g.drawString(hint, x + fm.stringWidth(count) + 5, baseline);
            }
        } finally {
            g.dispose();
        }
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

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible && isDisplayable()) {
            timer.start();
        } else {
            timer.stop();
        }
    }
}

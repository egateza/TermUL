package dev.egateza.termul.app.ui.streak;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.DerivedFonts;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Animasi besar saat aplikasi dibuka: layar diredupkan, api muncul di tengah dengan angka streak, lalu terbang mengecil
 * ke ikon di pojok kiri bawah. Untuk streak yang padam/beku, animasi menunggu api diklik (atau "Nanti saja" / Esc).
 * Komponen ini menutupi layered pane selama tampil dan melepas dirinya sendiri setelah selesai. EDT.
 */
final class StreakSplash extends JComponent {

    /** Penghubung ke controller. */
    interface Host {
        /** Dasar tengah api ikon pojok, dalam koordinat splash. */
        Point2D anchor(StreakSplash splash);

        /** Api besar diklik saat menunggu dinyalakan lagi; controller membalas lewat {@link #update}. */
        void flameClicked();

        /** Animasi selesai dan komponen sudah dilepas. */
        void closed();
    }

    enum Kind { COUNTED, KINDLING, PENDING, EXPIRED }

    static final double FLY_SECONDS = .7;
    private static final Color STRIKE = new Color(255, 255, 255, 200);

    private final Host host;
    private final FlameActor actor = new FlameActor();
    private final Timer timer = new Timer(16, e -> tick());
    private final JButton later = new JButton(I18n.t("streak.later"));
    private Kind kind;
    private FlameLook shown;
    private int from;
    private int to;
    private long absent;
    private double t;
    private long lastNanos;
    private boolean ticked;
    private boolean dying;
    private double reviveAt = -1;
    private double flyStart = -1;
    private double popAt = -1;
    private boolean closed;

    StreakSplash(Host host) {
        this.host = host;
        setOpaque(false);
        setLayout(null);
        later.setFocusable(false);
        later.addActionListener(e -> fly());
        later.setVisible(false);
        add(later);
        var mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    pressed(e.getX(), e.getY());
                }
            }
        };
        addMouseListener(mouse); // juga menahan klik supaya tidak tembus ke terminal di bawahnya
        addMouseWheelListener(e -> { });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "streak.skip");
        getActionMap().put("streak.skip", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fly();
            }
        });
    }

    void playCounted(int from, int to) {
        start(Kind.COUNTED, FlameLook.active(from));
        this.from = from;
        this.to = to;
    }

    void playKindling(int day) {
        start(Kind.KINDLING, FlameLook.active(day));
        this.to = day;
    }

    void playPending(FlameLook look) {
        start(Kind.PENDING, look);
    }

    void playExpired(int lost, long missed) {
        start(Kind.EXPIRED, FlameLook.active(lost));
        this.from = lost;
        this.absent = missed;
    }

    /** Api sudah dinyalakan/esnya pecah dan animasi sedang menuju selesai (untuk test). */
    boolean reviving() {
        return reviveAt >= 0;
    }

    /** Hasil klik api (dari controller): efek + keadaan baru. */
    void update(FlameLook look, FlameActor.Effect effect) {
        if (kind != Kind.PENDING || reviveAt >= 0) {
            return;
        }
        shown = look;
        actor.react(effect, look.tier().palette(t));
        if (effect == FlameActor.Effect.REVIVE || effect == FlameActor.Effect.SHATTER) {
            reviveAt = t;
            popAt = t;
            later.setVisible(false);
        }
        layoutScene();
        repaint();
    }

    Kind kind() {
        return kind;
    }

    boolean waiting() {
        return kind == Kind.PENDING && reviveAt < 0 && flyStart < 0;
    }

    /** Langsung terbang ke ikon pojok (Esc, "Nanti saja", atau klik di luar api). */
    void fly() {
        if (flyStart < 0) {
            flyStart = t;
            later.setVisible(false);
        }
    }

    /** Lepas tanpa animasi (mis. streak dimatikan dari menu). */
    void close() {
        timer.stop();
        if (closed) {
            return;
        }
        closed = true;
        var parent = getParent();
        if (parent != null) {
            parent.remove(this);
            parent.repaint(getX(), getY(), getWidth(), getHeight());
        }
        host.closed();
    }

    private void start(Kind kind, FlameLook look) {
        this.kind = kind;
        this.shown = look;
        later.setVisible(kind == Kind.PENDING);
        lastNanos = System.nanoTime();
        timer.start();
    }

    private void pressed(int x, int y) {
        if (flyStart >= 0) {
            return;
        }
        double size = bigSize(); // ukuran penuh, walau api masih membesar
        boolean onFlame = Math.abs(x - getWidth() / 2.0) <= size * .6 && y >= getHeight() * .5 - size * 1.1
                && y <= getHeight() * .5 + size * .1;
        if (waiting()) {
            if (onFlame) {
                host.flameClicked();
            }
        } else if (kind != Kind.PENDING) {
            fly(); // klik saat animasi otomatis = lewati
        }
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min(.05, (now - lastNanos) / 1e9);
        lastNanos = now;
        t += dt;
        switch (kind) {
            case COUNTED -> {
                if (!ticked && t >= 1.0) {
                    ticked = true;
                    shown = FlameLook.active(to);
                    actor.burst(shown.tier().palette(t), true);
                    popAt = t;
                }
                if (t >= 3.0) {
                    fly();
                }
            }
            case KINDLING -> {
                if (t >= 2.8) {
                    fly();
                }
            }
            case EXPIRED -> {
                if (!dying && t >= 1.0) {
                    dying = true;
                    var tier = StreakTier.of(from);
                    actor.extinguish(tier.palette(t), tier.tongues);
                }
                if (!ticked && t >= 3.2) {
                    ticked = true;
                    shown = FlameLook.active(1);
                    actor.relight();
                    actor.burst(StreakTier.BARA.palette(t), false);
                    popAt = t;
                }
                if (t >= 5.6) {
                    fly();
                }
            }
            case PENDING -> {
                if (reviveAt >= 0 && t >= reviveAt + 1.8) {
                    fly();
                }
            }
        }
        if (flyStart >= 0 && t - flyStart >= FLY_SECONDS) {
            close();
            return;
        }
        layoutScene();
        repaint();
    }

    private double bigSize() {
        return Math.min(150, getHeight() * .36);
    }

    // --- teks ---

    private Font base() {
        Font f = UIManager.getFont("Label.font");
        return f != null ? f : getFont();
    }

    private Color accent() {
        return switch (shown.mode()) {
            case GRAY -> new Color(0xB4B8BD);
            case FROZEN -> new Color(0xBFE6FF);
            case ACTIVE -> shown.tier() == StreakTier.PELANGI ? new Color(0xFFD36B) : shown.tier().palette(0).mid();
        };
    }

    /** Baris-baris teks saat ini (angka, satuan, badge, pesan, progres). */
    record Texts(String count, String unit, String badge, String message, String progress, boolean struck) {
    }

    Texts texts() {
        String unit = I18n.t("streak.unit");
        String kindlingUnit = I18n.t("streak.unit.kindling");
        return switch (kind) {
            case KINDLING -> new Texts(to + "/3", kindlingUnit, "", I18n.t("streak.msg.kindling"), "", false);
            case COUNTED -> {
                if (!ticked) {
                    yield new Texts(String.valueOf(from), from < 3 ? kindlingUnit : unit, "", "", "", false);
                }
                var tier = StreakTier.of(to);
                String badge = from < 3 ? I18n.t("streak.badge.lit", tier.displayName())
                        : StreakTier.of(from) != tier ? I18n.t("streak.badge.levelUp", tier.displayName()) : "";
                yield new Texts(String.valueOf(to), unit, badge, "", "", false);
            }
            case EXPIRED -> ticked
                    ? new Texts("1/3", kindlingUnit, "", I18n.t("streak.msg.restart"), "", false)
                    : new Texts(String.valueOf(from), unit, "", I18n.t("streak.msg.expired", absent, from), "", dying);
            case PENDING -> {
                if (reviveAt >= 0) {
                    yield new Texts(String.valueOf(shown.count()), unit, shown.tier().displayName(),
                            I18n.t("streak.msg.revived"), "", false);
                }
                if (shown.mode() == FlameLook.Mode.FROZEN) {
                    yield new Texts(String.valueOf(shown.count()), unit, "",
                            I18n.t("streak.msg.frozen", shown.missed(), shown.clicks()),
                            I18n.t("streak.msg.cracks", shown.cracks(), shown.clicks()), false);
                }
                yield new Texts(String.valueOf(shown.count()), unit, "", I18n.t("streak.msg.gray", shown.missed()), "",
                        false);
            }
        };
    }

    private static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        var lines = new ArrayList<String>();
        var line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(next) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private Font countFont() {
        return DerivedFonts.derive(base(), Font.BOLD, 54f);
    }

    private Font unitFont() {
        return DerivedFonts.derive(base(), Font.BOLD, 11.5f);
    }

    private Font badgeFont() {
        return DerivedFonts.derive(base(), Font.BOLD, 12.5f);
    }

    private Font messageFont() {
        return DerivedFonts.derive(base(), Font.PLAIN, 14f);
    }

    private int textTop() {
        return (int) (getHeight() * .52);
    }

    /** Tombol "Nanti saja" di bawah pesan. */
    private void layoutScene() {
        if (!later.isVisible()) {
            return;
        }
        var texts = texts();
        int y = textTop() + getFontMetrics(countFont()).getHeight() + getFontMetrics(unitFont()).getHeight() + 8;
        var msg = getFontMetrics(messageFont());
        y += wrap(texts.message(), msg, messageWidth()).size() * msg.getHeight() + 6;
        if (!texts.progress().isEmpty()) {
            y += getFontMetrics(unitFont()).getHeight() + 6;
        }
        var size = later.getPreferredSize();
        later.setBounds((getWidth() - size.width) / 2, y + 4, size.width, size.height);
    }

    private int messageWidth() {
        return Math.max(120, Math.min(getWidth() - 32, 360));
    }

    // --- gambar ---

    @Override
    protected void paintComponent(Graphics graphics) {
        if (kind == null) {
            return;
        }
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double fly = flyStart < 0 ? 0 : Easing.inOutCubic(Math.min(1, (t - flyStart) / FLY_SECONDS));
            paintBackdrop(g, 1 - fly);
            double big = bigSize();
            double cx = getWidth() / 2.0, by = getHeight() * .5;
            double size = big * Easing.outBack(Math.min(1, t / .7));
            if (fly > 0) {
                var target = host.anchor(this);
                cx = Easing.lerp(cx, target.getX(), fly);
                by = Easing.lerp(by, target.getY(), fly);
                size = Easing.lerp(big, StreakChip.FLAME, fly);
            }
            actor.paint(g, cx, by, Math.max(1, size), System.nanoTime() / 1e9, frameDt(), shown);
            double textAlpha = flyStart < 0 ? 1 : Math.max(0, 1 - (t - flyStart) / .25);
            if (textAlpha > 0) {
                g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) textAlpha));
                paintTexts(g);
            }
        } finally {
            g.dispose();
        }
    }

    private double lastFrame = -1;

    private double frameDt() {
        double dt = lastFrame < 0 ? 0 : Math.max(0, t - lastFrame);
        lastFrame = t;
        return dt;
    }

    private void paintBackdrop(Graphics2D g, double alpha) {
        if (alpha <= 0) {
            return;
        }
        float radius = (float) Math.max(getWidth(), getHeight()) * .75f;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(getWidth() / 2.0, getHeight() * .4), radius,
                new float[] {0f, 1f},
                new Color[] {new Color(40, 22, 10, (int) (184 * alpha)), new Color(6, 7, 9, (int) (237 * alpha))},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private void paintTexts(Graphics2D g) {
        var texts = texts();
        int cx = getWidth() / 2;
        int y = textTop();
        Color accent = accent();

        g.setFont(countFont());
        FontMetrics fm = g.getFontMetrics();
        int width = fm.stringWidth(texts.count());
        int baseline = y + fm.getAscent();
        var count = (Graphics2D) g.create();
        if (popAt >= 0 && t - popAt < .45) {
            double s = .55 + .45 * Easing.outBack((t - popAt) / .45);
            double pivotY = baseline - fm.getAscent() / 2.0;
            count.transform(AffineTransform.getTranslateInstance(cx, pivotY));
            count.scale(s, s);
            count.transform(AffineTransform.getTranslateInstance(-cx, -pivotY));
        }
        count.setColor(texts.struck() ? FlamePalette.fade(accent, .7) : accent);
        count.drawString(texts.count(), cx - width / 2, baseline);
        if (texts.struck()) {
            count.setColor(STRIKE);
            count.setStroke(new BasicStroke(4f));
            int strikeY = baseline - fm.getAscent() / 3;
            count.drawLine(cx - width / 2 - 4, strikeY, cx + width / 2 + 4, strikeY);
        }
        count.dispose();
        y += fm.getHeight();

        g.setFont(unitFont());
        fm = g.getFontMetrics();
        g.setColor(new Color(255, 255, 255, 166));
        String unit = texts.unit().toUpperCase(java.util.Locale.ROOT);
        g.drawString(unit, cx - fm.stringWidth(unit) / 2, y + fm.getAscent());
        y += fm.getHeight() + 8;

        if (!texts.badge().isEmpty()) {
            g.setFont(badgeFont());
            fm = g.getFontMetrics();
            int w = fm.stringWidth(texts.badge()) + 22, h = fm.getHeight() + 6;
            g.setColor(accent);
            g.fill(new RoundRectangle2D.Double(cx - w / 2.0, y, w, h, h, h));
            g.setColor(new Color(0x14100C));
            g.drawString(texts.badge(), cx - w / 2 + 11, y + 3 + fm.getAscent());
            y += h + 8;
        }
        if (!texts.message().isEmpty()) {
            g.setFont(messageFont());
            fm = g.getFontMetrics();
            g.setColor(new Color(255, 255, 255, 220));
            for (String line : wrap(texts.message(), fm, messageWidth())) {
                g.drawString(line, cx - fm.stringWidth(line) / 2, y + fm.getAscent());
                y += fm.getHeight();
            }
            y += 6;
        }
        if (!texts.progress().isEmpty()) {
            g.setFont(unitFont());
            fm = g.getFontMetrics();
            g.setColor(new Color(0xBFE6FF));
            g.drawString(texts.progress(), cx - fm.stringWidth(texts.progress()) / 2, y + fm.getAscent());
        }
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }
}

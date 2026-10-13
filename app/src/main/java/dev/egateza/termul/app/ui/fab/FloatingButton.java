package dev.egateza.termul.app.ui.fab;

import dev.egateza.termul.app.ui.AppIcon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Tombol mengambang (floating action button) di atas konten lewat {@link FloatingOverlay}. Bulat kalau hanya ikon,
 * berbentuk pil kalau berlabel; label bisa disusutkan dengan animasi ({@link #setCollapsed}). Gaya dibaca dari tema
 * aktif saat digambar ({@link FloatingStyle}). Tidak pernah mengambil fokus, supaya ketikan di terminal tidak
 * berpindah ke tombol. EDT.
 */
public final class FloatingButton extends JButton {

    /** Ukuran tinggi badan tombol (tanpa bayangan). */
    public enum Size {
        NORMAL(48, 20, 14),
        MINI(34, 16, 12);

        final int height;
        final int iconSize;
        final int padding;

        Size(int height, int iconSize, int padding) {
            this.height = height;
            this.iconSize = iconSize;
            this.padding = padding;
        }
    }

    /** Ruang bayangan lembut di sekeliling badan tombol. */
    static final int SOFT_MARGIN = 4;
    private static final int GAP = 8;
    private static final int ANIMATION_MS = 160;

    private final FloatingStyle.Kind kind;
    private final Size size;
    /** 0 = label tampil penuh, 1 = hanya ikon. EDT. */
    private float collapse;
    private float collapseTarget;
    private final Timer animation = new Timer(15, e -> stepAnimation());
    private long animationStart;
    private float animationFrom;

    public FloatingButton(AppIcon icon, String text, FloatingStyle.Kind kind, Size size) {
        super(text);
        this.kind = kind;
        this.size = size;
        setIcon(icon.icon(size.iconSize, () -> style().foreground()));
        setFocusable(false);
        setRequestFocusEnabled(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setOpaque(false);
        getAccessibleContext().setAccessibleName(text);
    }

    public FloatingStyle style() {
        return FloatingStyle.current(kind);
    }

    /** Susutkan label (hanya ikon) atau tampilkan lagi, dengan animasi singkat. */
    public void setCollapsed(boolean collapsed) {
        float target = collapsed ? 1f : 0f;
        if (target == collapseTarget) {
            return;
        }
        collapseTarget = target;
        animationFrom = collapse;
        animationStart = System.nanoTime();
        animation.restart();
    }

    public boolean isCollapsed() {
        return collapseTarget == 1f;
    }

    private void stepAnimation() {
        float t = Math.min(1f, (System.nanoTime() - animationStart) / (ANIMATION_MS * 1_000_000f));
        float eased = 1f - (1f - t) * (1f - t);
        collapse = animationFrom + (collapseTarget - animationFrom) * eased;
        if (t >= 1f) {
            collapse = collapseTarget;
            animation.stop();
        }
        revalidate();
        repaint();
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        getAccessibleContext().setAccessibleName(text);
        revalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        FloatingStyle s = style();
        int h = size.height;
        int w = h;
        String text = getText();
        if (text != null && !text.isEmpty()) {
            FontMetrics fm = getFontMetrics(labelFont());
            int full = size.padding + size.iconSize + GAP + fm.stringWidth(text) + size.padding + 4;
            w = Math.max(h, Math.round(full + (h - full) * collapse));
        }
        int pad = shadowPad(s);
        return new Dimension(w + pad, h + pad);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    /** Hanya badan tombol yang menerima klik; ruang bayangan tembus ke konten di bawahnya. */
    @Override
    public boolean contains(int x, int y) {
        return body(style(), false).contains(x, y);
    }

    @Override
    protected void paintComponent(Graphics g) {
        FloatingStyle s = style();
        var g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean pressed = getModel().isArmed() && getModel().isPressed();
            RoundRectangle2D body = body(s, pressed);
            paintShadow(g2, s, body, pressed);
            Color fill = pressed && !s.hardShadow() ? FloatingStyle.blend(s.background(), s.foreground(), 0.2f)
                    : getModel().isRollover() ? s.hoverBackground() : s.background();
            if (!isEnabled()) {
                fill = FloatingStyle.blend(fill, UIManager.getColor("Panel.background") == null ? Color.GRAY
                        : UIManager.getColor("Panel.background"), 0.5f);
            }
            g2.setColor(fill);
            g2.fill(body);
            if (s.borderWidth() > 0) {
                float bw = s.borderWidth();
                g2.setColor(s.borderColor());
                g2.setStroke(new BasicStroke(bw));
                g2.draw(new RoundRectangle2D.Float((float) body.getX() + bw / 2, (float) body.getY() + bw / 2,
                        (float) body.getWidth() - bw, (float) body.getHeight() - bw,
                        (float) Math.max(0, body.getArcWidth() - bw), (float) Math.max(0, body.getArcHeight() - bw)));
            }
            paintContent(g2, s, body.getBounds());
        } finally {
            g2.dispose();
        }
    }

    private void paintShadow(Graphics2D g2, FloatingStyle s, RoundRectangle2D body, boolean pressed) {
        if (s.hardShadow()) {
            if (pressed) {
                return; // ditekan "masuk" ke halaman
            }
            int o = s.shadowOffset();
            g2.setColor(s.shadowColor());
            g2.fill(new RoundRectangle2D.Double(body.getX() + o, body.getY() + o, body.getWidth(), body.getHeight(),
                    body.getArcWidth(), body.getArcHeight()));
            return;
        }
        Color base = s.shadowColor();
        for (int i = SOFT_MARGIN; i >= 1; i--) {
            int alpha = Math.round(base.getAlpha() * (SOFT_MARGIN - i + 1) / (float) (SOFT_MARGIN * 3));
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
            g2.fill(new RoundRectangle2D.Double(body.getX() - i, body.getY() - i + 2, body.getWidth() + 2 * i,
                    body.getHeight() + 2 * i, body.getArcWidth() + 2 * i, body.getArcHeight() + 2 * i));
        }
    }

    private void paintContent(Graphics2D g2, FloatingStyle s, Rectangle body) {
        Icon icon = getIcon();
        String text = getText();
        boolean hasText = text != null && !text.isEmpty() && collapse < 0.98f;
        if (!hasText) {
            icon.paintIcon(this, g2, body.x + (body.width - icon.getIconWidth()) / 2,
                    body.y + (body.height - icon.getIconHeight()) / 2);
            return;
        }
        // ikon bergeser dari rata kiri (label penuh) ke tengah (hanya ikon) mengikuti animasi
        int start = body.x + size.padding;
        int center = body.x + (body.height - icon.getIconWidth()) / 2;
        int left = Math.round(start + (center - start) * collapse);
        icon.paintIcon(this, g2, left, body.y + (body.height - icon.getIconHeight()) / 2);
        Shape clip = g2.getClip();
        g2.clipRect(body.x, body.y, body.width - size.padding / 2, body.height);
        g2.setFont(labelFont());
        g2.setColor(new Color(s.foreground().getRed(), s.foreground().getGreen(), s.foreground().getBlue(),
                Math.round(255 * (1f - collapse))));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, left + size.iconSize + GAP,
                body.y + (body.height - fm.getHeight()) / 2 + fm.getAscent());
        g2.setClip(clip);
    }

    /** Badan tombol di dalam komponen; sisanya ruang bayangan. */
    private RoundRectangle2D body(FloatingStyle s, boolean pressed) {
        int pad = shadowPad(s);
        double w = getWidth() - pad;
        double h = getHeight() - pad;
        double x = s.hardShadow() ? (pressed ? s.shadowOffset() : 0) : SOFT_MARGIN / 2.0;
        double y = s.hardShadow() ? (pressed ? s.shadowOffset() : 0) : SOFT_MARGIN / 2.0 - 1;
        double arc = Math.min(s.arc(), Math.min(w, h));
        return new RoundRectangle2D.Double(x, Math.max(0, y), w, h, arc, arc);
    }

    private static int shadowPad(FloatingStyle s) {
        return s.hardShadow() ? s.shadowOffset() : SOFT_MARGIN;
    }

    private java.awt.Font labelFont() {
        var base = UIManager.getFont("Label.font");
        if (base == null) {
            base = getFont();
        }
        return base.deriveFont(java.awt.Font.BOLD, size == Size.MINI ? base.getSize2D() - 0.5f : base.getSize2D() + 0.5f);
    }

    @Override
    public void removeNotify() {
        animation.stop();
        super.removeNotify();
    }
}

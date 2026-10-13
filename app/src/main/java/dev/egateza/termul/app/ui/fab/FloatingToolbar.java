package dev.egateza.termul.app.ui.fab;

import dev.egateza.termul.app.ui.AppIcon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * Beberapa aksi kontekstual dalam satu pil mengambang (mis. Cari / Salin / Sudo di atas terminal). Gaya wadah
 * mengikuti {@link FloatingStyle.Kind#SECONDARY}; tombolnya tombol toolbar biasa yang tidak bisa difokus, supaya
 * ketikan tetap di terminal. EDT.
 */
public final class FloatingToolbar extends JPanel {

    private static final int PAD = 4;

    public FloatingToolbar() {
        super(new FlowLayout(FlowLayout.CENTER, 2, 0));
        setOpaque(false);
        updateInsets();
    }

    /** @return tombol yang ditambahkan, untuk diatur tampil/sembunyinya */
    public JButton addAction(AppIcon icon, String text, String tooltip, Runnable action) {
        var b = new JButton(text, icon.icon(14));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setFocusable(false);
        b.setRequestFocusEnabled(false);
        b.setToolTipText(tooltip);
        b.addActionListener(e -> action.run());
        add(b);
        return b;
    }

    /** true kalau ada tombol yang tampil; toolbar tanpa tombol sebaiknya disembunyikan. */
    public boolean hasVisibleActions() {
        for (var c : getComponents()) {
            if (c.isVisible()) {
                return true;
            }
        }
        return false;
    }

    private void updateInsets() {
        FloatingStyle s = FloatingStyle.current(FloatingStyle.Kind.SECONDARY);
        int shadow = s.hardShadow() ? s.shadowOffset() : FloatingButton.SOFT_MARGIN;
        int edge = PAD + s.borderWidth();
        setBorder(BorderFactory.createEmptyBorder(edge, edge + 4, edge + shadow, edge + 4 + shadow));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (getLayout() != null) {
            updateInsets(); // ganti tema: ketebalan border/bayangan bisa berubah
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        FloatingStyle s = FloatingStyle.current(FloatingStyle.Kind.SECONDARY);
        var g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int shadow = s.hardShadow() ? s.shadowOffset() : FloatingButton.SOFT_MARGIN;
            double w = getWidth() - shadow;
            double h = getHeight() - shadow;
            double arc = Math.min(s.arc(), h);
            if (s.hardShadow()) {
                g2.setColor(s.shadowColor());
                g2.fill(new RoundRectangle2D.Double(shadow, shadow, w, h, arc, arc));
            } else {
                Color c = s.shadowColor();
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha() / 3));
                g2.fill(new RoundRectangle2D.Double(1, 3, w + 2, h + 1, arc, arc));
            }
            var body = new RoundRectangle2D.Double(0, 0, w, h, arc, arc);
            g2.setColor(s.background());
            g2.fill(body);
            if (s.borderWidth() > 0) {
                float bw = s.borderWidth();
                g2.setColor(s.borderColor());
                g2.setStroke(new BasicStroke(bw));
                g2.draw(new RoundRectangle2D.Double(bw / 2, bw / 2, w - bw, h - bw, Math.max(0, arc - bw),
                        Math.max(0, arc - bw)));
            }
        } finally {
            g2.dispose();
        }
    }
}

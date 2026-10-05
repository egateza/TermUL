package dev.egateza.termul.app.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;
import javax.swing.UIManager;

/** Ikon "panel samping" (jendela dengan kolom kiri) untuk tombol panel host di tab bar; warna teks tema. */
final class SidebarIcon implements Icon {

    private static final int SIZE = AppIcon.SIZE;

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        var g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Color fg = UIManager.getColor("Label.foreground");
            g2.setColor(fg != null ? fg : Color.LIGHT_GRAY);
            g2.translate(x, y);
            g2.setStroke(new BasicStroke(1.3f));
            g2.draw(new RoundRectangle2D.Float(1.5f, 2.5f, 13f, 11f, 3.5f, 3.5f));
            g2.draw(new Line2D.Float(6f, 2.5f, 6f, 13.5f));
            g2.fill(new RoundRectangle2D.Float(2.6f, 3.6f, 2.6f, 8.8f, 1.5f, 1.5f));
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }
}

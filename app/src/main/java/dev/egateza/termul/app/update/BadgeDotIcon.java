package dev.egateza.termul.app.update;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;
import javax.swing.UIManager;

/** Titik notifikasi (badge) untuk tombol/menu update; warna mengikuti tema (FlatLaf {@code Actions.Red}). */
public final class BadgeDotIcon implements Icon {

    private static final int SIZE = 8;
    private static final Color FALLBACK = new Color(0xE5, 0x53, 0x53);

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        var g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = UIManager.getColor("Actions.Red");
            g2.setColor(color == null ? FALLBACK : color);
            g2.fillOval(x, y, SIZE, SIZE);
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

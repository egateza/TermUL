package dev.egateza.termul.app.monitor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.JComponent;

/**
 * Grafik kecil gaya Task Manager klasik: latar hitam, grid hijau tua yang bergeser ke kiri tiap sampel, dan grafik
 * area hijau dari {@link History} (skala 0..1). Grafik berubah kuning/merah saat nilai terakhir mendekati batas
 * ({@link Level}); garis putus-putus merah menandai {@link Level#CRITICAL_AT}. Teks kecil (mis. {@code CPU 3%}) di
 * pojok kiri atas. Warnanya sengaja tetap (tidak ikut tema) supaya terbaca sama di tema terang maupun gelap. EDT.
 */
final class Sparkline extends JComponent {

    static final int GRID = 6;
    private static final Color BACKGROUND = Color.BLACK;
    private static final Color GRID_COLOR = new Color(0x00, 0x5A, 0x20);
    private static final Color BORDER = new Color(0x3C, 0x3C, 0x3C);
    private static final Color LIMIT = new Color(0xFF, 0x40, 0x40, 150);
    private static final Color NORMAL = new Color(0x00, 0xE6, 0x40);
    private static final Color WARN = new Color(0xFF, 0xD0, 0x00);
    private static final Color CRITICAL = new Color(0xFF, 0x48, 0x48);

    private final History history;
    private String text = "";
    private Level level = Level.NORMAL;

    Sparkline(History history, int width, int height) {
        this.history = history;
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
        setOpaque(true);
    }

    void update(String text, Level level) {
        this.text = text;
        this.level = level;
        repaint();
    }

    static Color colorOf(Level level) {
        return switch (level) {
            case NORMAL -> NORMAL;
            case WARN -> WARN;
            case CRITICAL -> CRITICAL;
        };
    }

    @Override
    protected void paintComponent(Graphics g) {
        var g2 = (Graphics2D) g.create();
        try {
            int w = getWidth();
            int h = getHeight();
            g2.setColor(BACKGROUND);
            g2.fillRect(0, 0, w, h);
            paintGrid(g2, w, h);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintGraph(g2, w, h, colorOf(level));

            g2.setColor(LIMIT);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {2f, 2f}, 0f));
            int limitY = (int) Math.round((h - 1) * (1 - Level.CRITICAL_AT));
            g2.drawLine(0, limitY, w - 1, limitY);

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(BORDER);
            g2.drawRect(0, 0, w - 1, h - 1);

            if (!text.isEmpty()) {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                var font = getFont();
                g2.setFont(font.deriveFont(Font.BOLD, Math.max(9f, Math.min(font.getSize2D() - 1, h - 4f))));
                var fm = g2.getFontMetrics();
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.setColor(BACKGROUND);
                g2.drawString(text, 4, ty + 1); // bayangan supaya tetap terbaca di atas grafik
                g2.setColor(colorOf(level).brighter());
                g2.drawString(text, 3, ty);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintGrid(Graphics2D g2, int w, int h) {
        g2.setColor(GRID_COLOR);
        for (int y = h - 1; y >= 0; y -= GRID) {
            g2.drawLine(0, y, w - 1, y);
        }
        int shift = (int) (history.added() % GRID); // grid ikut bergeser bersama grafik
        for (int x = w - 1 - (GRID - shift) % GRID; x >= 0; x -= GRID) {
            g2.drawLine(x, 0, x, h - 1);
        }
    }

    private void paintGraph(Graphics2D g2, int w, int h, Color color) {
        int n = history.size();
        if (n < 1) {
            return;
        }
        double step = (double) (w - 1) / Math.max(1, history.capacity() - 1);
        double x0 = (history.capacity() - n) * step; // sampel terbaru di tepi kanan, riwayat memanjang ke kiri
        var line = new Path2D.Double();
        var area = new Path2D.Double();
        boolean started = false;
        double lastX = x0;
        for (int i = 0; i < n; i++) {
            double v = history.get(i);
            if (Double.isNaN(v)) {
                continue;
            }
            double x = x0 + i * step;
            double y = (h - 1) * (1 - Math.max(0, Math.min(1, v)));
            if (!started) {
                line.moveTo(x, y);
                area.moveTo(x, h);
                area.lineTo(x, y);
                started = true;
            } else {
                line.lineTo(x, y);
                area.lineTo(x, y);
            }
            lastX = x;
        }
        if (!started) {
            return;
        }
        area.lineTo(lastX, h);
        area.closePath();
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 110));
        g2.fill(area);
        g2.setColor(color);
        g2.draw(line);
    }
}

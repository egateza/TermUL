package dev.egateza.termul.app.ui.anim;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;

/** Helper gambar kecil yang dipakai beberapa animasi. */
final class Pixels {

    private Pixels() {
    }

    /** Sprite pixel-art: tiap {@code 'X'} di {@code rows} menjadi kotak {@code px}×{@code px}. */
    static void sprite(Graphics2D g, String[] rows, double x, double y, int px, Color color) {
        g.setColor(color);
        for (int j = 0; j < rows.length; j++) {
            String row = rows[j];
            for (int i = 0; i < row.length(); i++) {
                if (row.charAt(i) == 'X') {
                    g.fillRect((int) Math.round(x + i * px), (int) Math.round(y + j * px), px, px);
                }
            }
        }
    }

    static int width(String[] rows) {
        return rows[0].length();
    }

    /** Lingkaran terisi dengan pusat ({@code cx},{@code cy}). */
    static void dot(Graphics2D g, double cx, double cy, double r) {
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }
}

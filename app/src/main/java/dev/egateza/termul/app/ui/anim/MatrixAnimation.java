package dev.egateza.termul.app.ui.anim;

import java.awt.Graphics2D;
import java.util.SplittableRandom;

/**
 * Hujan karakter ala Matrix. Hanya karakter ASCII (hex dan simbol shell) supaya tidak bergantung pada font yang punya
 * glyph katakana.
 */
final class MatrixAnimation implements Animation {

    private static final String GLYPHS = "0123456789ABCDEF$#{}<>/=+*:;~";

    private static final class Column {
        double y;
        double speed;
        final char[] chars;

        Column(int length) {
            chars = new char[length];
        }
    }

    private final float fontSize;
    private final int colWidth;
    private final int rows;
    private final int trail;
    private final Column[] columns;
    private final SplittableRandom random = new SplittableRandom(3);

    MatrixAnimation(Size size) {
        boolean large = size.large();
        fontSize = large ? 11f : 8f;
        colWidth = large ? 10 : 7;
        rows = (int) Math.ceil(size.height() / fontSize);
        trail = large ? 5 : 3;
        columns = new Column[size.width() / colWidth];
        for (int i = 0; i < columns.length; i++) {
            var col = new Column(rows + 8);
            col.y = -random.nextDouble() * rows * 3;
            col.speed = (large ? 0.18 : 0.12) + random.nextDouble() * (large ? 0.3 : 0.2);
            for (int k = 0; k < col.chars.length; k++) {
                col.chars[k] = glyph();
            }
            columns[i] = col;
        }
    }

    private char glyph() {
        return GLYPHS.charAt(random.nextInt(GLYPHS.length()));
    }

    @Override
    public void step() {
        for (var col : columns) {
            col.y += col.speed;
            if (col.y - trail > rows) {
                col.y = -random.nextDouble() * rows * 2;
            }
            if (random.nextDouble() < 0.05) {
                col.chars[random.nextInt(col.chars.length)] = glyph();
            }
        }
    }

    @Override
    public void paint(Graphics2D g, int w, int h, Palette c) {
        g.setFont(Palette.mono(fontSize));
        int ascent = g.getFontMetrics().getAscent();
        for (int i = 0; i < columns.length; i++) {
            var col = columns[i];
            int head = (int) Math.floor(col.y);
            for (int k = 0; k <= trail; k++) {
                int row = head - k;
                if (row < 0 || row >= rows) {
                    continue;
                }
                double a = k == 0 ? 1 : (1 - k / (trail + 1.0)) * 0.85;
                g.setColor(Palette.alpha(k == 0 ? c.fg() : c.green(), a));
                g.drawString(String.valueOf(col.chars[row % col.chars.length]), i * colWidth + 1,
                        Math.round(row * fontSize) + ascent);
            }
        }
    }
}

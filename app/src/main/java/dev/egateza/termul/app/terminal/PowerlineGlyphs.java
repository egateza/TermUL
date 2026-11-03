package dev.egateza.termul.app.terminal;

import java.awt.BasicStroke;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * Pemisah powerline (U+E0B0–U+E0B7: segitiga, chevron, setengah lingkaran) sebagai bentuk seukuran satu sel penuh,
 * seperti Windows Terminal dan iTerm. Glyph dari font tidak setinggi sel (tinggi baris lebih besar dari tinggi
 * glyph), sehingga sambungan antar-segmen prompt (oh-my-posh, starship, Powerlevel10k) terpotong miring di atas dan
 * bawah. Murni geometri, bisa dites.
 */
final class PowerlineGlyphs {

    private PowerlineGlyphs() {
    }

    /** @return true kalau {@code cp} digambar sendiri oleh kelas ini */
    static boolean handles(int cp) {
        return cp >= 0xE0B0 && cp <= 0xE0B7;
    }

    /** @return bentuk glyph {@code cp} di {@code cell}, atau null kalau tidak ditangani */
    static Shape shape(int cp, Rectangle cell) {
        double x = cell.x;
        double y = cell.y;
        double w = cell.width;
        double h = cell.height;
        float line = (float) Math.max(1, Math.round(w / 8.0)); // tebal garis varian tipis
        return switch (cp) {
            case 0xE0B0 -> triangle(x, y, x + w, y + h / 2, x, y + h); // ▶ penuh
            case 0xE0B2 -> triangle(x + w, y, x, y + h / 2, x + w, y + h); // ◀ penuh
            case 0xE0B1 -> stroke(polyline(x, y, x + w, y + h / 2, x, y + h), line); // 〉 tipis
            case 0xE0B3 -> stroke(polyline(x + w, y, x, y + h / 2, x + w, y + h), line); // 〈 tipis
            case 0xE0B4 -> clipped(new Ellipse2D.Double(x - w, y, 2 * w, h), cell); // ◗ setengah lingkaran kanan
            case 0xE0B6 -> clipped(new Ellipse2D.Double(x, y, 2 * w, h), cell); // ◖ setengah lingkaran kiri
            case 0xE0B5 -> clipped(stroke(new Arc2D.Double(x - w, y, 2 * w, h, -90, 180, Arc2D.OPEN), line), cell);
            case 0xE0B7 -> clipped(stroke(new Arc2D.Double(x, y, 2 * w, h, 90, 180, Arc2D.OPEN), line), cell);
            default -> null;
        };
    }

    private static Shape triangle(double x1, double y1, double x2, double y2, double x3, double y3) {
        var p = polyline(x1, y1, x2, y2, x3, y3);
        p.closePath();
        return p;
    }

    private static Path2D polyline(double x1, double y1, double x2, double y2, double x3, double y3) {
        var p = new Path2D.Double();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        p.lineTo(x3, y3);
        return p;
    }

    private static Shape stroke(Shape s, float width) {
        return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER).createStrokedShape(s);
    }

    private static Shape clipped(Shape s, Rectangle cell) {
        var area = new Area(s);
        area.intersect(new Area(cell));
        return area;
    }
}

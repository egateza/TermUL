package dev.egateza.termul.app.terminal;

import com.jediterm.terminal.CursorShape;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

/**
 * Geometri kursor garis (bawah / tegak) yang digambar sendiri oleh panel terminal. JediTerm 3.76 menggambar garis
 * 2 px; garis tegaknya setinggi sel penuh (termasuk spasi baris dan ruang aksen di atas huruf) dan garis bawahnya di
 * luar sel (menimpa baris berikutnya). Di sini garisnya tipis, garis tegak setinggi huruf yang benar-benar tergambar
 * (puncak {@code H}/{@code l} sampai ekor {@code g}/{@code y}), dan garis bawah tepat di bawah ekor huruf, di dalam sel.
 */
final class LineCursor {

    /** Tebal garis, px (koordinat user; di layar ber-scaling ikut membesar sedikit). */
    static final int THICKNESS = 1;
    /** Huruf tertinggi dan berekor terdalam yang umum di prompt/perintah. */
    private static final String SAMPLE = "Hlbdfhk|gjpqy";

    /**
     * Rentang tegak huruf relatif terhadap baseline, px.
     *
     * @param top    puncak huruf (negatif: di atas baseline)
     * @param bottom ujung bawah ekor huruf (positif: di bawah baseline)
     */
    record Glyphs(int top, int bottom) {
        static Glyphs of(Font font, FontRenderContext frc) {
            Rectangle2D v = font.createGlyphVector(frc, SAMPLE).getVisualBounds();
            return new Glyphs((int) Math.floor(v.getMinY()), (int) Math.ceil(v.getMaxY()));
        }
    }

    private LineCursor() {
    }

    /** true kalau bentuk ini digambar sendiri (bukan blok bawaan JediTerm). */
    static boolean isLine(CursorShape shape) {
        return shape != null && shape != CursorShape.BLINK_BLOCK && shape != CursorShape.STEADY_BLOCK;
    }

    /**
     * @param cellX    kiri sel kursor, px
     * @param cellY    atas sel kursor, px
     * @param baseline baseline teks diukur dari atas sel (sama seperti JediTerm menggambar teks)
     * @return area garis kursor (selalu di dalam sel), atau null kalau bentuknya blok
     */
    static Rectangle bounds(CursorShape shape, int cellX, int cellY, int cellWidth, int cellHeight, int baseline,
                            Glyphs glyphs) {
        if (!isLine(shape)) {
            return null;
        }
        int top = Math.clamp(baseline + glyphs.top(), 0, cellHeight - THICKNESS);
        int bottom = Math.clamp(baseline + glyphs.bottom(), top + THICKNESS, cellHeight);
        return switch (shape) {
            case BLINK_VERTICAL_BAR, STEADY_VERTICAL_BAR ->
                    new Rectangle(cellX, cellY + top, THICKNESS, bottom - top);
            default -> new Rectangle(cellX, cellY + bottom - THICKNESS, cellWidth, THICKNESS);
        };
    }

    /**
     * Baseline teks dari atas sel, rumus yang sama dengan {@code TerminalPanel} JediTerm 3.76: spasi baris dibagi
     * atas-bawah, lalu descent font.
     */
    static int baseline(int cellHeight, int fontHeight, int descent) {
        int spaceBetweenLines = Math.max(0, (cellHeight - fontHeight) / 2 * 2);
        return cellHeight - spaceBetweenLines / 2 - descent;
    }
}

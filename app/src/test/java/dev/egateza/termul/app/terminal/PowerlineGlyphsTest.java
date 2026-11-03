package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

class PowerlineGlyphsTest {

    private static final Rectangle CELL = new Rectangle(10, 20, 8, 18);

    @Test
    void hanyaPemisahPowerlineYangDitangani() {
        assertThat(PowerlineGlyphs.handles(0xE0B0)).isTrue();
        assertThat(PowerlineGlyphs.handles(0xE0B7)).isTrue();
        assertThat(PowerlineGlyphs.handles(0xE0A0)).as("ikon git branch tetap dari font").isFalse();
        assertThat(PowerlineGlyphs.handles('A')).isFalse();
    }

    /** Glyph font tidak setinggi sel; bentuk ini harus menyentuh tepi atas dan bawah sel (sambungan tanpa celah). */
    @Test
    void segitigaPenuhMemenuhiTinggiSel() {
        var right = PowerlineGlyphs.shape(0xE0B0, CELL);
        assertThat(right.getBounds2D()).isEqualTo(new Rectangle(10, 20, 8, 18).getBounds2D());
        assertThat(right.contains(10.2, 20.5)).as("dekat pojok kiri atas").isTrue();
        assertThat(right.contains(17, 21)).as("pojok kanan atas kosong").isFalse();
        assertThat(right.contains(17, 29)).as("ujung di tengah kanan").isTrue();

        var left = PowerlineGlyphs.shape(0xE0B2, CELL);
        assertThat(left.contains(17.8, 20.5)).isTrue();
        assertThat(left.contains(11, 21)).isFalse();
    }

    @Test
    void setengahLingkaranTetapDiDalamSel() {
        for (int cp : new int[] {0xE0B4, 0xE0B5, 0xE0B6, 0xE0B7}) {
            var bounds = PowerlineGlyphs.shape(cp, CELL).getBounds2D(); // batas kurva bisa lebih longgar dari aslinya
            assertThat(bounds.getMinX()).as("U+%X", cp).isGreaterThanOrEqualTo(CELL.x - 0.5);
            assertThat(bounds.getMaxX()).as("U+%X", cp).isLessThanOrEqualTo(CELL.getMaxX() + 0.5);
        }
        var rightRound = PowerlineGlyphs.shape(0xE0B4, CELL);
        assertThat(rightRound.contains(11, 29)).isTrue();
        assertThat(rightRound.contains(17, 21)).as("pojok kanan atas membulat").isFalse();
    }

    @Test
    void variantTipisHanyaGaris() {
        var chevron = PowerlineGlyphs.shape(0xE0B1, CELL);
        assertThat(chevron.contains(11, 29)).as("bagian dalam chevron kosong").isFalse();
        assertThat(chevron.getBounds2D().getHeight()).isGreaterThanOrEqualTo(18);
    }

    @Test
    void codepointLainTidakPunyaBentuk() {
        assertThat(PowerlineGlyphs.shape(0xE0A0, CELL)).isNull();
    }
}

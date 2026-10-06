package dev.egateza.termul.app.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class BadgeDotIconTest {

    @Test
    void intiOpaqueMerahDenganHaloMemudarKeTepi() {
        var icon = new BadgeDotIcon();
        var img = paint(icon);
        int mid = icon.getIconWidth() / 2;

        int core = img.getRGB(mid, mid);
        assertThat(core >>> 24).isEqualTo(0xFF);
        assertThat((core >> 16) & 0xFF).isGreaterThan(core & 0xFF);

        int halo = img.getRGB(mid - 6, mid) >>> 24;
        assertThat(halo).isBetween(1, 0xFE);
        assertThat(img.getRGB(0, 0) >>> 24).isZero();
    }

    @Test
    void badgeBerdenyutTetapMenggambarHalo() {
        var icon = new BadgeDotIcon(true);
        var img = paint(icon);

        assertThat(img.getRGB(icon.getIconWidth() / 2 - 6, icon.getIconHeight() / 2) >>> 24).isPositive();
    }

    private static BufferedImage paint(BadgeDotIcon icon) {
        var img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        try {
            icon.paintIcon(null, g, 0, 0);
        } finally {
            g.dispose();
        }
        return img;
    }
}

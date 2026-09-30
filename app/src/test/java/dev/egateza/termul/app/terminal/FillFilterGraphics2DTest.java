package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class FillFilterGraphics2DTest {

    private static final Color SKIP = new Color(1, 36, 86);
    private static final Color UNDER = new Color(200, 100, 50); // isi gambar latar yang harus tetap terlihat

    private BufferedImage canvas() {
        var image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(UNDER);
        g.fillRect(0, 0, 20, 20);
        g.dispose();
        return image;
    }

    @Test
    void fillRectInSkipColorIsDropped() {
        var image = canvas();
        Graphics2D raw = image.createGraphics();
        var g = new FillFilterGraphics2D(raw, SKIP);

        g.setColor(SKIP);
        g.fillRect(0, 0, 20, 20);
        g.dispose();

        assertThat(image.getRGB(5, 5)).isEqualTo(UNDER.getRGB());
    }

    @Test
    void fillRectInOtherColorsIsPainted() {
        var image = canvas();
        Graphics2D raw = image.createGraphics();
        var g = new FillFilterGraphics2D(raw, SKIP);

        g.setColor(Color.RED);
        g.fillRect(0, 0, 10, 10);
        g.dispose();

        assertThat(image.getRGB(5, 5)).isEqualTo(Color.RED.getRGB());
        assertThat(image.getRGB(15, 15)).isEqualTo(UNDER.getRGB());
    }

    @Test
    void createdChildGraphicsKeepsFiltering() {
        var image = canvas();
        Graphics2D raw = image.createGraphics();
        var g = new FillFilterGraphics2D(raw, SKIP);
        Graphics2D child = (Graphics2D) g.create();

        child.setColor(SKIP);
        child.fillRect(0, 0, 20, 20);
        child.setColor(Color.GREEN);
        child.fillRect(0, 0, 4, 4);
        child.dispose();
        g.dispose();

        assertThat(child).isInstanceOf(FillFilterGraphics2D.class);
        assertThat(image.getRGB(10, 10)).isEqualTo(UNDER.getRGB());
        assertThat(image.getRGB(1, 1)).isEqualTo(Color.GREEN.getRGB());
    }

    @Test
    void otherDrawingOperationsAreNotFiltered() {
        // teks inverse digambar dengan warna latar default sebagai warna teks: tidak boleh ikut terbuang
        var image = canvas();
        Graphics2D raw = image.createGraphics();
        var g = new FillFilterGraphics2D(raw, SKIP);

        g.setColor(SKIP);
        g.drawLine(0, 10, 19, 10);
        g.fill(new java.awt.Rectangle(0, 0, 3, 3));
        g.dispose();

        assertThat(image.getRGB(10, 10)).isEqualTo(SKIP.getRGB());
        assertThat(image.getRGB(1, 1)).isEqualTo(SKIP.getRGB());
    }
}

package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackgroundImagesTest {

    @TempDir
    Path dir;

    private static BufferedImage solid(int w, int h, Color c) {
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }

    @Test
    void coverCropsSidesOfWideImage() {
        // gambar 400x100 (4:1) ke area 100x100 (1:1): ambil kotak 100x100 di tengah
        assertThat(BackgroundImages.coverSource(400, 100, 100, 100)).isEqualTo(new Rectangle(150, 0, 100, 100));
    }

    @Test
    void coverCropsTopAndBottomOfTallImage() {
        assertThat(BackgroundImages.coverSource(100, 400, 100, 100)).isEqualTo(new Rectangle(0, 150, 100, 100));
    }

    @Test
    void coverKeepsWholeImageWhenRatioMatches() {
        assertThat(BackgroundImages.coverSource(800, 400, 200, 100)).isEqualTo(new Rectangle(0, 0, 800, 400));
    }

    @Test
    void coverProducesRequestedSizeAndKeepsCenterColor() {
        // kiri merah, tengah biru, kanan merah; area persegi hanya mengambil bagian tengah
        var image = solid(300, 100, Color.RED);
        var g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(100, 0, 100, 100);
        g.dispose();

        var out = BackgroundImages.cover(image, 50, 50);

        assertThat(out.getWidth()).isEqualTo(50);
        assertThat(out.getHeight()).isEqualTo(50);
        assertThat(out.getRGB(25, 25) & 0xFFFFFF).isEqualTo(Color.BLUE.getRGB() & 0xFFFFFF);
        assertThat(out.getRGB(2, 25) & 0xFFFFFF).isEqualTo(Color.BLUE.getRGB() & 0xFFFFFF); // tepi pun biru: merah terpotong
    }

    @Test
    void loadReadsPngFromDisk() throws IOException {
        Path file = dir.resolve("latar.png");
        ImageIO.write(solid(40, 30, Color.GREEN), "png", file.toFile());

        var image = BackgroundImages.load(file.toString());

        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(40);
        assertThat(image.getHeight()).isEqualTo(30);
    }

    @Test
    void loadReturnsNullForMissingBlankOrNonImage() throws IOException {
        Path text = dir.resolve("bukan-gambar.png");
        Files.writeString(text, "ini bukan gambar");

        assertThat(BackgroundImages.load(dir.resolve("tidak-ada.png").toString())).isNull();
        assertThat(BackgroundImages.load(text.toString())).isNull();
        assertThat(BackgroundImages.load(dir.toString())).isNull(); // folder
        assertThat(BackgroundImages.load("  ")).isNull();
        assertThat(BackgroundImages.load(null)).isNull();
        assertThat(BackgroundImages.load("\0tidak-valid")).isNull(); // path tidak sah
    }

    @Test
    void largeImagesAreShrunkKeepingRatio() {
        var big = solid(BackgroundImages.MAX_SIDE * 2, 1000, Color.BLACK);

        var shrunk = BackgroundImages.shrink(big);

        assertThat(shrunk.getWidth()).isEqualTo(BackgroundImages.MAX_SIDE);
        assertThat(shrunk.getHeight()).isEqualTo(500);
    }

    @Test
    void smallImagesAreLeftAlone() {
        var small = solid(100, 100, Color.BLACK);

        assertThat(BackgroundImages.shrink(small)).isSameAs(small);
    }
}

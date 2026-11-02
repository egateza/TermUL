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

    @Test
    void blurRadiusFollowsAreaNotImageResolution() {
        assertThat(BackgroundImages.blurRadius(800, 600, 0)).isZero();
        assertThat(BackgroundImages.blurRadius(800, 600, 100)).isEqualTo(30); // 5% dari sisi terpendek
        assertThat(BackgroundImages.blurRadius(800, 600, 50)).isEqualTo(15);
        assertThat(BackgroundImages.blurRadius(800, 600, 500)).isEqualTo(30); // dibatasi ke 100%
        assertThat(BackgroundImages.blurRadius(0, 600, 100)).isZero();
    }

    @Test
    void boxBlurSoftensHardEdgeButKeepsFlatAreas() {
        // separuh kiri hitam, separuh kanan putih
        var image = solid(100, 20, Color.BLACK);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(50, 0, 50, 20);
        g.dispose();

        BackgroundImages.boxBlur(image, 4);

        int atEdge = image.getRGB(50, 10) & 0xFF;
        assertThat(atEdge).isBetween(60, 200); // tepi jadi abu-abu
        assertThat(image.getRGB(2, 10) & 0xFF).isZero(); // jauh dari tepi tetap hitam
        assertThat(image.getRGB(97, 10) & 0xFF).isEqualTo(255); // tepi gambar tidak menggelap
        assertThat(image.getRGB(97, 0) & 0xFF).isEqualTo(255);
    }

    @Test
    void coverWithBlurHasAreaSizeAndKeepsUniformColor() {
        var image = solid(640, 400, new Color(10, 120, 200));

        var blurred = BackgroundImages.cover(image, 300, 200, 80);

        assertThat(blurred.getWidth()).isEqualTo(300);
        assertThat(blurred.getHeight()).isEqualTo(200);
        assertThat(new Color(blurred.getRGB(150, 100))).isEqualTo(new Color(10, 120, 200));
        assertThat(new Color(blurred.getRGB(0, 0))).isEqualTo(new Color(10, 120, 200));
    }

    @Test
    void coverWithoutBlurIsPlainCover() {
        var image = solid(64, 64, Color.RED);
        var sharp = BackgroundImages.cover(image, 32, 32, 0);
        assertThat(new Color(sharp.getRGB(5, 5))).isEqualTo(Color.RED);
        assertThat(sharp.getWidth()).isEqualTo(32);
    }
}

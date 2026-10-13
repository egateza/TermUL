package dev.egateza.termul.app.ui.streak;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class FlameActorTest {

    private static BufferedImage paint(FlameLook look, double size, FlameActor actor, int frames) {
        var image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < frames; i++) {
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(0, 0, 200, 200);
            g.setComposite(java.awt.AlphaComposite.SrcOver);
            actor.paint(g, 100, 180, size, i * .04, .04, look);
        }
        g.dispose();
        return image;
    }

    private static int opaquePixels(BufferedImage image) {
        int n = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 0) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    void semuaKeadaanTergambarDiUkuranBesarDanKecil() {
        var looks = new FlameLook[] {FlameLook.active(1), FlameLook.active(47), FlameLook.active(800),
                new FlameLook(FlameLook.Mode.GRAY, 47, 1, 0), new FlameLook(FlameLook.Mode.FROZEN, 47, 5, 3)};
        for (var look : looks) {
            for (double size : new double[] {18, 140}) {
                assertThat(opaquePixels(paint(look, size, new FlameActor(), 3))).as("%s ukuran %s", look, size)
                        .isPositive();
            }
        }
    }

    @Test
    void apiBesarMengeluarkanPercikanApiKecilTidak() {
        var big = new FlameActor();
        paint(FlameLook.active(200), 140, big, 20);
        assertThat(big.particleCount()).isPositive();

        var small = new FlameActor();
        paint(FlameLook.active(200), 18, small, 20);
        assertThat(small.particleCount()).isZero();
    }

    @Test
    void apiPadamSampaiHilangLaluMenyalaLagi() {
        var actor = new FlameActor();
        actor.extinguish(StreakTier.MAGENTA.palette(0), 1);
        var image = paint(FlameLook.active(47), 140, actor, 60); // 2,4 detik > durasi padam
        var center = new Color(image.getRGB(100, 120), true);
        assertThat(center.getAlpha()).as("api sudah hilang, tinggal asap tipis").isLessThan(200);

        actor.relight();
        var lit = paint(FlameLook.active(47), 140, actor, 1);
        assertThat(new Color(lit.getRGB(100, 150), true).getAlpha()).isEqualTo(255);
    }

    @Test
    void retakanEsBertambahSesuaiKlik() {
        assertThat(FlamePainter.CRACKS.length).isEqualTo(10);
        var none = paint(new FlameLook(FlameLook.Mode.FROZEN, 47, 3, 0), 140, new FlameActor(), 1);
        var some = paint(new FlameLook(FlameLook.Mode.FROZEN, 47, 3, 4), 140, new FlameActor(), 1);
        assertThat(none.getRGB(0, 0)).isEqualTo(some.getRGB(0, 0));
        int differing = 0;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 200; x++) {
                if (none.getRGB(x, y) != some.getRGB(x, y)) {
                    differing++;
                }
            }
        }
        assertThat(differing).isPositive();
    }
}

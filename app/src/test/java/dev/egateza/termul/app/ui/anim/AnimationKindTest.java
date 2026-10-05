package dev.egateza.termul.app.ui.anim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.config.AppConfig;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class AnimationKindTest {

    @Test
    void idUnikDanBolakBalik() {
        assertThat(Arrays.stream(AnimationKind.values()).map(AnimationKind::id)).doesNotHaveDuplicates()
                .doesNotContain(AppConfig.ANIMATION_OFF, AppConfig.ANIMATION_RANDOM);
        for (var k : AnimationKind.values()) {
            assertThat(AnimationKind.fromId(k.id())).isEqualTo(k);
        }
    }

    @Test
    void idTidakDikenalJatuhKePacman() {
        assertThat(AnimationKind.fromId("tidak-ada")).isEqualTo(AnimationKind.PACMAN);
        assertThat(AnimationKind.fromId(null)).isEqualTo(AnimationKind.PACMAN);
        assertThat(AnimationKind.fromId(AppConfig.DEFAULT_ANIMATION)).isEqualTo(AnimationKind.PACMAN);
    }

    @ParameterizedTest
    @EnumSource(AnimationKind.class)
    void punyaLabelDiSemuaBahasa(AnimationKind kind) {
        for (var language : I18n.SUPPORTED) {
            assertThat(I18n.tIn(language.tag(), "anim." + kind.id())).isNotEqualTo("anim." + kind.id());
        }
    }

    static Stream<Arguments> kindsAndSizes() {
        return Arrays.stream(AnimationKind.values())
                .flatMap(k -> Arrays.stream(Animation.Size.values()).map(s -> Arguments.of(k, s)));
    }

    /**
     * Jalankan ribuan frame (beberapa putaran, termasuk reset Snake/Invaders) sambil menggambar: tidak boleh
     * exception, dan harus ada yang tergambar di dalam area.
     */
    @ParameterizedTest
    @MethodSource("kindsAndSizes")
    void berjalanLamaTanpaErrorDanMenggambarSesuatu(AnimationKind kind, Animation.Size size) {
        var animation = kind.create(size, "termul@prod-01");
        var palette = new Palette(java.awt.Color.WHITE, java.awt.Color.LIGHT_GRAY, java.awt.Color.GRAY,
                java.awt.Color.BLUE, java.awt.Color.GREEN, java.awt.Color.ORANGE, java.awt.Color.DARK_GRAY,
                java.awt.Color.BLACK);
        boolean painted = false;
        for (int frame = 0; frame < 3000; frame++) {
            animation.step();
            if (frame % 37 == 0) {
                var image = new BufferedImage(size.width(), size.height(), BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try {
                    animation.paint(g, size.width(), size.height(), palette);
                } finally {
                    g.dispose();
                }
                painted |= hasPixel(image);
            }
        }
        assertThat(painted).as("%s %s menggambar sesuatu", kind, size).isTrue();
    }

    private static boolean hasPixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }
}

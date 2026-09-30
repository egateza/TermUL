package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AppIconTest {

    private static final Pattern VIEW_BOX = Pattern.compile("viewBox=\"(-?\\d+) (-?\\d+) (\\d+) (\\d+)\"");

    @ParameterizedTest
    @EnumSource(AppIcon.class)
    void resourceIsSquareSvg(AppIcon icon) throws IOException {
        try (var in = AppIcon.class.getClassLoader().getResourceAsStream(icon.resource())) {
            assertThat(in).as(icon.resource()).isNotNull();
            String svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            var m = VIEW_BOX.matcher(svg);
            assertThat(m.find()).as("viewBox %s", icon).isTrue();
            assertThat(m.group(3)).as("viewBox persegi %s", icon).isEqualTo(m.group(4));
            assertThat(svg).contains("Font Awesome Free");
        }
    }

    @ParameterizedTest
    @EnumSource(AppIcon.class)
    void paintsWithRequestedColor(AppIcon icon) {
        var svg = icon.icon(AppIcon.SIZE, () -> Color.RED);
        assertThat(svg.hasFound()).isTrue();
        assertThat(svg.getIconWidth()).isEqualTo(AppIcon.SIZE);

        var img = new BufferedImage(AppIcon.SIZE, AppIcon.SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        svg.paintIcon(null, g, 0, 0);
        g.dispose();

        boolean red = false;
        for (int x = 0; x < img.getWidth() && !red; x++) {
            for (int y = 0; y < img.getHeight() && !red; y++) {
                int argb = img.getRGB(x, y);
                red = (argb >>> 24) > 128 && ((argb >> 16) & 0xFF) > 200 && ((argb >> 8) & 0xFF) < 60;
            }
        }
        assertThat(red).as("%s tergambar merah", icon).isTrue();
    }

    @Test
    void licenseIsBundled() {
        assertThat(AppIcon.class.getClassLoader().getResource(AppIcon.DIR + "LICENSE.txt")).isNotNull();
    }
}

package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.swing.Icon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AppIconTest {

    private static final Pattern VIEW_BOX = Pattern.compile("viewBox=\"(-?[\\d.]+) (-?[\\d.]+) ([\\d.]+) ([\\d.]+)\"");

    static Stream<Arguments> allIcons() {
        return Arrays.stream(IconSet.values())
                .flatMap(set -> Arrays.stream(AppIcon.values()).map(icon -> Arguments.of(set, icon)));
    }

    @AfterEach
    void resetSet() {
        AppIcon.use(IconSet.FONT_AWESOME);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("allIcons")
    void everySetHasSquareSvg(IconSet set, AppIcon icon) throws IOException {
        try (InputStream in = AppIcon.class.getClassLoader().getResourceAsStream(icon.resource(set))) {
            assertThat(in).as("%s belum ada; jalankan tools/AddIcon.java", icon.resource(set)).isNotNull();
            String svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            var m = VIEW_BOX.matcher(svg);
            assertThat(m.find()).as("viewBox %s", icon).isTrue();
            assertThat(m.group(3)).as("viewBox persegi %s %s", set, icon).isEqualTo(m.group(4));
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("allIcons")
    void paintsWithRequestedColor(IconSet set, AppIcon icon) {
        AppIcon.use(set);
        Icon i = icon.icon(AppIcon.SIZE, () -> Color.RED);
        assertThat(i.getIconWidth()).isEqualTo(AppIcon.SIZE);
        assertThat(icon.svg(set, AppIcon.SIZE, () -> Color.RED).hasFound()).isTrue();
        assertThat(hasRed(paint(i))).as("%s %s tergambar merah", set, icon).isTrue();
    }

    @Test
    void switchingSetChangesExistingIcon() {
        Icon icon = AppIcon.SERVER.icon(AppIcon.SIZE, () -> Color.RED);
        int[] fa = pixels(paint(icon));
        AppIcon.use(IconSet.MATERIAL);
        int[] material = pixels(paint(icon));
        assertThat(material).isNotEqualTo(fa);
    }

    @Test
    void manifestMatchesEnum() throws IOException {
        var manifest = new Properties();
        try (InputStream in = AppIcon.class.getClassLoader().getResourceAsStream(AppIcon.DIR + "icons.properties")) {
            assertThat(in).isNotNull();
            manifest.load(in);
        }
        assertThat(manifest.stringPropertyNames())
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(AppIcon.values()).map(Enum::name).toList());
    }

    @Test
    void licensesAreBundled() {
        for (IconSet set : IconSet.values()) {
            assertThat(AppIcon.class.getClassLoader().getResource(AppIcon.DIR + set.dir() + "/LICENSE.txt"))
                    .as(set.name()).isNotNull();
        }
    }

    @Test
    void unknownSetIdFallsBackToFontAwesome() {
        assertThat(IconSet.fromId("material")).isEqualTo(IconSet.MATERIAL);
        assertThat(IconSet.fromId(null)).isEqualTo(IconSet.FONT_AWESOME);
        assertThat(IconSet.fromId("tidak-ada")).isEqualTo(IconSet.FONT_AWESOME);
    }

    private static BufferedImage paint(Icon icon) {
        var img = new BufferedImage(AppIcon.SIZE, AppIcon.SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return img;
    }

    private static int[] pixels(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    private static boolean hasRed(BufferedImage img) {
        for (int argb : pixels(img)) {
            if ((argb >>> 24) > 128 && ((argb >> 16) & 0xFF) > 200 && ((argb >> 8) & 0xFF) < 60) {
                return true;
            }
        }
        return false;
    }
}

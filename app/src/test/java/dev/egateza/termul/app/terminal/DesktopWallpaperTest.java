package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.theme.ThemeTemplates;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopWallpaperTest {

    @TempDir
    Path dir;

    private Path file(String name) throws IOException {
        return Files.writeString(dir.resolve(name), "x");
    }

    @Test
    void apiPathWinsWhenTheFileExists() throws IOException {
        Path api = file("wall.jpg");
        Path transcoded = file("TranscodedWallpaper");

        assertThat(DesktopWallpaper.resolve(api::toString, transcoded)).contains(api.toString());
    }

    @Test
    void fallsBackToTranscodedWhenApiPathIsEmptyMissingOrInvalid() throws IOException {
        Path transcoded = file("TranscodedWallpaper");
        String expected = transcoded.toString();

        assertThat(DesktopWallpaper.resolve(() -> "", transcoded)).contains(expected);
        assertThat(DesktopWallpaper.resolve(() -> null, transcoded)).contains(expected);
        assertThat(DesktopWallpaper.resolve(() -> dir.resolve("hilang.jpg").toString(), transcoded)).contains(expected);
        assertThat(DesktopWallpaper.resolve(() -> "\0tidak-sah", transcoded)).contains(expected);
    }

    @Test
    void emptyWhenNothingExists() {
        assertThat(DesktopWallpaper.resolve(() -> dir.resolve("hilang.jpg").toString(), dir.resolve("juga-hilang")))
                .isEmpty();
        assertThat(DesktopWallpaper.resolve(() -> null, null)).isEmpty();
        assertThat(DesktopWallpaper.resolve(() -> dir.toString(), null)).isEmpty(); // folder bukan file
    }

    @Test
    void paletteKeyDistinguishesFileWallpaperAndNone() {
        var base = ThemeTemplates.dark("x", "X").terminal();

        assertThat(BackgroundImages.key(base)).isNull();
        assertThat(BackgroundImages.key(base.withBackgroundImage("C:/a.png", 40))).isEqualTo("C:/a.png");
        assertThat(BackgroundImages.key(base.withDesktopWallpaper(true))).isEqualTo(BackgroundImages.WALLPAPER_KEY);
        // flag wallpaper menang atas path file
        assertThat(BackgroundImages.key(base.withBackgroundImage("C:/a.png", 40).withDesktopWallpaper(true)))
                .isEqualTo(BackgroundImages.WALLPAPER_KEY);
    }

    @Test
    void loadForWithoutRequestedBackdropReturnsNull() {
        assertThat(BackgroundImages.loadFor(ThemeTemplates.dark("x", "X").terminal())).isNull();
    }
}

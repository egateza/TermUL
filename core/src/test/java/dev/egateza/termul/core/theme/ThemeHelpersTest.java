package dev.egateza.termul.core.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeHelpersTest {

    @TempDir
    Path dir;

    @Test
    void idFromNameIsSlugAndUnique() {
        assertThat(ThemeIds.fromName("Laut Malam", Set.of())).isEqualTo("laut-malam");
        assertThat(ThemeIds.fromName("  Éméraude!! ", Set.of())).isEqualTo("emeraude");
        assertThat(ThemeIds.fromName("default", Set.of("default"))).isEqualTo("default-2");
        assertThat(ThemeIds.fromName("x", Set.of("x", "x-2"))).isEqualTo("x-3");
        assertThat(ThemeIds.fromName("???", Set.of())).isEqualTo("tema");
    }

    @Test
    void longNameStillProducesValidId() {
        String id = ThemeIds.fromName("a".repeat(100), Set.of("a".repeat(34)));

        assertThat(id.length()).isLessThanOrEqualTo(40);
        assertThat(ThemeTemplates.dark(id, "X").id()).isEqualTo(id); // lolos validasi CustomTheme
    }

    @Test
    void contrastMatchesWcagReferenceValues() {
        assertThat(Contrast.ratio("#000000", "#FFFFFF")).isCloseTo(21.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(Contrast.ratio("#777777", "#777777")).isEqualTo(1.0);
        assertThat(Contrast.ratio("#FFFFFF", "#000000")).isEqualTo(Contrast.ratio("#000000", "#FFFFFF"));
        // abu-abu #767676 di atas putih = 4.54 (batas AA yang dikenal)
        assertThat(Contrast.ratio("#767676", "#FFFFFF")).isGreaterThan(Contrast.TEXT_MIN);
        assertThat(Contrast.ratio("#777777", "#FFFFFF")).isLessThan(Contrast.TEXT_MIN);
    }

    @Test
    void templatesAreReadable() {
        for (var t : new CustomTheme[] {ThemeTemplates.dark("a", "A"), ThemeTemplates.light("b", "B")}) {
            assertThat(Contrast.ratio(t.terminal().foreground(), t.terminal().background()))
                    .isGreaterThan(Contrast.TEXT_MIN);
        }
    }

    @Test
    void exportThenImportRoundTrips() throws IOException {
        var store = new ThemeStore(dir.resolve("themes"));
        var theme = ThemeTemplates.dark("dracula", "Dracula");
        Path file = dir.resolve("dracula-share.json"); // nama file bebas untuk impor/ekspor

        store.exportTo(theme, file);

        assertThat(store.importFrom(file)).isEqualTo(theme);
    }

    @Test
    void importRejectsInvalidThemeWithReadableMessage() throws IOException {
        var store = new ThemeStore(dir);
        Path file = dir.resolve("buruk.json");
        Files.writeString(file, """
                {"id":"x","name":"X","base":"dark",
                 "terminal":{"background":"#000000","foreground":"merah","selection":"#333333","ansi":[]}}
                """);

        assertThatThrownBy(() -> store.importFrom(file))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("#RRGGBB");
        Files.writeString(file, "bukan json");
        assertThatThrownBy(() -> store.importFrom(file)).isInstanceOf(IOException.class);
    }

    @Test
    void themeWithoutImageFieldsLoadsWithDefaults() throws IOException {
        // file tema dari versi sebelum ada gambar latar
        Path file = dir.resolve("lama.json");
        Files.writeString(file, """
                {"id":"lama","name":"Lama","base":"dark",
                 "terminal":{"background":"#012456","foreground":"#CCCCCC","selection":"#3A6EA5",
                  "ansi":["#000000","#000000","#000000","#000000","#000000","#000000","#000000","#000000",
                          "#000000","#000000","#000000","#000000","#000000","#000000","#000000","#000000"]}}
                """);

        var theme = new ThemeStore(dir).importFrom(file);

        assertThat(theme.terminal().backgroundImage()).isNull();
        assertThat(theme.terminal().imageVisibility())
                .isEqualTo(CustomTheme.TerminalPalette.DEFAULT_VISIBILITY);
    }

    @Test
    void backgroundImageRoundTripsAndIsNormalized() throws IOException {
        var base = ThemeTemplates.dark("x", "X");
        var withImage = new CustomTheme("x", "X", "dark", base.ui(),
                base.terminal().withBackgroundImage("  C:/gambar/laut.png ", 250));
        Path file = dir.resolve("x.json");
        var store = new ThemeStore(dir);

        store.exportTo(withImage, file);
        var loaded = store.importFrom(file);

        assertThat(loaded.terminal().backgroundImage()).isEqualTo("C:/gambar/laut.png");
        assertThat(loaded.terminal().imageVisibility()).isEqualTo(100); // dibatasi
        assertThat(base.terminal().withBackgroundImage("  ", -5).backgroundImage()).isNull();
        assertThat(base.terminal().withBackgroundImage(null, -5).imageVisibility()).isEqualTo(0);
    }

    @Test
    void tooLongImagePathIsRejected() {
        var terminal = ThemeTemplates.dark("x", "X").terminal();

        assertThatThrownBy(() -> terminal.withBackgroundImage("a".repeat(2000), 40))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void desktopWallpaperFlagDefaultsToFalseAndRoundTrips() throws IOException {
        var base = ThemeTemplates.dark("x", "X").terminal();
        assertThat(base.desktopWallpaper()).isFalse();
        assertThat(base.hasBackdrop()).isFalse();

        var withWallpaper = new CustomTheme("x", "X", "dark", ThemeTemplates.dark("x", "X").ui(),
                base.withDesktopWallpaper(true));
        Path file = dir.resolve("wall.json");
        var store = new ThemeStore(dir);
        store.exportTo(withWallpaper, file);
        var loaded = store.importFrom(file).terminal();

        assertThat(loaded.desktopWallpaper()).isTrue();
        assertThat(loaded.hasBackdrop()).isTrue();
        // mengganti file gambar tidak mematikan flag wallpaper, dan sebaliknya
        assertThat(loaded.withBackgroundImage("C:/a.png", 50).desktopWallpaper()).isTrue();
        assertThat(base.withBackgroundImage("C:/a.png", 50).hasBackdrop()).isTrue();
        assertThat(loaded.withDesktopWallpaper(false).hasBackdrop()).isFalse();
    }
}

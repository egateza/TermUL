package dev.egateza.termul.core.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.core.theme.CustomTheme.TerminalPalette;
import dev.egateza.termul.core.theme.CustomTheme.UiPalette;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeStoreTest {

    @TempDir
    Path dir;

    private static TerminalPalette terminal() {
        return new TerminalPalette("#1e1e1e", "#d4d4d4", "#264f78",
                Collections.nCopies(TerminalPalette.ANSI_COUNT, "#abcdef"));
    }

    private static CustomTheme theme(String id, String name) {
        return new CustomTheme(id, name, "dark", new UiPalette("#3b82f6", null, null, null, null), terminal());
    }

    @Test
    void saveThenListRoundTrips() {
        var store = new ThemeStore(dir.resolve("themes")); // direktori dibuat otomatis
        store.save(theme("nord", "Nord"));

        List<CustomTheme> all = store.list();

        assertThat(all).hasSize(1);
        CustomTheme loaded = all.getFirst();
        assertThat(loaded.id()).isEqualTo("nord");
        assertThat(loaded.ui().accent()).isEqualTo("#3B82F6"); // dinormalkan ke huruf besar
        assertThat(loaded.ui().background()).isNull();
        assertThat(loaded.terminal().ansi()).hasSize(16);
    }

    @Test
    void listIsEmptyWhenDirectoryMissing() {
        assertThat(new ThemeStore(dir.resolve("tidak-ada")).list()).isEmpty();
    }

    @Test
    void listIsSortedByName() {
        var store = new ThemeStore(dir);
        store.save(theme("b", "Zebra"));
        store.save(theme("a", "apel"));

        assertThat(store.list()).extracting(CustomTheme::name).containsExactly("apel", "Zebra");
    }

    @Test
    void corruptOrInvalidFilesAreSkippedAndKept() throws IOException {
        var store = new ThemeStore(dir);
        store.save(theme("ok", "Ok"));
        Files.writeString(dir.resolve("rusak.json"), "{ bukan json");
        Files.writeString(dir.resolve("warna-salah.json"), """
                {"id":"warna-salah","name":"X","base":"dark",
                 "terminal":{"background":"merah","foreground":"#fff000","selection":"#fff000","ansi":[]}}
                """);

        assertThat(store.list()).extracting(CustomTheme::id).containsExactly("ok");
        assertThat(dir.resolve("rusak.json")).exists();
    }

    @Test
    void fileNameMustMatchId() throws IOException {
        var store = new ThemeStore(dir);
        store.save(theme("asli", "Asli"));
        Files.move(dir.resolve("asli.json"), dir.resolve("lain.json"));

        assertThat(store.list()).isEmpty();
    }

    @Test
    void deleteRemovesFile() {
        var store = new ThemeStore(dir);
        store.save(theme("hapus", "Hapus"));

        assertThat(store.delete("hapus")).isTrue();
        assertThat(store.delete("hapus")).isFalse();
        assertThat(store.list()).isEmpty();
    }

    @Test
    void idCannotEscapeDirectory() {
        var store = new ThemeStore(dir);

        assertThatThrownBy(() -> store.delete("../config")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> theme("../x", "X")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> theme("A", "X")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesColorsAndAnsiCount() {
        assertThatThrownBy(() -> new UiPalette("biru", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("#RRGGBB");
        assertThatThrownBy(() -> new TerminalPalette("#000000", "#ffffff", "#333333", List.of("#000000")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("16");
    }

    @Test
    void unknownBaseFallsBackToDark() {
        var t = new CustomTheme("x", "X", "aneh", null, terminal());

        assertThat(t.base()).isEqualTo(CustomTheme.BASE_DARK);
        assertThat(t.ui().accent()).isNull();
    }
}

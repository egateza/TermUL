package dev.egateza.termul.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppPathsTest {

    @TempDir
    Path base;

    @Test
    void movesLegacyFolderWhenNewOneIsMissing() throws Exception {
        Path legacy = Files.createDirectories(base.resolve(AppPaths.LEGACY_APP_DIR));
        Files.writeString(legacy.resolve("profiles.json"), "{}");
        var notes = new ArrayList<String>();

        Path dir = AppPaths.appDir(base, notes);

        assertThat(dir).isEqualTo(base.resolve(AppPaths.APP_DIR));
        assertThat(dir.resolve("profiles.json")).hasContent("{}");
        assertThat(legacy).doesNotExist();
        assertThat(notes).singleElement().asString().contains("dipindah");
    }

    @Test
    void keepsNewFolderAndLeavesLegacyAlone() throws Exception {
        Files.createDirectories(base.resolve(AppPaths.APP_DIR));
        Path legacy = Files.createDirectories(base.resolve(AppPaths.LEGACY_APP_DIR));
        var notes = new ArrayList<String>();

        assertThat(AppPaths.appDir(base, notes)).isEqualTo(base.resolve(AppPaths.APP_DIR));
        assertThat(legacy).exists();
        assertThat(notes).isEmpty();
    }

    @Test
    void freshInstallUsesNewFolder() {
        var notes = new ArrayList<String>();

        assertThat(AppPaths.appDir(base, notes)).isEqualTo(base.resolve(AppPaths.APP_DIR));
        assertThat(notes).isEmpty();
    }
}

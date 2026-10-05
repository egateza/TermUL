package dev.egateza.termul.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
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

    @Test
    void windowsMemakaiAppDataDanLocalAppData() {
        Path roaming = base.resolve("AppData").resolve("Roaming");
        Path local = base.resolve("AppData").resolve("Local");
        var env = Map.of("APPDATA", roaming.toString(), "LOCALAPPDATA", local.toString());
        var b = AppPaths.bases(Os.WINDOWS, env::get, base);

        assertThat(b.configDir()).isEqualTo(roaming);
        assertThat(b.cacheDir()).isEqualTo(local);
    }

    @Test
    void macMemakaiFolderLibrary() {
        var b = AppPaths.bases(Os.MAC, Map.of("APPDATA", "abaikan")::get, base);

        assertThat(b.configDir()).isEqualTo(base.resolve("Library").resolve("Application Support"));
        assertThat(b.cacheDir()).isEqualTo(base.resolve("Library").resolve("Caches"));
    }

    @Test
    void osLainMengikutiXdgDenganFallbackDotConfig() {
        assertThat(AppPaths.bases(Os.OTHER, Map.<String, String>of()::get, base))
                .isEqualTo(new AppPaths(base.resolve(".config"), base.resolve(".cache")));
        var xdg = Map.of("XDG_CONFIG_HOME", base.resolve("cfg").toString(), "XDG_CACHE_HOME", " ");
        assertThat(AppPaths.bases(Os.OTHER, xdg::get, base))
                .isEqualTo(new AppPaths(base.resolve("cfg"), base.resolve(".cache")));
    }
}

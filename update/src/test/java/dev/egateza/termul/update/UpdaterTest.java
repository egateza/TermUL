package dev.egateza.termul.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdaterTest {

    @TempDir
    Path tmp;

    private final KeyPair key = Fixtures.keyPair();
    private final Fixtures.Release release = new Fixtures.Release().version("0.1.200")
            .jar("termul-app.jar", "app baru").jar("lib-a.jar", "library A");

    private Updater updater(Fixtures.FakeFeed feed, Updater.Environment env) {
        return new Updater(feed, new UpdateStore(tmp.resolve("updates")), key.getPublic(), env);
    }

    private static Updater.Environment env(String running, int generation) {
        return new Updater.Environment(ReleaseVersion.parseOrNull(running), generation, null, "");
    }

    @Test
    void newerVersionUntukBadge() throws Exception {
        assertThat(updater(release.feed(key), env("0.1.100", 1)).newerVersion()).contains(ReleaseVersion.parse("0.1.200"));
        assertThat(updater(release.feed(key), env("0.1.200", 1)).newerVersion()).isEmpty();
        assertThat(updater(release.feed(key), env("dev", 1)).newerVersion()).isEmpty();
        // tanpa Bootstrap / butuh installer tetap diberi tahu; dialog menjelaskan cara memasangnya
        assertThat(updater(release.generation(2).feed(key), env("0.1.100", 0)).newerVersion()).isPresent();
    }

    @Test
    void sudahTerbaru() throws Exception {
        var check = updater(release.feed(key), env("0.1.200", 1)).check();
        assertThat(check).isInstanceOf(Updater.UpToDate.class);
    }

    @Test
    void versiBerjalanLebihBaruDariRilisTetapTerbaru() throws Exception {
        var check = updater(release.feed(key), env("0.1.300", 1)).check();
        assertThat(check).isInstanceOf(Updater.UpToDate.class);
    }

    @Test
    void buildDevHanyaManual() throws Exception {
        var check = updater(release.feed(key), env("dev", 1)).check();
        assertThat(check).isEqualTo(new Updater.ManualOnly(check.latest(), Updater.Reason.DEV_BUILD));
    }

    @Test
    void tanpaBootstrapHanyaManual() throws Exception {
        var check = updater(release.feed(key), env("0.1.100", 0)).check();
        assertThat(check).isInstanceOf(Updater.ManualOnly.class)
                .extracting(c -> ((Updater.ManualOnly) c).reason()).isEqualTo(Updater.Reason.NO_BOOTSTRAP);
    }

    @Test
    void butuhInstallerBaru() throws Exception {
        var check = updater(release.generation(2).feed(key), env("0.1.100", 1)).check();
        assertThat(check).isInstanceOf(Updater.ManualOnly.class)
                .extracting(c -> ((Updater.ManualOnly) c).reason()).isEqualTo(Updater.Reason.NEEDS_INSTALLER);
    }

    @Test
    void tersediaDenganJarBawaanDipakaiUlang() throws Exception {
        var bundled = new Fixtures.Release().jar("lib-a.jar", "library A").writeJars(tmp.resolve("app"));
        String classPath = bundled.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
        var feed = release.feed(key);
        var u = updater(feed, new Updater.Environment(ReleaseVersion.parse("0.1.100"), 1, null, classPath));

        var available = (Updater.Available) u.check();
        var installed = u.install(available.plan(), n -> { });

        assertThat(available.plan().download()).extracting(UpdateManifest.FileEntry::name)
                .containsExactly("termul-app.jar");
        assertThat(feed.downloaded).containsExactly("termul-app.jar");
        assertThat(installed.manifest().version()).hasToString("0.1.200");
    }

    @Test
    void jarDariUpdateYangBerjalanDipakaiUlangDanFolderNyaDisimpan() throws Exception {
        var store = new UpdateStore(tmp.resolve("updates"));
        var old = new Fixtures.Release().version("0.1.150").jar("lib-a.jar", "library A").feed(key);
        store.install(InstallPlan.of(old.release, new LocalJars(java.util.List.of())), old, key.getPublic(), n -> { });
        var feed = release.feed(key);
        var running = ReleaseVersion.parse("0.1.150");
        var u = new Updater(feed, store, key.getPublic(), new Updater.Environment(running, 1, running, ""));

        u.install(((Updater.Available) u.check()).plan(), n -> { });

        assertThat(feed.downloaded).containsExactly("termul-app.jar");
        assertThat(store.versionDir(running)).isDirectory();
        assertThat(store.current()).contains(ReleaseVersion.parse("0.1.200"));
    }

    @Test
    void downgradeTidakDipasang() throws Exception {
        var feed = release.feed(key);
        var plan = InstallPlan.of(feed.release, new LocalJars(java.util.List.of()));

        assertThatThrownBy(() -> updater(feed, env("0.1.200", 1)).install(plan, n -> { }))
                .isInstanceOf(UpdateException.class);
        assertThat(Files.exists(tmp.resolve("updates").resolve("current"))).isFalse();
    }
}

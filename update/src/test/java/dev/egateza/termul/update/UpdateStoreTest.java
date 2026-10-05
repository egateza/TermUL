package dev.egateza.termul.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdateStoreTest {

    private static final ReleaseVersion BUNDLED = ReleaseVersion.parse("0.1.100");

    @TempDir
    Path tmp;

    private final KeyPair key = Fixtures.keyPair();
    private UpdateStore store;
    private Fixtures.Release release;

    @BeforeEach
    void setUp() {
        store = new UpdateStore(tmp.resolve("updates"));
        release = new Fixtures.Release().jar("termul-app.jar", "app baru").jar("lib-a.jar", "library A")
                .jar("lib-b.jar", "library B");
    }

    @Test
    void jarLokalDisalinSisanyaDiunduh() throws Exception {
        var bundled = new Fixtures.Release().jar("lib-a-lama-namanya.jar", "library A").writeJars(tmp.resolve("app"));
        var feed = release.feed(key);
        var plan = InstallPlan.of(feed.release, new LocalJars(bundled));
        var bytes = new AtomicLong();

        var installed = store.install(plan, feed, key.getPublic(), bytes::addAndGet);

        assertThat(feed.downloaded).containsExactly("termul-app.jar", "lib-b.jar");
        assertThat(plan.downloadBytes()).isEqualTo(bytes.get());
        assertThat(store.current()).contains(ReleaseVersion.parse("0.1.200"));
        assertThat(installed.jars()).extracting(p -> p.getFileName().toString())
                .containsExactly("termul-app.jar", "lib-a.jar", "lib-b.jar");
        assertThat(UpdateStore.verify(installed.dir(), key.getPublic()).manifest()).isEqualTo(feed.release.manifest());
        try (var left = Files.list(store.dir())) {
            assertThat(left).noneMatch(p -> p.getFileName().toString().startsWith(UpdateStore.STAGING_PREFIX));
        }
    }

    @Test
    void unduhanRusakTidakMengubahVersiAktif() throws Exception {
        var feed = release.feed(key);
        feed.jars.put("lib-b.jar", "isi lain".getBytes()); // server mengirim isi yang beda dari manifest

        assertThatThrownBy(() -> store.install(InstallPlan.of(feed.release, new LocalJars(List.of())), feed,
                key.getPublic(), n -> { }))
                .isInstanceOf(UpdateException.class).hasMessageContaining("lib-b.jar");
        assertThat(store.current()).isEmpty();
        try (var left = Files.list(store.dir())) {
            assertThat(left).isEmpty();
        }
    }

    @Test
    void dibatalkanLewatInterrupt() throws Exception {
        var feed = release.feed(key);
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> store.install(InstallPlan.of(feed.release, new LocalJars(List.of())), feed,
                key.getPublic(), n -> { })).isInstanceOf(InterruptedException.class);
        assertThat(store.current()).isEmpty();
    }

    @Test
    void selectMemilihUpdateValid() throws Exception {
        install(release);
        var notes = new ArrayList<String>();

        var selected = store.select(key.getPublic(), BUNDLED, 1, notes);

        assertThat(selected).isPresent();
        assertThat(selected.get().manifest().version()).hasToString("0.1.200");
        assertThat(notes).isEmpty();
    }

    @Test
    void tanpaUpdatePakaiBawaanTanpaCatatan() {
        var notes = new ArrayList<String>();
        assertThat(store.select(key.getPublic(), BUNDLED, 1, notes)).isEmpty();
        assertThat(notes).isEmpty();
    }

    @Test
    void jarDiubahSetelahTerpasangDitolakSaatStart() throws Exception {
        var installed = install(release);
        Files.writeString(installed.dir().resolve("lib-a.jar"), "disusupi");
        var notes = new ArrayList<String>();

        assertThat(store.select(key.getPublic(), BUNDLED, 1, notes)).isEmpty();
        assertThat(notes).singleElement().asString().contains("lib-a.jar");
    }

    @Test
    void manifestDiubahDitolakSaatStart() throws Exception {
        var installed = install(release);
        Path manifest = installed.dir().resolve(UpdateProtocol.MANIFEST);
        Files.writeString(manifest, Files.readString(manifest).replace("Perbaikan", "Disusupi"));
        var notes = new ArrayList<String>();

        assertThat(store.select(key.getPublic(), BUNDLED, 1, notes)).isEmpty();
        assertThat(notes).singleElement().asString().contains("Tanda tangan");
    }

    @Test
    void keyLainDitolakSaatStart() throws Exception {
        install(release);
        assertThat(store.select(Fixtures.keyPair().getPublic(), BUNDLED, 1, new ArrayList<>())).isEmpty();
    }

    @Test
    void installerLebihBaruMengalahkanUpdate() throws Exception {
        install(release);
        var notes = new ArrayList<String>();

        assertThat(store.select(key.getPublic(), ReleaseVersion.parse("0.1.200"), 1, notes)).isEmpty();
        assertThat(notes).singleElement().asString().contains("versi bawaan");
    }

    @Test
    void generationLebihTinggiDitolak() throws Exception {
        install(release.generation(2));
        var notes = new ArrayList<String>();

        assertThat(store.select(key.getPublic(), BUNDLED, 1, notes)).isEmpty();
        assertThat(notes).singleElement().asString().contains("generation");
    }

    @Test
    void gagalStartBerulangKembaliKeBawaan() throws Exception {
        install(release);
        var v = ReleaseVersion.parse("0.1.200");
        for (int i = 0; i < UpdateStore.MAX_UNHEALTHY_STARTS; i++) {
            assertThat(store.select(key.getPublic(), BUNDLED, 1, new ArrayList<>())).isPresent();
            store.recordStart(v);
        }
        var notes = new ArrayList<String>();

        assertThat(store.select(key.getPublic(), BUNDLED, 1, notes)).isEmpty();
        assertThat(notes).singleElement().asString().contains("gagal start");
    }

    @Test
    void versiSehatTidakDihitungGagal() throws Exception {
        install(release);
        var v = ReleaseVersion.parse("0.1.200");
        store.recordStart(v);
        store.markHealthy(v);
        for (int i = 0; i < UpdateStore.MAX_UNHEALTHY_STARTS + 2; i++) {
            store.recordStart(v);
        }

        assertThat(store.select(key.getPublic(), BUNDLED, 1, new ArrayList<>())).isPresent();
        assertThat(store.attempts(v)).isZero();
    }

    @Test
    void installUlangVersiSamaTidakMengunduhLagi() throws Exception {
        install(release);
        var feed = release.feed(key);

        store.install(InstallPlan.of(feed.release, new LocalJars(List.of())), feed, key.getPublic(), n -> { });

        assertThat(feed.downloaded).isEmpty();
    }

    @Test
    void cleanupMenyisakanVersiYangDisimpan() throws Exception {
        install(release.version("0.1.150"));
        install(release.version("0.1.200"));
        install(release.version("0.1.250"));
        Files.createDirectories(store.dir().resolve(UpdateStore.STAGING_PREFIX + "sisa"));

        store.cleanup(Set.of(ReleaseVersion.parse("0.1.200"), ReleaseVersion.parse("0.1.250")));

        try (var left = Files.list(store.dir())) {
            assertThat(left.map(p -> p.getFileName().toString()))
                    .containsExactlyInAnyOrder("0.1.200", "0.1.250", UpdateStore.CURRENT);
        }
    }

    private UpdateStore.Installed install(Fixtures.Release r) throws Exception {
        var feed = r.feed(key);
        return store.install(InstallPlan.of(feed.release, new LocalJars(List.of())), feed, key.getPublic(), n -> { });
    }
}

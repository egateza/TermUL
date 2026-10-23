package dev.egateza.termul.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileStoreTest {

    @TempDir
    Path dir;

    @Test
    void fileBelumAdaMenghasilkanSnapshotKosong() {
        var store = new ProfileStore(dir.resolve("profiles.json"));

        assertThat(store.load().profiles()).isEmpty();
    }

    @Test
    void roundTripKeDisk() {
        Path file = dir.resolve("sub/profiles.json");
        var store = new ProfileStore(file);
        var p = new HostProfile(UUID.randomUUID(), "db", "Produksi/Backend", "10.1.1.1", 2222, "dev",
                AuthMethod.KEY, "C:\\keys\\id_ed25519", null, EnvironmentTag.PROD, "/srv", "catatan", false);
        store.save(p);
        store.addGroup("Staging");

        var loaded = new ProfileStore(file).load();

        assertThat(loaded.profiles()).containsExactly(p);
        assertThat(loaded.allGroups()).containsExactly("Produksi", "Produksi/Backend", "Staging");
        assertThat(dir.resolve("sub")).isDirectoryNotContaining("glob:**.tmp");
    }

    @Test
    void exportMenulisSalinanYangBisaDimuatLagi() {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        var p = HostProfile.create("web", "10.0.0.5", "ega");
        store.save(p);
        store.addGroup("Staging");
        Path target = dir.resolve("out/termul-profiles.json");

        int count = store.exportTo(target);

        assertThat(count).isEqualTo(1);
        var loaded = new ProfileStore(target).load();
        assertThat(loaded.profiles()).containsExactly(p);
        assertThat(loaded.allGroups()).containsExactly("Staging");
    }

    @Test
    void distroWslIkutTersimpan() {
        Path file = dir.resolve("profiles.json");
        var p = HostProfile.create("Ubuntu", "localhost", "ega").withWslDistro("Ubuntu-24.04");
        new ProfileStore(file).save(p);

        assertThat(new ProfileStore(file).load().profiles()).singleElement()
                .extracting(HostProfile::wslDistro).isEqualTo("Ubuntu-24.04");
    }

    @Test
    void flagSensitifIkutTersimpanDanProfilLamaTanpaFlagTetapTerbaca() throws Exception {
        Path file = dir.resolve("profiles.json");
        var explicit = HostProfile.create("staging-db", "10.0.0.7", "ega").withSensitive(true);
        var legacy = HostProfile.create("web", "10.0.0.5", "ega");
        var store = new ProfileStore(file);
        store.save(explicit);
        store.save(legacy);
        // file dari versi lama: tanpa properti "sensitive" sama sekali
        Files.writeString(file, Files.readString(file).replaceAll(",?\\s*\"sensitive\"\\s*:\\s*null", ""));
        assertThat(Files.readString(file)).containsOnlyOnce("\"sensitive\"");

        var loaded = new ProfileStore(file).load();

        assertThat(loaded.find(explicit.id())).get().extracting(HostProfile::sensitive).isEqualTo(true);
        assertThat(loaded.find(legacy.id())).get().extracting(HostProfile::sensitive).isNull();
    }

    @Test
    void saveMenggantiProfilDenganIdSama() {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        var p = HostProfile.create("a", "h", "u");
        store.save(p);
        store.save(p.withGroup("Infra"));

        assertThat(store.snapshot().profiles()).singleElement()
                .extracting(HostProfile::group).isEqualTo("Infra");
    }

    @Test
    void deleteGroupDitolakKalauMasihBerisiProfil() {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        store.save(HostProfile.create("a", "h", "u").withGroup("Infra/DNS"));

        assertThatThrownBy(() -> store.deleteGroup("Infra")).isInstanceOf(IllegalStateException.class);

        store.addGroup("Kosong/Sub");
        store.deleteGroup("Kosong");
        assertThat(store.snapshot().allGroups()).doesNotContain("Kosong", "Kosong/Sub");
    }

    @Test
    void renameGroupMemindahkanSubgrupDanProfil() {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        var p = HostProfile.create("a", "h", "u").withGroup("Old/Sub");
        var other = HostProfile.create("b", "h", "u").withGroup("Older");
        store.save(p);
        store.save(other);
        store.addGroup("Old/Empty");

        store.renameGroup("Old", "New");

        var s = store.snapshot();
        assertThat(s.find(p.id())).get().extracting(HostProfile::group).isEqualTo("New/Sub");
        assertThat(s.find(other.id())).get().extracting(HostProfile::group).isEqualTo("Older");
        assertThat(s.allGroups()).contains("New/Empty").doesNotContain("Old", "Old/Empty");
    }

    @Test
    void fileRusakTidakDitimpa() throws Exception {
        Path file = dir.resolve("profiles.json");
        Files.writeString(file, "{ ini bukan json");
        var store = new ProfileStore(file);

        assertThatThrownBy(store::load).isInstanceOf(ProfileStoreException.class);
        assertThat(Files.readString(file)).isEqualTo("{ ini bukan json");
    }

    @Test
    void listenerDipanggilSetelahMutasi() {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        var count = new AtomicInteger();
        store.addListener(s -> count.incrementAndGet());

        store.save(HostProfile.create("a", "h", "u"));
        store.addGroup("G");

        assertThat(count).hasValue(2);
    }

    @Test
    void warnaTerminalPerHostTersimpanDanFileLamaTanpaFieldTetapTerbaca() throws Exception {
        Path file = dir.resolve("profiles.json");
        var p = HostProfile.create("prod", "h", "u").withTerminalTheme("builtin:prod-red");
        new ProfileStore(file).save(p);

        assertThat(new ProfileStore(file).load().find(p.id())).map(HostProfile::terminalTheme)
                .contains("builtin:prod-red");

        String json = Files.readString(file);
        String old = json.replaceAll(",\\s*\"terminalTheme\"\\s*:\\s*\"[^\"]*\"", ""); // profiles.json versi lama
        assertThat(old).doesNotContain("terminalTheme");
        Files.writeString(file, old);
        assertThat(new ProfileStore(file).load().find(p.id())).map(HostProfile::terminalTheme).isEmpty();
    }

    @Test
    void protokolTersimpanDanFileLamaTanpaFieldMenjadiSsh() throws Exception {
        Path file = dir.resolve("profiles.json");
        var ftp = HostProfile.create("ftp", "h", "u").withProtocol(Protocol.FTPS);
        var ssh = HostProfile.create("ssh", "h2", "u");
        var store = new ProfileStore(file);
        store.save(ftp);
        store.save(ssh);

        assertThat(new ProfileStore(file).load().find(ftp.id())).get()
                .satisfies(p -> {
                    assertThat(p.protocol()).isEqualTo(Protocol.FTPS);
                    assertThat(p.port()).isEqualTo(21);
                });
        String json = Files.readString(file);
        assertThat(json).doesNotContain("\"ftp\" :"); // isFtp() tidak ikut diserialisasi

        String old = json.replaceAll(",\\s*\"protocol\"\\s*:\\s*\"[^\"]*\"", ""); // profiles.json versi lama
        assertThat(old).doesNotContain("protocol");
        Files.writeString(file, old);
        assertThat(new ProfileStore(file).load().find(ssh.id())).map(HostProfile::protocol).contains(Protocol.SSH);
    }

    @Test
    void jsonTidakMengandungFieldSecret() throws Exception {
        Path file = dir.resolve("profiles.json");
        new ProfileStore(file).save(HostProfile.create("a", "h", "u"));

        assertThat(Files.readString(file)).doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("secret");
    }

    @Test
    void favoritesTersimpanDanBisaDikeluarkan() {
        Path file = dir.resolve("profiles.json");
        var store = new ProfileStore(file);
        var a = HostProfile.create("a", "h1", "u");
        var b = HostProfile.create("b", "h2", "u");
        store.save(a);
        store.save(b);
        store.setFavorite(b.id(), true);
        store.setFavorite(a.id(), true);
        store.setFavorite(a.id(), true);

        assertThat(new ProfileStore(file).load().favoriteProfiles()).containsExactly(b, a);

        store.setFavorite(b.id(), false);
        assertThat(store.snapshot().favorites()).containsExactly(a.id());
        assertThat(store.snapshot().isFavorite(b.id())).isFalse();
    }

    @Test
    void lastUsedMenyimpanLimaProfilTerakhirTerbaruDiDepan() {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        var profiles = new java.util.ArrayList<HostProfile>();
        for (int i = 0; i < 7; i++) {
            var p = HostProfile.create("p" + i, "h" + i, "u");
            profiles.add(p);
            store.save(p);
            store.markUsed(p.id());
        }
        store.markUsed(profiles.get(4).id());

        assertThat(store.snapshot().recentProfiles()).extracting(HostProfile::name)
                .containsExactly("p4", "p6", "p5", "p3", "p2");

        store.clearRecent();
        assertThat(store.snapshot().recent()).isEmpty();
    }

    @Test
    void hapusProfilJugaMengeluarkannyaDariFavoritesDanLastUsed() {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        var p = HostProfile.create("a", "h", "u");
        store.save(p);
        store.setFavorite(p.id(), true);
        store.markUsed(p.id());

        store.delete(p.id());

        assertThat(store.snapshot().favorites()).isEmpty();
        assertThat(store.snapshot().recent()).isEmpty();
    }

    @Test
    void idTakDikenalDiabaikan() {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        store.setFavorite(UUID.randomUUID(), true);
        store.markUsed(UUID.randomUUID());

        assertThat(store.snapshot().favorites()).isEmpty();
        assertThat(store.snapshot().recent()).isEmpty();
    }

    @Test
    void fileLamaTanpaFavoritesTetapTerbaca() throws Exception {
        Path file = dir.resolve("profiles.json");
        Files.writeString(file, """
                {"version":1,"groups":["Infra"],"profiles":[]}
                """);

        var loaded = new ProfileStore(file).load();

        assertThat(loaded.favorites()).isEmpty();
        assertThat(loaded.recent()).isEmpty();
    }
}

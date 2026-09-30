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
        var p = new HostProfile(UUID.randomUUID(), "db", "Produksi/Backend", "10.1.1.1", 2222, "ega",
                AuthMethod.KEY, "C:\\keys\\id_ed25519", null, EnvironmentTag.PROD, "/srv", "catatan", false);
        store.save(p);
        store.addGroup("Staging");

        var loaded = new ProfileStore(file).load();

        assertThat(loaded.profiles()).containsExactly(p);
        assertThat(loaded.allGroups()).containsExactly("Produksi", "Produksi/Backend", "Staging");
        assertThat(dir.resolve("sub")).isDirectoryNotContaining("glob:**.tmp");
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
    void jsonTidakMengandungFieldSecret() throws Exception {
        Path file = dir.resolve("profiles.json");
        new ProfileStore(file).save(HostProfile.create("a", "h", "u"));

        assertThat(Files.readString(file)).doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("secret");
    }
}

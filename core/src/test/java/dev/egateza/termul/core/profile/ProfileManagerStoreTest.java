package dev.egateza.termul.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Operasi Profile manager: urutan folder/profil, aksi massal, hapus folder, export/import. */
class ProfileManagerStoreTest {

    @TempDir
    Path dir;

    private static final ProfileTransfer.Source NONE = new ProfileTransfer.Source(List.of(), List.of());

    private static HostProfile p(String name, String group, String host) {
        return new HostProfile(UUID.randomUUID(), name, group, host, 22, "admin", AuthMethod.PASSWORD, null, null,
                EnvironmentTag.NONE, null, null, false);
    }

    @Test
    void urutanFolderDanProfilTersimpanDanDipakaiDaftarHost() {
        Path file = dir.resolve("profiles.json");
        var store = new ProfileStore(file);
        var a = p("alpha", "Prod", "10.0.0.1");
        var b = p("beta", "Prod", "10.0.0.2");
        var c = p("gamma", "Prod", "10.0.0.3");
        store.saveAll(List.of(a, b, c, p("x", "Dev", "10.0.1.1"), p("y", "Clients/A", "10.0.2.1")));

        assertThat(store.snapshot().childGroups("")).containsExactly("Clients", "Dev", "Prod"); // default abjad
        store.moveGroup("Prod", -1);
        store.moveGroup("Prod", -1);
        store.reorderProfiles(List.of(c.id(), a.id(), b.id()));

        var loaded = new ProfileStore(file).load();
        assertThat(loaded.allGroups()).containsExactly("Prod", "Clients", "Clients/A", "Dev");
        assertThat(loaded.profilesIn("Prod")).extracting(HostProfile::name).containsExactly("gamma", "alpha", "beta");
    }

    @Test
    void fileLamaTanpaUrutanTetapTerbacaUrutAbjad() throws Exception {
        Path file = dir.resolve("profiles.json");
        Files.writeString(file, """
                {"version":1,"groups":["B","A"],"profiles":[],"favorites":[],"recent":[]}""");

        var loaded = new ProfileStore(file).load();

        assertThat(loaded.allGroups()).containsExactly("A", "B");
        assertThat(loaded.groupOrder()).isEmpty();
        assertThat(loaded.profileOrder()).isEmpty();
    }

    @Test
    void hapusProfilMengubahPemakaiJumpHostJadiLangsung() {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        var gw = p("gw", "", "203.0.113.5");
        var inner = p("inner", "", "10.0.0.9").withJumpHostId(gw.id());
        store.saveAll(List.of(gw, inner));

        store.deleteAll(List.of(gw.id()));

        assertThat(store.snapshot().profiles()).singleElement()
                .satisfies(x -> assertThat(x.jumpHostId()).isNull());
    }

    @Test
    void pindahDanHapusFolderMemindahkanProfilKeUngrouped() {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        var a = p("a", "Old", "10.0.0.1");
        var b = p("b", "Old/Sub", "10.0.0.2");
        store.saveAll(List.of(a, b));
        store.moveGroup("Old", 1); // tidak ada saudara: tidak berubah

        store.deleteGroupKeepProfiles("Old");

        assertThat(store.snapshot().profiles()).extracting(HostProfile::group).containsOnly("");
        assertThat(store.snapshot().allGroups()).isEmpty();

        store.moveProfiles(List.of(a.id()), "New");
        assertThat(store.snapshot().find(a.id()).orElseThrow().group()).isEqualTo("New");
    }

    @Test
    void renameFolderIkutMengubahUrutan() {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        store.saveAll(List.of(p("a", "A", "1"), p("b", "B", "2")));
        store.moveGroup("B", -1);

        store.renameGroup("B", "Z");

        assertThat(store.snapshot().childGroups("")).containsExactly("Z", "A");
    }

    @Test
    void exportLaluImportUlangMemperbaruiBukanMenggandakan() throws Exception {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        var gw = p("Gateway", "Prod", "10.0.55.50");
        var mail = p("Mail", "Prod", "10.0.55.6");
        store.saveAll(List.of(gw, mail));
        byte[] file = ProfileTransfer.export(store.snapshot(), store.snapshot().profiles(), null);
        assertThat(new String(file, java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("password");

        var source = ProfileTransfer.read(file);
        var rows = ProfileTransfer.plan(store.snapshot(), source.profiles());

        assertThat(rows).extracting(ProfileTransfer.Row::status).containsOnly(ProfileTransfer.Status.SAME);
        var result = ProfileTransfer.apply(store.snapshot(), rows, Map.of(), null, source);
        assertThat(result.toSave()).isEmpty();
        assertThat(result.skipped()).isEqualTo(2);
    }

    @Test
    void importKonflikSkipOverwriteKeepBoth() throws Exception {
        var current = new ProfileSnapshot(1, List.of(), List.of(p("Gateway", "Prod", "10.0.55.50"),
                p("Mail", "Prod", "10.0.55.6"), p("Monitor", "Prod", "10.0.55.7")));
        var incoming = List.of(p("edge-proxy-01", "X", "10.0.55.60"), p("gateway", "X", "10.0.55.99"),
                p("Mail", "X", "10.0.55.66"), p("Monitor", "X", "10.0.55.77"), p("Same", "X", "1"));
        var rows = ProfileTransfer.plan(current, incoming);
        assertThat(rows).extracting(ProfileTransfer.Row::status).containsExactly(ProfileTransfer.Status.NEW,
                ProfileTransfer.Status.CONFLICT, ProfileTransfer.Status.CONFLICT, ProfileTransfer.Status.CONFLICT,
                ProfileTransfer.Status.NEW);

        var result = ProfileTransfer.apply(current, rows, Map.of(1, ProfileTransfer.Action.OVERWRITE,
                2, ProfileTransfer.Action.KEEP_BOTH), "Imported", NONE);

        assertThat(result.added()).isEqualTo(3);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1); // Monitor: tanpa pilihan = skip
        var gw = result.toSave().stream().filter(x -> x.name().equals("Gateway")).findFirst().orElseThrow();
        assertThat(gw.host()).isEqualTo("10.0.55.99");
        assertThat(gw.group()).as("overwrite tidak memindah folder").isEqualTo("Prod");
        assertThat(result.toSave()).extracting(HostProfile::name).contains("Mail" + ProfileTransfer.IMPORTED_SUFFIX);
        assertThat(result.toSave()).filteredOn(x -> !x.name().equals("Gateway"))
                .extracting(HostProfile::group).containsOnly("Imported");
    }

    @Test
    void jumpHostDiFileDiarahkanKeIdHasilImport() throws Exception {
        var gw = p("gw", "", "203.0.113.5");
        var inner = p("inner", "", "10.0.0.9").withJumpHostId(gw.id());
        var lonely = p("lonely", "", "10.0.0.10").withJumpHostId(UUID.randomUUID());
        var current = ProfileSnapshot.empty();

        var result = ProfileTransfer.apply(current, ProfileTransfer.plan(current, List.of(gw, inner, lonely)),
                Map.of(), "", NONE);

        assertThat(result.toSave()).filteredOn(x -> x.name().equals("inner")).singleElement()
                .satisfies(x -> assertThat(x.jumpHostId()).isEqualTo(gw.id()));
        assertThat(result.toSave()).filteredOn(x -> x.name().equals("lonely")).singleElement()
                .satisfies(x -> assertThat(x.jumpHostId()).isNull());
    }

    /** Backup lengkap: favorit dan urutan profil ikut diekspor dan dipulihkan, folder ikut urutannya. */
    @Test
    void exportImportMembawaFavoritDanUrutan() throws Exception {
        var src = new ProfileStore(dir.resolve("a.json"));
        var a = p("alpha", "Prod", "10.0.0.1");
        var b = p("beta", "Prod", "10.0.0.2");
        var c = p("gamma", "Prod", "10.0.0.3");
        src.saveAll(List.of(a, b, c, p("x", "Dev", "10.0.1.1")));
        src.moveGroup("Prod", -1);
        src.reorderProfiles(List.of(c.id(), a.id(), b.id()));
        src.setFavorite(b.id(), true);
        byte[] file = ProfileTransfer.export(src.snapshot(), List.of(a, b, c), null);

        var dst = new ProfileStore(dir.resolve("b.json"));
        var old = p("aaa-lama", "Prod", "10.9.9.9");
        dst.save(old);
        var source = ProfileTransfer.read(file);
        var result = ProfileTransfer.apply(dst.snapshot(), ProfileTransfer.plan(dst.snapshot(), source.profiles()),
                Map.of(), null, source);
        dst.importProfiles(result.newFolders(), result.toSave(), result.favorites(), result.order());

        var s = dst.snapshot();
        assertThat(source.favorites()).containsExactly(b.id());
        assertThat(s.profilesIn("Prod")).extracting(HostProfile::name)
                .containsExactly("aaa-lama", "gamma", "alpha", "beta"); // yang sudah ada tetap di atas
        assertThat(s.favoriteProfiles()).extracting(HostProfile::name).containsExactly("beta");
        assertThat(s.childGroups("")).containsExactly("Dev", "Prod");
    }

    /** File dari menu File lama (salinan profiles.json) juga membawa favorit dan urutan. */
    @Test
    void importSalinanProfilesJsonMembacaFavoritDanUrutan() throws Exception {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        var a = p("a", "", "1");
        var b = p("b", "", "2");
        store.saveAll(List.of(a, b));
        store.setFavorite(a.id(), true);
        store.reorderProfiles(List.of(b.id(), a.id()));

        var source = ProfileTransfer.read(Files.readAllBytes(dir.resolve("profiles.json")));

        assertThat(source.favorites()).containsExactly(a.id());
        assertThat(source.profileOrder()).containsExactly(b.id(), a.id());
    }

    @Test
    void bukanFileProfilDitolak() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ProfileTransfer.read("{\"a\":1}".getBytes()))
                .isInstanceOf(java.io.IOException.class);
    }
}

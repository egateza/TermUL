package dev.egateza.termul.app.ui.tree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.ui.tree.HostTreeModelBuilder.BuiltinGroup;
import dev.egateza.termul.app.ui.tree.HostTreeModelBuilder.GroupNode;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class HostTreeModelBuilderTest {

    private final HostProfile web = HostProfile.create("web-1", "10.0.0.1", "dev").withGroup("Produksi/Backend");
    private final HostProfile db = HostProfile.create("db-1", "10.0.0.2", "dev").withGroup("Produksi");
    private final HostProfile lab = HostProfile.create("lab", "192.168.1.5", "root");
    private final ProfileSnapshot snapshot =
            new ProfileSnapshot(1, List.of("Staging"), List.of(web, db, lab));

    @Test
    void grupDitampilkanSebelumHostDanTerurut() {
        var root = HostTreeModelBuilder.build(snapshot, "");

        assertThat(labels(root)).containsExactly("INSTANCE", "FAVORITES", "RECENT", "Produksi", "Staging", "lab");
        var produksi = (DefaultMutableTreeNode) root.getChildAt(3);
        assertThat(labels(produksi)).containsExactly("Backend", "db-1");
    }

    /** Urutan folder dan profil yang diatur di Profile manager (Reorder / Manage folders) dipakai panel host. */
    @Test
    void urutanDariProfileManagerDipakaiDaftarHost() {
        var db2 = HostProfile.create("db-2", "10.0.0.3", "dev").withGroup("Produksi");
        var ordered = new ProfileSnapshot(1, List.of("Staging"), List.of(web, db, db2, lab), List.of(), List.of(),
                List.of("Staging", "Produksi"), List.of(db2.id(), db.id()));

        var root = HostTreeModelBuilder.build(ordered, "");

        assertThat(labels(root)).containsExactly("INSTANCE", "FAVORITES", "RECENT", "Staging", "Produksi", "lab");
        assertThat(labels((DefaultMutableTreeNode) root.getChildAt(4))).containsExactly("Backend", "db-2", "db-1");
    }

    @Test
    void filterMenyembunyikanGrupTanpaHasil() {
        var root = HostTreeModelBuilder.build(snapshot, "10.0.0.1");

        assertThat(labels(root)).containsExactly("Produksi");
        var backend = (DefaultMutableTreeNode) root.getChildAt(0).getChildAt(0);
        assertThat(((GroupNode) backend.getUserObject()).path()).isEqualTo("Produksi/Backend");
        assertThat(labels(backend)).containsExactly("web-1");
    }

    @Test
    void filterCaseInsensitivePadaUserDanGrup() {
        assertThat(labels(HostTreeModelBuilder.build(snapshot, "ROOT"))).containsExactly("lab");
        assertThat(HostTreeModelBuilder.matches(web, "backend")).isTrue();
    }

    @Test
    void grupBawaanBerisiFavoritesDanLastUsedSesuaiUrutan() {
        var s = new ProfileSnapshot(1, List.of("Staging"), List.of(web, db, lab),
                List.of(lab.id(), web.id()), List.of(db.id(), lab.id()));

        var root = HostTreeModelBuilder.build(s, "");

        var favorites = (DefaultMutableTreeNode) root.getChildAt(1);
        var recent = (DefaultMutableTreeNode) root.getChildAt(2);
        assertThat(favorites.getUserObject()).isEqualTo(BuiltinGroup.FAVORITES);
        assertThat(labels(favorites)).containsExactly("lab", "web-1");
        assertThat(recent.getUserObject()).isEqualTo(BuiltinGroup.RECENT);
        assertThat(labels(recent)).containsExactly("db-1", "lab");
        // profil tetap ada di grup aslinya
        assertThat(labels(root)).endsWith("lab");
    }

    @Test
    void grupBawaanKosongDisembunyikanSaatFilter() {
        var s = new ProfileSnapshot(1, List.of(), List.of(web, db, lab), List.of(lab.id()), List.of());

        assertThat(labels(HostTreeModelBuilder.build(s, "root"))).containsExactly("FAVORITES", "lab");
        assertThat(labels(HostTreeModelBuilder.build(s, "10.0.0.2"))).containsExactly("Produksi");
    }

    /** WSL Manager dihapus (ADR 0011): profil SSH ke distro WSL kini tampil seperti profil biasa di grupnya sendiri. */
    @Test
    void profilBekasWslTampilSebagaiProfilBiasa() {
        var ubuntu = HostProfile.create("Ubuntu-ega", "localhost", "ega").withGroup("WSL");
        var s = new ProfileSnapshot(1, List.of(), List.of(web, ubuntu), List.of(ubuntu.id()), List.of());

        var root = HostTreeModelBuilder.build(s, "");

        assertThat(labels(root)).containsExactly("INSTANCE", "FAVORITES", "RECENT", "Produksi", "WSL");
        assertThat(labels((DefaultMutableTreeNode) root.getChildAt(1))).containsExactly("Ubuntu-ega");
        assertThat(labels(HostTreeModelBuilder.build(s, "localhost"))).containsExactly("FAVORITES", "WSL");
    }

    @Test
    void nodeTerminalLokalPalingAtasDanIkutPencarian() {
        var root = HostTreeModelBuilder.build(snapshot, "");
        assertThat(((DefaultMutableTreeNode) root.getChildAt(0)).getUserObject())
                .isEqualTo(HostTreeModelBuilder.LocalTerminalNode.INSTANCE);
        assertThat(((DefaultMutableTreeNode) root.getChildAt(0)).getAllowsChildren()).isFalse();

        assertThat(labels(HostTreeModelBuilder.build(snapshot, "shell"))).containsExactly("INSTANCE");
        assertThat(labels(HostTreeModelBuilder.build(snapshot, "LOCAL"))).containsExactly("INSTANCE");
        assertThat(labels(HostTreeModelBuilder.build(snapshot, "10.0.0.1"))).doesNotContain("INSTANCE");
    }

    @Test
    void grupTerminalLokalBerisiProfilDanIkutPencarian() {
        var zsh = new dev.egateza.termul.app.terminal.LocalProfiles.Choice(java.util.UUID.randomUUID(), "zsh",
                List.of("/bin/zsh", "-l"), "", java.util.Map.of(), null, true, true);
        var proyek = new dev.egateza.termul.app.terminal.LocalProfiles.Choice(java.util.UUID.randomUUID(), "Proyek API",
                List.of("/bin/zsh", "-l"), "~/api", java.util.Map.of(), null, false, false);

        var root = HostTreeModelBuilder.build(snapshot, "", List.of(zsh, proyek));
        var local = (DefaultMutableTreeNode) root.getChildAt(0);
        assertThat(local.getAllowsChildren()).isTrue();
        assertThat(labels(local)).containsExactly("zsh", "Proyek API");

        var filtered = HostTreeModelBuilder.build(snapshot, "api", List.of(zsh, proyek));
        assertThat(labels(filtered)).containsExactly("INSTANCE");
        assertThat(labels((DefaultMutableTreeNode) filtered.getChildAt(0))).containsExactly("Proyek API");
        assertThat(labels(HostTreeModelBuilder.build(snapshot, "10.0.0.1", List.of(zsh, proyek))))
                .doesNotContain("INSTANCE");
    }

    private static List<String> labels(DefaultMutableTreeNode node) {
        var result = new ArrayList<String>();
        for (var child : Collections.list(node.children())) {
            Object o = ((DefaultMutableTreeNode) child).getUserObject();
            result.add(o instanceof HostProfile p ? p.name() : o.toString());
        }
        return result;
    }
}

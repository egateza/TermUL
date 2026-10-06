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

        assertThat(labels(root)).containsExactly("FAVORITES", "RECENT", "Produksi", "Staging", "lab");
        var produksi = (DefaultMutableTreeNode) root.getChildAt(2);
        assertThat(labels(produksi)).containsExactly("Backend", "db-1");
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

        var favorites = (DefaultMutableTreeNode) root.getChildAt(0);
        var recent = (DefaultMutableTreeNode) root.getChildAt(1);
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

    @Test
    void grupWslTidakTampilKalauMatiAtauTidakAdaDistroBerjalan() {
        assertThat(labels(HostTreeModelBuilder.build(snapshot, "", null))).doesNotContain("WSL");
        assertThat(labels(HostTreeModelBuilder.build(snapshot, "", List.of()))).doesNotContain("WSL");
    }

    @Test
    void grupWslBerisiProfilDistroAtauNodeDistroTanpaProfil() {
        var ubuntu = HostProfile.create("Ubuntu dev", "localhost", "ega").withWslDistro("Ubuntu-22.04");
        var s = new ProfileSnapshot(1, List.of(), List.of(web, ubuntu));

        var root = HostTreeModelBuilder.build(s, "", List.of("ubuntu-22.04", "Debian"));

        assertThat(labels(root)).containsExactly("FAVORITES", "RECENT", "WSL", "Produksi");
        var wsl = (DefaultMutableTreeNode) root.getChildAt(2);
        assertThat(wsl.getUserObject()).isEqualTo(BuiltinGroup.WSL);
        assertThat(labels(wsl)).containsExactly("Ubuntu dev", "Debian");
        assertThat(((DefaultMutableTreeNode) wsl.getChildAt(1)).getUserObject())
                .isEqualTo(new HostTreeModelBuilder.WslDistroNode("Debian"));
    }

    @Test
    void profilWslHanyaDiGrupWslDanHilangSaatFiturMati() {
        // profil lama dibuat di grup biasa "WSL": tidak boleh muncul sebagai grup kedua
        var ubuntu = HostProfile.create("Ubuntu-ega", "localhost", "ega").withGroup("WSL").withWslDistro("Ubuntu-22.04");
        var s = new ProfileSnapshot(1, List.of("Kosong"), List.of(web, ubuntu), List.of(ubuntu.id()), List.of(ubuntu.id()));

        var on = HostTreeModelBuilder.build(s, "", List.of());
        assertThat(labels(on)).containsExactly("FAVORITES", "RECENT", "WSL", "Kosong", "Produksi");
        assertThat(labels((DefaultMutableTreeNode) on.getChildAt(2))).containsExactly("Ubuntu-ega"); // distro berhenti tetap tampil

        var off = HostTreeModelBuilder.build(s, "", null);
        assertThat(labels(off)).containsExactly("FAVORITES", "RECENT", "Kosong", "Produksi");
        assertThat(labels((DefaultMutableTreeNode) off.getChildAt(0))).isEmpty();
        assertThat(labels((DefaultMutableTreeNode) off.getChildAt(1))).isEmpty();
        assertThat(labels(HostTreeModelBuilder.build(s, "ubuntu", null))).isEmpty();
    }

    @Test
    void filterBerlakuDiGrupWsl() {
        var ubuntu = HostProfile.create("dev box", "localhost", "ega").withWslDistro("Ubuntu-22.04");
        var s = new ProfileSnapshot(1, List.of(), List.of(ubuntu));

        assertThat(labels(HostTreeModelBuilder.build(s, "ubuntu", List.of("Ubuntu-22.04", "Debian"))))
                .containsExactly("WSL");
        assertThat(labels(HostTreeModelBuilder.build(s, "debian", List.of("Ubuntu-22.04", "Debian"))))
                .containsExactly("WSL");
        assertThat(labels(HostTreeModelBuilder.build(s, "tidak-ada", List.of("Ubuntu-22.04", "Debian")))).isEmpty();
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

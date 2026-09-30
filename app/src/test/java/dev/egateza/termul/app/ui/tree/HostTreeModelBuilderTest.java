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

    private final HostProfile web = HostProfile.create("web-1", "10.0.0.1", "ega").withGroup("Produksi/Backend");
    private final HostProfile db = HostProfile.create("db-1", "10.0.0.2", "ega").withGroup("Produksi");
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

    private static List<String> labels(DefaultMutableTreeNode node) {
        var result = new ArrayList<String>();
        for (var child : Collections.list(node.children())) {
            Object o = ((DefaultMutableTreeNode) child).getUserObject();
            result.add(o instanceof HostProfile p ? p.name() : o.toString());
        }
        return result;
    }
}

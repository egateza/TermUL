package dev.egateza.myterm.app.ui.tree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.myterm.app.ui.tree.HostTreeModelBuilder.GroupNode;
import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.core.profile.ProfileSnapshot;
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

        assertThat(labels(root)).containsExactly("Produksi", "Staging", "lab");
        var produksi = (DefaultMutableTreeNode) root.getChildAt(0);
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

    private static List<String> labels(DefaultMutableTreeNode node) {
        var result = new ArrayList<String>();
        for (var child : Collections.list(node.children())) {
            Object o = ((DefaultMutableTreeNode) child).getUserObject();
            result.add(o instanceof HostProfile p ? p.name() : o.toString());
        }
        return result;
    }
}

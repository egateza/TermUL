package dev.egateza.termul.app.ui.tree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class HostTreePanelFoldTest {

    private final HostProfile web = HostProfile.create("web", "10.0.0.1", "u").withGroup("Prod");
    private final HostProfile db = HostProfile.create("db", "10.0.0.2", "u").withGroup("Prod/Db");
    private final HostProfile stg = HostProfile.create("stg", "10.0.0.3", "u").withGroup("Staging");
    private final ProfileSnapshot snapshot =
            new ProfileSnapshot(ProfileSnapshot.CURRENT_VERSION, List.of(), List.of(web, db, stg));

    /** Argumen setiap panggilan collapsedGroupsChanged, berurutan. */
    private final List<Set<String>> saved = new ArrayList<>();

    @Test
    void tutupGrupDisimpanBukaLagiDihapus() {
        var panel = panel();
        panel.setSnapshot(snapshot);
        var tree = tree(panel);

        tree.collapsePath(group(tree, "Staging"));
        assertThat(saved.getLast()).containsExactly("group:Staging");

        tree.expandPath(group(tree, "Staging"));
        assertThat(saved.getLast()).isEmpty();
    }

    @Test
    void statusTersimpanDipulihkanSaatStartTanpaMemicuSimpan() {
        var panel = panel();
        panel.setCollapsedGroups(List.of("group:Staging"));
        panel.setSnapshot(snapshot);
        var tree = tree(panel);

        assertThat(tree.isExpanded(group(tree, "Staging"))).isFalse();
        assertThat(tree.isExpanded(group(tree, "Prod"))).isTrue();
        assertThat(saved).as("memulihkan status bukan pilihan user").isEmpty();
    }

    @Test
    void subgrupTerbukaTetapDiingatSaatIndukDitutup() {
        var panel = panel();
        panel.setCollapsedGroups(List.of("group:Prod"));
        panel.setSnapshot(snapshot);
        var tree = tree(panel);

        assertThat(tree.isExpanded(group(tree, "Prod"))).isFalse();
        tree.expandPath(group(tree, "Prod"));

        assertThat(tree.isExpanded(group(tree, "Prod/Db"))).as("subgrup tidak ikut tertutup").isTrue();
    }

    @Test
    void pencarianMembukaSemuaGrupSementaraLaluKembaliKeStatusUser() {
        var panel = panel();
        panel.setCollapsedGroups(List.of("group:Staging"));
        panel.setSnapshot(snapshot);
        var search = search(panel);

        search.setText("s"); // cocok dengan stg (dan alamat lain): grup dibuka untuk hasil pencarian
        var tree = tree(panel);
        assertThat(tree.isExpanded(group(tree, "Staging"))).isTrue();

        search.setText("");
        assertThat(tree.isExpanded(group(tree, "Staging"))).isFalse();
        assertThat(saved).isEmpty();
    }

    @Test
    void kunciGrupBawaanDanTerminalLokal() {
        assertThat(HostTreePanel.foldKey(new HostTreeModelBuilder.GroupNode("Prod/Db"))).isEqualTo("group:Prod/Db");
        assertThat(HostTreePanel.foldKey(HostTreeModelBuilder.BuiltinGroup.FAVORITES)).isEqualTo("builtin:favorites");
        assertThat(HostTreePanel.foldKey(web)).isNull();
    }

    private HostTreePanel panel() {
        var actions = (HostTreePanel.Actions) Proxy.newProxyInstance(HostTreePanel.Actions.class.getClassLoader(),
                new Class<?>[] {HostTreePanel.Actions.class}, (proxy, method, args) -> {
                    if (method.getName().equals("collapsedGroupsChanged")) {
                        @SuppressWarnings("unchecked")
                        var set = (Set<String>) args[0];
                        saved.add(set);
                    }
                    return null;
                });
        return new HostTreePanel(actions);
    }

    private static TreePath group(JTree tree, String path) {
        var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        for (var node : java.util.Collections.list(root.depthFirstEnumeration())) {
            var n = (DefaultMutableTreeNode) node;
            if (n.getUserObject() instanceof HostTreeModelBuilder.GroupNode g && g.path().equals(path)) {
                return new TreePath(n.getPath());
            }
        }
        throw new AssertionError("Grup tidak ada: " + path);
    }

    private static JTree tree(Container root) {
        return find(root, JTree.class);
    }

    private static JTextField search(Container root) {
        return find(root, JTextField.class);
    }

    private static <T> T find(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
            if (c instanceof Container child) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}

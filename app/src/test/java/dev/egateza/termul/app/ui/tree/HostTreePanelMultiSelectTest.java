package dev.egateza.termul.app.ui.tree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.junit.jupiter.api.Test;

class HostTreePanelMultiSelectTest {

    private final HostProfile a = HostProfile.create("a", "10.0.0.1", "u").withGroup("G");
    private final HostProfile b = HostProfile.create("b", "10.0.0.2", "u").withGroup("G");
    private final HostProfile c = HostProfile.create("c", "10.0.0.3", "u").withGroup("G");

    @Test
    void treeMengizinkanSeleksiTidakBerurutan() {
        var panel = new HostTreePanel(noActions());

        assertThat(tree(panel).getSelectionModel().getSelectionMode())
                .isEqualTo(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    }

    @Test
    void profilTerpilihUrutTreeTanpaDuplikatDanTanpaNodeGrup() {
        var panel = new HostTreePanel(noActions());
        // a juga favorit: muncul di Favorites dan di grup G
        panel.setSnapshot(new ProfileSnapshot(ProfileSnapshot.CURRENT_VERSION, List.of(), List.of(a, b, c),
                List.of(a.id()), List.of()));
        var tree = tree(panel);

        var paths = new ArrayList<TreePath>(pathsOf(tree, c));
        paths.addAll(pathsOf(tree, a)); // dua node untuk a
        paths.add(groupPath(tree));
        tree.setSelectionPaths(paths.toArray(TreePath[]::new));

        assertThat(panel.selectedProfiles()).extracting(HostProfile::id).containsExactly(a.id(), c.id());
    }

    @Test
    void seleksiBertahanSaatSnapshotBerubah() {
        var panel = new HostTreePanel(noActions());
        panel.setSnapshot(new ProfileSnapshot(ProfileSnapshot.CURRENT_VERSION, List.of(), List.of(a, b, c)));
        var tree = tree(panel);
        tree.setSelectionPaths(new TreePath[] {pathsOf(tree, a).getFirst(), pathsOf(tree, c).getFirst()});

        var aSensitive = a.withSensitive(true);
        panel.setSnapshot(new ProfileSnapshot(ProfileSnapshot.CURRENT_VERSION, List.of(), List.of(aSensitive, b, c)));

        assertThat(panel.selectedProfiles()).containsExactly(aSensitive, c);
    }

    private static List<TreePath> pathsOf(JTree tree, HostProfile profile) {
        var result = new ArrayList<TreePath>();
        for (int row = 0; row < tree.getRowCount(); row++) {
            var path = tree.getPathForRow(row);
            if (userObject(path) instanceof HostProfile p && p.id().equals(profile.id())) {
                result.add(path);
            }
        }
        return result;
    }

    private static TreePath groupPath(JTree tree) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            var path = tree.getPathForRow(row);
            if (userObject(path) instanceof HostTreeModelBuilder.GroupNode) {
                return path;
            }
        }
        throw new AssertionError("Node grup tidak ada");
    }

    private static Object userObject(TreePath path) {
        return ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
    }

    private static HostTreePanel.Actions noActions() {
        return (HostTreePanel.Actions) Proxy.newProxyInstance(HostTreePanel.Actions.class.getClassLoader(),
                new Class<?>[] {HostTreePanel.Actions.class}, (proxy, method, args) -> null);
    }

    private static JTree tree(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTree tree) {
                return tree;
            }
            if (c instanceof Container child) {
                JTree found = tree(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}

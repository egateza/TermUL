package dev.egateza.myterm.app.ui.tree;

import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.core.profile.ProfileSnapshot;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;

/** Membangun node {@code JTree} dari {@link ProfileSnapshot}, dengan filter teks opsional. Tanpa state. */
public final class HostTreeModelBuilder {

    /** User object node grup. */
    public record GroupNode(String path) {
        public String name() {
            int idx = path.lastIndexOf('/');
            return idx < 0 ? path : path.substring(idx + 1);
        }

        @Override
        public String toString() {
            return name();
        }
    }

    private HostTreeModelBuilder() {
    }

    /**
     * @param filter teks pencarian (nama, host, user, grup); kosong = semua. Kalau filter aktif,
     *               grup yang tidak berisi hasil disembunyikan.
     */
    public static DefaultMutableTreeNode build(ProfileSnapshot snapshot, String filter) {
        String q = filter == null ? "" : filter.strip().toLowerCase(Locale.ROOT);
        var root = new DefaultMutableTreeNode(new GroupNode(""));
        Map<String, DefaultMutableTreeNode> groups = new HashMap<>();
        groups.put("", root);

        for (String g : snapshot.allGroups()) {
            if (q.isEmpty()) {
                groupNode(groups, g);
            }
        }
        for (String g : snapshotGroupsWithRoot(snapshot)) {
            for (HostProfile p : snapshot.profilesIn(g)) {
                if (matches(p, q)) {
                    groupNode(groups, g).add(new DefaultMutableTreeNode(p, false));
                }
            }
        }
        sortGroupsFirst(root);
        return root;
    }

    static boolean matches(HostProfile p, String q) {
        if (q.isEmpty()) {
            return true;
        }
        return p.name().toLowerCase(Locale.ROOT).contains(q)
                || p.host().toLowerCase(Locale.ROOT).contains(q)
                || p.username().toLowerCase(Locale.ROOT).contains(q)
                || p.group().toLowerCase(Locale.ROOT).contains(q);
    }

    private static Iterable<String> snapshotGroupsWithRoot(ProfileSnapshot snapshot) {
        var list = new java.util.ArrayList<String>();
        list.add("");
        list.addAll(snapshot.allGroups());
        return list;
    }

    private static DefaultMutableTreeNode groupNode(Map<String, DefaultMutableTreeNode> groups, String path) {
        var existing = groups.get(path);
        if (existing != null) {
            return existing;
        }
        int idx = path.lastIndexOf('/');
        var parent = groupNode(groups, idx < 0 ? "" : path.substring(0, idx));
        var node = new DefaultMutableTreeNode(new GroupNode(path), true);
        parent.add(node);
        groups.put(path, node);
        return node;
    }

    /** Grup di atas profil; grup terurut nama (profil sudah terurut dari snapshot). */
    private static void sortGroupsFirst(DefaultMutableTreeNode node) {
        var groups = new java.util.ArrayList<DefaultMutableTreeNode>();
        var hosts = new java.util.ArrayList<DefaultMutableTreeNode>();
        for (int i = 0; i < node.getChildCount(); i++) {
            var child = (DefaultMutableTreeNode) node.getChildAt(i);
            (child.getUserObject() instanceof GroupNode ? groups : hosts).add(child);
        }
        groups.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                ((GroupNode) a.getUserObject()).name(), ((GroupNode) b.getUserObject()).name()));
        node.removeAllChildren();
        groups.forEach(g -> {
            node.add(g);
            sortGroupsFirst(g);
        });
        hosts.forEach(node::add);
    }
}

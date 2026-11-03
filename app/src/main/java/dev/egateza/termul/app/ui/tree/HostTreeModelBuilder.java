package dev.egateza.termul.app.ui.tree;

import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.util.HashMap;
import java.util.List;
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

    /**
     * User object grup bawaan yang tampil di atas dan tidak bisa di-rename/hapus. Isinya referensi ke profil yang sama
     * dengan di grup aslinya.
     */
    public enum BuiltinGroup {
        FAVORITES,
        RECENT
    }

    /** Node tetap paling atas: terminal lokal (ADR 0005); berisi {@link LocalProfileNode} kalau daftarnya diberikan. */
    public enum LocalTerminalNode {
        INSTANCE
    }

    /** Satu shell terdeteksi atau profil terminal lokal di bawah {@link LocalTerminalNode}. */
    public record LocalProfileNode(dev.egateza.termul.app.terminal.LocalProfiles.Choice choice) {
        @Override
        public String toString() {
            return choice.name();
        }
    }

    private HostTreeModelBuilder() {
    }

    /**
     * @param filter teks pencarian (nama, host, user, grup); kosong = semua. Kalau filter aktif, grup yang tidak berisi
     *               hasil disembunyikan.
     */
    public static DefaultMutableTreeNode build(ProfileSnapshot snapshot, String filter) {
        return build(snapshot, filter, null);
    }

    /**
     * @param localChoices isi grup Terminal lokal (default dulu); null = node tunggal tanpa isi
     */
    public static DefaultMutableTreeNode build(ProfileSnapshot snapshot, String filter,
                                               List<dev.egateza.termul.app.terminal.LocalProfiles.Choice> localChoices) {
        String q = filter == null ? "" : filter.strip().toLowerCase(Locale.ROOT);
        var root = new DefaultMutableTreeNode(new GroupNode(""));
        Map<String, DefaultMutableTreeNode> groups = new HashMap<>();
        groups.put("", root);

        if (q.isEmpty()) {
            snapshot.allGroups().stream().filter(g -> !g.isEmpty()).forEach(g -> groupNode(groups, g)); // termasuk yang kosong
        }
        for (String g : snapshotGroupsWithRoot(snapshot)) {
            for (HostProfile p : snapshot.profilesIn(g)) {
                if (matches(p, q)) {
                    groupNode(groups, g).add(new DefaultMutableTreeNode(p, false));
                }
            }
        }
        sortGroupsFirst(root, snapshot.groupComparator());
        int index = 0;
        for (BuiltinGroup b : BuiltinGroup.values()) {
            var node = new DefaultMutableTreeNode(b, true);
            addProfiles(node, switch (b) {
                case FAVORITES -> snapshot.favoriteProfiles();
                case RECENT -> snapshot.recentProfiles();
            }, q);
            if (q.isEmpty() || node.getChildCount() > 0) {
                root.insert(node, index++);
            }
        }
        var local = new DefaultMutableTreeNode(LocalTerminalNode.INSTANCE, localChoices != null);
        boolean groupMatches = q.isEmpty() || matchesLocal(q);
        if (localChoices != null) {
            localChoices.stream().filter(c -> groupMatches || matchesLocal(c, q))
                    .forEach(c -> local.add(new DefaultMutableTreeNode(new LocalProfileNode(c), false)));
        }
        if (groupMatches || local.getChildCount() > 0) {
            root.insert(local, 0);
        }
        return root;
    }

    /** Pencarian pada nama dan perintah profil terminal lokal ({@code q} sudah lowercase). */
    public static boolean matchesLocal(dev.egateza.termul.app.terminal.LocalProfiles.Choice c, String q) {
        return q.isEmpty() || c.name().toLowerCase(Locale.ROOT).contains(q)
                || c.commandLine().toLowerCase(Locale.ROOT).contains(q);
    }

    /** Pencarian "lokal", "local", "terminal", "shell", dst. tetap menampilkan node terminal lokal. */
    public static boolean matchesLocal(String q) {
        return dev.egateza.termul.app.i18n.I18n.t("tree.local").toLowerCase(Locale.ROOT).contains(q)
                || "local terminal shell".contains(q);
    }

    private static void addProfiles(DefaultMutableTreeNode node, List<HostProfile> profiles, String q) {
        profiles.stream().filter(p -> matches(p, q)).forEach(p -> node.add(new DefaultMutableTreeNode(p, false)));
    }

    public static boolean matches(HostProfile p, String q) {
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

    /**
     * Grup di atas profil. Grup terurut seperti di Profile manager (urutan yang diatur user, lalu nama); profil sudah
     * terurut dari snapshot.
     */
    private static void sortGroupsFirst(DefaultMutableTreeNode node, java.util.Comparator<String> order) {
        var groups = new java.util.ArrayList<DefaultMutableTreeNode>();
        var hosts = new java.util.ArrayList<DefaultMutableTreeNode>();
        for (int i = 0; i < node.getChildCount(); i++) {
            var child = (DefaultMutableTreeNode) node.getChildAt(i);
            (child.getUserObject() instanceof GroupNode ? groups : hosts).add(child);
        }
        groups.sort((a, b) -> order.compare(((GroupNode) a.getUserObject()).path(),
                ((GroupNode) b.getUserObject()).path()));
        node.removeAllChildren();
        groups.forEach(g -> {
            node.add(g);
            sortGroupsFirst(g, order);
        });
        hosts.forEach(node::add);
    }
}

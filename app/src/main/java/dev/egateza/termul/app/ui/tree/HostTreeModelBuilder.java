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
     * dengan di grup aslinya. {@link #WSL} hanya tampil kalau manajemen WSL aktif dan ada distro yang berjalan.
     */
    public enum BuiltinGroup {
        FAVORITES,
        RECENT,
        WSL
    }

    /** User object distro WSL yang sedang berjalan tapi belum punya profil SSH (lihat {@link HostProfile#wslDistro()}). */
    public record WslDistroNode(String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private HostTreeModelBuilder() {
    }

    /** Tanpa grup WSL. */
    public static DefaultMutableTreeNode build(ProfileSnapshot snapshot, String filter) {
        return build(snapshot, filter, null);
    }

    /**
     * Profil WSL ({@link HostProfile#wslDistro()} terisi) hanya tampil di grup bawaan {@link BuiltinGroup#WSL}, tidak
     * di grup biasanya; selama manajemen WSL mati, profil itu disembunyikan dari seluruh tree (termasuk Favorites/Last
     * used) dan grup biasa yang hanya berisi profil WSL ikut hilang.
     *
     * @param filter     teks pencarian (nama, host, user, grup); kosong = semua. Kalau filter aktif,
     *                   grup yang tidak berisi hasil disembunyikan.
     * @param wslRunning nama distro WSL yang sedang berjalan; null = manajemen WSL mati (grup WSL tidak tampil)
     */
    public static DefaultMutableTreeNode build(ProfileSnapshot snapshot, String filter, List<String> wslRunning) {
        String q = filter == null ? "" : filter.strip().toLowerCase(Locale.ROOT);
        boolean wslOn = wslRunning != null;
        var root = new DefaultMutableTreeNode(new GroupNode(""));
        Map<String, DefaultMutableTreeNode> groups = new HashMap<>();
        groups.put("", root);

        if (q.isEmpty()) {
            // grup kosong tetap tampil, kecuali grup yang hanya terbentuk dari profil WSL
            var visibleGroups = new java.util.TreeSet<String>(snapshot.groups());
            snapshot.profiles().stream().filter(p -> !isWsl(p)).forEach(p -> visibleGroups.add(p.group()));
            visibleGroups.stream().filter(g -> !g.isEmpty()).forEach(g -> groupNode(groups, g));
        }
        for (String g : snapshotGroupsWithRoot(snapshot)) {
            for (HostProfile p : snapshot.profilesIn(g)) {
                if (!isWsl(p) && matches(p, q)) {
                    groupNode(groups, g).add(new DefaultMutableTreeNode(p, false));
                }
            }
        }
        sortGroupsFirst(root);
        int index = 0;
        for (BuiltinGroup b : BuiltinGroup.values()) {
            var node = new DefaultMutableTreeNode(b, true);
            switch (b) {
                case FAVORITES -> addProfiles(node, visible(snapshot.favoriteProfiles(), wslOn), q);
                case RECENT -> addProfiles(node, visible(snapshot.recentProfiles(), wslOn), q);
                case WSL -> {
                    if (wslOn) {
                        addWsl(node, snapshot, wslRunning, q);
                    }
                }
            }
            // grup WSL kosong (tidak ada profil WSL maupun distro berjalan) tidak ditampilkan sama sekali
            boolean show = b == BuiltinGroup.WSL ? node.getChildCount() > 0 : q.isEmpty() || node.getChildCount() > 0;
            if (show) {
                root.insert(node, index++);
            }
        }
        return root;
    }

    private static boolean isWsl(HostProfile p) {
        return p.wslDistro() != null;
    }

    private static List<HostProfile> visible(List<HostProfile> profiles, boolean wslOn) {
        return wslOn ? profiles : profiles.stream().filter(p -> !isWsl(p)).toList();
    }

    private static void addProfiles(DefaultMutableTreeNode node, List<HostProfile> profiles, String q) {
        profiles.stream().filter(p -> matches(p, q)).forEach(p -> node.add(new DefaultMutableTreeNode(p, false)));
    }

    /**
     * Semua profil WSL (termasuk yang distronya berhenti), urut nama, lalu {@link WslDistroNode} untuk distro yang
     * berjalan tapi belum punya profil.
     */
    private static void addWsl(DefaultMutableTreeNode node, ProfileSnapshot snapshot, List<String> running, String q) {
        var profiles = snapshot.profiles().stream().filter(HostTreeModelBuilder::isWsl)
                .sorted(java.util.Comparator.comparing(HostProfile::name, String.CASE_INSENSITIVE_ORDER)).toList();
        addProfiles(node, profiles, q);
        for (String distro : running) {
            boolean hasProfile = profiles.stream().anyMatch(p -> distro.equalsIgnoreCase(p.wslDistro()));
            if (!hasProfile && (q.isEmpty() || distro.toLowerCase(Locale.ROOT).contains(q))) {
                node.add(new DefaultMutableTreeNode(new WslDistroNode(distro), false));
            }
        }
    }

    static boolean matches(HostProfile p, String q) {
        if (q.isEmpty()) {
            return true;
        }
        return p.name().toLowerCase(Locale.ROOT).contains(q)
                || (p.wslDistro() != null && p.wslDistro().toLowerCase(Locale.ROOT).contains(q))
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

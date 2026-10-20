package dev.egateza.termul.app.ui.highlight;

import dev.egateza.termul.app.terminal.LocalProfiles;
import dev.egateza.termul.core.highlight.HighlightProfile;
import dev.egateza.termul.core.highlight.HighlightSettings;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Target penugasan profil highlight (bagian "Berlaku untuk" di dialog): grup host, host, semua terminal lokal, dan profil
 * terminal lokal. Murni (tanpa komponen Swing selain node tree) supaya bisa di-test.
 */
final class HighlightTargets {

    sealed interface Target permits Group, Host, AllLocals, Local {
    }

    /** @param path grup lengkap, mis. {@code Prod/DB}; {@code name} = bagian terakhir */
    record Group(String path, String name) implements Target {
    }

    record Host(HostProfile profile) implements Target {
    }

    enum AllLocals implements Target {
        INSTANCE
    }

    record Local(LocalProfiles.Choice choice) implements Target {
    }

    /** Pemilik target: profil yang memegangnya langsung, atau yang diwarisi dari grup induk / semua lokal. */
    record Owner(String profileId, boolean inherited) {
    }

    private HighlightTargets() {
    }

    /** Tree: grup (bersarang) dan host di dalamnya, host tanpa grup, lalu node Terminal lokal dengan profilnya. */
    static DefaultMutableTreeNode tree(ProfileSnapshot snapshot, List<LocalProfiles.Choice> locals) {
        var root = new DefaultMutableTreeNode();
        var paths = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        snapshot.allGroups().forEach(g -> addWithParents(paths, g));
        snapshot.profiles().forEach(p -> addWithParents(paths, p.group()));
        var groupNodes = new HashMap<String, DefaultMutableTreeNode>();
        for (String path : paths) {
            int slash = path.lastIndexOf('/');
            var node = new DefaultMutableTreeNode(new Group(path, slash < 0 ? path : path.substring(slash + 1)));
            DefaultMutableTreeNode parent = slash < 0 ? root : groupNodes.getOrDefault(path.substring(0, slash), root);
            parent.add(node);
            groupNodes.put(path, node);
        }
        snapshot.profiles().stream()
                .sorted(Comparator.comparing(HostProfile::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(p -> groupNodes.getOrDefault(p.group(), root).add(new DefaultMutableTreeNode(new Host(p))));
        var localRoot = new DefaultMutableTreeNode(AllLocals.INSTANCE);
        locals.forEach(c -> localRoot.add(new DefaultMutableTreeNode(new Local(c))));
        root.add(localRoot);
        return root;
    }

    private static void addWithParents(TreeSet<String> paths, String group) {
        String g = HostProfile.normalizeGroup(group);
        while (!g.isEmpty()) {
            paths.add(g);
            int slash = g.lastIndexOf('/');
            g = slash < 0 ? "" : g.substring(0, slash);
        }
    }

    /** Profil yang memegang target ini langsung (null = tidak ada). */
    static String direct(Target target, HighlightSettings s) {
        return switch (target) {
            case Group g -> s.groups().get(g.path());
            case Host h -> s.hosts().get(h.profile().id());
            case AllLocals _ -> s.allLocalsProfileId();
            case Local l -> s.locals().get(l.choice().id());
        };
    }

    /** Pemilik efektif tanpa profil global: langsung, lalu grup induk / semua lokal. */
    static Optional<Owner> owner(Target target, HighlightSettings s) {
        String direct = direct(target, s);
        if (direct != null) {
            return Optional.of(new Owner(direct, false));
        }
        String inherited = switch (target) {
            case Group g -> parentGroupOwner(g.path(), s);
            case Host h -> groupOwner(h.profile().group(), s);
            case AllLocals _ -> null;
            case Local _ -> s.allLocalsProfileId();
        };
        return inherited == null ? Optional.empty() : Optional.of(new Owner(inherited, true));
    }

    private static String parentGroupOwner(String path, HighlightSettings s) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? null : groupOwner(path.substring(0, slash), s);
    }

    private static String groupOwner(String group, HighlightSettings s) {
        String g = HostProfile.normalizeGroup(group);
        while (!g.isEmpty()) {
            String owner = s.groups().get(g);
            if (owner != null) {
                return owner;
            }
            int slash = g.lastIndexOf('/');
            g = slash < 0 ? "" : g.substring(0, slash);
        }
        return null;
    }

    /** true kalau target dicentang untuk profil {@code profileId}. */
    static boolean checked(Target target, HighlightSettings s, String profileId) {
        return Objects.equals(direct(target, s), profileId);
    }

    /**
     * true kalau target tidak dicentang langsung tapi sudah ikut profil {@code profileId} lewat grup induk atau "Terminal
     * lokal (semua)". Di tree tampil tercentang abu-abu.
     */
    static boolean inheritedChecked(Target target, HighlightSettings s, String profileId) {
        return owner(target, s).filter(o -> o.inherited() && o.profileId().equals(profileId)).isPresent();
    }

    /** Profil lain yang memegang target ini langsung dan akan kehilangan target kalau dicentang untuk {@code profileId}. */
    static Optional<String> takenFrom(Target target, HighlightSettings s, String profileId) {
        return Optional.ofNullable(direct(target, s)).filter(owner -> !owner.equals(profileId));
    }

    /** Centang/hilangkan centang target untuk profil {@code profileId}; target milik profil lain pindah ke sini. */
    static HighlightSettings toggle(Target target, HighlightSettings s, String profileId) {
        boolean on = !checked(target, s, profileId);
        var hosts = mine(s.hosts(), profileId);
        var groups = mine(s.groups(), profileId);
        var locals = mine(s.locals(), profileId);
        boolean all = profileId.equals(s.allLocalsProfileId());
        switch (target) {
            case Group g -> set(groups, g.path(), on);
            case Host h -> set(hosts, h.profile().id(), on);
            case AllLocals _ -> all = on;
            case Local l -> set(locals, l.choice().id(), on);
        }
        return s.withAssignments(profileId, hosts, groups, locals, all);
    }

    private static <K> HashSet<K> mine(Map<K, String> map, String profileId) {
        var out = new HashSet<K>();
        map.forEach((k, v) -> {
            if (v.equals(profileId)) {
                out.add(k);
            }
        });
        return out;
    }

    private static <K> void set(HashSet<K> set, K key, boolean on) {
        if (on) {
            set.add(key);
        } else {
            set.remove(key);
        }
    }

    /** Keterangan abu-abu di belakang target: siapa pemiliknya kalau bukan profil yang sedang diedit. */
    static String note(Target target, HighlightSettings s, String profileId, Map<String, String> names) {
        return owner(target, s).filter(o -> o.inherited() || !o.profileId().equals(profileId))
                .map(o -> {
                    String name = names.getOrDefault(o.profileId(), "?");
                    if (o.inherited() && o.profileId().equals(profileId)) {
                        return dev.egateza.termul.app.i18n.I18n.t(target instanceof Local
                                ? "highlight.target.followsLocals" : "highlight.target.followsGroup");
                    }
                    if (!o.inherited()) {
                        return dev.egateza.termul.app.i18n.I18n.t("highlight.target.owner", name);
                    }
                    return dev.egateza.termul.app.i18n.I18n.t(target instanceof Local
                            ? "highlight.target.viaLocals" : "highlight.target.viaGroup", name);
                })
                .orElse("");
    }

    /** id profil → nama, untuk keterangan pemilik. */
    static Map<String, String> names(List<HighlightProfile> profiles) {
        var out = new HashMap<String, String>();
        profiles.forEach(p -> out.put(p.id(), p.name()));
        return out;
    }

    /** Jumlah target yang dipegang profil ini (untuk ringkasan di combo profil). */
    static int count(HighlightSettings s, String profileId) {
        var all = new ArrayList<String>();
        all.addAll(s.hosts().values());
        all.addAll(s.groups().values());
        all.addAll(s.locals().values());
        if (s.allLocalsProfileId() != null) {
            all.add(s.allLocalsProfileId());
        }
        return (int) all.stream().filter(profileId::equals).count();
    }
}

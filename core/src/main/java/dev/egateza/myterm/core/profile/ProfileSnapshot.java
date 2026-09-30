package dev.egateza.myterm.core.profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Snapshot immutable isi {@code profiles.json}.
 *
 * @param version  versi format file
 * @param groups   grup eksplisit (termasuk grup kosong); grup dari profil ditambahkan otomatis oleh {@link #allGroups()}
 * @param profiles daftar profil
 */
public record ProfileSnapshot(int version, List<String> groups, List<HostProfile> profiles) {

    public static final int CURRENT_VERSION = 1;

    public ProfileSnapshot {
        groups = groups == null ? List.of() : groups.stream()
                .map(HostProfile::normalizeGroup)
                .filter(g -> !g.isEmpty())
                .distinct()
                .sorted()
                .toList();
        profiles = profiles == null ? List.of() : List.copyOf(profiles);
        long distinct = profiles.stream().map(HostProfile::id).distinct().count();
        if (distinct != profiles.size()) {
            throw new IllegalArgumentException("Terdapat id profil duplikat");
        }
    }

    public static ProfileSnapshot empty() {
        return new ProfileSnapshot(CURRENT_VERSION, List.of(), List.of());
    }

    public Optional<HostProfile> find(UUID id) {
        return profiles.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** Semua grup (eksplisit + dari profil + semua parent-nya), terurut. */
    public List<String> allGroups() {
        var result = new TreeSet<String>();
        groups.forEach(g -> addWithParents(result, g));
        profiles.forEach(p -> addWithParents(result, p.group()));
        return List.copyOf(result);
    }

    /** Profil tepat di grup {@code group} (bukan subgrup), terurut nama. */
    public List<HostProfile> profilesIn(String group) {
        String g = HostProfile.normalizeGroup(group);
        return profiles.stream()
                .filter(p -> p.group().equals(g))
                .sorted(Comparator.comparing(HostProfile::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    ProfileSnapshot withProfile(HostProfile profile) {
        var list = new ArrayList<HostProfile>(profiles.size() + 1);
        boolean replaced = false;
        for (HostProfile p : profiles) {
            if (p.id().equals(profile.id())) {
                list.add(profile);
                replaced = true;
            } else {
                list.add(p);
            }
        }
        if (!replaced) {
            list.add(profile);
        }
        return new ProfileSnapshot(version, groups, list);
    }

    ProfileSnapshot withoutProfile(UUID id) {
        return new ProfileSnapshot(version, groups, profiles.stream().filter(p -> !p.id().equals(id)).toList());
    }

    ProfileSnapshot withGroups(List<String> newGroups) {
        return new ProfileSnapshot(version, newGroups, profiles);
    }

    private static void addWithParents(TreeSet<String> set, String group) {
        String g = group;
        while (!g.isEmpty()) {
            set.add(g);
            int idx = g.lastIndexOf('/');
            g = idx < 0 ? "" : g.substring(0, idx);
        }
    }
}

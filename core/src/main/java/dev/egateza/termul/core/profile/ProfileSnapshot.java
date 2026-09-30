package dev.egateza.termul.core.profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Snapshot immutable isi {@code profiles.json}.
 *
 * @param version  versi format file
 * @param groups   grup eksplisit (termasuk grup kosong); grup dari profil ditambahkan otomatis oleh {@link #allGroups()}
 * @param profiles  daftar profil
 * @param favorites id profil favorit, urut sesuai waktu ditambahkan
 * @param recent    id profil yang terakhir dipakai, terbaru di depan, maksimal {@link #RECENT_LIMIT}
 */
public record ProfileSnapshot(int version, List<String> groups, List<HostProfile> profiles,
                              List<UUID> favorites, List<UUID> recent) {

    public static final int CURRENT_VERSION = 1;
    public static final int RECENT_LIMIT = 5;

    public ProfileSnapshot(int version, List<String> groups, List<HostProfile> profiles) {
        this(version, groups, profiles, List.of(), List.of());
    }

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
        var ids = profiles.stream().map(HostProfile::id).collect(Collectors.toSet());
        favorites = existingIds(favorites, ids, Integer.MAX_VALUE);
        recent = existingIds(recent, ids, RECENT_LIMIT);
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

    public boolean isFavorite(UUID id) {
        return favorites.contains(id);
    }

    /** Profil favorit sesuai urutan {@link #favorites()}. */
    public List<HostProfile> favoriteProfiles() {
        return resolve(favorites);
    }

    /** Profil yang terakhir dipakai, terbaru di depan. */
    public List<HostProfile> recentProfiles() {
        return resolve(recent);
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
        return new ProfileSnapshot(version, groups, list, favorites, recent);
    }

    ProfileSnapshot withoutProfile(UUID id) {
        return new ProfileSnapshot(version, groups, profiles.stream().filter(p -> !p.id().equals(id)).toList(),
                favorites, recent);
    }

    ProfileSnapshot withGroups(List<String> newGroups) {
        return new ProfileSnapshot(version, newGroups, profiles, favorites, recent);
    }

    ProfileSnapshot withFavorite(UUID id, boolean favorite) {
        var list = new ArrayList<>(favorites);
        list.remove(id);
        if (favorite) {
            list.add(id);
        }
        return new ProfileSnapshot(version, groups, profiles, list, recent);
    }

    ProfileSnapshot withUsed(UUID id) {
        var list = new ArrayList<UUID>(recent.size() + 1);
        list.add(id);
        recent.stream().filter(r -> !r.equals(id)).forEach(list::add);
        return new ProfileSnapshot(version, groups, profiles, favorites, list);
    }

    ProfileSnapshot withoutRecent() {
        return new ProfileSnapshot(version, groups, profiles, favorites, List.of());
    }

    private List<HostProfile> resolve(List<UUID> ids) {
        return ids.stream().map(this::find).flatMap(Optional::stream).toList();
    }

    /** Buang id yang tidak ada di profil (mis. profil sudah dihapus), duplikat, dan null; potong ke {@code limit}. */
    private static List<UUID> existingIds(List<UUID> list, Set<UUID> ids, int limit) {
        if (list == null) {
            return List.of();
        }
        return list.stream().filter(Objects::nonNull).filter(ids::contains).distinct().limit(limit).toList();
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

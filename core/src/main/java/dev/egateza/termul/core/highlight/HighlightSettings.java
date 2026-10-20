package dev.egateza.termul.core.highlight;

import dev.egateza.termul.core.profile.HostProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Pengaturan highlight teks. Profil dipilih per tab dengan urutan prioritas: host (atau profil terminal lokal) →
 * grup paling spesifik (atau semua terminal lokal) → profil global. Satu host/grup/profil lokal hanya dipegang oleh
 * satu profil highlight (berupa map target → id profil).
 *
 * @param enabled          saklar utama
 * @param alternateDisable jangan highlight di alternate screen (vim, less, htop)
 * @param globalProfileId  profil untuk tab yang tidak punya penugasan; null = tidak ada
 * @param hosts            id host → id profil
 * @param groups           grup host (mis. {@code Prod/DB}, juga untuk subgrupnya) → id profil
 * @param locals           id profil terminal lokal (atau shell terdeteksi) → id profil
 * @param allLocalsProfileId profil untuk semua terminal lokal yang tidak punya penugasan sendiri; null = ikut global
 */
public record HighlightSettings(boolean enabled, boolean alternateDisable, String globalProfileId,
                                List<HighlightProfile> profiles, Map<UUID, String> hosts, Map<String, String> groups,
                                Map<UUID, String> locals, String allLocalsProfileId) {

    public HighlightSettings {
        profiles = List.copyOf(profiles);
        hosts = Map.copyOf(hosts);
        groups = Map.copyOf(groups);
        locals = Map.copyOf(locals);
    }

    /** Isi awal: profil Default (ERROR/WARN/INFO) sebagai global, tapi highlight belum aktif. */
    public static HighlightSettings defaults() {
        var p = HighlightProfile.defaults();
        return new HighlightSettings(false, false, p.id(), List.of(p), Map.of(), Map.of(), Map.of(), null);
    }

    public Optional<HighlightProfile> profile(String id) {
        return id == null ? Optional.empty() : profiles.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** Profil untuk tab SSH: host → grup paling spesifik → global. Kosong kalau highlight mati atau tidak ada. */
    public Optional<HighlightProfile> forHost(UUID hostId, String group) {
        if (!enabled) {
            return Optional.empty();
        }
        String chosen = hostId == null ? null : hosts.get(hostId);
        if (chosen == null) {
            chosen = forGroup(group);
        }
        return profile(chosen == null ? globalProfileId : chosen);
    }

    /** Profil untuk tab terminal lokal: profil lokal itu → semua terminal lokal → global. */
    public Optional<HighlightProfile> forLocal(UUID localId) {
        if (!enabled) {
            return Optional.empty();
        }
        String chosen = localId == null ? null : locals.get(localId);
        if (chosen == null) {
            chosen = allLocalsProfileId;
        }
        return profile(chosen == null ? globalProfileId : chosen);
    }

    private String forGroup(String group) {
        String g = HostProfile.normalizeGroup(group == null ? "" : group);
        while (!g.isEmpty()) {
            String chosen = groups.get(g);
            if (chosen != null) {
                return chosen;
            }
            int slash = g.lastIndexOf('/');
            g = slash < 0 ? "" : g.substring(0, slash);
        }
        return null;
    }

    public HighlightSettings withEnabled(boolean value) {
        return new HighlightSettings(value, alternateDisable, globalProfileId, profiles, hosts, groups, locals,
                allLocalsProfileId);
    }

    public HighlightSettings withAlternateDisable(boolean value) {
        return new HighlightSettings(enabled, value, globalProfileId, profiles, hosts, groups, locals,
                allLocalsProfileId);
    }

    public HighlightSettings withGlobalProfile(String id) {
        return new HighlightSettings(enabled, alternateDisable, id, profiles, hosts, groups, locals, allLocalsProfileId);
    }

    /** Tambah atau ganti profil (berdasarkan id), urutan profil lain tetap. */
    public HighlightSettings withProfile(HighlightProfile profile) {
        var list = new ArrayList<>(profiles);
        int at = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(profile.id())) {
                at = i;
            }
        }
        if (at < 0) {
            list.add(profile);
        } else {
            list.set(at, profile);
        }
        return new HighlightSettings(enabled, alternateDisable, globalProfileId, list, hosts, groups, locals,
                allLocalsProfileId);
    }

    /** Hapus profil beserta semua penugasannya. */
    public HighlightSettings withoutProfile(String id) {
        Predicate<String> keep = v -> !v.equals(id);
        return new HighlightSettings(enabled, alternateDisable, id.equals(globalProfileId) ? null : globalProfileId,
                profiles.stream().filter(p -> !p.id().equals(id)).toList(), filter(hosts, keep), filter(groups, keep),
                filter(locals, keep), id.equals(allLocalsProfileId) ? null : allLocalsProfileId);
    }

    /**
     * Ganti penugasan milik satu profil. Target yang sebelumnya milik profil lain pindah ke profil ini.
     *
     * @param allLocals profil ini berlaku untuk semua terminal lokal
     */
    public HighlightSettings withAssignments(String profileId, java.util.Set<UUID> hostIds,
                                             java.util.Set<String> groupPaths, java.util.Set<UUID> localIds,
                                             boolean allLocals) {
        Predicate<String> notMine = v -> !v.equals(profileId);
        var h = new HashMap<>(filter(hosts, notMine));
        hostIds.forEach(id -> h.put(id, profileId));
        var g = new HashMap<>(filter(groups, notMine));
        groupPaths.forEach(path -> g.put(HostProfile.normalizeGroup(path), profileId));
        var l = new HashMap<>(filter(locals, notMine));
        localIds.forEach(id -> l.put(id, profileId));
        String all = allLocals ? profileId : Objects.equals(allLocalsProfileId, profileId) ? null : allLocalsProfileId;
        return new HighlightSettings(enabled, alternateDisable, globalProfileId, profiles, h, g, l, all);
    }

    private static <K> Map<K, String> filter(Map<K, String> map, Predicate<String> keep) {
        var out = new HashMap<K, String>();
        map.forEach((k, v) -> {
            if (keep.test(v)) {
                out.put(k, v);
            }
        });
        return out;
    }
}

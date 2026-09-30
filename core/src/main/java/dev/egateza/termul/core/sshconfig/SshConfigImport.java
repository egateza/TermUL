package dev.egateza.termul.core.sshconfig;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mengubah host dari {@code ~/.ssh/config} menjadi {@link HostProfile}. Profil yang sudah ada (user, host, dan port
 * sama) dipakai ulang dan tidak diubah. Hop {@code ProxyJump} yang bukan alias dan belum ada dibuat sebagai profil
 * baru; rantai {@code A,B} berarti tujuan lewat B, dan B lewat A.
 */
public final class SshConfigImport {

    /**
     * @param profile  profil hasil impor (atau profil lama yang cocok)
     * @param existing true kalau profil ini sudah ada (tidak disimpan ulang)
     * @param fromJump true kalau dibuat dari hop ProxyJump (bukan alias Host)
     * @param note     keterangan untuk user (mis. fitur yang tidak didukung), boleh kosong
     */
    public record Candidate(HostProfile profile, boolean existing, boolean fromJump, String note) {
    }

    private SshConfigImport() {
    }

    public static List<Candidate> plan(List<SshConfigHost> hosts, ProfileSnapshot snapshot, String defaultUser,
                                       String group) {
        var byAddress = new HashMap<String, HostProfile>();
        snapshot.profiles().forEach(p -> byAddress.putIfAbsent(key(p.username(), p.host(), p.port()), p));
        var result = new LinkedHashMap<UUID, Candidate>();
        var byAlias = new HashMap<String, UUID>();

        for (SshConfigHost h : hosts) {
            String user = h.user() != null ? h.user() : defaultUser;
            int port = h.port() != null ? h.port() : HostProfile.DEFAULT_PORT;
            HostProfile old = byAddress.get(key(user, h.effectiveHost(), port));
            if (old != null) {
                result.putIfAbsent(old.id(), new Candidate(old, true, false, ""));
                byAlias.put(h.alias(), old.id());
                continue;
            }
            var notes = new ArrayList<String>();
            AuthMethod auth = AuthMethod.AGENT;
            String key = null;
            if (h.identityFile() != null) {
                if (Files.isRegularFile(Path.of(h.identityFile()))) {
                    auth = AuthMethod.KEY;
                    key = h.identityFile();
                } else {
                    notes.add("IdentityFile tidak ditemukan: " + h.identityFile());
                }
            }
            if (h.hasProxyCommand()) {
                notes.add("ProxyCommand tidak didukung (diimpor tanpa proxy)");
            }
            var p = new HostProfile(UUID.randomUUID(), h.alias(), group, h.effectiveHost(), port, user, auth, key,
                    null, EnvironmentTag.NONE, null, null, false);
            byAddress.put(key(user, p.host(), port), p);
            byAlias.put(h.alias(), p.id());
            result.put(p.id(), new Candidate(p, false, false, String.join("; ", notes)));
        }

        for (SshConfigHost h : hosts) {
            if (h.proxyJump().isEmpty()) {
                continue;
            }
            var hops = new ArrayList<UUID>();
            for (String spec : h.proxyJump()) {
                UUID hop = byAlias.get(spec);
                if (hop == null) {
                    hop = hopProfile(spec, defaultUser, group, byAddress, result);
                }
                if (hop != null) {
                    hops.add(hop);
                }
            }
            UUID target = byAlias.get(h.alias());
            // tujuan lewat hop terakhir; tiap hop lewat hop sebelumnya (hanya profil baru, tanpa jump sendiri)
            var chain = new ArrayList<UUID>(hops);
            chain.add(target);
            for (int i = chain.size() - 1; i >= 1; i--) {
                setJump(result, chain.get(i), chain.get(i - 1));
            }
        }
        return List.copyOf(result.values());
    }

    /** Profil baru yang perlu disimpan untuk pilihan user, termasuk jump host yang dibutuhkan. */
    public static List<HostProfile> toSave(Collection<Candidate> plan, Set<UUID> selected) {
        var byId = new HashMap<UUID, Candidate>();
        plan.forEach(c -> byId.put(c.profile().id(), c));
        var out = new LinkedHashMap<UUID, HostProfile>();
        for (UUID id : selected) {
            UUID cur = id;
            int guard = 0;
            while (cur != null && guard++ < 10) {
                Candidate c = byId.get(cur);
                if (c == null || c.existing()) {
                    break;
                }
                out.putIfAbsent(cur, c.profile());
                cur = c.profile().jumpHostId();
            }
        }
        // jump host disimpan lebih dulu (urutan tidak wajib, tapi rapi di file)
        var ordered = new ArrayList<>(out.values());
        java.util.Collections.reverse(ordered);
        return ordered;
    }

    private static void setJump(Map<UUID, Candidate> result, UUID id, UUID jump) {
        Candidate c = result.get(id);
        if (c == null || c.existing() || c.profile().jumpHostId() != null || id.equals(jump)) {
            return;
        }
        result.put(id, new Candidate(c.profile().withJumpHostId(jump), false, c.fromJump(), c.note()));
    }

    /** Hop {@code [user@]host[:port]} (IPv6 dalam {@code []}); profil lama dipakai kalau alamatnya sama. */
    private static UUID hopProfile(String spec, String defaultUser, String group, Map<String, HostProfile> byAddress,
                                   Map<UUID, Candidate> result) {
        String user = defaultUser;
        String rest = spec;
        int at = rest.lastIndexOf('@');
        if (at > 0) {
            user = rest.substring(0, at);
            rest = rest.substring(at + 1);
        }
        String host = rest;
        int port = HostProfile.DEFAULT_PORT;
        try {
            if (rest.startsWith("[")) {
                int close = rest.indexOf(']');
                host = rest.substring(1, close);
                if (close + 1 < rest.length() && rest.charAt(close + 1) == ':') {
                    port = Integer.parseInt(rest.substring(close + 2));
                }
            } else if (rest.indexOf(':') > 0 && rest.indexOf(':') == rest.lastIndexOf(':')) {
                host = rest.substring(0, rest.indexOf(':'));
                port = Integer.parseInt(rest.substring(rest.indexOf(':') + 1));
            }
        } catch (RuntimeException e) {
            return null; // spec tidak valid: dilewati
        }
        HostProfile old = byAddress.get(key(user, host, port));
        if (old != null) {
            result.putIfAbsent(old.id(), new Candidate(old, !isNew(result, old), true, ""));
            return old.id();
        }
        var p = new HostProfile(UUID.randomUUID(), spec, group, host, port, user, AuthMethod.AGENT, null, null,
                EnvironmentTag.NONE, null, null, false);
        byAddress.put(key(user, host, port), p);
        result.put(p.id(), new Candidate(p, false, true, "Jump host dari ProxyJump"));
        return p.id();
    }

    private static boolean isNew(Map<UUID, Candidate> result, HostProfile p) {
        Candidate c = result.get(p.id());
        return c != null && !c.existing();
    }

    private static String key(String user, String host, int port) {
        return user + "@" + host.toLowerCase(Locale.ROOT) + ":" + port;
    }
}

package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.terminal.LocalProfiles;
import dev.egateza.termul.app.ui.tree.HostTreeModelBuilder;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Isi popup Tab baru: terminal lokal (default dulu), lalu Favorites, Last used, dan host lainnya (urut grup lalu nama), disaring
 * dengan aturan yang sama seperti pencarian di daftar host. Murni (tanpa Swing) supaya bisa di-test.
 */
final class QuickOpenItems {

    /** Satu baris di popup. */
    sealed interface Item permits Local, Host {
    }

    /** Shell terdeteksi atau profil terminal lokal (ADR 0005). */
    record Local(LocalProfiles.Choice choice) implements Item {
    }

    /** @param pinned host dari Favorites atau Last used (ditampilkan dengan penanda) */
    record Host(HostProfile profile, boolean pinned) implements Item {
    }

    private QuickOpenItems() {
    }

    /**
     * @param query      teks pencarian (boleh kosong)
     * @param wslEnabled false = profil WSL disembunyikan, sama seperti daftar host saat Manajemen WSL mati
     * @param locals     pilihan terminal lokal, default dulu
     */
    static List<Item> build(ProfileSnapshot snapshot, String query, boolean wslEnabled,
                            List<LocalProfiles.Choice> locals) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        var items = new ArrayList<Item>();
        boolean allLocal = q.isEmpty() || HostTreeModelBuilder.matchesLocal(q);
        locals.stream().filter(c -> allLocal || HostTreeModelBuilder.matchesLocal(c, q))
                .forEach(c -> items.add(new Local(c)));
        var seen = new LinkedHashSet<UUID>();
        var pinned = new ArrayList<HostProfile>(snapshot.favoriteProfiles());
        pinned.addAll(snapshot.recentProfiles());
        for (HostProfile p : pinned) {
            if (visible(p, q, wslEnabled) && seen.add(p.id())) {
                items.add(new Host(p, true));
            }
        }
        snapshot.profiles().stream()
                .filter(p -> visible(p, q, wslEnabled) && !seen.contains(p.id()))
                .sorted(Comparator.comparing(HostProfile::group, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(HostProfile::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(p -> items.add(new Host(p, false)));
        return items;
    }

    private static boolean visible(HostProfile p, String q, boolean wslEnabled) {
        return (wslEnabled || p.wslDistro() == null) && HostTreeModelBuilder.matches(p, q);
    }
}

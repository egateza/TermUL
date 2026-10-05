package dev.egateza.termul.update;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rencana memasang sebuah rilis: jar mana yang cukup disalin dari lokal (hash sama) dan mana yang harus diunduh.
 *
 * @param local    entry → file lokal dengan ukuran dan hash yang sama
 * @param download entry yang tidak ada di lokal
 */
public record InstallPlan(SignedRelease release, Map<UpdateManifest.FileEntry, Path> local,
                          List<UpdateManifest.FileEntry> download) {

    public InstallPlan {
        local = Map.copyOf(local);
        download = List.copyOf(download);
    }

    /** Membandingkan setiap entry manifest dengan jar lokal yang tersedia. */
    public static InstallPlan of(SignedRelease release, LocalJars localJars) {
        var local = new HashMap<UpdateManifest.FileEntry, Path>();
        var download = new ArrayList<UpdateManifest.FileEntry>();
        for (var entry : release.manifest().files()) {
            localJars.find(entry).ifPresentOrElse(p -> local.put(entry, p), () -> download.add(entry));
        }
        return new InstallPlan(release, local, download);
    }

    public long downloadBytes() {
        return download.stream().mapToLong(UpdateManifest.FileEntry::size).sum();
    }
}

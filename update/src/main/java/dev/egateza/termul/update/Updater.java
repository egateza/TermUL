package dev.egateza.termul.update;

import java.io.IOException;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;

/**
 * Alur "Periksa update" di aplikasi: cek rilis terbaru, tentukan apakah bisa dipasang lewat menu, lalu pasang.
 * Semua method blocking (jaringan + disk): panggil di luar EDT.
 */
public final class Updater {

    /**
     * Kondisi instalasi yang sedang berjalan.
     *
     * @param running    versi yang sedang berjalan; null = build dev (dari IDE)
     * @param generation generation Bootstrap yang terpasang; 0 = tidak dijalankan lewat Bootstrap
     * @param runningUpdate versi update yang sedang berjalan (folder-nya tidak boleh dihapus); null = versi bawaan
     * @param classPath  {@code java.class.path}: jar bawaan yang bisa dipakai ulang
     */
    public record Environment(ReleaseVersion running, int generation, ReleaseVersion runningUpdate, String classPath) {

        /** Dari system property yang diset Bootstrap. */
        public static Environment detect(String appVersion) {
            return new Environment(ReleaseVersion.parseOrNull(appVersion),
                    Integer.getInteger(UpdateProtocol.PROP_GENERATION, 0),
                    ReleaseVersion.parseOrNull(System.getProperty(UpdateProtocol.PROP_RUNNING_UPDATE)),
                    System.getProperty("java.class.path", ""));
        }
    }

    /** Hasil pemeriksaan. */
    public sealed interface Check {
        SignedRelease latest();
    }

    /** Versi yang berjalan sudah yang terbaru (atau lebih baru). */
    public record UpToDate(SignedRelease latest) implements Check {
    }

    /** Ada versi baru yang bisa dipasang lewat menu. */
    public record Available(SignedRelease latest, InstallPlan plan) implements Check {
    }

    /** Ada versi baru, tapi harus diunduh manual dari halaman rilis. */
    public record ManualOnly(SignedRelease latest, Reason reason) implements Check {
    }

    public enum Reason {
        /** Dijalankan dari IDE / tanpa versi rilis. */
        DEV_BUILD,
        /** Tidak lewat Bootstrap (mis. instalasi lama sebelum fitur update). */
        NO_BOOTSTRAP,
        /** Rilis butuh runtime/Bootstrap yang lebih baru. */
        NEEDS_INSTALLER
    }

    private final ReleaseFeed feed;
    private final UpdateStore store;
    private final PublicKey key;
    private final Environment env;

    public Updater(ReleaseFeed feed, UpdateStore store, PublicKey key, Environment env) {
        this.feed = feed;
        this.store = store;
        this.key = key;
        this.env = env;
    }

    public Environment environment() {
        return env;
    }

    /**
     * Pemeriksaan ringan untuk badge: versi rilis terbaru (tanda tangan sudah diverifikasi) kalau lebih baru dari yang
     * berjalan. Tidak menghitung hash jar lokal. Build dev selalu kosong.
     */
    public Optional<ReleaseVersion> newerVersion() throws UpdateException, InterruptedException {
        if (env.running() == null) {
            return Optional.empty();
        }
        ReleaseVersion latest = feed.fetchLatest(key).version();
        return latest.isNewerThan(env.running()) ? Optional.of(latest) : Optional.empty();
    }

    public Check check() throws UpdateException, InterruptedException {
        SignedRelease latest = feed.fetchLatest(key);
        UpdateManifest m = latest.manifest();
        if (env.running() != null && !m.version().isNewerThan(env.running())) {
            return new UpToDate(latest);
        }
        if (env.running() == null) {
            return new ManualOnly(latest, Reason.DEV_BUILD);
        }
        if (env.generation() <= 0) {
            return new ManualOnly(latest, Reason.NO_BOOTSTRAP);
        }
        if (m.generation() > env.generation()) {
            return new ManualOnly(latest, Reason.NEEDS_INSTALLER);
        }
        var extraDirs = new ArrayList<Path>();
        if (env.runningUpdate() != null) {
            extraDirs.add(store.versionDir(env.runningUpdate()));
        }
        return new Available(latest, InstallPlan.of(latest, LocalJars.fromClassPath(env.classPath(), extraDirs)));
    }

    /**
     * Pasang rilis lalu bersihkan versi lama (kecuali yang baru dan yang sedang berjalan). Aktif saat start berikutnya.
     *
     * @param progress byte unduhan yang baru diterima; total = {@link InstallPlan#downloadBytes()}
     */
    public UpdateStore.Installed install(InstallPlan plan, LongConsumer progress)
            throws UpdateException, IOException, InterruptedException {
        ReleaseVersion version = plan.release().version();
        if (env.running() == null || !version.isNewerThan(env.running())) {
            throw new UpdateException("Versi " + version + " tidak lebih baru dari versi yang berjalan; tidak dipasang.");
        }
        var installed = store.install(plan, feed, key, progress);
        var keep = new HashSet<ReleaseVersion>(List.of(version));
        if (env.runningUpdate() != null) {
            keep.add(env.runningUpdate());
        }
        store.cleanup(keep);
        return installed;
    }
}

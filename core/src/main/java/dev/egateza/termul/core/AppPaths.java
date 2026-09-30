package dev.egateza.termul.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lokasi data aplikasi.
 *
 * <ul>
 *   <li>{@code configDir} = {@code %APPDATA%\TermUL}: profil, known_hosts, vault, log</li>
 *   <li>{@code cacheDir} = {@code %LOCALAPPDATA%\TermUL}: cache file remote edit</li>
 * </ul>
 * System property {@code termul.home} meng-override keduanya (dev/test), supaya data asli tidak tersentuh.
 * Folder dari nama lama aplikasi ({@value #LEGACY_APP_DIR}) dipindah otomatis sekali ke nama baru.
 */
public record AppPaths(Path configDir, Path cacheDir) {

    public static final String APP_DIR = "TermUL";
    public static final String HOME_OVERRIDE_PROPERTY = "termul.home";
    /** Nama folder sebelum aplikasi di-rename menjadi TermUL. */
    public static final String LEGACY_APP_DIR = "MyTerm";

    /**
     * Hasil {@link #detect()}.
     *
     * @param notes catatan migrasi folder lama, untuk di-log setelah logger siap (logger butuh {@code logDir})
     */
    public record Detected(AppPaths paths, List<String> notes) {
        public Detected {
            notes = List.copyOf(notes);
        }
    }

    public AppPaths {
        Objects.requireNonNull(configDir, "configDir");
        Objects.requireNonNull(cacheDir, "cacheDir");
    }

    /** Resolusi default berdasarkan environment OS, termasuk migrasi folder lama. */
    public static Detected detect() {
        String override = System.getProperty(HOME_OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return new Detected(underRoot(Path.of(override)), List.of());
        }
        Path userHome = Path.of(System.getProperty("user.home"));
        Path appData = envPath("APPDATA", userHome.resolve(".config"));
        Path localAppData = envPath("LOCALAPPDATA", userHome.resolve(".cache"));
        var notes = new ArrayList<String>();
        var paths = new AppPaths(appDir(appData, notes), appDir(localAppData, notes));
        return new Detected(paths, notes);
    }

    /**
     * Folder aplikasi di bawah {@code base}. Kalau folder baru belum ada tapi folder lama ada, folder lama di-rename
     * (profil, vault, known_hosts ikut). Kalau rename gagal (mis. dikunci proses lain), folder lama tetap dipakai
     * supaya data tidak terlihat hilang.
     */
    static Path appDir(Path base, List<String> notes) {
        Path target = base.resolve(APP_DIR);
        Path legacy = base.resolve(LEGACY_APP_DIR);
        if (Files.exists(target) || !Files.isDirectory(legacy)) {
            return target;
        }
        try {
            Files.move(legacy, target);
            notes.add("Folder data lama dipindah: " + legacy + " -> " + target);
            return target;
        } catch (IOException e) {
            notes.add("Folder data lama " + legacy + " tidak bisa dipindah (" + e + "), tetap memakai folder lama");
            return legacy;
        }
    }

    /** Semua data di bawah satu root (dipakai test dan mode dev). */
    public static AppPaths underRoot(Path root) {
        return new AppPaths(root.resolve("config"), root.resolve("cache"));
    }

    public Path profilesFile() {
        return configDir.resolve("profiles.json");
    }

    public Path configFile() {
        return configDir.resolve("config.json");
    }

    public Path knownHostsFile() {
        return configDir.resolve("known_hosts");
    }

    public Path vaultFile() {
        return configDir.resolve("vault.bin");
    }

    public Path logDir() {
        return configDir.resolve("logs");
    }

    public Path editCacheDir() {
        return cacheDir.resolve("edit");
    }

    private static Path envPath(String name, Path fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Path.of(value);
    }
}

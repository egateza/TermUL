package dev.egateza.termul.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Lokasi data aplikasi.
 *
 * <ul>
 *   <li>{@code configDir}: profil, known_hosts, vault, log. Windows {@code %APPDATA%\TermUL}, macOS
 *       {@code ~/Library/Application Support/TermUL}, lainnya {@code $XDG_CONFIG_HOME/TermUL} ({@code ~/.config})</li>
 *   <li>{@code cacheDir}: cache file remote edit. Windows {@code %LOCALAPPDATA%\TermUL}, macOS
 *       {@code ~/Library/Caches/TermUL}, lainnya {@code $XDG_CACHE_HOME/TermUL} ({@code ~/.cache})</li>
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
        var bases = bases(Os.current(), System::getenv, Path.of(System.getProperty("user.home")));
        var notes = new ArrayList<String>();
        var paths = new AppPaths(appDir(bases.configDir(), notes), appDir(bases.cacheDir(), notes));
        return new Detected(paths, notes);
    }

    /**
     * Folder induk (sebelum {@value #APP_DIR}) untuk config dan cache sesuai konvensi OS.
     *
     * @param env pembaca environment variable (null kalau tidak ada)
     */
    static AppPaths bases(Os os, Function<String, String> env, Path userHome) {
        return switch (os) {
            case WINDOWS -> new AppPaths(envPath(env, "APPDATA", userHome.resolve(".config")),
                    envPath(env, "LOCALAPPDATA", userHome.resolve(".cache")));
            case MAC -> new AppPaths(userHome.resolve("Library").resolve("Application Support"),
                    userHome.resolve("Library").resolve("Caches"));
            case OTHER -> new AppPaths(envPath(env, "XDG_CONFIG_HOME", userHome.resolve(".config")),
                    envPath(env, "XDG_CACHE_HOME", userHome.resolve(".cache")));
        };
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

    public Path themesDir() {
        return configDir.resolve("themes");
    }

    public Path logDir() {
        return configDir.resolve("logs");
    }

    public Path editCacheDir() {
        return cacheDir.resolve("edit");
    }

    /** Update aplikasi yang diunduh lewat menu (lihat {@code docs/adr/0003-self-update.md}); tidak ikut roaming. */
    public Path updatesDir() {
        return cacheDir.resolve("updates");
    }

    private static Path envPath(Function<String, String> env, String name, Path fallback) {
        String value = env.apply(name);
        return value == null || value.isBlank() ? fallback : Path.of(value);
    }
}

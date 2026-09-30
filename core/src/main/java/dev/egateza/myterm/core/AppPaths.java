package dev.egateza.myterm.core;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Lokasi data aplikasi.
 *
 * <ul>
 *   <li>{@code configDir} = {@code %APPDATA%\MyTerm}: profil, known_hosts, vault, log</li>
 *   <li>{@code cacheDir} = {@code %LOCALAPPDATA%\MyTerm}: cache file remote edit</li>
 * </ul>
 * System property {@code myterm.home} meng-override keduanya (dev/test), supaya data asli tidak tersentuh.
 */
public record AppPaths(Path configDir, Path cacheDir) {

    public static final String APP_DIR = "MyTerm";
    public static final String HOME_OVERRIDE_PROPERTY = "myterm.home";

    public AppPaths {
        Objects.requireNonNull(configDir, "configDir");
        Objects.requireNonNull(cacheDir, "cacheDir");
    }

    /** Resolusi default berdasarkan environment OS. */
    public static AppPaths detect() {
        String override = System.getProperty(HOME_OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return underRoot(Path.of(override));
        }
        Path userHome = Path.of(System.getProperty("user.home"));
        Path appData = envPath("APPDATA", userHome.resolve(".config"));
        Path localAppData = envPath("LOCALAPPDATA", userHome.resolve(".cache"));
        return new AppPaths(appData.resolve(APP_DIR), localAppData.resolve(APP_DIR));
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

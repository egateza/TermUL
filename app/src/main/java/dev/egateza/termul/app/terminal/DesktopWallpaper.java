package dev.egateza.termul.app.terminal;

import com.sun.jna.Library;
import com.sun.jna.Native;
import dev.egateza.termul.core.Os;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Wallpaper desktop saat ini. Hanya Windows yang didukung (lewat {@code SystemParametersInfo}); di OS lain atau
 * tanpa GUI tidak tersedia, dan opsinya tidak ditampilkan.
 */
public final class DesktopWallpaper {

    private static final int SPI_GETDESKWALLPAPER = 0x0073;
    private static final int MAX_PATH_CHARS = 520;

    /** Fungsi Win32 yang dipakai; dimuat malas supaya OS lain tidak pernah menyentuh user32. */
    interface User32 extends Library {
        boolean SystemParametersInfoW(int action, int param, char[] buffer, int winIni);
    }

    private DesktopWallpaper() {
    }

    /** @return true kalau OS-nya Windows dan ada GUI */
    public static boolean supported() {
        return Os.current().isWindows() && !GraphicsEnvironment.isHeadless();
    }

    /**
     * Path file wallpaper desktop saat ini (blocking ringan: jangan panggil di EDT).
     *
     * @return kosong kalau tidak didukung atau wallpaper tidak bisa ditemukan (mis. warna polos)
     */
    public static Optional<String> currentPath() {
        if (!supported()) {
            return Optional.empty();
        }
        String appData = System.getenv("APPDATA");
        Path transcoded = appData == null ? null
                : Path.of(appData, "Microsoft", "Windows", "Themes", "TranscodedWallpaper");
        return resolve(DesktopWallpaper::windowsApiPath, transcoded);
    }

    /**
     * Pilih file wallpaper yang benar-benar ada. {@code apiPath} dicoba dulu; kalau kosong atau filenya hilang
     * (mis. wallpaper slideshow), dipakai salinan wallpaper aktif yang disimpan Windows di {@code transcoded}.
     */
    static Optional<String> resolve(Supplier<String> apiPath, Path transcoded) {
        String fromApi = apiPath.get();
        if (fromApi != null && !fromApi.isBlank() && isFile(fromApi)) {
            return Optional.of(fromApi);
        }
        if (transcoded != null && Files.isRegularFile(transcoded)) {
            return Optional.of(transcoded.toString());
        }
        return Optional.empty();
    }

    private static boolean isFile(String path) {
        try {
            return Files.isRegularFile(Path.of(path));
        } catch (RuntimeException e) { // path tidak sah
            return false;
        }
    }

    private static String windowsApiPath() {
        try {
            var buffer = new char[MAX_PATH_CHARS];
            var user32 = Native.load("user32", User32.class);
            if (!user32.SystemParametersInfoW(SPI_GETDESKWALLPAPER, buffer.length, buffer, 0)) {
                return null;
            }
            int end = 0;
            while (end < buffer.length && buffer[end] != 0) {
                end++;
            }
            return new String(buffer, 0, end);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            return null; // native access ditolak atau user32 tidak ada: jatuh ke wallpaper hasil transcode
        }
    }
}

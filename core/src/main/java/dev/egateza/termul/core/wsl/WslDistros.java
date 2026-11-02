package dev.egateza.termul.core.wsl;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Distro WSL yang terpasang, sebagai pilihan terminal lokal ({@code wsl.exe -d <distro>}, ADR 0005). Hanya membaca
 * daftar: menjalankan dan menghentikan distro diurus WSL sendiri (distro hidup selama tab terbuka, mati saat idle).
 * Hanya bermakna di Windows. Blocking: jangan panggil di EDT.
 */
public final class WslDistros {

    private static final Logger log = LoggerFactory.getLogger(WslDistros.class);

    private static final Duration LIST_TIMEOUT = Duration.ofSeconds(15);

    /** Nama distro yang aman diteruskan sebagai argumen {@code wsl.exe} (nama dari WSL sendiri selalu cocok). */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private final WslRunner runner;

    public WslDistros(WslRunner runner) {
        this.runner = runner;
    }

    /**
     * Nama distro terpasang, urut seperti {@code wsl --list}. Distro internal Docker Desktop dilewati. Kosong kalau
     * WSL tidak ada, belum ada distro (keduanya membuat {@code wsl --list} keluar dengan kode bukan 0), atau gagal.
     */
    public List<String> installed() {
        if (!runner.available()) {
            return List.of();
        }
        try {
            var r = runner.run(List.of("--list", "--quiet"), LIST_TIMEOUT);
            if (!r.ok()) {
                log.debug("wsl --list keluar dengan kode {}: {}", r.exitCode(), r.output().strip());
                return List.of();
            }
            return names(r.output()).stream().filter(n -> !isInternal(n)).toList();
        } catch (IOException e) {
            log.debug("Daftar distro WSL tidak terbaca: {}", e.getMessage());
            return List.of();
        }
    }

    /** Nama distro dari output {@code wsl --list --quiet}; baris yang bukan nama distro valid dilewati. */
    static List<String> names(String output) {
        return WslOutput.lines(output).stream().filter(WslDistros::isValidName).toList();
    }

    /** Distro milik Docker Desktop ({@code docker-desktop}, {@code docker-desktop-data}): dikelola Docker sendiri. */
    static boolean isInternal(String name) {
        return name.toLowerCase(Locale.ROOT).startsWith("docker-desktop");
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }
}

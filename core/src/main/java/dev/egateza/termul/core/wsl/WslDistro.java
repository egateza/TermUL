package dev.egateza.termul.core.wsl;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Satu distro WSL yang terpasang di Windows.
 *
 * @param name    nama distro persis seperti di {@code wsl --list}, mis. {@code Ubuntu-24.04}
 * @param running true kalau distro sedang berjalan
 */
public record WslDistro(String name, boolean running) {

    /** Nama distro yang aman diteruskan sebagai argumen {@code wsl.exe} (nama dari WSL sendiri selalu cocok). */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public WslDistro {
        Objects.requireNonNull(name, "name");
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Nama distro WSL tidak valid: " + name);
        }
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }
}

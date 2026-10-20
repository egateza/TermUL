package dev.egateza.termul.localterm;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shell lokal yang akan dijalankan di PTY: perintah + argumen, direktori awal, dan environment lengkap. Tidak pernah
 * berisi secret (ADR 0005).
 *
 * @param command     executable diikuti argumennya, mis. {@code [/bin/zsh, -l]}
 * @param directory   direktori awal
 * @param environment environment lengkap untuk proses (bukan tambahan)
 */
public record LocalShell(List<String> command, Path directory, Map<String, String> environment) {

    public LocalShell {
        command = List.copyOf(command);
        if (command.isEmpty() || command.getFirst().isBlank()) {
            throw new IllegalArgumentException("Perintah shell kosong");
        }
        Objects.requireNonNull(directory, "directory");
        environment = Map.copyOf(environment);
    }

    /** Nama pendek untuk judul tab: nama file executable tanpa folder dan tanpa {@code .exe}, mis. "zsh". */
    public String label() {
        String exe = command.getFirst();
        int slash = Math.max(exe.lastIndexOf('/'), exe.lastIndexOf('\\'));
        String name = exe.substring(slash + 1);
        return name.regionMatches(true, Math.max(0, name.length() - 4), ".exe", 0, 4) && name.length() > 4
                ? name.substring(0, name.length() - 4) : name;
    }
}

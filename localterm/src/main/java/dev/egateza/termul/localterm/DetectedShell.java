package dev.egateza.termul.localterm;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Shell yang terdeteksi otomatis di mesin ini (tidak disimpan; dideteksi ulang setiap start).
 *
 * @param key     identitas stabil, mis. {@code cmd}, {@code git-bash}, {@code wsl:Ubuntu}, {@code /bin/zsh}
 * @param name    nama tampilan, mis. "PowerShell 7"
 * @param command executable + argumen
 */
public record DetectedShell(String key, String name, List<String> command) {

    public DetectedShell {
        command = List.copyOf(command);
    }

    /** Id stabil (sama di setiap start) supaya bisa dipilih sebagai default dan dibuka ulang dari "Duplikat tab". */
    public UUID id() {
        return UUID.nameUUIDFromBytes(("detected:" + key).getBytes(StandardCharsets.UTF_8));
    }
}

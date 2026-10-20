package dev.egateza.termul.core.localshell;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Profil terminal lokal buatan user (ADR 0005 tahap 2): perintah + argumen, direktori awal, environment tambahan, dan
 * warna terminal. <b>Bukan tempat secret</b>: tersimpan sebagai teks biasa di {@code local-profiles.json}.
 *
 * @param command       executable diikuti argumennya, mis. {@code [C:\Program Files\Git\bin\bash.exe, --login, -i]}
 * @param directory     direktori awal; kosong = home user; {@code ~} di awal = home
 * @param environment   variabel tambahan/pengganti; nilai kosong = variabel itu dihapus dari environment shell
 * @param terminalTheme id warna terminal ({@code HostTerminalColors}); null = ikut pengaturan
 */
public record LocalProfile(UUID id, String name, List<String> command, String directory,
                           Map<String, String> environment, String terminalTheme) {

    public static final int MAX_NAME = 80;
    public static final int MAX_ARGS = 64;
    public static final int MAX_VALUE = 4096;
    public static final int MAX_ENV = 100;
    /** Nama variabel: huruf/angka/underscore, plus kurung untuk nama Windows seperti {@code ProgramFiles(x86)}. */
    private static final Pattern ENV_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_()]*");

    public LocalProfile {
        Objects.requireNonNull(id, "id");
        name = name == null ? "" : name.strip();
        command = command == null ? List.of() : List.copyOf(command);
        directory = directory == null ? "" : directory.strip();
        environment = environment == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(environment));
        terminalTheme = terminalTheme == null || terminalTheme.isBlank() ? null : terminalTheme;
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Nama profil wajib diisi.");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Nama profil maksimal " + MAX_NAME + " karakter.");
        }
        if (command.isEmpty() || command.getFirst().isBlank()) {
            throw new IllegalArgumentException("Perintah (executable) wajib diisi.");
        }
        if (command.size() > MAX_ARGS) {
            throw new IllegalArgumentException("Argumen maksimal " + MAX_ARGS + ".");
        }
        for (String arg : command) {
            requireText(arg, "Argumen");
        }
        requireText(directory, "Direktori awal");
        if (environment.size() > MAX_ENV) {
            throw new IllegalArgumentException("Environment maksimal " + MAX_ENV + " variabel.");
        }
        for (var e : environment.entrySet()) {
            if (e.getKey() == null || !ENV_NAME.matcher(e.getKey()).matches()) {
                throw new IllegalArgumentException("Nama variabel environment tidak valid: " + e.getKey());
            }
            requireText(e.getValue() == null ? "" : e.getValue(), "Nilai " + e.getKey());
        }
    }

    public static LocalProfile create(String name, List<String> command) {
        return new LocalProfile(UUID.randomUUID(), name, command, "", Map.of(), null);
    }

    /** Satu baris tanpa karakter kontrol (baris baru/NUL bisa memecah perintah atau environment). */
    private static void requireText(String value, String what) {
        if (value.length() > MAX_VALUE) {
            throw new IllegalArgumentException(what + " maksimal " + MAX_VALUE + " karakter.");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 && c != '\t' || c == 0x7F) {
                throw new IllegalArgumentException(what + " harus satu baris tanpa karakter kontrol.");
            }
        }
    }
}

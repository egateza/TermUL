package dev.egateza.termul.core.snippet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Perintah tersimpan yang bisa ditempel ke terminal. Perintah boleh beberapa baris (dipisah {@code \n}), tapi tanpa
 * karakter kontrol lain (ESC, Tab, CR, ...), supaya snippet tidak bisa mengirim urutan escape atau tombol tersembunyi.
 * Enter di akhir hanya dikirim kalau {@link #enter()} (di file: perintah diakhiri baris baru). Snippet bukan tempat
 * secret (tersimpan sebagai teks biasa).
 *
 * @param category  id {@link SnippetCategory}; {@code ""} = tanpa kategori
 * @param dangerous ditandai berbahaya oleh user; lihat juga {@link #isDangerous()}
 * @param variables nilai default variabel {@code ${nama}} (boleh tidak lengkap)
 */
public record Snippet(UUID id, String title, String command, boolean enter, String category, boolean favorite,
                      boolean dangerous, SnippetScope scope, Map<String, String> variables) {

    public static final int MAX_TITLE = 80;
    public static final int MAX_COMMAND = 8000;

    public Snippet {
        Objects.requireNonNull(id, "id");
        title = title == null ? "" : title.strip();
        command = command == null ? "" : command.replace("\r\n", "\n").replace('\r', '\n');
        if (!command.isBlank() && command.substring(command.stripTrailing().length()).contains("\n")) {
            enter = true; // format file: perintah diakhiri baris baru = langsung dijalankan
        }
        command = command.strip();
        category = category == null ? "" : category.strip();
        scope = scope == null ? SnippetScope.ALL : scope;
        if (title.isEmpty()) {
            throw new IllegalArgumentException("Judul snippet wajib diisi.");
        }
        if (title.length() > MAX_TITLE) {
            throw new IllegalArgumentException("Judul snippet maksimal " + MAX_TITLE + " karakter.");
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Perintah snippet wajib diisi.");
        }
        if (command.length() > MAX_COMMAND) {
            throw new IllegalArgumentException("Perintah snippet maksimal " + MAX_COMMAND + " karakter.");
        }
        if (hasControlCharacter(command.replace("\n", "")) || hasControlCharacter(title)
                || hasControlCharacter(category)) {
            throw new IllegalArgumentException(
                    "Snippet tidak boleh berisi karakter kontrol (Tab, ESC, dll.); hanya baris baru yang diizinkan.");
        }
        var vars = new LinkedHashMap<String, String>();
        if (variables != null) {
            variables.forEach((name, value) -> {
                if (SnippetVariables.isValidName(name) && value != null && !hasControlCharacter(value)
                        && value.length() <= SnippetVariables.MAX_VALUE) {
                    vars.put(name, value);
                }
            });
        }
        variables = java.util.Collections.unmodifiableMap(vars);
    }

    /** Snippet baru tanpa kategori, untuk semua host. */
    public static Snippet create(String title, String command) {
        return new Snippet(UUID.randomUUID(), title, command, false, "", false, false, SnippetScope.ALL, Map.of());
    }

    public Snippet withFavorite(boolean on) {
        return new Snippet(id, title, command, enter, category, on, dangerous, scope, variables);
    }

    public Snippet withCategory(String categoryId) {
        return new Snippet(id, title, command, enter, categoryId, favorite, dangerous, scope, variables);
    }

    /** Ditandai user, atau perintahnya cocok dengan pola berbahaya ({@link DangerousCommands}). */
    public boolean isDangerous() {
        return dangerous || DangerousCommands.detect(command).isPresent();
    }

    public boolean appliesTo(String hostName, String hostGroup) {
        return scope.appliesTo(hostName, hostGroup);
    }

    public List<String> variableNames() {
        return SnippetVariables.names(command);
    }

    public List<String> lines() {
        return List.of(command.split("\n", -1));
    }

    /** Teks perintah seperti di file: diakhiri baris baru kalau {@link #enter()}. */
    public String commandWithEnter() {
        return enter ? command + "\n" : command;
    }

    static boolean hasControlCharacter(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c == 0x7F || (c >= 0x80 && c < 0xA0)) {
                return true;
            }
        }
        return false;
    }
}

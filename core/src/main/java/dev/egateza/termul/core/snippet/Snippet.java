package dev.egateza.termul.core.snippet;

import dev.egateza.termul.core.profile.HostProfile;
import java.util.Objects;
import java.util.UUID;

/**
 * Perintah tersimpan yang bisa diketik ke terminal dengan sekali klik. Snippet selalu <b>satu baris tanpa karakter
 * kontrol</b> (termasuk Enter/baris baru dan ESC), jadi mengetik snippet tidak pernah menjalankan apa pun sendiri:
 * user yang menekan Enter setelah memeriksanya. Snippet bukan tempat secret (tersimpan sebagai teks biasa).
 *
 * @param group grup host tempat snippet berlaku (mis. {@code Prod/DB}, juga untuk subgrupnya); kosong = semua host
 */
public record Snippet(UUID id, String name, String command, String group) {

    public static final int MAX_NAME = 80;
    public static final int MAX_COMMAND = 2000;

    public Snippet {
        Objects.requireNonNull(id, "id");
        name = name == null ? "" : name.strip();
        command = command == null ? "" : command.strip();
        group = HostProfile.normalizeGroup(group == null ? "" : group);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Nama snippet wajib diisi.");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Nama snippet maksimal " + MAX_NAME + " karakter.");
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Perintah snippet wajib diisi.");
        }
        if (command.length() > MAX_COMMAND) {
            throw new IllegalArgumentException("Perintah snippet maksimal " + MAX_COMMAND + " karakter.");
        }
        if (hasControlCharacter(command) || hasControlCharacter(name)) {
            throw new IllegalArgumentException(
                    "Snippet harus satu baris tanpa karakter kontrol (Enter, Tab, ESC), supaya tidak pernah berjalan sendiri.");
        }
    }

    public static Snippet create(String name, String command, String group) {
        return new Snippet(UUID.randomUUID(), name, command, group);
    }

    public boolean isGlobal() {
        return group.isEmpty();
    }

    /** true kalau snippet ini berlaku untuk host di {@code hostGroup} (global, grup yang sama, atau grup induknya). */
    public boolean appliesTo(String hostGroup) {
        if (isGlobal()) {
            return true;
        }
        String host = HostProfile.normalizeGroup(hostGroup == null ? "" : hostGroup);
        return host.equals(group) || host.startsWith(group + "/");
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

package dev.egateza.termul.app.ui;

import dev.egateza.termul.vault.CredentialVault;
import dev.egateza.termul.vault.SecretType;
import java.util.Map;
import java.util.UUID;

/** Perubahan secret dari dialog profil. Field yang tidak diubah tidak muncul di map. */
public sealed interface SecretChange {

    /** Secret baru; array di-zero oleh vault saat disimpan. */
    record Set(char[] value) implements SecretChange {
    }

    /** Hapus secret dari vault. */
    record Clear() implements SecretChange {
    }

    /** Menerapkan semua perubahan ke vault (harus unlocked). Panggil di luar EDT. */
    static void applyAll(CredentialVault vault, UUID profileId, Map<SecretType, SecretChange> changes) {
        for (var e : changes.entrySet()) {
            switch (e.getValue()) {
                case Set(char[] value) -> vault.put(profileId, e.getKey(), value);
                case Clear() -> vault.remove(profileId, e.getKey());
            }
        }
    }

    /** Zero semua secret yang belum dipakai (mis. user batal membuka vault). */
    static void discardAll(Map<SecretType, SecretChange> changes) {
        for (var c : changes.values()) {
            if (c instanceof Set(char[] value)) {
                java.util.Arrays.fill(value, '\0');
            }
        }
    }
}

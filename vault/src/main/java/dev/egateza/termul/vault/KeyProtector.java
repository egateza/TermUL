package dev.egateza.termul.vault;

/** Pembungkus key terikat OS (DPAPI di Windows, Keychain di macOS) untuk opsi "ingat di PC ini". */
public interface KeyProtector {

    boolean isAvailable();

    /** @return blob terproteksi; {@code data} tidak diubah */
    byte[] protect(byte[] data);

    /** @return data asli (pemanggil wajib zero); exception kalau blob tidak bisa dibuka */
    byte[] unprotect(byte[] blob);

    /** Akhiran file blob di samping {@code vault.bin}; beda per OS supaya blob OS lain tidak pernah dicoba dibuka. */
    default String blobSuffix() {
        return ".dpapi";
    }

    /** Hapus material key yang disimpan OS di luar file blob (mis. item Keychain). Dipanggil saat opsi dimatikan. */
    default void discard() {
    }

    /** Tidak tersedia (non-Windows / test). */
    KeyProtector UNAVAILABLE = new KeyProtector() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public byte[] protect(byte[] data) {
            throw new UnsupportedOperationException("Proteksi key OS tidak tersedia");
        }

        @Override
        public byte[] unprotect(byte[] blob) {
            throw new UnsupportedOperationException("Proteksi key OS tidak tersedia");
        }
    };
}

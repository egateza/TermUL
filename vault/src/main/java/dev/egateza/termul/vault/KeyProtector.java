package dev.egateza.termul.vault;

/** Pembungkus key terikat OS (DPAPI di Windows) untuk opsi "ingat di PC ini". */
public interface KeyProtector {

    boolean isAvailable();

    /** @return blob terproteksi; {@code data} tidak diubah */
    byte[] protect(byte[] data);

    /** @return data asli (pemanggil wajib zero); exception kalau blob tidak bisa dibuka */
    byte[] unprotect(byte[] blob);

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

package dev.egateza.termul.vault;

import java.util.UUID;

/**
 * Penyimpanan secret terenkripsi per profil.
 *
 * <p>Aturan: semua secret masuk/keluar sebagai {@code char[]}. Input di-zero oleh vault setelah
 * dipakai; output menjadi milik pemanggil dan wajib di-zero setelah dipakai. Method yang menulis
 * file melakukan disk I/O + kripto: jangan dipanggil di EDT.
 */
public interface CredentialVault {

    /** true kalau file vault sudah ada. */
    boolean exists();

    boolean isUnlocked();

    /** Membuat vault baru dan langsung unlock. {@code masterPassword} di-zero. */
    void create(char[] masterPassword);

    /** @throws VaultException.WrongPassword kalau password salah. {@code masterPassword} di-zero. */
    void unlock(char[] masterPassword);

    /** Unlock memakai key yang dibungkus OS (DPAPI). @return false kalau tidak tersedia/gagal. */
    boolean unlockWithOsKey();

    /** Menghapus key dari memory. */
    void lock();

    /** @return salinan secret (pemanggil wajib zero), atau null kalau tidak ada */
    char[] get(UUID profileId, SecretType type);

    boolean has(UUID profileId, SecretType type);

    /** Menyimpan/mengganti secret. {@code secret} di-zero. */
    void put(UUID profileId, SecretType type, char[] secret);

    void remove(UUID profileId, SecretType type);

    /** Menghapus semua secret milik profil (dipakai saat profil dihapus). */
    void removeProfile(UUID profileId);

    /** Kedua argumen di-zero. */
    void changeMasterPassword(char[] oldPassword, char[] newPassword);

    boolean isRememberedOnThisPc();

    /** Mengaktifkan/mematikan opsi "ingat di PC ini" (DPAPI). Vault harus unlocked untuk mengaktifkan. */
    void setRememberOnThisPc(boolean remember);
}

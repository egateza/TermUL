# ADR 0006: "Ingat di Mac ini" lewat macOS Keychain

- Status: Diterima
- Tanggal: 2026-10-20
- Terkait: ADR 0002 (desain vault), `docs/SECURITY.md` bagian Vault

## Konteks
Vault (ADR 0002) dibuka dengan master password. Di Windows ada opsi "Ingat di PC ini": DEK vault dibungkus DPAPI dan
disimpan di `vault.bin.dpapi`, sehingga vault terbuka otomatis dengan login Windows. Di macOS opsi ini belum ada
(`KeyProtector.UNAVAILABLE`), jadi user Mac selalu mengetik master password.

Opsi yang dipertimbangkan:

1. **Simpan DEK langsung di Keychain.** Sederhana, tapi status "diingat" tidak lagi ditandai file di samping vault, dan
   jalur kode berbeda dari DPAPI.
2. **Kunci pembungkus acak di Keychain, DEK terbungkus di file.** Pola sama dengan DPAPI: `FileCredentialVault` tetap
   memakai interface `KeyProtector`, format vault tidak berubah.
3. **CLI `security add-generic-password`.** Tanpa JNA, tapi secret lewat argumen proses (terlihat di `ps`).

## Keputusan
Opsi **2**.

1. `KeychainKeyProtector` (modul `vault`) memakai JNA ke Security.framework (`SecItemAdd`, `SecItemCopyMatching`,
   `SecItemDelete`), item *generic password* di login keychain. Layanan `TermUL`, account `vault-<16 hex SHA-256 path
   vault>` supaya dua folder data tidak saling menimpa.
2. Saat opsi dinyalakan, dibuat kunci acak 256-bit baru (item lama dihapus). DEK dibungkus AES-256-GCM (nonce acak,
   AAD `TermUL-vault-dek-keychain-v1`) dan disimpan di `vault.bin.keychain`. Vault hanya terbuka otomatis kalau file
   **dan** item Keychain ada dan cocok; kalau tidak, kembali ke master password.
3. Mematikan opsi atau "Lupa master password" menghapus file **dan** item Keychain (`KeyProtector.discard()`).
4. Item dibuat tanpa `kSecAttrSynchronizable`, jadi tidak ikut iCloud Keychain. Data protection keychain
   (`kSecUseDataProtectionKeychain`) tidak dipakai karena butuh entitlement dan aplikasi yang di-sign.
5. **Hanya untuk TermUL hasil instalasi (`.app`)**, dideteksi lewat `jpackage.app-path`. Dari JAR portable yang meminta
   akses Keychain adalah `java`, sehingga izin "Always Allow" berlaku untuk semua program Java milik user. Di JAR
   menu disembunyikan.
6. Akhiran file blob per protector (`.dpapi` / `.keychain`), jadi blob dari OS lain tidak pernah dicoba dibuka.

## Konsekuensi
- (+) User Mac tidak perlu mengetik master password selama login keychain terbuka.
- (+) ACL Keychain terikat ke aplikasi pembuat item: program lain memicu dialog izin macOS.
- (−) Launcher `.app` belum di-sign dengan Developer ID. Kalau binary launcher berubah (instal ulang dari DMG baru),
  macOS bisa menanyakan izin lagi ("TermUL ingin memakai Keychain"). Update delta yang hanya mengganti jar tidak
  mengubah launcher. Kalau user menolak, vault kembali meminta master password.
- (−) Sama seperti DPAPI: malware yang berjalan sebagai user dan diizinkan mengakses item bisa membuka vault. Opsi ini
  default mati dan diberi tooltip peringatan.
- Kunci manual (menu Vault → Kunci vault) tetap berlaku seperti DPAPI: key OS tidak dipakai sampai user membuka vault.

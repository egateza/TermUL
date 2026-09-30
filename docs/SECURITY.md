# Security

Aplikasi ini memegang akses SSH dan password sudo/root ke server produksi. Kalau aplikasi ini bocor, server ikut bocor. Semua keputusan desain mengikuti dokumen ini.

## Threat model (ringkas)

| Ancaman | Contoh | Mitigasi |
|---|---|---|
| Pencurian file vault/profil | Laptop hilang, malware membaca `%APPDATA%` | Vault terenkripsi (DPAPI terikat user Windows, atau master password + Argon2id). Profil tidak berisi secret. |
| MITM saat connect | DNS/ARP spoofing di jaringan kantor | Strict host key checking + TOFU dengan fingerprint. Host key berubah = koneksi ditolak. |
| **Prompt spoofing** | Remote mencetak `[sudo] password for ega:` (log di-`cat`, script jahat) sehingga auto-fill mengirim password ke proses lain | Guard auto-trigger (lihat bawah), dan hotkey sebagai default |
| Secret bocor via log/crash | Password masuk stack trace/log | Tidak ada `String` untuk secret. Logger tidak menerima objek secret. Review wajib. |
| Secret tertinggal di memory | Heap dump | `char[]`/`byte[]` di-zero setelah pakai. Tidak ada cache password di memory lebih dari yang diperlukan (atau cache dengan TTL, opsional). |
| Lockout akun | Password vault salah, lalu auto-retry memicu `pam_faillock` | One-shot: berhenti saat `Sorry, try again`. |
| Kerusakan config server | Upload terpotong / CRLF / konflik edit | Upload atomic, conflict check, line-ending guard, validation hook + backup. |
| File cache edit bocor | Config sensitif (mis. `.env`, `pg_hba.conf`) tertinggal di `%LOCALAPPDATA%` | Cache dihapus saat edit ditutup. Opsi purge saat aplikasi keluar. Folder cache dengan ACL user saja. |

## Vault

Dua opsi. **Keputusan terbuka** (catat di ADR saat dipilih):

**Opsi 1: Windows DPAPI (`CryptProtectData`, scope CurrentUser) via JNA**
- (+) Tanpa master password, terikat ke login Windows.
- (−) Proses apa pun yang berjalan sebagai user yang sama bisa mendekripsi. Tidak portable.

**Opsi 2: Master password → Argon2id → AES-256-GCM**
- Parameter Argon2id awal: m=64 MiB, t=3, p=1 (ukur supaya ±0.5 detik). Salt 16 byte acak disimpan di header vault.
- Satu entry per secret: nonce 12 byte acak, AAD = `profileId|secretType`, supaya ciphertext tidak bisa dipindah antar entry.
- Unlock sekali per sesi aplikasi, dengan auto-lock setelah idle (mis. 15 menit).
- Library: Bouncy Castle (Argon2), `javax.crypto` (AES-GCM).
- (+) Portable, dan malware sebagai user yang sama tetap butuh master password.
- (−) Harus mengetik master password.

Kombinasi yang disarankan: Opsi 2, dengan opsi menyimpan master key yang dibungkus DPAPI ("remember on this PC").

Tipe secret per profil: `LOGIN_PASSWORD`, `KEY_PASSPHRASE`, `SUDO_PASSWORD`, `ROOT_PASSWORD`.

## Aturan auto-sudo / inject password

**Default: hotkey** (`Ctrl+Shift+P`). Dengan hotkey, user yang memutuskan kapan password dikirim, sehingga tidak ada yang bisa di-spoof.

**Auto-trigger** (opsional per host, default OFF untuk tag `prod`) hanya boleh mengirim password kalau **semua** kondisi berikut terpenuhi:

1. **Armed**: user menekan Enter, dan baris perintah (diambil dari buffer layar pada baris kursor, setelah prompt shell) diawali `sudo ` atau berupa `su`/`su ...`.
2. **Dalam window**: ≤ 5 detik sejak armed.
3. **Pola cocok di akhir output** (prompt sedang menunggu input), setelah ANSI escape di-strip:
   - sudo: `\[sudo\] (password for|kata sandi untuk) <user>:\s*$`, dan `<user>` harus sama dengan username profil
   - su: `^(Password|Kata sandi):\s*$` pada baris terakhir
4. **One-shot**: maksimal satu kali kirim per perintah yang di-arm.
5. **Stop on failure**: kalau `Sorry, try again` / `Authentication failure` muncul, disarm dan tampilkan notifikasi. Jangan kirim ulang.
6. Secret yang dipakai: sudo → `SUDO_PASSWORD`, su → `ROOT_PASSWORD`. Kalau tidak ada, tidak mengirim apa pun.

Catatan keterbatasan: dari sisi client tidak bisa diketahui apakah remote dalam mode echo-off (status termios tidak dikirim lewat SSH). Karena itu guard di atas wajib, dan hotkey tetap menjadi cara yang paling aman.

Rekomendasi operasional (di luar aplikasi): untuk server prod, pertimbangkan akses berbasis key + sudo yang dibatasi per command, supaya ketergantungan pada password root berkurang.

## Host key

- `known_hosts` milik aplikasi (format OpenSSH), dengan opsi import dari `~/.ssh/known_hosts`.
- Host baru: dialog berisi fingerprint `SHA256:...` + algoritma. User harus konfirmasi.
- Key berubah: tolak, tampilkan fingerprint lama & baru. Penggantian hanya bisa lewat aksi eksplisit di pengaturan.

## Remote edit

- File cache dengan ACL hanya untuk user saat ini, dan dihapus setelah edit selesai & tersinkron.
- Upload atomic, conflict check berbasis `mtime + size` (opsional: hash via `sha256sum` exec).
- `sudo install` menjaga owner/mode. File temp di `/tmp` dibuat dengan mode 600 dan dihapus setelahnya.
- Jangan pernah menaruh password di argumen command (terlihat di `ps`). Password selalu dikirim via stdin (`sudo -S -p ''`).

## Keterbatasan yang diketahui

- **Password login & passphrase key di MINA berupa `String`.** API Apache MINA SSHD (`UserInteraction`, `FilePasswordProvider`) hanya menerima `String`. Konversi `char[] → String` dibatasi di satu tempat (`ssh/.../auth/AuthSetup.java`), dilakukan tepat saat MINA memintanya, dan `char[]` asal langsung di-zero. String itu tidak disimpan di field, tidak di-log, dan menjadi garbage setelah paket auth terkirim. Password sudo/su (inject ke terminal) tetap `char[]`/`byte[]` penuh.

## Logging

- Level default INFO. Konten terminal tidak di-log.
- Session log (fitur opsional) harus melakukan masking pada baris setelah prompt password.
- Checklist review: grep `password`, `secret`, `toString()` pada class yang memegang secret.

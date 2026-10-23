# Security

Aplikasi ini memegang akses SSH dan password sudo/root ke server produksi. Kalau aplikasi ini bocor, server ikut bocor. Semua keputusan desain mengikuti dokumen ini.

## Threat model (ringkas)

| Ancaman | Contoh | Mitigasi |
|---|---|---|
| Pencurian file vault/profil | Laptop hilang, malware membaca `%APPDATA%` | Vault terenkripsi (DPAPI terikat user Windows, atau master password + Argon2id). Profil tidak berisi secret. |
| MITM saat connect | DNS/ARP spoofing di jaringan kantor | Strict host key checking + TOFU dengan fingerprint. Host key berubah = koneksi ditolak. |
| **Prompt spoofing** | Remote mencetak `[sudo] password for user:` (log di-`cat`, script jahat) sehingga auto-fill mengirim password ke proses lain | Guard auto-trigger (lihat bawah), dan hotkey sebagai default |
| Secret bocor via log/crash | Password masuk stack trace/log | Tidak ada `String` untuk secret. Logger tidak menerima objek secret. Review wajib. |
| Secret tertinggal di memory | Heap dump | `char[]`/`byte[]` di-zero setelah pakai. Tidak ada cache password di memory lebih dari yang diperlukan (atau cache dengan TTL, opsional). |
| Lockout akun | Password vault salah, lalu auto-retry memicu `pam_faillock` | One-shot: berhenti saat `Sorry, try again`. |
| Kerusakan config server | Upload terpotong / CRLF / konflik edit | Upload atomic, conflict check, line-ending guard, validation hook + backup. |
| File cache edit bocor | Config sensitif (mis. `.env`, `pg_hba.conf`) tertinggal di `%LOCALAPPDATA%` | Cache dihapus saat edit ditutup. Opsi purge saat aplikasi keluar. Folder cache dengan ACL user saja. |

## Vault

Dua opsi di bawah. **Diputuskan: kombinasi**, lihat `docs/adr/0002-vault-design.md`.

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

Di macOS padanannya **"Ingat di Mac ini"** (ADR 0006): kunci pembungkus acak 256-bit disimpan di login keychain (item
generic password, layanan `TermUL`, tidak ikut iCloud), DEK terbungkus AES-GCM di `vault.bin.keychain`. Hanya tersedia
di `.app` hasil instalasi, tidak di JAR portable (izin Keychain untuk `java` berlaku ke semua program Java). Mematikan
opsi atau lupa master password menghapus file dan item Keychain. Akses lewat JNA `SecItem*`, bukan CLI `security`.

Kunci manual (menu **Vault → Kunci vault**) berlaku sampai user sendiri membuka vault lagi. Selama itu key DPAPI tidak
dipakai untuk unlock on-demand: inject dan connect meminta master password, dan auto-inject sudo/su dilewati dengan
pemberitahuan. Hanya **Vault → Buka vault** yang boleh memakai DPAPI lagi. Auto-lock idle tidak lengket: dengan
"remember on this PC" vault terbuka lagi lewat DPAPI saat dibutuhkan.

Tipe secret per profil: `LOGIN_PASSWORD`, `KEY_PASSPHRASE`, `SUDO_PASSWORD`, `ROOT_PASSWORD`.

## Aturan auto-sudo / inject password

**Default: hotkey** (`Ctrl+Shift+P`). Dengan hotkey, user yang memutuskan kapan password dikirim, sehingga tidak ada yang bisa di-spoof.
Hotkey inject bisa diganti (Pengaturan → Shortcut), tapi wajib memakai modifier utama (Ctrl / ⌘): kombinasi tanpa
modifier, dengan Alt/Option, atau hanya ⌃ di Mac ditolak, supaya password tidak terkirim karena tombol yang tidak sengaja
tertekan saat mengetik.

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
- Implementasi (`SudoWriter`): file baru ditulis ke `/tmp/termul-<acak>/` (direktori dibuat mode 700 **sebelum** ada isinya), lalu satu `sudo -S -k -p '' sh -c <script>` sebagai root: `readlink -f` target (symlink tetap symlink), backup `cp -p` ke `/var/backups/termul` (mode 700, 10 versi per file), `install -m/-o/-g` sesuai file lama ke temp di direktori yang sama, `mv -f` (atomic), hook validasi, dan rollback dari backup kalau validasi gagal. Semua dalam satu sudo sehingga rollback tetap jalan walau yang rusak `sudoers`.
- `-k` memaksa sudo selalu membaca password dari stdin (tidak memakai cache), supaya password tidak pernah sampai ke script. Tanpa password tersimpan dicoba `sudo -n` dulu (NOPASSWD), baru user diminta (tidak disimpan). Password salah tidak diulang.
- Hook validasi (`config.json` → `validationHooks`) adalah command milik user sendiri yang dijalankan sebagai root; hanya bisa diubah lewat config lokal.

## Profil sensitif

Mencegah salah eksekusi karena salah tab (mengira di server dev, ternyata di produksi). Flag `sensitive` per profil;
kosong = ikut environment (`PROD` = sensitif), jadi profil lama tetap terlindungi. Satu-satunya sumber keputusan adalah
`HostProfile.needsGuard()`; jangan cek `EnvironmentTag.isProduction()` langsung untuk guard.

- **Kunci saat kembali** (`SensitiveLock`): fokus pindah ke tab/panel/aplikasi lain lalu kembali setelah ≥ 1,5 detik →
  lapisan merah dengan nama host menutupi terminal dan semua input ditahan (termasuk paste dan Ctrl+C/Ctrl+D) sampai
  Enter. Klik di lapisan tidak membuka kunci, supaya klik untuk fokus tidak sekaligus melewati peringatan. Shortcut
  aplikasi dan Ctrl+Tab tetap jalan. Dialog/popup milik jendela utama (mis. konfirmasi paste) tidak dihitung pergi.
- Bingkai merah di keempat sisi terminal (selain titik warna environment di tab).
- Konfirmasi paste multi-baris, auto-sudo perlu konfirmasi saat diaktifkan, peringatan saat upload antar-server.

## Upload antar-server (SFTP ke SFTP)

- Isi file mengalir langsung dari stream SFTP sumber ke stream SFTP tujuan lewat TermUL: **tidak ada salinan di disk
  lokal**, dan server sumber tidak pernah diberi akses ke server tujuan (tanpa `scp` antar-server, tanpa agent forwarding).
- Tulis di tujuan memakai aturan upload biasa: file temp lalu rename, file milik user lain / tidak bisa ditulis ditolak,
  mode file lama dipertahankan (file baru memakai mode sumber; folder baru mendapat mode sumber setelah isinya disalin).
- Selalu ada konfirmasi yang menyebut folder dan host tujuan, dengan tombol default Batal; host sensitif diberi
  peringatan (lihat "Profil sensitif"). Di server yang sama, salin ke lokasinya sendiri atau folder ke dalam dirinya sendiri ditolak.

## FTP/FTPS (ADR 0007)

- Default protokol baru tetap SSH; FTPS dianjurkan untuk server tanpa SSH. **FTP biasa** mengirim username, password,
  dan isi file tanpa enkripsi: dialog profil menampilkan peringatan dan meminta konfirmasi saat profil FTP disimpan.
- Sertifikat FTPS: lolos validasi CA (truststore JDK) + hostname diterima. Kalau tidak (self-signed, hostname beda,
  kedaluwarsa), dialog menampilkan alasan + fingerprint `SHA256:` (base64 dan hex ala `openssl -fingerprint`) dan user
  harus konfirmasi (TOFU); hasilnya disimpan di `ftps_known_certs`. Fingerprint tersimpan yang berbeda = koneksi
  **ditolak** tanpa opsi "percaya saja"; penggantian hanya lewat klik kanan host > Lupakan sertifikat FTPS.
- Kanal data dan koneksi transfer wajib memakai sertifikat yang sama dengan koneksi kontrol (dicek per service).
- Password tidak pernah menjadi `String`: perintah `PASS` ditulis dari `char[]` langsung ke stream kontrol, tanpa
  listener perintah. Karena koneksi transfer dan reconnect setelah idle butuh login ulang, `FtpFileService` memegang
  salinan `char[]` selama tab terbuka dan meng-zero-nya saat ditutup. (Buffer internal `BufferedWriter`/encoder JDK bisa
  menyisakan salinan sampai tertimpa; sama seperti socket SSH, tidak bisa dikontrol dari aplikasi.)
- Tidak ada sudo/exec lewat FTP. Upload memakai file temp lalu rename, tetapi tanpa pemeriksaan owner/izin sebelum
  menulis (FTP tidak punya padanan `test -w`/uid), dan rename yang menimpa bisa jatuh ke hapus + rename (tidak atomic).

## Single instance & run at startup

- Hanya satu proses TermUL per folder data (`core.instance.SingleInstance`): `FileLock` di `instance.lock`, supaya vault, `known_hosts`, dan profil tidak ditulis dua proses sekaligus.
- Instance kedua meminta instance pertama menampilkan window lewat socket **loopback** (`127.0.0.1`, port acak). Port + token acak 32 byte ada di `instance.port` di folder data user (POSIX: mode 600; Windows: ACL profil). Permintaan tanpa token yang cocok (dibandingkan waktu konstan) diabaikan, request dibatasi 256 byte dan timeout 2 detik, dan satu-satunya perintah yang dikenal adalah "tampilkan window". Token dibuat ulang setiap start dan bukan credential user.
- Run at startup (`app.startup.AutoStart`) menulis entri milik user saja (Windows `HKCU\...\Run`, macOS `~/Library/LaunchAgents`), tanpa admin. Command-nya hanya path launcher + `--tray`: aplikasi start tersembunyi **tanpa** membuka vault, tanpa prompt master password, dan tanpa konek ke server.

## Update aplikasi

Detail di `docs/adr/0003-self-update.md`.

- Manifest rilis ditandatangani Ed25519. Public key ditanam di kode (`UpdateKeys`), private key hanya di mesin rilis
  (`~/.termul-release/update-signing.key`, di luar repo, cadangkan offline). `ReleaseTool` menolak menerbitkan rilis yang
  tanda tangannya tidak cocok dengan public key aplikasi, atau build dari working tree `-dirty`.
- Setiap jar diverifikasi ukuran + SHA-256 saat dipasang **dan setiap start** (oleh Bootstrap dari kode bawaan installer),
  jadi folder update yang diubah setelah terpasang tidak dijalankan.
- Tidak ada downgrade: menu hanya memasang versi lebih baru dari yang berjalan; Bootstrap hanya memakai update yang lebih
  baru dari versi bawaan. URL wajib HTTPS (redirect ke HTTP tidak diikuti), ukuran respons dan jar dibatasi, nama file di
  manifest tidak boleh berisi folder.
- Risiko sisa: malware dengan hak user yang sama bisa mengganggu folder update (terdeteksi, lalu fallback ke versi bawaan),
  sama seperti ia sudah bisa membuka DPAPI. Kebocoran private key = update palsu diterima semua instalasi: buat key baru,
  naikkan `UpdateProtocol.GENERATION`, dan distribusikan installer baru.

## Keterbatasan yang diketahui

- Auto-sudo meng-arm hanya kalau baris kursor dikenali sebagai prompt shell (heuristik `ExitGuard`); prompt dengan spasi (mis. `(venv) user@host:~$`) dan prompt `sudo-rs` (`[sudo: authenticate] Password:`, tanpa username) tidak dikenali, jadi auto-sudo tidak jalan (fail-safe). Hotkey tetap bisa dipakai.
- Selama validasi berjalan (antara `mv` dan rollback), file baru yang invalid sempat terpasang sebentar.
- ProxyJump memakai local forward `127.0.0.1:<port acak>` selama koneksi tujuan hidup. Proses lain di PC yang sama bisa memakai port itu untuk menjangkau port SSH host tujuan lewat jump host (tetap harus lolos auth SSH tujuan). Listener hanya di loopback dan ditutup bersama koneksi. Host key tujuan diverifikasi dengan alamat aslinya, bukan alamat tunnel.
- Paste multi-baris ke host sensitif selalu dikonfirmasi (preview + jumlah baris); host lain tidak.
- Kunci profil sensitif hanya melindungi dari salah tab/jendela, bukan dari perintah berbahaya yang diketik sengaja
  setelah kunci dibuka. Tidak ada parsing perintah (mis. `rm -rf`), jadi tidak ada jaminan di luar itu.
- FTP biasa (ADR 0007) tidak terenkripsi sama sekali; satu-satunya mitigasi adalah peringatan + konfirmasi di dialog profil.
- **Password login & passphrase key di MINA berupa `String`.** API Apache MINA SSHD (`UserInteraction`, `FilePasswordProvider`) hanya menerima `String`. Konversi `char[] → String` dibatasi di satu tempat (`ssh/.../auth/AuthSetup.java`), dilakukan tepat saat MINA memintanya, dan `char[]` asal langsung di-zero. String itu tidak disimpan di field, tidak di-log, dan menjadi garbage setelah paket auth terkirim. Password sudo/su (inject ke terminal) tetap `char[]`/`byte[]` penuh.

## Terminal lokal (ADR 0005)

- Shell lokal berjalan sebagai user yang sama dengan TermUL, tanpa elevasi. TermUL tidak menyuntikkan apa pun ke
  shell selain environment terminal (`TERM`, `COLORTERM`, `TERM_PROGRAM`, `LANG` kalau kosong).
- **Tidak ada secret ke proses lokal.** Tab lokal tidak punya inject password (menu, hotkey, tombol toolbar) maupun
  auto-sudo; `PromptResponder` tidak dipasang dan vault tidak pernah diakses dari tab lokal. Ini dijaga di tipe
  (`TermPane` sealed; inject hanya ada di `TerminalTab`) dan di `TerminalMenuState.LOCAL`.
- Profil terminal lokal (`local-profiles.json`) tersimpan sebagai **teks biasa**, termasuk environment-nya. Dialog
  memperingatkan agar tidak mengisi password/token; nilai divalidasi satu baris tanpa karakter kontrol. Profil tidak
  ikut ekspor profil (mobile).
- Dialog tutup/keluar hanya menampilkan **nama** proses yang masih berjalan, tanpa argumen (argumen bisa berisi
  secret, mis. `mysql -p...`).
- Library native pty4j diekstrak ke folder temp pengguna dan dimuat lewat JNA. Kalau `.app` nanti di-notarize,
  library itu harus dikirim sebagai file yang ditandatangani (lihat ADR 0005).

## Highlight teks

- `highlight.json` hanya berisi keyword dan warna (teks biasa, tanpa secret); ekspor JSON tidak membawa penugasan
  host/grup. Isi terminal tidak pernah ditulis ke log oleh highlight (error hanya dicatat sekali, tanpa isi baris).
- Regex dari user atau file import bisa sangat lambat (ReDoS). Pencocokan berjalan di thread terpisah, dengan batas
  waktu per baris dan panjang baris maksimum, jadi terminal dan UI tetap responsif. Keyword JavaScript dari Tabby tidak
  pernah dijalankan.

## WSL (opsional)

- Hanya aktif kalau dinyalakan di Pengaturan. Profil WSL adalah koneksi SSH biasa, jadi verifikasi host key (TOFU) dan
  vault tetap berlaku. Host key sshd distro ikut tercatat di `known_hosts` sebagai `[localhost]:<port>`.
- TermUL menjalankan `wsl.exe` dengan daftar argumen (tanpa shell Windows). Nama distro divalidasi
  (`[A-Za-z0-9][A-Za-z0-9._-]*`) sebelum dipakai sebagai argumen. Script untuk menjalankan sshd sebagai root berupa
  konstanta, tidak ada input user di dalamnya.
- TermUL tidak mengubah konfigurasi sshd. Panduan menyarankan port selain 22, dan `ListenAddress 127.0.0.1` untuk mode
  networking `mirrored`, karena di mode itu port distro bisa terjangkau dari LAN.

## Logging

- Level default INFO. Konten terminal tidak di-log.
- Session log (fitur opsional) harus melakukan masking pada baris setelah prompt password.
- Checklist review: grep `password`, `secret`, `toString()` pada class yang memegang secret.

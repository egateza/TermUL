# Plan & Roadmap

Dikerjakan di sela pekerjaan utama. Target ±1–2 jam/hari. Setiap fase menghasilkan sesuatu yang **langsung bisa dipakai sehari-hari**, supaya project ini sekaligus menjadi alat kerja.

Legenda: `[ ]` belum, `[~]` sedang, `[x]` selesai.

## Fase 0: Perencanaan (sekarang)
- [x] Konteks project (`CLAUDE.md`)
- [x] Requirements, arsitektur, security, plan, setup
- [x] Putuskan nama aplikasi & base package: `TermUL`, `dev.egateza.termul`
- [x] Siapkan VM/container uji `openssh-server` (lihat `SETUP.md`): container Docker (IT) + staging `server01`

## Fase 1: Skeleton & terminal SSH pertama (±2 minggu)
- [x] Maven multi-module (`core`, `vault`, `ssh`, `terminal`, `sftp`, `app`): parent POM dengan `dependencyManagement`/`pluginManagement`, Maven Wrapper, enforcer (Java 25, Maven ≥ 3.9)
- [x] Main window FlatLaf: split host tree | tab area
- [x] `HostProfile` record + `ProfileStore` (JSON, atomic write)
- [x] Dialog tambah/edit profil
- [x] `SessionManager` dengan MINA: connect, auth key/agent/password (prompt manual dulu)
- [x] `HostKeyVerifier`: known_hosts + dialog TOFU
- [x] `SshTtyConnector` + tab JediTerm, resize, dan close yang rapi
- [x] Keep-alive + deteksi putus + tombol reconnect

**Acceptance**: bisa connect ke 3 server berbeda di 3 tab, menjalankan `htop` dan `vim` dengan tampilan benar, dan resize window berfungsi. Menutup tab tidak meninggalkan thread atau koneksi yang bocor (dicek dengan jconsole/VisualVM).

## Fase 2: Vault & inject password (±1 minggu)
- [x] Putuskan desain vault (ADR 0002)
- [x] `CredentialVault` + implementasi + unit test (round-trip, tamper detection GCM)
- [x] Field secret di dialog profil (login, sudo, root)
- [x] Auth password dari vault
- [x] Hotkey `Ctrl+Shift+P` inject sudo password, `Ctrl+Shift+R` inject root password

**Acceptance**: `sudo su` dan `su -` bisa dijalankan tanpa mengetik password. Tidak ada secret di `profiles.json` maupun di log.

## Fase 3: Panel SFTP (±2 minggu)
- [x] `RemoteFileService` di atas `SftpClient` (memakai connection handle yang sama)
- [x] Panel: list dir, navigasi, sort, refresh, tampilan mode/owner/size/mtime
- [x] Upload/download + drag & drop dari Explorer + progress/cancel
- [x] Rename, mkdir, delete (konfirmasi), chmod

**Acceptance**: transfer file 500 MB tidak membuat UI freeze dan bisa di-cancel.

## Fase 4: Edit dengan editor lokal (±2 minggu)
- [x] Konfigurasi editor per ekstensi (default `code --wait`)
- [x] `RemoteEditSession`: download ke cache + baseline stat
- [x] WatchService + debounce + hash compare
- [x] Upload atomic + preserve mode
- [x] Conflict dialog (overwrite / diff / batal)
- [x] Line ending guard (LF/CRLF)
- [x] EditTracker panel (daftar file yang diedit + status)

**Acceptance**: edit `~/app/config.yml` di VS Code, lalu Ctrl+S, dan file langsung ter-update di server dengan permission yang sama. Kalau file diubah di server sebelum save, dialog konflik muncul.

## Fase 5: Edit file root & auto-trigger (±2 minggu)
- [x] `SudoWriter`: upload ke `/tmp`, lalu `sudo -S install` dengan owner/mode, lalu cleanup
- [x] Backup + validation hook per pola path (`/etc/nginx/**` menjalankan `nginx -t`) + rollback
- [x] `PromptResponder` auto-trigger dengan semua guard + unit test (termasuk test spoofing: prompt palsu tanpa armed tidak boleh memicu)
- [x] Toggle auto-trigger per host (default OFF untuk tag prod)

**Acceptance**: edit `/etc/nginx/sites-available/x` dari VS Code berhasil dengan owner `root:root` tetap. Config yang invalid di-rollback otomatis.

Catatan implementasi: satu `sudo sh -c` per upload (backup ke `/var/backups/termul`, `install` ke temp + `mv`, validasi, rollback) supaya rollback tetap jalan walau yang rusak `sudoers`. Hook validasi di **Pengaturan → Validasi file root (sudo)**. File di `/tmp` ditaruh di direktori privat mode 700.

## Fase 6: Polish & distribusi
- [x] Import `~/.ssh/config` (File → Impor dari ~/.ssh/config), jump host (ProxyJump, termasuk rantai; ProxyCommand tidak didukung)
- [x] Warna tab per environment (titik di judul tab + garis di atas terminal), konfirmasi paste multi-baris di prod (semua jalur paste lewat `TerminalCopyPasteHandler`)
- [x] Pengaturan font/tema (font + tema bawaan)
- [x] Tema custom (user mengatur palet sendiri):
  - [x] `CustomTheme` + `ThemeStore` di `core` (JSON per tema di `%APPDATA%\TermUL\themes\`, warna sebagai hex, id divalidasi)
  - [x] Terapkan palet UI (override FlatLaf di atas base terang/gelap) dan palet terminal (16 warna ANSI, background, foreground, selection; JediTerm tidak menyediakan warna cursor)
  - [x] Opsi "terapkan ke UI dan terminal" (default aktif); kalau dimatikan, tema terminal dipilih terpisah
  - [x] Dialog editor dengan preview terminal live, duplikat/hapus tema
  - [x] Impor/ekspor `.json`, peringatan kontras (< 4.5:1)
  - [x] Opsi wallpaper desktop sebagai gambar latar (Windows saja) dan pemilih gambar bawaan OS dengan thumbnail
  - [x] Gambar latar terminal (per tema, keterlihatan diatur) dan transparansi jendela (Pengaturan > Transparansi jendela; aktif dari 100% butuh restart karena window memakai title bar FlatLaf)
  - [x] (opsional) warna terminal per profil host (Edit host → Warna terminal: ikut pengaturan / latar merah produksi / tema custom; hanya tab host itu, tanpa gambar latar)
- [ ] Profile Maven `package-win`: `jlink` runtime yang di-trim + `jpackage` → installer `.msi`
- [ ] (backlog) port forwarding, snippet library, session log dengan masking, follow cwd (OSC 7)

## Keputusan terbuka
| # | Keputusan | Opsi | Status |
|---|---|---|---|
| D1 | Nama aplikasi & base package | `TermUL` / lainnya; `dev.<nama>.termul` | Diputuskan: `TermUL`, `dev.egateza.termul` |
| D2 | Desain vault | DPAPI / master password / kombinasi | Diputuskan: kombinasi (ADR 0002) |
| D3 | Format storage profil | JSON / SQLite | Rekomendasi JSON (mudah di-diff & backup) |
| D4 | Repo & lisensi | Private GitHub/GitLab | Terbuka |

## Strategi belajar
- Setiap fase adalah kesempatan belajar yang konkret: internal SSH protocol (channel, window, subsystem) di Fase 1 & 3, kriptografi terapan di Fase 2, concurrency + file system events di Fase 4.
- Pakai aplikasinya sendiri untuk pekerjaan harian sejak akhir Fase 1. Bug dan kebutuhan nyata akan menentukan prioritas berikutnya.

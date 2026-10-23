# Plan & Roadmap

Dikerjakan di sela pekerjaan utama. Target ±1–2 jam/hari. Setiap fase menghasilkan sesuatu yang **langsung bisa dipakai sehari-hari**, supaya project ini sekaligus menjadi alat kerja.

Legenda: `[ ]` belum, `[~]` sedang, `[x]` selesai.

## Fase 0: Perencanaan (sekarang)
- [x] Konteks project (`CLAUDE.md`)
- [x] Requirements, arsitektur, security, plan, setup
- [x] Putuskan nama aplikasi & base package: `TermUL`, `dev.egateza.termul`
- [x] Siapkan VM/container uji `openssh-server` (lihat `SETUP.md`): container Docker (IT) + server staging

## Fase 1: Skeleton & terminal SSH pertama (±2 minggu)
- [x] Maven multi-module (`core`, `vault`, `ssh`, `terminal`, `sftp`, `app`): parent POM dengan `dependencyManagement`/`pluginManagement`, Maven Wrapper, enforcer (Java 25, Maven ≥ 3.9)
- [x] Main window FlatLaf: split host tree | tab area
- [x] `HostProfile` record + `ProfileStore` (JSON, atomic write)
- [x] Dialog tambah/edit profil
- [x] `SessionManager` dengan MINA: connect, auth key/agent/password (prompt manual dulu)
- [x] `HostKeyVerifier`: known_hosts + dialog TOFU
- [x] `SshTtyConnector` + tab JediTerm, resize, dan close yang rapi
- [x] Keep-alive + deteksi putus + tombol reconnect

**Acceptance** ✔ (lulus uji manual user, 2026-10-01): bisa connect ke 3 server berbeda di 3 tab, menjalankan `htop` dan `vim` dengan tampilan benar, dan resize window berfungsi. Menutup tab tidak meninggalkan thread atau koneksi yang bocor (dicek dengan jconsole/VisualVM).

## Fase 2: Vault & inject password (±1 minggu)
- [x] Putuskan desain vault (ADR 0002)
- [x] `CredentialVault` + implementasi + unit test (round-trip, tamper detection GCM)
- [x] Field secret di dialog profil (login, sudo, root)
- [x] Auth password dari vault
- [x] Hotkey `Ctrl+Shift+P` inject sudo password, `Ctrl+Shift+R` inject root password

**Acceptance** ✔ (lulus uji manual user, 2026-10-01): `sudo su` dan `su -` bisa dijalankan tanpa mengetik password. Tidak ada secret di `profiles.json` maupun di log.

## Fase 3: Panel SFTP (±2 minggu)
- [x] `RemoteFileService` di atas `SftpClient` (memakai connection handle yang sama)
- [x] Panel: list dir, navigasi, sort, refresh, tampilan mode/owner/size/mtime
- [x] Upload/download + drag & drop dari Explorer + progress/cancel
- [x] Rename, mkdir, delete (konfirmasi), chmod

**Acceptance** ✔ (lulus uji manual user, 2026-10-01): transfer file 500 MB tidak membuat UI freeze dan bisa di-cancel.

## Fase 4: Edit dengan editor lokal (±2 minggu)
- [x] Konfigurasi editor per ekstensi (default `code --wait`)
- [x] `RemoteEditSession`: download ke cache + baseline stat
- [x] WatchService + debounce + hash compare
- [x] Upload atomic + preserve mode
- [x] Conflict dialog (overwrite / diff / batal)
- [x] Line ending guard (LF/CRLF)
- [x] EditTracker panel (daftar file yang diedit + status)

**Acceptance** ✔ (lulus uji manual user, 2026-10-01): edit `~/app/config.yml` di VS Code, lalu Ctrl+S, dan file langsung ter-update di server dengan permission yang sama. Kalau file diubah di server sebelum save, dialog konflik muncul.

## Fase 5: Edit file root & auto-trigger (±2 minggu)
- [x] `SudoWriter`: upload ke `/tmp`, lalu `sudo -S install` dengan owner/mode, lalu cleanup
- [x] Backup + validation hook per pola path (`/etc/nginx/**` menjalankan `nginx -t`) + rollback
- [x] `PromptResponder` auto-trigger dengan semua guard + unit test (termasuk test spoofing: prompt palsu tanpa armed tidak boleh memicu)
- [x] Toggle auto-trigger per host (default OFF untuk tag prod)

**Acceptance** ✔ (lulus uji manual user, 2026-10-01): edit `/etc/nginx/sites-available/x` dari VS Code berhasil dengan owner `root:root` tetap. Config yang invalid di-rollback otomatis.

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
  - [x] Gambar latar terminal (per tema, keterlihatan diatur) dan transparansi (Pengaturan > Transparansi jendela, ADR 0008): slider jendela (bagian selain terminal) dan latar terminal, langsung berlaku. Windows: bingkai sendiri yang berperilaku native (`WinCustomFrame`, Snap tetap jalan); macOS: aktif dari 100% butuh restart
  - [x] (opsional) warna terminal per profil host (Edit host → Warna terminal: ikut pengaturan / latar merah produksi / tema custom; hanya tab host itu, tanpa gambar latar)
- [x] Profile Maven `package-win`: `jlink` runtime yang di-trim + `jpackage` → app-image (default) atau installer `.msi` (`-Djpackage.type=msi`, butuh WiX; belum diuji karena WiX tidak terpasang)
- [x] WSL Manager opsional (ADR 0004): start/stop distro, grup WSL di panel host, profil SSH ke sshd distro (`localhost`)
- [x] Tray icon + run at startup + single instance (Pengaturan → Tray & startup)
- [ ] (backlog) port forwarding, session log dengan masking

## Fase 7: Terminal lokal (ADR 0005)
- [x] Tahap 0–1: tab terminal lokal dengan shell default (modul `localterm`, pty4j). Rincian dan progres di
  [`local-terminal-plan.md`](local-terminal-plan.md)
- [ ] Acceptance manual macOS + Windows (ConPTY)
- [x] Tahap 2: profil terminal lokal (deteksi PowerShell/cmd/Git Bash/WSL/`/etc/shells`, profil custom, default)
- [ ] Acceptance manual tahap 2 (Windows: PowerShell 7, cmd, Git Bash, WSL)

## Fase 8: Highlight teks
- [x] Profil keyword (teks/regex, warna Tabby 0–255/#RRGGBB/nama, gaya), penugasan host/grup/terminal lokal/global,
  import/export JSON Tabby, dialog Pengaturan → Highlight teks
- [x] Highlight di buffer JediTerm (`TextHighlighter`): baris soft-wrap digabung, karakter lebar, alternate screen opsional
- [ ] Acceptance manual (SSH + lokal, log besar `tail -f`, vim dengan/ tanpa "matikan di alternate screen")

## Fase 9: Ingat di Mac ini (Keychain, ADR 0006)
- [x] `KeychainKeyProtector` (JNA `SecItem*`), blob `vault.bin.keychain`, item dihapus saat opsi dimatikan / lupa password
- [x] Hanya di `.app` (bukan JAR portable); menu Vault → Ingat di Mac ini
- [ ] Acceptance manual: nyalakan opsi, restart app (vault terbuka tanpa password), instal ulang DMG (dialog izin
  Keychain), matikan opsi (item hilang di Keychain Access)

## Fase 10: Shortcut bisa diganti
- [x] Daftar aksi + default per OS, `shortcuts.json` (hanya override), dialog Pengaturan → Shortcut (rekam tombol,
  kosongkan, reset, reset semua, konfirmasi bentrok), langsung berlaku tanpa restart
- [ ] Acceptance manual: ganti shortcut saat fokus di terminal (SSH + lokal), zoom custom, ⌘1..9 custom, Windows
- [ ] Menyusul: copy/paste/find milik JediTerm, dua shortcut per aksi

## Fase 11: Upload antar-panel SFTP
- [x] Klik kanan → Upload ke ▸ (grup sendiri, lalu tab lain), drag & drop antar-panel, konfirmasi folder/host tujuan
  (+ peringatan PROD), stream langsung tanpa file lokal, folder rekursif, cegah salin ke dirinya sendiri
- [ ] Acceptance manual: dua server sungguhan, file besar (progress/batal), tujuan PROD, Windows

## Fase 12: Ikuti folder terminal & lepas tab (ide dari MobaXterm)
- [x] Ikuti folder terminal: tombol di toolbar panel SFTP (global, `sftpFollowTerminal`). Sumber folder: OSC 7 dari shell,
  atau probe `/proc` lewat exec setelah Enter (penanda `LC_TERMUL_TTY` per shell, `ShellCwd`); tidak mengetik ke shell
- [x] Lepas tab ke window baru (klik kanan tab / menu Terminal / seret tab menjauh dari baris tab), kembalikan ke window
  utama; shortcut, konfirmasi tutup/keluar, dan pengaturan tetap berlaku di window terpisah
- [x] Breadcrumb path di panel SFTP remote & lokal (`PathBar`): klik segmen = buka folder, klik area kosong / pensil =
  ketik path (Enter buka, Esc batal, gagal = field tetap terbuka bertanda error), segmen kiri dilipat ke "…" kalau
  sempit, klik kanan = salin/edit path, drop file ke segmen = upload/download ke folder itu
- [ ] Acceptance manual: follow di Ubuntu (`AcceptEnv LC_*` bawaan), server tanpa AcceptEnv (pesan), `sudo -i` (tetap di
  folder), vim (tidak probe); lepas/kembalikan tab grup split, Ctrl/⌘+W & inject sudo di window terpisah, keluar aplikasi
  dengan window terpisah aktif, Windows + macOS; breadcrumb: drive Windows/UNC, path panjang di dua panel, drop dari
  Explorer/Finder dan antar-panel ke segmen

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

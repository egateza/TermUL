# Plan Recap — catatan progres eksekusi

> File ini diperbarui **setiap kali sebuah task selesai** (bersamaan dengan commit-nya).
> Kalau sesi AI terhenti (limit habis, dll.), lanjutkan dari bagian **"Langkah berikutnya"**.
> Detail roadmap lengkap tetap di [docs/PLAN.md](docs/PLAN.md).

## Cara melanjutkan

1. `git log --oneline` — lihat commit terakhir (setiap task = 1 commit kecil, pesan Bahasa Indonesia).
2. Baca bagian **Langkah berikutnya** di bawah.
3. `mvnw.cmd verify -DskipITs` harus hijau sebelum mulai task baru.
4. Selesai satu task → centang di sini + di `docs/PLAN.md` → commit.

## Keputusan yang diambil saat eksekusi

| # | Keputusan | Pilihan | Catatan |
|---|---|---|---|
| D1 | Nama & base package | `TermUL` (Terminal Utility), groupId/package `dev.egateza.termul` | Di-rename dari `MyTerm` 2026-09-30 (permintaan user). Folder data `%APPDATA%\MyTerm` & `%LOCALAPPDATA%\MyTerm` dipindah otomatis saat start. Konstanta format vault (`MyTerm-vault-dek-v1`, `MYTERM-VAULT-OK`) sengaja tidak diubah |
| D3 | Format storage profil | JSON (Jackson) | Sesuai rekomendasi PLAN |
| D2 | Desain vault | Master password + Argon2id + AES-256-GCM, opsi DPAPI | `docs/adr/0002-vault-design.md` |

## Status per fase

Legenda: `[ ]` belum, `[~]` sedang, `[x]` selesai.

### Fase 0: Perencanaan
- [x] Dokumentasi awal
- [x] Keputusan D1 (nama & package)
- [x] Server uji `openssh-server` — container Docker untuk integration test + server **staging** `server01`
  (192.0.2.10, dikonfirmasi user 2026-09-30) untuk uji manual

### Fase 1: Skeleton & terminal SSH pertama
- [x] Maven multi-module + wrapper + enforcer
- [x] Main window FlatLaf (host tree | tab area)
- [x] `HostProfile` + `ProfileStore` (JSON, atomic write)
- [x] Dialog tambah/edit profil
- [x] `SessionManager` (connect, auth key/agent/password prompt)
- [x] `HostKeyVerifier` (known_hosts + TOFU)
- [x] `SshTtyConnector` + tab JediTerm
- [x] Keep-alive + deteksi putus + reconnect

### Fase 1: acceptance (manual, oleh user) — boleh dikerjakan sekarang, tidak perlu menunggu fase lain
- [~] Connect ke 3 server di 3 tab, `htop` & `vim` tampil benar, resize berfungsi
  (sudah dikonfirmasi user 2026-09-30: SSH ke `server01` (192.0.2.10) berhasil — login password dari vault, TOFU
  host key, beberapa tab satu koneksi, inject sudo, logout + reconnect; belum: 3 server berbeda, htop/vim, resize)
- [ ] Tutup tab tidak meninggalkan thread/koneksi bocor (thread dump `jcmd <pid> Thread.print` sebelum/sesudah;
  log harus menunjukkan `Pemakai koneksi … dilepas (sisa: 0)` lalu `Menutup koneksi` setelah grace 30 detik)

### Fase 2: Vault & inject password
- [x] ADR 0002 desain vault
- [x] `CredentialVault` + implementasi + unit test (round-trip, tamper detection GCM)
- [x] Field secret di dialog profil (login, sudo, root)
- [x] Auth password dari vault
- [x] Hotkey `Ctrl+Shift+P` inject sudo password, `Ctrl+Shift+R` inject root password
- [~] Acceptance (manual): `sudo su` & `su -` tanpa mengetik password; tidak ada secret di `profiles.json` maupun log
  (2026-10-01: scan data dev bersih — profil/config tanpa field secret, log hanya mencatat jenis password, `vault.bin`
  terenkripsi; inject sudo sudah dikonfirmasi user 2026-09-30. Belum: `su -` dengan password root)

### Fase 3: Panel SFTP
- [x] `RemoteFileService` di atas `SftpClient` (connection handle yang sama)
- [x] Panel: list dir, navigasi, sort, refresh, mode/owner/size/mtime
- [x] Upload/download + drag & drop + progress/cancel
- [x] Rename, mkdir, delete (konfirmasi), chmod
- [ ] Acceptance (manual): transfer 500 MB tidak membuat UI freeze dan bisa di-cancel
  (disiapkan 2026-10-01: download `/config/uji/besar-500mb.bin` di `uji-1`, SHA-256 `970fd59e…77bc`; upload
  `D:\tmp\myterm-dev\uji-upload-500mb.bin`)

### Fase 4: Edit dengan editor lokal
- [x] Konfigurasi editor per ekstensi (default `code --wait`)
- [x] `RemoteEditSession`: download ke cache + baseline stat
- [x] WatchService + debounce + hash compare
- [x] Upload atomic + preserve mode
- [x] Conflict dialog (overwrite / diff / batal)
- [x] Line ending guard (LF/CRLF)
- [x] EditTracker panel
- [ ] Acceptance (manual): edit `~/app/config.yml` di VS Code → Ctrl+S → ter-update di server dengan permission sama; konflik memunculkan dialog

### Fase 5: Edit file root & auto-trigger
- [x] `SudoWriter` (upload /tmp → `sudo -S install` → cleanup)
- [x] Backup + validation hook per path + rollback
- [x] `PromptResponder` auto-trigger dengan semua guard + unit test spoofing
- [x] Toggle auto-trigger per host (default OFF untuk prod)
- [ ] Acceptance (manual): edit `/etc/nginx/sites-available/x` dari VS Code → owner `root:root` tetap; config invalid di-rollback;
  auto-sudo: `sudo ls` di host dengan toggle aktif mengirim password sekali, prompt palsu (`echo "[sudo] password for ega: "`) tidak memicu
  (disiapkan 2026-10-01: nginx di `uji-1`, file root `/etc/nginx/http.d/uji.conf`; config invalid → `nginx -t` gagal → rollback)

### Fase 6: Polish & distribusi
- [x] Import `~/.ssh/config` (File → Impor dari ~/.ssh/config), jump host (ProxyJump, termasuk rantai; ProxyCommand tidak didukung)
- [x] Warna tab per environment (titik + garis di atas terminal), konfirmasi paste multi-baris di prod
- [ ] Pengaturan font/tema
- [x] Profile Maven `package-win` (jlink + jpackage → app-image; `.msi` dengan `-Djpackage.type=msi`, butuh WiX)

### Tambahan dari uji manual user (setelah Fase 4)
- [x] Auth "Default": coba key `~/.ssh`, lalu jatuh ke password (sebelumnya error kalau tidak ada key)
- [x] Terminal tidak lagi terlihat hang setelah logout: banner Reconnect + Enter untuk reconnect
- [x] Konfirmasi keluar: Ctrl+D di prompt kosong, `exit`/`logout` + Enter
- [x] Konfirmasi tutup tab (sesi aktif/transfer berjalan) dan keluar aplikasi (daftar sesi/transfer/edit aktif)
- [x] Zoom terminal per tab: Ctrl++ / Ctrl+- / Ctrl+0
- [x] Log per shell (buka/tutup) dan jumlah pemakai koneksi bersama
- [x] Ikon OS otomatis: `cat /etc/os-release` lewat exec channel sekali per profil per run → disimpan di profil
  (`HostProfile.os`), badge warna distro di host tree + tab, tooltip nama lengkap (permintaan user)
  - Ubuntu memakai logo resmi (PNG dari user, `app/src/main/resources/dev/egateza/termul/app/icons/ubuntu.png`,
    varian 1x–4x untuk HiDPI); distro lain tetap badge. Tambah logo lain: taruh PNG + daftarkan di `OsIcons.LOGOS`
- [x] Panel log aplikasi di bawah window: menu **Bantuan → Tampilkan log** (`Ctrl+Shift+L`), plus "Buka folder log"
  dan "Tentang TermUL" (permintaan user). `UiLogAppender` (logback) → ring buffer `LogBuffer` (2000 entry terakhir,
  di memori) → `LogPanel` polling tiap 300 ms hanya saat terlihat; WARN/ERROR berwarna merah. Isi sama dengan file log
- [x] Ikon Font Awesome Free 7.3.1 (SVG, lisensi CC BY 4.0, atribusi di Bantuan → Tentang) lewat enum `AppIcon` +
  `FlatSVGIcon`: toolbar SFTP, ikon folder/file/symlink di tabel SFTP, folder grup di host tree, panel log, tombol
  reconnect/batal. Di menu **hanya** Host baru, Grup baru, dan Tentang TermUL (keputusan user: menu lain tanpa ikon). Warna dibaca dari `UIManager` saat digambar → siap untuk dark/light theme.
  Tambah ikon: unduh SVG ke `app/src/main/resources/dev/egateza/termul/app/icons/fa/` (viewBox dibuat persegi) +
  konstanta di `AppIcon` (permintaan user)
- [x] Tombol laci (« / ») pendek (44 px) di strip 18 px yang menempel di tepi kanan panel host (satu warna dengan panel), di
  tengah vertikal, untuk buka/tutup panel host; strip tetap ada saat host tree tertutup (divider dikunci); lebar terakhir diingat selama aplikasi
  jalan, Ctrl+F (cari host) membuka panel kalau tertutup. Bukan checkbox menu (permintaan user)
- [x] Dua set ikon yang bisa dipilih di **Pengaturan → Set ikon** (Font Awesome / Material Symbols Rounded), ganti langsung
  tanpa restart, disimpan di `config.json` (`iconSet`). Tambah/ganti ikon: `java tools/AddIcon.java NAMA fa-nama material_nama`
  (lihat `docs/SETUP.md`) (permintaan user)
- [x] Panel host punya dua mode, dipilih di **Pengaturan → Panel host** (ganti langsung, disimpan di `config.json`
  `hostPanelMode`): *Panel di samping (tetap)* = split + strip tombol seperti sebelumnya (default); *Tombol melayang* =
  hanya tombol kapsul di tepi kiri area terminal (`HostDrawer`), klik menampilkan daftar host DI ATAS terminal (terminal
  tidak di-resize). Laci menutup lewat tombol, Esc, klik di luar, atau saat host dibuka; lebarnya bisa digeser.
  Transparansi tombol: 100/70/40/20% (`hostButtonOpacity`), solid lagi saat di-hover. Diverifikasi lewat screenshot.
  Juga: strip mode panel satu warna dengan daftar host; divider panel log disembunyikan bersama panelnya
  (sebelumnya meninggalkan titik-titik di tepi bawah window)
- [x] Menu Terminal ikut status tab/sesi (`TerminalMenuState`): tanpa tab semua item nonaktif kecuali "File yang sedang
  diedit"; Duplikat/Reconnect/Tutup tab/Zoom butuh ada tab (Reconnect sengaja tidak menunggu sesi aktif); Panel SFTP dan
  Inject password sudo/root butuh sesi tersambung (SFTP tetap aktif selama panelnya terbuka, supaya bisa ditutup).
  Diperbarui saat tab berganti, sesi connect/gagal/berakhir, menu dibuka, dan sebelum shortcut dicari (permintaan user)
- [ ] (usulan, menunggu keputusan user) Fallback koneksi kedua kalau server menolak channel (`MaxSessions`, default 10),
  atau opsi per profil "koneksi terpisah per tab"

## Log progres

| Tanggal | Commit | Task |
|---|---|---|
| 2026-10-01 | `feat(app): auto-inject password sudo/su per host lewat PromptResponder ...` | Wiring auto-sudo di tab (Fase 5) |
| 2026-10-01 | `feat(terminal): PromptResponder auto-inject sudo/su dengan guard ...` | PromptResponder + AnsiStripper + test spoofing (Fase 5) |
| 2026-10-01 | `feat(app): edit file root lewat sudo ...` | Edit sebagai root, tawaran sudo untuk file read-only, dialog validasi (Fase 5) |
| 2026-10-01 | `fix(ssh): exec yang ditolak server langsung selesai ...` | Exec want-reply |
| 2026-10-01 | `feat(sftp): SudoWriter untuk edit file root ...` | SudoWriter + backup/validasi/rollback + IT sudo asli (Fase 5) |
| 2026-10-01 | `feat(ssh): RemoteExec ...` | Exec channel dengan stdin/timeout (Fase 5) |
| 2026-10-01 | `feat(core): hook validasi per pola path ...` | ValidationHooks di config.json (Fase 5) |
| 2026-09-30 | `feat(app): menu Terminal nonaktif kalau tidak ada tab/sesi aktif` | Enable/disable menu Terminal sesuai sesi (permintaan user) |
| 2026-09-30 | `feat(app): mode tombol melayang untuk panel host dengan opsi transparansi` | Panel host: mode melayang + panel di samping (permintaan user) |
| 2026-09-30 | `feat(app): pilihan set ikon Font Awesome / Material Symbols dan tool AddIcon` | Dua set ikon + tool tambah ikon (permintaan user) |
| 2026-09-30 | `feat(app): tombol laci untuk buka/tutup panel host` | Buka/tutup panel host (permintaan user) |
| 2026-09-30 | `refactor(app): ikon menu hanya untuk host/grup baru dan Tentang` | Kurangi ikon menu (permintaan user) |
| 2026-09-30 | `feat(app): ikon Font Awesome Free di menu, toolbar SFTP, dan host tree` | Ikon Font Awesome (permintaan user) |
| 2026-09-30 | `refactor: rename aplikasi MyTerm menjadi TermUL` | Rename ke TermUL + migrasi folder data (permintaan user) |
| 2026-09-30 | `feat(app): panel log aplikasi di bawah window dan menu Bantuan` | Panel log + menu Bantuan (permintaan user) |
| 2026-09-30 | `feat(app): pakai logo resmi Ubuntu untuk ikon OS` | Logo Ubuntu (permintaan user) |
| 2026-09-30 | `feat(app): ikon OS otomatis (deteksi saat connect) di host tree dan tab` | Ikon OS otomatis (+ OsInfo, OsDetector) |
| 2026-09-30 | `feat(ssh): log buka/tutup shell per tab dan jumlah pemakai koneksi bersama` | Logging koneksi bersama (pertanyaan user) |
| 2026-09-30 | `feat(app): zoom terminal per tab` | Zoom terminal Ctrl++/Ctrl+-/Ctrl+0 (permintaan user) |
| 2026-09-30 | `feat(app): konfirmasi saat menutup tab ... dan saat keluar aplikasi` | Konfirmasi tutup tab & keluar aplikasi (permintaan user) |
| 2026-09-30 | `feat(app): konfirmasi sebelum keluar ...` | Konfirmasi logout + Enter untuk reconnect (permintaan user) |
| 2026-09-30 | `fix(terminal): tampilkan banner reconnect setelah logout` | Fix terminal terlihat hang setelah Ctrl+D/logout |
| 2026-09-30 | `fix(ssh): auth default mencoba key ~/.ssh lalu jatuh ke password seperti klien ssh biasa` | Fix auth default tanpa key |
| 2026-09-30 | `feat(app): edit file remote dengan editor lokal, auto-upload saat disimpan, dialog konflik/CRLF, dan EditTracker` | EditManager + dialog konflik/CRLF + EditTracker |
| 2026-09-30 | `refactor(sftp): RemoteEditSession bisa rebind ke koneksi SFTP baru untuk retry setelah reconnect` | Refactor Uploader + rebind |
| 2026-09-30 | `feat(sftp): tambah EditWatcher dengan WatchService dan debounce per file` | EditWatcher (WatchService + debounce) |
| 2026-09-30 | `feat(sftp): tambah RemoteEditSession dengan cache aman, hash compare, deteksi konflik, dan guard line ending` | RemoteEditSession + EditCache + LineEndings |
| 2026-09-30 | `feat: tambah konfigurasi editor lokal per ekstensi (config.json) dan launcher editor Windows` | Konfigurasi editor per ekstensi |
| 2026-09-30 | `feat(app): tambah upload/download dengan antrean + progress/cancel, drag & drop, rename, mkdir, delete, chmod di panel SFTP` | Operasi SFTP: transfer queue, DnD, rename/mkdir/delete/chmod |
| 2026-09-30 | `feat(app): tambah panel SFTP (browse, sort, refresh) per tab dan perbaiki shortcut agar jalan saat fokus di terminal` | Panel SFTP browse + dispatcher shortcut |
| 2026-09-30 | `feat(sftp): tambah RemoteFileService dengan upload atomic, progress/cancel, dan integration test OpenSSH` | RemoteFileService (SFTP) |
| 2026-09-30 | `feat(app): tambah hotkey Ctrl+Shift+P/Ctrl+Shift+R untuk inject password sudo/root dari vault` | Hotkey inject sudo/root |
| 2026-09-30 | `feat(app): tambah field password login/passphrase/sudo/root di dialog profil yang disimpan ke vault` | Field secret di dialog profil |
| 2026-09-30 | `feat(app): ambil password login/passphrase dari vault dengan unlock on-demand dan auto-lock idle` | Auth password dari vault + menu Vault |
| 2026-09-30 | `feat(vault): tambah vault terenkripsi Argon2id + AES-256-GCM dengan opsi DPAPI` | CredentialVault + FileCredentialVault + DPAPI |
| 2026-09-30 | `docs: tambah ADR 0002 desain vault (Argon2id + AES-256-GCM + opsi DPAPI)` | ADR 0002 desain vault |
| 2026-09-30 | `test(ssh): tambah integration test heartbeat mendeteksi server hang` | Keep-alive + deteksi putus + reconnect |
| 2026-09-30 | `feat(app): tambah tab terminal JediTerm, dialog TOFU, prompt password, dan integration test OpenSSH` | Tab terminal JediTerm + dialog TOFU/password + IT OpenSSH |
| 2026-09-30 | `feat(terminal): tambah SshTtyConnector JediTerm di atas ChannelShell dengan writer thread terpisah` | SshTtyConnector + SshTerminalFactory |
| 2026-09-30 | `feat(ssh): tambah SessionManager dengan auth key/password, reference counting, dan keep-alive` | SessionManager (connect, auth key/password, ref-count, keep-alive) |
| 2026-09-30 | `feat(ssh): tambah verifikasi host key strict dengan known_hosts aplikasi dan TOFU` | HostKeyVerifier (known_hosts + TOFU, logic) |
| 2026-09-30 | `feat(app): tambah dialog tambah/edit profil host` | Dialog profil |
| 2026-09-30 | `feat(app): tambah main window FlatLaf dengan host tree, grup, dan pencarian` | Main window + host tree |
| 2026-09-30 | `feat(core): tambah HostProfile, AppPaths, dan ProfileStore JSON dengan atomic write` | HostProfile + ProfileStore |
| 2026-09-30 | `build: tambah skeleton Maven multi-module, wrapper, dan enforcer` | Skeleton Maven multi-module |
| 2026-09-30 | `docs: tambah dokumentasi perencanaan awal (Fase 0)` | Dokumentasi awal |

## Langkah berikutnya

**Status 2026-10-01:** Fase 1–5 selesai (kode + test), plus perbaikan dari uji manual (lihat "Tambahan dari uji manual").
Fase 5 dikerjakan di branch `feat/fase5-root-edit` (bercabang dari `feat/tema-custom`). Fase 6 sebagian (tema). Menunggu: **acceptance manual Fase 1–5**.

**Pertanyaan terbuka untuk user:**
- Perlu fallback `MaxSessions` / opsi koneksi terpisah per tab?

**Prosedur acceptance Fase 1** (AI bisa membantu langkah 4–5):
1. ~~Koneksi ke server nyata~~ ✔ (server01). Buat 2 profil lagi ke container uji (sudah dijalankan 2026-09-30):
   `uji-1` = `127.0.0.1:2222`, `uji-2` = `127.0.0.1:2223`, user `dev` / password `devpass` (sudo pakai password yang sama;
   htop, vim, bash, terminfo sudah di-install). Stop: `docker stop myterm-sshd myterm-sshd2`; hapus: `docker rm -f ...`.
   Kalau container dihapus lalu dibuat ulang, host key berubah → TermUL akan MENOLAK koneksi (benar), hapus entry lama
   di `D:\tmp\myterm-dev\config\known_hosts`.
2. Buka 3 tab, jalankan `htop` dan `vim` di masing-masing (cek warna, garis box, scroll).
3. Resize window & split host tree → `stty size` / `htop` ikut berubah; coba zoom Ctrl++/Ctrl+-.
4. Tutup semua tab (aplikasi tetap terbuka), tunggu >30 detik.
5. `powershell -ExecutionPolicy Bypass -File tools\leak-check.ps1` (thread dump via `jcmd` + cek log): harus "Tidak ada thread
   terminal/SFTP yang tertinggal" dan `sisa: 0`. Lalu centang di sini + `docs/PLAN.md`.

1. **Review oleh user** (urutan saran, per modul):
   - `core/` → `HostProfile`, `ProfileStore`, `config/EditorConfig`
   - `vault/` → `FileCredentialVault` (format file di `docs/adr/0002-vault-design.md`)
   - `ssh/` → `SessionManager`, `auth/AuthSetup` (batas String MINA), `hostkey/AppServerKeyVerifier`
   - `terminal/` → `SshTtyConnector`
   - `sftp/` → `RemoteFileService`, `edit/RemoteEditSession`, `edit/EditWatcher`, `edit/EditCache`
   - `app/` → `TermULApp` (wiring), `ui/MainFrame`, `terminal/TerminalTab`, `sftp/SftpPanel`, `edit/EditManager`
2. **Acceptance test manual** Fase 1–4 (checkbox "Acceptance" di atas), dengan data dev terpisah:
   `mvnw.cmd -pl app exec:java -Dtermul.home=D:\tmp\myterm-dev`
3. **Fase 5 selesai (kode)** — uji manual: klik kanan file root di SFTP → *Edit sebagai root (sudo)* (atau buka file read-only → tawaran sudo),
   simpan config nginx yang valid (owner/mode tetap, backup di `/var/backups/termul`) dan yang invalid (rollback + dialog
   output `nginx -t`). Auto-sudo: centang di Edit host, lalu `sudo ls` / `su -`. Hook validasi: Pengaturan → Validasi file root.
   Keterbatasan: shell login harus POSIX (bash/dash/zsh); prompt `sudo-rs` (`[sudo: authenticate] Password:`) belum dikenali
   auto-sudo (hotkey tetap bisa); prompt shell dengan spasi di depan (mis. `(venv) ega@host:~$`) tidak meng-arm; file yang
   tidak bisa **dibaca** user login belum bisa dibuka (download masih lewat SFTP biasa).
4. Lalu **Fase 6** (lihat checklist di atas).

## Shortcut aplikasi (untuk uji manual)

| Shortcut | Aksi |
|---|---|
| Ctrl+N / Ctrl+F (fokus di luar terminal) | Host baru / cari host |
| Enter / F2 / Delete di host tree | Buka terminal / edit / hapus |
| Ctrl+Shift+T / Ctrl+Shift+W / Ctrl+F5 | Duplikat tab / tutup tab / reconnect |
| Ctrl+Shift+F | Panel SFTP |
| Ctrl+Shift+E | File yang sedang diedit |
| Ctrl+Shift+P / Ctrl+Shift+R | Inject password sudo / root dari vault |
| Ctrl++ (atau Ctrl+=) / Ctrl+- / Ctrl+0 | Zoom in / zoom out / ukuran default (per tab) |
| Ctrl+D di prompt, `exit`/`logout` | Konfirmasi keluar sesi |
| Enter setelah sesi berakhir | Reconnect |
| Ctrl+Shift+L | Tampilkan/sembunyikan panel log |

## Catatan / blocker

- JediTerm 3.76 tidak ada di Maven Central → pakai repo `https://packages.jetbrains.team/maven/p/ij/intellij-dependencies`.
- Auth `AGENT` (label "Default") **sementara** memakai key default `~/.ssh/id_ed25519|id_ecdsa|id_rsa` lalu jatuh ke password (bukan agent sungguhan). Agent Windows (named pipe `\\.\pipe\openssh-ssh-agent`) bisa dibuat dengan subclass `AbstractAgentProxy` MINA — belum dikerjakan.
- Jump host (ProxyJump): local forward `127.0.0.1:<acak>` lewat koneksi jump host (listener hanya loopback, ditutup bersama koneksi tujuan); host key tujuan dicek dengan alamat aslinya. Jump host harus `AllowTcpForwarding yes`.
- Password login/passphrase harus jadi `String` di batas API MINA — dicatat di `docs/SECURITY.md` bagian "Keterbatasan yang diketahui".
- Integration test (`*IT`) butuh Docker Desktop menyala; tanpa Docker otomatis di-skip.
- **Satu koneksi SSH per profil** dipakai bersama oleh semua tab/SFTP/edit (requirement N5); tiap tab = channel shell
  sendiri. Konsekuensi: koneksi putus → semua tab profil itu putus; OpenSSH `MaxSessions` default 10 channel per koneksi.
- JediTerm memanggil `TtyConnector.close()` sendiri setelah stream EOF → penutupan oleh user memakai `closeByUser()`.
- VS Code: kalau muncul `ClassNotFoundException TermULApp`, folder `target/classes` terhapus → `mvnw.cmd install -DskipTests`
  atau *Java: Force Java Compilation (Full)*. Extension Oracle Java + Red Hat Java terpasang bersamaan bisa bentrok.

## Cara menjalankan (dev)

```bash
mvnw.cmd install -DskipITs                       # build + unit test
mvnw.cmd verify                                  # + integration test (butuh Docker)
mvnw.cmd -pl app exec:java                       # jalankan app (data asli di %APPDATA%\TermUL)
mvnw.cmd -pl app exec:java -Dtermul.home=D:\tmp\myterm-dev   # data dev terpisah
```

Dari VS Code: `.vscode/launch.json` (tidak di-commit) konfigurasi `TermULApp`, `projectName: termul-app`,
`vmArgs: --enable-native-access=ALL-UNNAMED -Dtermul.home=D:\\tmp\\myterm-dev`, lalu F5.
Log aplikasi dev: `D:\tmp\myterm-dev\config\logs\termul.log`.

Server uji cepat: lihat `docs/SETUP.md` (container `lscr.io/linuxserver/openssh-server`, port 2222, user `dev`/`devpass`).

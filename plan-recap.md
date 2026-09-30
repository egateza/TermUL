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
| D1 | Nama & base package | `MyTerm`, groupId/package `dev.egateza.myterm` | Masih bisa di-rename (refactor package di IntelliJ) |
| D3 | Format storage profil | JSON (Jackson) | Sesuai rekomendasi PLAN |
| D2 | Desain vault | Master password + Argon2id + AES-256-GCM, opsi DPAPI | `docs/adr/0002-vault-design.md` |

## Status per fase

Legenda: `[ ]` belum, `[~]` sedang, `[x]` selesai.

### Fase 0: Perencanaan
- [x] Dokumentasi awal
- [x] Keputusan D1 (nama & package)
- [ ] Server uji `openssh-server` (manual, lihat `docs/SETUP.md`)

### Fase 1: Skeleton & terminal SSH pertama
- [x] Maven multi-module + wrapper + enforcer
- [x] Main window FlatLaf (host tree | tab area)
- [x] `HostProfile` + `ProfileStore` (JSON, atomic write)
- [x] Dialog tambah/edit profil
- [x] `SessionManager` (connect, auth key/agent/password prompt)
- [x] `HostKeyVerifier` (known_hosts + TOFU)
- [x] `SshTtyConnector` + tab JediTerm
- [x] Keep-alive + deteksi putus + reconnect

### Fase 1: acceptance (manual, oleh user)
- [ ] Connect ke 3 server di 3 tab, `htop` & `vim` tampil benar, resize berfungsi
- [ ] Tutup tab tidak meninggalkan thread/koneksi bocor (cek VisualVM/jconsole)

### Fase 2: Vault & inject password
- [x] ADR 0002 desain vault
- [x] `CredentialVault` + implementasi + unit test (round-trip, tamper detection GCM)
- [x] Field secret di dialog profil (login, sudo, root)
- [x] Auth password dari vault
- [x] Hotkey `Ctrl+Shift+P` inject sudo password, `Ctrl+Shift+R` inject root password
- [ ] Acceptance (manual): `sudo su` & `su -` tanpa mengetik password; tidak ada secret di `profiles.json` maupun log

### Fase 3: Panel SFTP
- [x] `RemoteFileService` di atas `SftpClient` (connection handle yang sama)
- [x] Panel: list dir, navigasi, sort, refresh, mode/owner/size/mtime
- [x] Upload/download + drag & drop + progress/cancel
- [x] Rename, mkdir, delete (konfirmasi), chmod
- [ ] Acceptance (manual): transfer 500 MB tidak membuat UI freeze dan bisa di-cancel

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
- [ ] `SudoWriter` (upload /tmp → `sudo -S install` → cleanup)
- [ ] Backup + validation hook per path + rollback
- [ ] `PromptResponder` auto-trigger dengan semua guard + unit test spoofing
- [ ] Toggle auto-trigger per host (default OFF untuk prod)

### Fase 6: Polish & distribusi
- [ ] Import `~/.ssh/config`, jump host (ProxyJump)
- [ ] Warna tab per environment (sudah dasar), konfirmasi paste multi-baris di prod
- [ ] Pengaturan font/tema
- [ ] Profile Maven `package-win` (jlink + jpackage → .msi)

## Log progres

| Tanggal | Commit | Task |
|---|---|---|
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

- Review hasil Fase 1–4 oleh user + acceptance test manual. Setelah itu Fase 5 lalu Fase 6.

## Shortcut aplikasi (untuk uji manual)

| Shortcut | Aksi |
|---|---|
| Ctrl+N / Ctrl+F (fokus di luar terminal) | Host baru / cari host |
| Enter / F2 / Delete di host tree | Buka terminal / edit / hapus |
| Ctrl+Shift+T / Ctrl+Shift+W / Ctrl+F5 | Duplikat tab / tutup tab / reconnect |
| Ctrl+Shift+F | Panel SFTP |
| Ctrl+Shift+E | File yang sedang diedit |
| Ctrl+Shift+P / Ctrl+Shift+R | Inject password sudo / root dari vault |

## Catatan / blocker

- JediTerm 3.76 tidak ada di Maven Central → pakai repo `https://packages.jetbrains.team/maven/p/ij/intellij-dependencies`.
- Auth `AGENT` (label "Default") **sementara** memakai key default `~/.ssh/id_ed25519|id_ecdsa|id_rsa` lalu jatuh ke password (bukan agent sungguhan). Agent Windows (named pipe `\\.\pipe\openssh-ssh-agent`) bisa dibuat dengan subclass `AbstractAgentProxy` MINA — belum dikerjakan.
- Jump host (ProxyJump) ditolak dengan pesan jelas; dijadwalkan Fase 6.
- Password login/passphrase harus jadi `String` di batas API MINA — dicatat di `docs/SECURITY.md` bagian "Keterbatasan yang diketahui".
- Integration test (`*IT`) butuh Docker Desktop menyala; tanpa Docker otomatis di-skip.

## Cara menjalankan (dev)

```bash
mvnw.cmd install -DskipITs                       # build + unit test
mvnw.cmd verify                                  # + integration test (butuh Docker)
mvnw.cmd -pl app exec:java                       # jalankan app (data asli di %APPDATA%\MyTerm)
mvnw.cmd -pl app exec:java -Dmyterm.home=D:\tmp\myterm-dev   # data dev terpisah
```

Server uji cepat: lihat `docs/SETUP.md` (container `lscr.io/linuxserver/openssh-server`, port 2222, user `dev`/`devpass`).

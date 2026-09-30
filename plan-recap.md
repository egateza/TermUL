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
- [ ] ADR 0002 desain vault
- [ ] `CredentialVault` + implementasi + unit test (round-trip, tamper detection GCM)
- [ ] Field secret di dialog profil (login, sudo, root)
- [ ] Auth password dari vault
- [ ] Hotkey `Ctrl+Shift+P` inject sudo password, `Ctrl+Shift+R` inject root password

### Fase 3: Panel SFTP
- [ ] `RemoteFileService` di atas `SftpClient` (connection handle yang sama)
- [ ] Panel: list dir, navigasi, sort, refresh, mode/owner/size/mtime
- [ ] Upload/download + drag & drop + progress/cancel
- [ ] Rename, mkdir, delete (konfirmasi), chmod

### Fase 4: Edit dengan editor lokal
- [ ] Konfigurasi editor per ekstensi (default `code --wait`)
- [ ] `RemoteEditSession`: download ke cache + baseline stat
- [ ] WatchService + debounce + hash compare
- [ ] Upload atomic + preserve mode
- [ ] Conflict dialog (overwrite / diff / batal)
- [ ] Line ending guard (LF/CRLF)
- [ ] EditTracker panel

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

- Fase 2: ADR 0002 desain vault, lalu CredentialVault (AES-256-GCM + Argon2id) + unit test. (Acceptance test manual Fase 1 masih perlu dilakukan user.)

## Catatan / blocker

- JediTerm 3.76 tidak ada di Maven Central → pakai repo `https://packages.jetbrains.team/maven/p/ij/intellij-dependencies`.
- Auth `AGENT` **sementara** memakai key default `~/.ssh/id_ed25519|id_ecdsa|id_rsa` (bukan agent sungguhan). Agent Windows (named pipe `\\.\pipe\openssh-ssh-agent`) bisa dibuat dengan subclass `AbstractAgentProxy` MINA — belum dikerjakan.
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

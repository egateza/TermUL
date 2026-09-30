# Plan & Roadmap

Dikerjakan di sela pekerjaan utama. Target ±1–2 jam/hari. Setiap fase menghasilkan sesuatu yang **langsung bisa dipakai sehari-hari**, supaya project ini sekaligus menjadi alat kerja.

Legenda: `[ ]` belum, `[~]` sedang, `[x]` selesai.

## Fase 0: Perencanaan (sekarang)
- [x] Konteks project (`CLAUDE.md`)
- [x] Requirements, arsitektur, security, plan, setup
- [x] Putuskan nama aplikasi & base package: `MyTerm`, `dev.egateza.myterm`
- [ ] Siapkan VM/container uji `openssh-server` (lihat `SETUP.md`)

## Fase 1: Skeleton & terminal SSH pertama (±2 minggu)
- [x] Maven multi-module (`core`, `vault`, `ssh`, `terminal`, `sftp`, `app`): parent POM dengan `dependencyManagement`/`pluginManagement`, Maven Wrapper, enforcer (Java 25, Maven ≥ 3.9)
- [x] Main window FlatLaf: split host tree | tab area
- [x] `HostProfile` record + `ProfileStore` (JSON, atomic write)
- [x] Dialog tambah/edit profil
- [ ] `SessionManager` dengan MINA: connect, auth key/agent/password (prompt manual dulu)
- [ ] `HostKeyVerifier`: known_hosts + dialog TOFU
- [ ] `SshTtyConnector` + tab JediTerm, resize, dan close yang rapi
- [ ] Keep-alive + deteksi putus + tombol reconnect

**Acceptance**: bisa connect ke 3 server berbeda di 3 tab, menjalankan `htop` dan `vim` dengan tampilan benar, dan resize window berfungsi. Menutup tab tidak meninggalkan thread atau koneksi yang bocor (dicek dengan jconsole/VisualVM).

## Fase 2: Vault & inject password (±1 minggu)
- [ ] Putuskan desain vault (ADR 0002)
- [ ] `CredentialVault` + implementasi + unit test (round-trip, tamper detection GCM)
- [ ] Field secret di dialog profil (login, sudo, root)
- [ ] Auth password dari vault
- [ ] Hotkey `Ctrl+Shift+P` inject sudo password, `Ctrl+Shift+R` inject root password

**Acceptance**: `sudo su` dan `su -` bisa dijalankan tanpa mengetik password. Tidak ada secret di `profiles.json` maupun di log.

## Fase 3: Panel SFTP (±2 minggu)
- [ ] `RemoteFileService` di atas `SftpClient` (memakai connection handle yang sama)
- [ ] Panel: list dir, navigasi, sort, refresh, tampilan mode/owner/size/mtime
- [ ] Upload/download + drag & drop dari Explorer + progress/cancel
- [ ] Rename, mkdir, delete (konfirmasi), chmod

**Acceptance**: transfer file 500 MB tidak membuat UI freeze dan bisa di-cancel.

## Fase 4: Edit dengan editor lokal (±2 minggu)
- [ ] Konfigurasi editor per ekstensi (default `code --wait`)
- [ ] `RemoteEditSession`: download ke cache + baseline stat
- [ ] WatchService + debounce + hash compare
- [ ] Upload atomic + preserve mode
- [ ] Conflict dialog (overwrite / diff / batal)
- [ ] Line ending guard (LF/CRLF)
- [ ] EditTracker panel (daftar file yang diedit + status)

**Acceptance**: edit `~/app/config.yml` di VS Code, lalu Ctrl+S, dan file langsung ter-update di server dengan permission yang sama. Kalau file diubah di server sebelum save, dialog konflik muncul.

## Fase 5: Edit file root & auto-trigger (±2 minggu)
- [ ] `SudoWriter`: upload ke `/tmp`, lalu `sudo -S install` dengan owner/mode, lalu cleanup
- [ ] Backup + validation hook per pola path (`/etc/nginx/**` menjalankan `nginx -t`) + rollback
- [ ] `PromptResponder` auto-trigger dengan semua guard + unit test (termasuk test spoofing: prompt palsu tanpa armed tidak boleh memicu)
- [ ] Toggle auto-trigger per host (default OFF untuk tag prod)

**Acceptance**: edit `/etc/nginx/sites-available/x` dari VS Code berhasil dengan owner `root:root` tetap. Config yang invalid di-rollback otomatis.

## Fase 6: Polish & distribusi
- [ ] Import `~/.ssh/config`, jump host (ProxyJump)
- [ ] Warna tab per environment, konfirmasi paste multi-baris di prod
- [ ] Pengaturan font/tema
- [ ] Profile Maven `package-win`: `jlink` runtime yang di-trim + `jpackage` → installer `.msi`
- [ ] (backlog) port forwarding, snippet library, session log dengan masking, follow cwd (OSC 7)

## Keputusan terbuka
| # | Keputusan | Opsi | Status |
|---|---|---|---|
| D1 | Nama aplikasi & base package | `MyTerm` / lainnya; `dev.<nama>.myterm` | Diputuskan: `MyTerm`, `dev.egateza.myterm` |
| D2 | Desain vault | DPAPI / master password / kombinasi | Terbuka (rekomendasi: kombinasi) |
| D3 | Format storage profil | JSON / SQLite | Rekomendasi JSON (mudah di-diff & backup) |
| D4 | Repo & lisensi | Private GitHub/GitLab | Terbuka |

## Strategi belajar
- Setiap fase adalah kesempatan belajar yang konkret: internal SSH protocol (channel, window, subsystem) di Fase 1 & 3, kriptografi terapan di Fase 2, concurrency + file system events di Fase 4.
- Pakai aplikasinya sendiri untuk pekerjaan harian sejak akhir Fase 1. Bug dan kebutuhan nyata akan menentukan prioritas berikutnya.

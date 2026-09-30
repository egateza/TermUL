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
- [ ] `SessionManager` (connect, auth key/agent/password prompt)
- [ ] `HostKeyVerifier` (known_hosts + TOFU)
- [ ] `SshTtyConnector` + tab JediTerm
- [ ] Keep-alive + deteksi putus + reconnect

### Fase 2 – 6
Belum dimulai. Lihat `docs/PLAN.md`.

## Log progres

| Tanggal | Commit | Task |
|---|---|---|
| 2026-09-30 | `feat(app): tambah dialog tambah/edit profil host` | Dialog profil |
| 2026-09-30 | `feat(app): tambah main window FlatLaf dengan host tree, grup, dan pencarian` | Main window + host tree |
| 2026-09-30 | `feat(core): tambah HostProfile, AppPaths, dan ProfileStore JSON dengan atomic write` | HostProfile + ProfileStore |
| 2026-09-30 | `build: tambah skeleton Maven multi-module, wrapper, dan enforcer` | Skeleton Maven multi-module |
| 2026-09-30 | `docs: tambah dokumentasi perencanaan awal (Fase 0)` | Dokumentasi awal |

## Langkah berikutnya

- SessionManager dengan MINA SSHD + HostKeyVerifier (modul ssh).

## Catatan / blocker

- JediTerm 3.76 tidak ada di Maven Central → pakai repo `https://packages.jetbrains.team/maven/p/ij/intellij-dependencies`.

# CLAUDE.md — Konteks Project untuk AI Assistant

> File ini adalah konteks utama project. Baca file ini dulu sebelum mengubah kode, lalu baca dokumen di `docs/` sesuai kebutuhan.

## Ringkasan

**MyTerm** (nama kerja, belum final) adalah SSH client desktop pribadi ala MobaXterm untuk Windows:

- Daftar host (tree/grup) dan profil SSH per server
- Terminal SSH dengan tab
- Inject password sudo/su (hotkey, plus auto-trigger opsional dengan guard)
- Browser remote directory (SFTP) dan edit file remote dengan editor lokal Windows (VS Code, Notepad++, dll.), lalu auto-upload saat file disimpan

Ini **bukan** terminal emulator lokal. PTY berada di server remote, sedangkan aplikasi merender stream dari SSH channel.

## Pemilik & konteks pemakaian

- Dipakai sendiri untuk mengelola server Linux (Ubuntu) produksi dan staging, termasuk platform Backend. **Keamanan credential adalah requirement utama, bukan fitur tambahan.**
- OS host utama: Windows. Project berada di `D:\IdeaProjects\my-personal-terminal`, IDE IntelliJ IDEA.
- Bahasa komunikasi: Bahasa Indonesia, istilah teknis tetap dalam bahasa Inggris.

## Tech stack (lihat `docs/adr/0001-tech-stack.md`)

| Area | Pilihan |
|---|---|
| Bahasa | Java 25 (LTS), `maven.compiler.release=25` |
| Build | Maven multi-module (parent POM + `dependencyManagement`) + Maven Wrapper (`mvnw`) |
| SSH/SFTP | Apache MINA SSHD (`sshd-core`, `sshd-sftp`) |
| Terminal widget | JediTerm (`jediterm-core`, `jediterm-ui`) dengan custom `TtyConnector` di atas `ChannelShell` |
| UI | Swing + FlatLaf (JediTerm berbasis Swing, jadi jangan pakai JavaFX) |
| Credential | Windows DPAPI via JNA, atau vault AES-256-GCM + Argon2id (keputusan terbuka, lihat `docs/SECURITY.md`) |
| Storage profil | JSON (Jackson) di `%APPDATA%\MyTerm\`; profil tidak boleh berisi secret |
| Logging | SLF4J + Logback |
| Test | JUnit 5, AssertJ, Testcontainers (container `openssh-server` untuk integration test) |
| Packaging | `jpackage` (.msi/.exe dengan runtime bawaan) |

## Struktur modul (target)

```
myterm/
├─ core/       domain model (HostProfile, Group), ProfileStore, event bus, utilities
├─ vault/      CredentialVault API + implementasi (DPAPI / AES-GCM)
├─ ssh/        SessionManager, koneksi MINA, known_hosts, auth (key/agent/password), jump host
├─ terminal/   JediTerm TtyConnector, PromptResponder (sudo inject)
├─ sftp/       RemoteFileService, RemoteEditSession (download → watch → upload atomic)
└─ app/        Swing UI (host tree, tabs, SFTP panel, dialogs), wiring, main()
```

Arah dependensi: `app → terminal, sftp → ssh → core`, dan `vault → core`. **`core` tidak boleh bergantung pada Swing atau MINA.**

### Konvensi Maven
- Parent `pom.xml` (packaging `pom`) memegang semua versi library di `<properties>` + `<dependencyManagement>`, dan versi plugin di `<pluginManagement>`. POM modul **tidak boleh** menulis versi sendiri.
- `maven-enforcer-plugin`: wajib Java 25 dan Maven ≥ 3.9, plus `dependencyConvergence`.
- Unit test (`*Test.java`) dijalankan oleh Surefire. Integration test (`*IT.java`, Testcontainers) dijalankan oleh Failsafe pada fase `verify`.
- Encoding `UTF-8`, dan build harus reproducible (`project.build.outputTimestamp`).

## Aturan & konvensi kode (wajib)

### Threading
- **EDT hanya untuk UI.** Tidak boleh ada network I/O, SFTP, atau disk I/O berat di EDT. Update UI dari thread lain dilakukan lewat `SwingUtilities.invokeLater`.
- Setiap sesi SSH punya reader thread atau executor sendiri. Write ke channel boleh blocking (SSH window penuh), jadi **jangan write sambil memegang lock** yang juga dipakai EDT.
- State yang diakses lintas thread ditandai jelas (`// guarded by X`) atau memakai tipe concurrent atau immutable (record).
- Semua resource (`ClientSession`, `ChannelShell`, `SftpClient`, stream) ditutup dengan try-with-resources atau lifecycle yang eksplisit. Saat tab ditutup, channel ditutup. Koneksi ditutup saat channel terakhir ditutup (reference counting).

### Security invariants (jangan dilanggar)
1. Secret selalu disimpan sebagai `char[]`/`byte[]`, **di-zero setelah dipakai** (`Arrays.fill`). Jangan pernah dikonversi ke `String`.
2. Secret **tidak pernah** di-log, masuk exception message, atau tersimpan di file profil, export, atau clipboard history.
3. Host key wajib diverifikasi terhadap `known_hosts` milik aplikasi. Host baru memakai TOFU dengan dialog fingerprint (SHA256). Kalau host key berubah, **tolak** koneksi dan tampilkan peringatan keras. Tidak boleh ada `AcceptAllServerKeyVerifier`, termasuk di kode dev.
4. Auto-sudo trigger hanya boleh aktif dengan guard lengkap (armed window, match di baris terakhir, one-shot, berhenti saat "Sorry, try again"). Lihat `docs/SECURITY.md`.
5. Upload remote edit wajib atomic (temp file lalu rename), memeriksa konflik (stat remote sebelum upload), dan mempertahankan mode file.

### Gaya
- Java 25: gunakan `record`, `sealed`, pattern matching (termasuk record patterns & `switch`), `var` secukupnya, dan flexible constructor bodies (validasi sebelum `super(...)`). Tidak memakai Lombok.
- **Tidak memakai fitur preview** (`--enable-preview`), misalnya Structured Concurrency yang masih preview di JDK 25. Hanya fitur final.
- Virtual threads boleh dipakai untuk operasi blocking (SFTP, exec, transfer). Sejak JDK 24, `synchronized` tidak lagi mem-pin carrier thread. Tetap jangan pakai virtual thread untuk EDT atau untuk loop CPU-bound.
- `ScopedValue` (final di JDK 25) boleh dipakai untuk konteks per-operasi (mis. `profileId` untuk logging), sebagai pengganti `ThreadLocal`.
- JNA (DPAPI) butuh native access. Jalankan dengan `--enable-native-access=ALL-UNNAMED` (atau nama module), supaya tidak muncul warning dan tetap kompatibel saat JDK nanti memblokir secara default.
- Error handling: exception domain yang jelas (`SshConnectException`, `RemoteConflictException`, dll.), dan pesan error untuk user dalam Bahasa Indonesia.
- Constructor injection manual (tanpa framework DI). Wiring dilakukan di modul `app`.
- Setiap fitur baru disertai unit test. Fitur SSH/SFTP juga disertai integration test (Testcontainers).

## Status saat ini

- **Fase 1–4 selesai (kode + test)**; acceptance manual & review user menunggu. Fase 5–6 ditunda. Progres & langkah berikutnya selalu di `plan-recap.md`.
- Langkah berikutnya: review, lalu Fase 5 di `docs/PLAN.md` (edit file root & auto-sudo).

## Peta dokumen

| File | Isi |
|---|---|
| `docs/REQUIREMENTS.md` | Kebutuhan fungsional & non-fungsional, out of scope |
| `docs/ARCHITECTURE.md` | Komponen, threading model, alur data utama, layout storage |
| `docs/SECURITY.md` | Threat model, desain vault, guard auto-sudo, risiko remote edit |
| `docs/PLAN.md` | Roadmap per fase + checklist + acceptance criteria |
| `docs/SETUP.md` | Tools, dependensi, environment dev & test |
| `docs/adr/` | Architecture Decision Records |

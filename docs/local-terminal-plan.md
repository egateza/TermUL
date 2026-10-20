# Plan: Terminal lokal (ADR 0005)

Tab terminal lokal (shell di mesin sendiri) berdampingan dengan tab SSH, lewat pty4j. Keputusan arsitekturnya ada di
[ADR 0005](adr/0005-local-terminal.md). File ini berisi rencana teknis dan progresnya.

**Status:** 🚧 Tahap 0–1 selesai di kode (macOS teruji), acceptance manual & Windows belum · terakhir diperbarui 2026-10-19

## Perlu diputuskan saat review

| # | Pertanyaan | Usulan |
|---|---|---|
| 1 | Shortcut "Terminal lokal baru" | **Ctrl+Shift+O / ⇧⌘O** (O = *open local*; masih kosong). Sejak 2026-10-20 juga ada di popup Tab baru (⌘T / Ctrl+Shift+T) |
| 2 | Shell keluar (`exit`) | Banner "Shell selesai (kode N)" + tombol **Jalankan ulang**, Enter juga menjalankan ulang. Sama dengan sesi SSH yang berakhir. Opsi "tutup tab otomatis kalau kode 0" ditunda ke tahap 2 |
| 3 | Shell default Windows | `pwsh.exe` kalau ada, selain itu `powershell.exe`. `cmd.exe` baru di tahap 2 (profil) |
| 4 | Letak di daftar host | Node tetap **Terminal lokal** paling atas di tree (di atas Favorites), double-click membuka tab. Plus File → Terminal lokal baru |
| 5 | Konfirmasi tutup tab lokal | Hanya kalau shell punya proses anak (perintah masih berjalan). Shell diam di prompt ditutup tanpa dialog |
| 6 | Notarization macOS | Di luar cakupan. `.app` belum di-sign, jadi native pty4j cukup diekstrak ke temp seperti bawaan |

## Prinsip

- **Tab SSH tidak berubah perilakunya.** Refactor dikerjakan dan di-commit terpisah, sebelum fitur lokal ditambahkan,
  dengan seluruh test lulus dan uji manual fitur SSH.
- **Tidak ada secret ke proses lokal.** Inject sudo/root, auto-sudo, dan vault tidak terhubung ke tab lokal sama sekali
  (bukan sekadar disembunyikan di UI).
- **EDT hanya untuk UI.** Start proses, baca/tulis PTY, dan mematikan pohon proses berjalan di luar EDT, sama seperti
  koneksi SSH.
- **Tidak ada proses yatim.** Setiap shell yang dibuat TermUL dimatikan bersama pohon prosesnya saat tab ditutup atau
  aplikasi keluar.

## Tahap 0: spike dependensi (±½ hari)

| | Item | Detail |
|---|---|---|
| ✅ | Tambah pty4j 0.14.0 | `pty4j.version` di parent `<properties>` + `dependencyManagement`, modul `localterm`. `kotlin.version` 2.4.0 → 2.4.20 |
| ✅ | Enforcer | `dependencyConvergence` lulus (slf4j/annotations dipin `dependencyManagement`) |
| ✅ | jdeps | pty4j hanya butuh `java.base`. `java.desktop` + `java.logging` berasal dari JNA yang sudah dipakai; `java.logging` sudah ikut runtime lewat `java.sql`. **Modul jlink tidak berubah → `GENERATION` tetap** |
| ✅ | Prototype macOS | `zsh -l` lewat pty4j **di runtime jlink `.app` kita**: output UTF-8, resize (`stty size` = 40 132), job latar, dan pemutusan proses lulus |
| ⬜ | Prototype Windows | `pwsh` lewat ConPTY di app-image, belum diuji (butuh mesin Windows). Dicakup `LocalTtyConnectorIT` di Windows + acceptance manual. Runtime macOS masih punya `bin/java` (dibutuhkan ConPTY) karena `--jlink-options` kita tidak memakai `--strip-native-commands`; pastikan sama di Windows |
| ✅ | Keputusan lanjut | macOS lulus → lanjut ke tahap 1. Windows divalidasi di 1d/1e |

**Temuan spike yang mengubah desain:**

- `UnixPtyProcess.toHandle()`/`descendants()` melempar `UnsupportedOperationException`. Pohon proses dibaca lewat
  `ProcessHandle.of(process.pid())`.
- Shell interaktif mengabaikan `destroy()` (SIGTERM): zsh tidak berhenti dalam 3 dtk. Cara yang benar sama dengan
  menutup window terminal: **tutup stream PTY (SIGHUP)**. zsh berhenti dalam ±100 ms dan job latarnya ikut
  mendapat HUP. Urutan mematikan: tutup PTY → tunggu ±2 dtk → `destroyForcibly()` proses yang tersisa di pohon.

## Tahap 1: tab lokal dengan shell default

### 1a. Refactor tab ke `TermPane` (commit terpisah, tanpa perubahan perilaku)

| | Item | Detail |
|---|---|---|
| ✅ | `abstract sealed class TermPane extends JPanel permits TerminalTab, LocalTerminalTab` | Kelas (bukan interface) karena `SplitPanes<T extends JComponent>`. `SnippetHost` pindah ke sini dan menerima grup (string), bukan `HostProfile`. Method bersama: `focusTerminal`, `zoom`, `reloadFont/Cursor/Colors`, `interceptKey`, `typeText`, `setSnippetHost`, `refreshSnippetButton`, `isSessionActive`, `isConnected`, `reconnect`, `dispose`, `setStateListener`, plus `tabLabel()`, `tabTooltip()`, `closeDescription()` untuk judul tab dan dialog |
| ✅ | `SplitPanes<TerminalTab>` → `SplitPanes<TermPane>` | `SplitPanes` sudah generik |
| ✅ | `MainFrame` | `currentTab()` mengembalikan `TermPane`. Aksi khusus SSH (SFTP, inject, edit tracker, deteksi OS, `markUsed`, `lastProfilesOf`, reveal/edit profil) lewat `currentSsh()` = pattern matching `instanceof TerminalTab`. `TerminalMenuState` mendapat status "tab lokal" (SFTP & inject nonaktif) |
| ✅ | Judul/ikon tab | Dihitung oleh pane (`tabLabel`), warna environment & ikon OS hanya untuk SSH |
| ⬜ | Uji | ✅ Semua unit test lulus (hanya 3 kegagalan lama di `EditorLauncherTest`/`InstallGuideTest`, sudah gagal sebelum refactor). ⬜ Uji manual: connect, split/gabung, SFTP, inject, reconnect, rename, drag, tutup tab aktif |

### 1b. Modul `localterm`

`localterm → core` (dan `jediterm-core`, `pty4j`). `app → localterm`. Modul `ssh`/`terminal` tidak tahu soal lokal.

| | Item | Detail |
|---|---|---|
| ✅ | `LocalShell` (record) | `command[]`, `directory`, `environment`. `LocalShells.defaultFor(Os, env, fileExists)` murni (bisa di-test): `$SHELL -l` / `/bin/zsh -l` (macOS) / `/bin/bash -l` (Linux); `pwsh.exe` / `powershell.exe` (Windows, dicari di PATH) |
| ✅ | Environment | Warisi env TermUL + `TERM=xterm-256color`, `COLORTERM=truecolor`, `TERM_PROGRAM=TermUL`, `LANG=en_US.UTF-8` kalau tidak ada. Variabel internal TermUL/JVM tidak ditambahkan |
| ✅ | `PtyHandle` | Interface tipis di atas `PtyProcess` (stream, `setWinSize`, `isAlive`, `waitFor`, `pid`, `destroyTree`) supaya connector bisa di-unit test tanpa native |
| ✅ | `LocalTtyConnector implements TtyConnector` | Baca di thread emulator JediTerm; tulis + resize berurutan di writer thread sendiri (pola `SshTtyConnector`, karena write ke PTY bisa blocking); resize di-debounce 150 ms; `closeByUser()`; close listener sekali; kode keluar |
| ✅ | `LocalTerminalFactory.open(LocalShell, size)` | Blocking, dipanggil di executor I/O. Windows: `setUseWinConPty(true)`. Gagal start → `LocalShellException` dengan pesan Bahasa Indonesia |
| ✅ | Mematikan proses | Catat pohon proses (`ProcessHandle.of(pid).descendants()`), tutup stream PTY (SIGHUP), tunggu ±2 dtk, lalu `destroyForcibly()` yang masih hidup. Dijalankan di luar EDT |

### 1c. UI tab lokal

| | Item | Detail |
|---|---|---|
| ✅ | `LocalTerminalTab extends TermPane` | Memakai `ZoomableTermWidget`, salinan `TerminalSettings` (zoom per tab), `TerminalFloats` (tombol Sambung ulang = jalankan ulang shell; tombol password **disembunyikan** tanpa injector, juga di prompt `sudo` lokal), `SnippetButton`, pencarian |
| ✅ | Judul & ikon | "Lokal · zsh" (nama shell), ikon terminal; tooltip = command + direktori awal. Rename tab tetap berlaku |
| ✅ | Shell selesai | Banner "Shell selesai (kode N)" + Jalankan ulang; Enter = jalankan ulang |
| ✅ | Tanpa fitur SSH | Tanpa `PromptResponder`, `PasteGuard` per environment, `ExitGuard` (exit lokal tidak memutus apa pun), SFTP, inject |
| ✅ | Entry point | File → **Terminal lokal baru** (shortcut #1), node **Terminal lokal** di tree, Duplikat tab untuk tab lokal |
| ✅ | Menu klik kanan tab | Item SSH (SFTP, inject, edit/reveal profil) nonaktif untuk tab lokal; rename, gabung, tutup tetap jalan |
| ✅ | Tutup tab / keluar | `isSessionActive()` = shell punya proses anak. Dialog menyebut nama prosesnya saja (tanpa argumen). Daftar "masih aktif" saat keluar ikut memuat tab lokal; semua shell dimatikan saat keluar |
| ✅ | i18n | Teks id/en baru |

### 1d. Test

| | Item | Detail |
|---|---|---|
| ✅ | `LocalShellsTest` | Pemilihan shell per OS, cadangan kalau `$SHELL` kosong atau tidak ada, `pwsh` vs `powershell` |
| ✅ | `LocalEnvTest` | `TERM`/`COLORTERM`/`LANG` ditambahkan tanpa menimpa nilai yang sudah ada (kecuali `TERM`) |
| ✅ | `LocalTtyConnectorTest` | Dengan `PtyHandle` palsu: urutan write, resize debounce, close idempotent, `closeByUser`, close listener sekali, write setelah close diabaikan |
| ✅ | `TerminalMenuStateTest` | Kasus tab lokal |
| ✅ | `LocalTtyConnectorIT` (Failsafe) | pty4j sungguhan: macOS/Linux `/bin/sh` (echo penanda, `stty size` setelah resize, kode keluar, pohon proses mati setelah close); Windows `cmd /c` lewat ConPTY (`@EnabledOnOs`, **belum dijalankan**: butuh mesin Windows). IT wajib terus membaca output (macOS menunggu PTY dikuras sebelum shell berakhir) |

### 1e. Distribusi & dokumen

| | Item | Detail |
|---|---|---|
| ✅ | jpackage | Komentar di `app/pom.xml`: jangan tambahkan `--strip-native-commands` (pty4j butuh `bin/java` untuk ConPTY) |
| ✅ | Update lewat menu | pty4j + kotlin-stdlib baru hanyalah jar tambahan di manifest (delta update biasa). Modul jlink tidak berubah (tahap 0), jadi `GENERATION` tetap |
| ✅ | Lisensi | pty4j (EPL-1.0) dicatat di tabel dependensi SETUP.md. Proyek belum punya daftar lisensi pihak ketiga tersendiri |
| ✅ | Dokumen | REQUIREMENTS (keluar dari out of scope), ARCHITECTURE (modul, threading, siklus hidup proses), SECURITY (bagian Terminal lokal), CLAUDE.md (ringkasan, struktur & arah modul), SETUP.md (dependensi), PLAN.md (Fase 7), ADR 0001 (catatan), ADR 0005 → Diterima |
| ⬜ | Acceptance manual | macOS (`.dmg`) dan Windows (app-image): buka, `vim`, `top`, Ctrl+C, resize, split dengan tab SSH, tutup saat perintah berjalan, keluar aplikasi tanpa proses tertinggal |

## Tahap 2: profil terminal lokal (dikerjakan 2026-10-20, permintaan user)

| | Item | Detail |
|---|---|---|
| ✅ | `LocalProfile` (record di `core`, bukan `HostProfile`) | `id`, `name`, `command[]`, `directory` (kosong/`~` = home), `environment` (nilai kosong = hapus variabel), `terminalTheme`. Disimpan di **`local-profiles.json` terpisah** (bukan `profiles.json`: tanpa migrasi skema, tidak ikut ekspor profil mobile) bersama `defaultId`. Validasi: satu baris tanpa karakter kontrol, nama variabel valid. Dialog memperingatkan agar tidak mengisi secret |
| ✅ | Deteksi otomatis (`LocalShells.detect`) | macOS/Linux: `$SHELL` lalu `/etc/shells` (satu per nama), semua dengan `-l`. Windows: PowerShell 7, Windows PowerShell, Command Prompt, Git Bash (`bin\bash.exe --login -i`, dari lokasi instalasi standar atau `git.exe` di PATH), distro WSL (`wsl.exe -d`) **hanya kalau** Manajemen WSL aktif. Id stabil (`UUID.nameUUIDFromBytes`) supaya bisa jadi default. Deteksi sekali saat start, di luar EDT |
| ✅ | Default | Pilihan tersimpan kalau masih ada; selain itu shell terdeteksi pertama. Dipakai Ctrl+Shift+O / ⇧⌘O dan double-click grup Terminal lokal |
| ✅ | Tree | Grup **Terminal lokal** berisi semua pilihan (default ditandai). Klik kanan: Buka, Jadikan default, Kelola profil |
| ✅ | Popup Tab baru | Setiap pilihan jadi baris sendiri; pencarian juga pada nama dan perintah |
| ✅ | Dialog **Pengaturan → Profil terminal lokal** | Baru, Duplikat (cara mengubah shell terdeteksi), Hapus, Jadikan default; field Program (+Pilih), Argumen (kutip ganda), Direktori awal (+Pilih), Environment (`NAMA=nilai` per baris), Warna terminal |
| ✅ | Tab | Nama "Lokal · <profil>", warna terminal profil, Duplikat tab membuka profil yang sama; direktori awal yang tidak ada → pesan jelas |
| ✅ | Test | `CommandLineTest`, `LocalProfileStoreTest`, deteksi Windows/Unix + build di `LocalShellsTest`, `LocalProfilesTest`, `LocalProfilesDialogTest`, grup tree, popup |
| ⬜ | Opsi | Tutup tab otomatis kalau shell keluar dengan kode 0 (belum diminta) |
| ⬜ | Acceptance manual | Windows: PowerShell 7, cmd, Git Bash, WSL lewat ConPTY; macOS: profil custom dengan direktori/environment |

## Perkiraan

| Tahap | Perkiraan |
|---|---|
| 0: spike | ±½ hari |
| 1a: refactor `TermPane` | ±1½ hari |
| 1b–1d: modul, UI, test | ±2–3 hari |
| 1e: distribusi & dokumen | ±½ hari |
| 2: profil | ±2–3 hari |

## Risiko

- **Regresi tab SSH saat refactor.** Mitigasi: commit terpisah, compiler membantu lewat `sealed` + pattern matching,
  dan uji manual fitur SSH sebelum fitur lokal masuk.
- **ConPTY di Windows** (resize, Unicode, `OpenConsole.exe` bawaan vs sistem). Mitigasi: prototype di tahap 0;
  `-Dcom.pty4j.windows.disable.bundled.conpty=true` sebagai jalan keluar.
- **kotlin-stdlib 2.4.0 → 2.4.20** juga dipakai JediTerm. Patch release, tapi tetap diuji di tahap 0.
- **Native diekstrak ke temp.** Antivirus/kebijakan perusahaan bisa memblokir eksekusi dari temp. Jalan keluarnya
  sama dengan notarization: kirim native sebagai file di folder aplikasi dan set `pty4j.preferred.native.folder`.
- **Notarization macOS** (lihat ADR 0005): ditunda, tapi jalurnya sudah diketahui.

## Riwayat

- 2026-10-19: draf pertama untuk review; disetujui (ADR 0005 Diterima).
- 2026-10-19: tahap 0 (spike) dan tahap 1 (1a refactor `TermPane`, 1b `localterm`, 1c UI, 1d test, 1e dokumen) dikerjakan.

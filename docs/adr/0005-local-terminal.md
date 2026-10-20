# ADR 0005: Terminal lokal lewat pty4j, berdampingan dengan SSH

- Status: Diterima
- Tanggal: 2026-10-19
- Mengubah: ADR 0001 (prinsip "bukan terminal emulator lokal"), REQUIREMENTS "Out of scope"
- Terkait: ADR 0004 (WSL lewat SSH)

## Konteks
TermUL dirancang sebagai SSH client: PTY selalu ada di server remote (ADR 0001), dan terminal lokal tercatat sebagai
*out of scope "untuk sekarang"* dengan catatan "bisa ditambah nanti via pty4j" (REQUIREMENTS). ADR 0004 juga memilih
SSH ke `localhost` untuk WSL dan membiarkan opsi PTY lokal terbuka.

Pemakaian sehari-hari ternyata sering butuh shell lokal di samping sesi SSH (git, build, `scp`/`kubectl`, skrip
lokal). Saat ini user harus pindah ke aplikasi terminal lain, sehingga tab, split, shortcut, tema, snippet, dan
zoom TermUL tidak ikut terpakai.

Opsi yang dipertimbangkan:

1. **Tetap SSH-only.** Tidak ada perubahan. Shell lokal harus lewat aplikasi lain, atau `ssh localhost` (butuh sshd
   aktif di mesin sendiri; macOS: Remote Login, Windows: OpenSSH Server). Rumit dan membuka port login hanya untuk
   ini.
2. **`ProcessBuilder` biasa (tanpa PTY).** Tanpa dependensi native, tapi shell tidak mendapat TTY: tidak ada prompt
   interaktif, `vim`/`top`/`less` tidak jalan, Ctrl+C tidak sampai sebagai sinyal, dan ukuran window tidak bisa
   dikirim. Tidak layak dipakai sebagai terminal.
3. **pty4j (JetBrains, `org.jetbrains.pty4j:pty4j` 0.14.0 di Maven Central, EPL-1.0).** PTY sungguhan: ConPTY atau
   WinPty di Windows, `forkpty` di macOS/Linux. Ini pasangan JediTerm yang dipakai IntelliJ. Dependensinya hanya
   JNA 5.19.1 (sama dengan yang sudah dipakai), kotlin-stdlib, annotations, dan slf4j. Tidak ada JNI sendiri dan
   tidak butuh `--add-opens`; `--enable-native-access=ALL-UNNAMED` yang sudah ada mencakup JNA-nya. Library native
   (jar ±2,8 MB) dibawa di dalam jar dan diekstrak ke folder temp saat dipakai. Butuh refactor tab yang sekarang
   terikat ke SSH.

## Keputusan
Opsi **3**, dengan batasan berikut.

1. **Tab lokal berdampingan dengan tab SSH**, memakai widget, tab, split, shortcut, tema, zoom, pencarian, dan
   snippet yang sama. Tab lokal bisa digabung (split) dengan tab SSH.
2. **Fitur khusus SSH tidak tersedia di tab lokal**: SFTP, remote edit, inject password sudo/root (manual maupun
   auto), known_hosts/TOFU, deteksi OS remote, reconnect bertahap, dan konfirmasi paste per environment. Tidak ada
   secret dari vault yang pernah dikirim ke proses lokal.
3. **Shell default per OS**, tanpa konfigurasi di tahap 1:
   - macOS/Linux: shell login user (`$SHELL`, cadangan `/bin/zsh` di macOS dan `/bin/bash` di Linux), dijalankan
     sebagai login shell (`-l`) supaya PATH dari profil shell terbaca. Ini penting karena aplikasi yang dibuka dari
     Finder hanya mendapat PATH minimal.
   - Windows: PowerShell 7 (`pwsh.exe`) kalau ada di PATH, selain itu Windows PowerShell (`powershell.exe`).
     Backend **ConPTY** (`setUseWinConPty(true)`; default pty4j adalah WinPty). pty4j sendiri jatuh ke WinPty kalau
     ConPTY gagal dimuat.
   - Direktori awal: home user. Environment: diwarisi dari TermUL, ditambah `TERM=xterm-256color`,
     `COLORTERM=truecolor`, `TERM_PROGRAM=TermUL`, dan `LANG=en_US.UTF-8` kalau belum ada (macOS dari Finder).
4. **Kode PTY di modul baru `localterm`** (`localterm → core`; `app → localterm`). Modul `terminal` dan `ssh` tidak
   berubah dependensinya, dan `core` tetap bebas dari Swing, MINA, dan native.
5. **Tab memakai abstraksi `sealed`.** `MainFrame` dan `SplitPanes` bekerja dengan `TermPane` (sealed:
   `TerminalTab` yang sekarang untuk SSH, dan `LocalTerminalTab`). Fitur khusus SSH diakses lewat pattern
   matching, bukan `null`/flag, supaya compiler menandai tempat yang lupa ditangani.
6. **Siklus hidup proses.** Shell lokal dimatikan saat tab ditutup atau aplikasi keluar dengan cara yang sama
   seperti menutup window terminal: stream PTY ditutup (SIGHUP; shell interaktif mengabaikan SIGTERM), lalu setelah
   tenggat singkat `destroyForcibly()` untuk proses di pohonnya yang masih hidup (`ProcessHandle.of(pid)`, karena
   `PtyProcess.toHandle()` tidak didukung). Tujuannya tidak ada proses yatim. Shell yang berakhir sendiri (`exit`) menampilkan banner dengan tombol
   **Jalankan ulang** (Enter juga menjalankan ulang), sama seperti sesi SSH yang berakhir.
7. **Konfirmasi tutup** hanya kalau shell sedang menjalankan proses anak (perintah yang masih berjalan), dideteksi
   lewat `ProcessHandle.descendants()`. Shell yang diam di prompt ditutup tanpa dialog.
8. **Bertahap.** Tahap 1: satu jenis tab lokal (shell default). Tahap 2: profil terminal lokal di tree (pilih
   shell/argumen, direktori awal, environment tambahan, tema), termasuk `cmd.exe` dan `wsl.exe -d <distro>`.
   Profil lokal **bukan** `HostProfile` dan tidak punya secret. *(Revisi 2026-10-20: tahap 2 dikerjakan. Profil
   disimpan di `local-profiles.json` terpisah, dan shell terpasang dideteksi otomatis sebagai pilihan hanya-baca;
   lihat `docs/local-terminal-plan.md`.)*
9. **WSL lewat SSH (ADR 0004) tetap jalan apa adanya.** Tab `wsl.exe` di tahap 2 adalah alternatif tanpa sshd, dan
   tidak punya SFTP/remote edit.

## Konsekuensi
- (+) Shell lokal dan sesi SSH dalam satu aplikasi, dengan shortcut, tema, split, dan snippet yang sama.
- (+) WSL tanpa sshd jadi mungkin (tahap 2), tanpa mengubah alur WSL lewat SSH.
- (−) Dependensi native baru. Library native ikut jar (+±2,8 MB) dan di-load lewat JNA dari folder temp. Binary macOS
  bawaan pty4j ditandatangani JetBrains tanpa hardened runtime, jadi kalau `.app` nanti di-notarize, binary itu harus
  dikeluarkan dari jar, di-sign ulang, dan dimuat lewat `-Dpty4j.preferred.native.folder`. Belum perlu selama `.app`
  belum di-sign.
- (−) `kotlin-stdlib` naik dari 2.4.0 ke 2.4.20 (versi yang dipakai pty4j 0.14.0).
- (−) Refactor `TerminalTab`/`MainFrame` ke `TermPane`: perubahan menyentuh banyak tempat (MainFrame ±60 referensi),
  walau perilaku tab SSH tidak berubah.
- (−) Permukaan serangan berubah: TermUL kini bisa menjalankan proses lokal. Proses itu berjalan sebagai user yang
  sama dengan hak yang sama seperti terminal lain, tanpa elevasi, dan tidak pernah menerima secret dari vault.
- (−) Tab lokal tidak bisa dipulihkan setelah aplikasi ditutup (proses mati bersama aplikasi), sama seperti sesi SSH.
- ADR 0001 tetap berlaku untuk pilihan stack. Hanya kalimat "PTY berada di server remote" yang kini menjadi "PTY
  berada di server remote, atau lokal lewat pty4j untuk tab lokal".

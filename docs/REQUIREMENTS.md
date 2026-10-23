# Requirements

## Latar belakang

Pekerjaan harian melibatkan banyak server Linux (produksi & staging). Masalah yang ingin diselesaikan:

1. Sulit mengingat host, port, user, dan key tiap server, sehingga dibutuhkan **daftar host + profil**.
2. Password sudo/su sering lupa atau typo, sehingga dibutuhkan **inject password otomatis**.
3. Mengedit file di server lewat nano/vim tidak nyaman (indentation berantakan, dll.), sehingga dibutuhkan **edit file remote dengan editor lokal**.

## Kebutuhan fungsional

### F1. Host & profil
- F1.1 Host tree dengan grup/folder (mis. `Produksi/Backend`, `Staging`, `Infra`), drag & drop, dan search/filter cepat.
- F1.2 Field profil: nama, host, port, username, metode auth (key / agent / password), path private key, jump host (ProxyJump), environment tag (prod/staging/dev) dengan warna tab, catatan, dan working directory awal.
- F1.3 Duplikasi profil, import dari `~/.ssh/config`, export profil **tanpa secret**.
- F1.4 Double-click host membuka tab terminal baru.

### F2. Terminal SSH
- F2.1 Multi-tab. Tab diberi warna sesuai environment (prod = merah).
- F2.2 Emulasi xterm-256color (vim, htop, tmux, less tampil benar), resize mengikuti window (`window-change`).
- F2.3 Copy/paste. Paste multi-baris meminta konfirmasi di host prod.
- F2.4 Font, tema, dan ukuran scrollback bisa dikonfigurasi.
- F2.5 Keep-alive dan deteksi koneksi putus, disertai tombol reconnect.
- F2.6 (nice to have) Duplicate session, broadcast input ke beberapa tab.
- F2.8 Lepas tab (tunggal atau grup split) ke window terpisah dan kembalikan ke window utama, tanpa memutus sesi.
- F2.7 Highlight teks: keyword/regex diwarnai saat tampil (latar, huruf, bold/italic/underline/dim/inverse), per profil
  highlight. Profil dipilih per tab: host / profil terminal lokal → grup terdekat / semua terminal lokal → global.
  Import/export JSON format plugin Tabby (`tabby-highlight`). Default mati.

### F3. Password sudo/su
- F3.1 Vault menyimpan per profil: password login (opsional), password sudo (user), dan password root (untuk `su`).
- F3.2 **Hotkey inject** (default `Ctrl+Shift+P`) mengirim password sudo ke terminal aktif.
- F3.3 **Auto-trigger** (opsional per host, default OFF untuk prod) mendeteksi prompt `[sudo] password for <user>:` atau `Password:` setelah user menjalankan `sudo`/`su`, lalu mengirim password. Guard wajib ada, lihat `SECURITY.md`.
- F3.4 Kalau password ditolak (`Sorry, try again`), jangan kirim ulang. Tampilkan notifikasi.

### F4. Remote directory (SFTP)
- F4.1 Panel SFTP di samping terminal yang memakai koneksi yang sama. Opsi "Ikuti folder terminal": panel pindah ke folder
  kerja shell (OSC 7 dari shell, atau probe `/proc` lewat exec setelah Enter; Linux, butuh `AcceptEnv LC_*` di server).
- F4.2 Browse, upload/download (drag & drop dari Explorer), rename, mkdir, delete (dengan konfirmasi), chmod, dan tampilan owner/mode/size/mtime.
- F4.3 Progress transfer + cancel.
- F4.4 Upload antar-server: klik kanan → Upload ke ▸ panel SFTP lain yang terbuka (grup sendiri dulu, lalu tab lain), atau
  drag & drop antar-panel. Selalu dengan konfirmasi folder & host tujuan (peringatan ekstra untuk PROD).

### F5. Edit file remote dengan editor lokal
- F5.1 Open file remote dengan editor pilihan. Mapping per ekstensi bisa dikonfigurasi (default: VS Code `code --wait`).
- F5.2 Setiap kali file disimpan di editor, upload otomatis (dengan debounce).
- F5.3 Conflict detection: kalau file remote berubah sejak dibuka, tampilkan dialog (Overwrite / Lihat diff / Batal).
- F5.4 Upload atomic dan mode/permission dipertahankan.
- F5.5 Line ending dijaga (LF tetap LF). Beri peringatan kalau editor mengubahnya menjadi CRLF.
- F5.6 **File milik root**: upload ke `/tmp`, lalu `sudo install` via exec channel, dengan password dari vault.
- F5.7 (nice to have) Hook validasi per path (mis. `nginx -t`, `sshd -t`, `visudo -c`) dan rollback kalau gagal.
- F5.8 Daftar "file yang sedang diedit", beserta status sinkronisasinya.

### F6. Lain-lain (backlog)
- Port forwarding (local/remote/dynamic), X11 tidak diperlukan.
- Snippet/command library per host.
- Session logging ke file (dengan masking prompt password).
- Shortcut bisa diganti per aksi (selesai, Fase 10; Pengaturan → Shortcut): default per OS, hanya override yang disimpan
  (`shortcuts.json`), validasi bentrok / tombol shell / shortcut sistem, inject password wajib modifier utama.

## Kebutuhan non-fungsional

| ID | Kebutuhan |
|---|---|
| N1 | Startup < 2 detik (JVM + UI) pada laptop kerja |
| N2 | Input latency terminal terasa native (< 30 ms lokal ke render) |
| N3 | Tidak ada secret dalam plaintext di disk, log, atau heap lebih lama dari yang diperlukan |
| N4 | Koneksi putus tidak membuat UI freeze, dan edit yang pending tidak hilang (file lokal tetap ada, upload bisa di-retry) |
| N5 | Satu koneksi SSH per host dipakai bersama oleh shell, SFTP, dan exec |
| N6 | Distribusi berupa installer Windows dengan runtime bawaan (tanpa perlu install JDK) |
| N7 | Konfigurasi & data di `%APPDATA%\TermUL`, cache edit di `%LOCALAPPDATA%\TermUL` (macOS: `~/Library/Application Support/TermUL` dan `~/Library/Caches/TermUL`) |
| N8 | Bisa dipakai di macOS: shortcut memakai Cmd, editor/aplikasi default macOS, distribusi JAR portable (butuh JDK 25) |
| N9 | Terminal lokal (ADR 0005): tab shell di mesin sendiri lewat PTY lokal, berdampingan dengan tab SSH; tanpa SFTP, inject password, atau vault |

## Out of scope (untuk sekarang)

- (Sudah tidak out of scope) Terminal lokal dengan profil (PowerShell, cmd, Git Bash, WSL tanpa sshd, shell
  `/etc/shells`, profil custom): ADR 0005. Distro WSL tetap juga bisa dimasuki lewat SSH ke `localhost`, dengan
  WSL Manager opsional untuk start/stop distro (ADR 0004).
- (Sudah tidak out of scope) FTP/FTPS sebagai browser file saja, tanpa terminal: ADR 0007.
- RDP, VNC, X11 server, protokol serial/telnet.
- Sinkronisasi profil ke cloud / multi-user.
- Linux sebagai target. macOS didukung (N8), termasuk `.app`/`.dmg` dan "Ingat di Mac ini" lewat Keychain (ADR 0006,
  hanya di `.app`, tidak di JAR portable).

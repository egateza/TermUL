# ADR 0004: WSL lewat SSH ke localhost, dengan WSL Manager opsional

- Status: Diterima
- Tanggal: 2026-10-06

## Konteks
User ingin distro WSL di Windows muncul di panel host dan bisa langsung dimasuki dari TermUL. User juga ingin start/stop distro tanpa membuka Windows Terminal. Ada dua cara masuk ke distro:

1. **PTY lokal** (pty4j/ConPTY menjalankan `wsl.exe -d <distro>`). Tidak perlu setup apa pun di distro. Kekurangannya: butuh dependensi native baru dan refactor `TerminalTab` (sekarang terikat ke `SshTtyConnector`, `SftpLinks`, sudo inject, dan deteksi OS). SFTP dan remote edit juga tidak jalan. Prinsip "bukan terminal emulator lokal" (ADR 0001) ikut berubah.
2. **SSH ke sshd di distro** (`localhost:<port>`). Semua fitur jalan: SFTP, edit dengan editor lokal, sudo inject, known_hosts/TOFU. Arsitektur tetap SSH-only. Kekurangannya: `openssh-server` harus dipasang sekali per distro, dan distro harus hidup saat connect.

Selain itu, WSL mematikan distro yang tidak punya sesi `wsl.exe` aktif (idle sekitar 8 detik), termasuk kalau di dalamnya ada service seperti `sshd`.

## Keputusan
1. **Masuk ke distro lewat SSH (opsi 2).** Profil WSL adalah `HostProfile` biasa (`localhost`, port mulai 2222) dengan field opsional `wslDistro`. Tidak ada PTY lokal.
2. **Opt-in.** Pengaturan → Manajemen WSL (`AppConfig.wslManager`, default mati, hanya tampil di Windows). Selama mati, TermUL tidak menjalankan `wsl.exe` sama sekali, dan field distro di dialog profil tidak tampil.
3. **WSL Manager** (File → WSL Manager, atau klik kanan grup WSL) menampilkan daftar distro (`wsl --list --quiet` dan `--running`) dengan aksi Start, Stop (`wsl --terminate`), Shutdown semua (`wsl --shutdown`, dengan konfirmasi), Buat profil SSH, dan Panduan SSH. Distro internal Docker Desktop disembunyikan.
4. **Keep-alive.** Start menahan proses `wsl.exe -d <distro> --exec sleep infinity` sampai Stop atau TermUL ditutup. Saat TermUL ditutup, keep-alive dilepas tapi distro tidak dihentikan paksa; distro mati sendiri saat idle kecuali dipakai di luar TermUL.
5. **Grup bawaan "WSL"** di panel host hanya tampil kalau ada distro yang berjalan. Isinya profil SSH distro tersebut, atau node "belum ada profil SSH" yang bisa di-double-click untuk membuat profil (user default distro dari `whoami`; port dari kolom "Port SSH" di WSL Manager, yang terisi dari `sshd -T` distro yang berjalan dan bisa diubah user, dengan 2222+ hanya sebagai cadangan). Status dibaca ulang setiap 15 detik selama fitur aktif, dan setelah setiap aksi.
6. **Sebelum connect** ke profil ber-`wslDistro`, TermUL menjalankan distro (keep-alive) lalu sshd sebagai root (`systemctl start ssh|sshd`, atau `service ssh|sshd start` tanpa systemd). Auto-reconnect setelah koneksi putus **tidak** menyalakan distro yang sedang berhenti, supaya distro yang sengaja di-stop tidak hidup lagi karena tab yang masih terbuka.
7. **TermUL tidak memasang atau mengubah konfigurasi sshd.** Panduan SSH hanya menampilkan perintah yang bisa di-copy: install, `Port`, `ListenAddress 127.0.0.1` opsional, beralih dari `ssh.socket` ke `ssh.service` (socket activation mengabaikan `Port` di `sshd_config`; port 22 tertulis di unit socket), lalu restart.
8. **Periksa SSH** (WSL Manager, atau klik kanan profil WSL) memeriksa sebagai root tanpa mengubah konfigurasi: sshd terpasang, `sshd -t`, port efektif (`sshd -T`), `ssh.socket` aktif, port yang listen (`ss`/`netstat`), dan TCP `localhost:<port>` dari Windows. Hasilnya daftar ✔/✘ dan perintah perbaikan yang bisa di-copy. Pemeriksaan yang sama dijalankan sebelum connect, jadi error connect menyebut masalahnya (mis. "ssh.socket listen di 22, bukan 2211") alih-alih "connection refused".

## Konsekuensi
- (+) Prinsip SSH-only tetap. Tidak ada dependensi baru, dan semua fitur SSH/SFTP jalan untuk WSL.
- (+) Bagi yang tidak memakai WSL, tidak ada biaya runtime apa pun.
- (−) Setup sshd sekali per distro, dan setiap distro butuh port berbeda.
- (−) Polling `wsl.exe` setiap 15 detik selama fitur aktif (dua proses singkat).
- (−) Kalau TermUL di-kill paksa, proses keep-alive `wsl.exe` bisa tertinggal sampai distro dihentikan.
- PTY lokal (opsi 1) tetap terbuka untuk nanti kalau dibutuhkan terminal tanpa sshd.

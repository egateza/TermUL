# ADR 0011: Hapus WSL Manager, WSL lewat terminal lokal saja

- Status: Diterima
- Tanggal: 2026-11-02
- Menggantikan: ADR 0004 (WSL lewat SSH + WSL Manager)
- Terkait: ADR 0005 (terminal lokal lewat pty4j)

## Konteks
WSL Manager (ADR 0004) dibuat supaya distro WSL bisa dimasuki dari TermUL. Waktu itu TermUL masih SSH-only, jadi
caranya SSH ke sshd di distro (`localhost:<port>`), ditambah start/stop distro, keep-alive, grup WSL di panel host,
Periksa SSH, dan panduan sshd.

Sejak ADR 0005, terminal lokal bisa membuka `wsl.exe -d <distro>` langsung: tanpa sshd, tanpa port, tanpa setup apa pun
di distro. Distro hidup sendiri saat tab dibuka dan mati saat idle setelah tab ditutup, jadi start, stop, dan
keep-alive tidak dibutuhkan lagi. Alasan utama WSL Manager (mengakses WSL lewat TermUL) sudah terpenuhi.

Yang hanya ada di jalur SSH: panel SFTP, remote edit, inject password sudo, dan tombol Stop/Shutdown di UI. File distro
tetap bisa dibuka dari Windows lewat `\\wsl.localhost\<distro>\...`, dan `wsl --shutdown` bisa dijalankan dari tab lokal.

Biaya mempertahankannya: ±2.500 baris (core `WslManager`/`SshCheck`, app `WslController`/`WslManagerDialog`/
`SshCheckReport`, plus test), field `HostProfile.wslDistro` dan `AppConfig.wslManager`, grup WSL di panel host, hook
`TerminalTab.BeforeConnect`, 90 kunci i18n per bahasa, dan polling `wsl.exe` setiap 15 detik selama fitur aktif.
Daftar distro untuk terminal lokal juga ikut bergantung pada WSL Manager: kalau Manajemen WSL mati, pilihan
"WSL · <distro>" hilang dari terminal lokal.

## Keputusan
1. **WSL Manager dihapus seluruhnya**: dialog, menu File → WSL Manager, Pengaturan → Manajemen WSL, grup bawaan WSL di
   panel host, Periksa SSH, panduan sshd, keep-alive, persiapan distro/sshd sebelum connect, dan polling.
2. **Distro WSL hanya lewat terminal lokal.** `core.wsl.WslDistros` membaca distro terpasang dengan
   `wsl --list --quiet` (distro internal Docker Desktop dilewati), di thread `ssh-ops`: saat start, lalu paling sering
   sekali per menit saat window diaktifkan, supaya distro yang baru dipasang ikut muncul. Tidak bergantung pada
   pengaturan apa pun.
3. **Data lama tetap terbaca.** Semua store mengabaikan properti JSON yang tidak dikenal. Profil dengan `wslDistro`
   menjadi profil SSH biasa ke `localhost` (tetap bisa dipakai kalau sshd di distro dijalankan sendiri), dan
   `wslManager` di `config.json` diabaikan. Kedua field hilang dengan sendirinya saat file disimpan berikutnya.
4. **Direktori awal distro WSL dalam bentuk Linux** (tambahan 2026-11-02). Untuk perintah `wsl.exe`, direktori kosong,
   `~`, `~/...`, dan `/...` dikirim sebagai `wsl.exe --cd <dir>` (`LocalShells.build`). Sebelumnya `~` dipakai sebagai
   working directory proses seperti shell lain, yaitu home Windows, sehingga distro mulai di `/mnt/c/Users/<user>`,
   bukan `/home/<user>`. Path Windows (`D:\proyek`) tetap menjadi working directory, dan WSL menerjemahkannya sendiri.

## Konsekuensi
- (+) Kode, UI, dan pengaturan lebih sedikit. Tidak ada proses `wsl.exe` yang ditahan atau di-polling di background.
- (+) Pilihan "WSL · <distro>" di terminal lokal selalu tersedia di Windows, tanpa harus menyalakan pengaturan.
- (−) WSL tidak lagi punya panel SFTP, remote edit, atau inject sudo dari TermUL. Kalau butuh, profil SSH ke
  `localhost` masih bisa dibuat manual, dengan sshd yang dijalankan sendiri di distro.
- (−) Tidak ada tombol Stop/Shutdown distro di UI; gantinya `wsl --terminate <distro>` / `wsl --shutdown`.

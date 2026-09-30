# ADR 0001: Tech stack: Java 25 + Maven + MINA SSHD + JediTerm + Swing/FlatLaf

- Status: Diterima
- Tanggal: 2026-09-30

## Konteks
Aplikasi ini adalah SSH client ala MobaXterm (host list, profil, inject password sudo, SFTP, edit remote dengan editor lokal) untuk Windows. PTY berada di server remote, jadi bagian yang sulit ada di SSH/SFTP, credential, prompt detection, dan sinkronisasi file, bukan di rendering terminal atau PTY lokal.

## Opsi yang dipertimbangkan
- **A. Pakai/extend Tabby** (Electron + xterm.js + ssh2): paling cepat, tapi kontrol terbatas, harus menulis plugin dalam TypeScript, dan footprint Electron besar.
- **B. Java 25 (LTS) + Apache MINA SSHD + JediTerm + Swing/FlatLaf**: memakai keahlian utama pemilik. MINA menyediakan SSH + SFTP yang lengkap dalam satu library. JediTerm adalah terminal widget yang teruji (IntelliJ).
- **C. Tauri + Rust (russh, russh-sftp) + xterm.js**: binary kecil dan UI modern, tapi harus belajar 2 bahasa baru, dan ekosistem SSH Rust belum semapan MINA.

## Keputusan
Opsi **B**.

Build tool: **Maven** (multi-module, parent POM + Maven Wrapper), karena sudah menjadi standar di project Java pemilik dan konvensinya familiar (lifecycle, Surefire/Failsafe, dependencyManagement).

JDK: **25 (LTS)**. Ini LTS terbaru, dengan fitur yang relevan: virtual threads tanpa pinning di `synchronized` (sejak JDK 24), `ScopedValue` final, flexible constructor bodies, dan `jpackage` yang mendukung WiX v4/v5.

## Konsekuensi
- (+) Produktif sejak awal, dan library SSH/SFTP matang.
- (+) Concurrency & resource lifecycle memakai model Java yang sudah dikuasai.
- (−) UI Swing: perlu FlatLaf supaya tampilannya modern. JediTerm terikat pada Swing, sehingga JavaFX/Compose tidak dipakai.
- (−) Startup JVM & ukuran installer (~60–90 MB dengan runtime yang di-trim via jlink). Bisa diterima untuk aplikasi desktop pribadi.
- Semua library (MINA SSHD, JediTerm, FlatLaf, JNA, Bouncy Castle) harus diverifikasi berjalan di JDK 25 saat Fase 1 (smoke test: connect, render terminal, DPAPI).
- Evaluasi ulang kalau kebutuhan bergeser ke terminal lokal dengan rendering GPU (lihat opsi C).

# ADR 0009: Menu klik kanan Explorer untuk membuka terminal lokal di folder

- Status: Diterima
- Tanggal: 2026-10-23
- Terkait: ADR 0005 (terminal lokal), `SingleInstance` (core), `docs/ARCHITECTURE.md` bagian "Menu klik kanan Explorer"

## Konteks
User ingin padanan "Open Git Bash here" untuk TermUL. Caranya: klik kanan sebuah folder di Explorer, pilih
**TermUL ▸ Buka &lt;shell&gt; di sini**, lalu tab terminal lokal terbuka di TermUL dengan working directory folder itu.
Shell-nya bisa shell apa saja dari daftar terminal lokal: Git Bash, PowerShell, cmd, distro WSL, atau profil buatan
user. Untuk saat ini hanya Windows. Padanannya di macOS (Quick Action Finder) ditunda.

## Keputusan
1. **Menu didaftarkan lewat registry klasik di `HKCU\Software\Classes`**, tanpa admin. Ini opt-in lewat Pengaturan →
   Tray & startup → "Tampilkan di menu klik kanan Explorer". Polanya sama dengan `AutoStart`, dan hanya tersedia untuk
   TermUL hasil instalasi (`jpackage.app-path`).
   - Menu dipasang di `Directory\Background\shell\TermUL` (area kosong di dalam folder), `Directory\shell\TermUL`
     (folder), dan `Drive\shell\TermUL` (drive).
   - Bentuknya cascading menu: `MUIVerb`, `Icon`, `SubCommands=""`, lalu item di subkey `shell\01..16`. Explorer
     mengurutkan item menurut nama key. Item pertama adalah shell default.
   - Command tiap item: `"<TermUL.exe>" --open-local=<id pilihan> --cwd "%V"`.
   - Value `TermULAppPath` di key root menandai lokasi instalasi yang memasang menu. Menu milik lokasi lain dianggap
     belum aktif, dan mencentang lagi memperbaikinya.
   - Daftar item ditulis ulang saat app start dan setiap kali daftar terminal lokal berubah (profil atau distro WSL),
     tapi hanya kalau menu sedang terpasang. Label mengikuti bahasa UI saat itu.
2. **`--cwd` selalu argumen terakhir.** Untuk root drive, `"%V"` menghasilkan `"C:\"`, dan aturan argv Windows membacanya
   sebagai `C:"` (kutip ter-escape). Parser membuang kutip sisa di akhir, menyambung argumen yang terpecah, dan mengubah
   `C:` menjadi `C:\`. Path harus absolut.
3. **Instance yang sudah berjalan menerima permintaan lewat `SingleInstance`.** Protokolnya diperluas dari satu
   perintah `show` menjadi `Request(command, argument)`:
   - argumen dikirim sebagai Base64 UTF-8;
   - request dibatasi 16 KiB;
   - permintaan yang datang selama aplikasi masih memuat diantre (maksimal 8, tanpa duplikat).
   Token tetap wajib.
4. **Argumen hanya berisi id pilihan dan path folder.** Perintah shell selalu diambil dari daftar terminal lokal milik
   aplikasi. Id yang tidak dikenal (misalnya profil sudah dihapus) jatuh ke shell default, sehingga argumen tidak bisa
   dipakai untuk menjalankan perintah sembarang.
5. **Git Bash tetap di folder itu**: environment tab diberi `CHERE_INVOKING=1`, sehingga login shell MSYS tidak `cd` ke
   home. Shell lain mengabaikan variabel ini. `wsl.exe` memakai working directory proses, yang diterjemahkan ke `/mnt/...`.

## Alternatif yang ditolak
- **`IExplorerCommand` + sparse package** supaya menu muncul langsung di menu klik kanan Windows 11 yang baru. Cara ini
  butuh DLL COM native, package identity, dan signing. Terlalu berat untuk saat ini. Konsekuensinya, di Windows 11 menu
  ada di "Show more options" (Shift+F10), sama seperti "Open Git Bash here".
- **Dua item saja** ("shell default" dan "pilih shell…" dengan dialog di app). Ditolak karena per shell lebih cepat
  dipakai dan sesuai kebiasaan "Open Git Bash here".
- **Mengirim command shell lengkap di argumen.** Ditolak: siapa pun yang bisa membuat key registry atau menjalankan
  TermUL dengan argumen bisa menyisipkan perintah. Dengan id saja, yang bisa dijalankan terbatas pada profil milik user.

## Konsekuensi
- Uninstall MSI tidak menghapus key registry ini, karena key-nya ditulis aplikasi, bukan installer. Matikan opsinya
  sebelum uninstall. Kalau terlanjur, item yang tersisa hanya menjalankan exe yang sudah tidak ada.
- Folder virtual (This PC, Library) tidak punya path filesystem, jadi Explorer tidak menampilkan menu ini di sana.
- Jumlah item dibatasi 16.

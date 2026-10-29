# ADR 0010: Ikon shortcut dan menu Explorer ikut berganti lewat update menu

- Status: Diterima
- Tanggal: 2026-10-29
- Terkait: ADR 0003 (update lewat GitHub Releases), ADR 0009 (menu klik kanan Explorer), `tools/MakeAppIcon.java`

## Konteks
Ikon aplikasi diganti ke "T-cursor" (sama dengan TermUL Mobile). Update lewat menu (ADR 0003) hanya mengganti jar.
Ikon di dalam jar (window, taskbar saat berjalan, tray) ikut berganti, tapi shortcut Start Menu dan desktop, pin
taskbar, dan menu klik kanan Explorer memakai ikon yang tertanam di `TermUL.exe` oleh `jpackage`. Tanpa cara lain, user
harus memasang zip/`.msi` baru hanya demi ikon.

## Keputusan
1. **`.ico` ikut di jar** (`app/icons/termul.ico`, dibuat `tools/MakeAppIcon.java` bersama `termul.ico` untuk
   `jpackage`). Saat start (Windows, hanya hasil instalasi: `jpackage.app-path` ada), file itu disalin ke
   `<cache>\app-icon\termul-<hash>.ico`. Nama memuat hash isi, jadi ikon baru = path baru dan cache ikon Windows tidak
   menampilkan ikon lama. Versi lama dihapus setelah shortcut dipindah.
2. **Shortcut diarahkan ke file itu** lewat COM `IShellLinkW` + `IPersistFile` (JNA), sehingga format `.lnk` diurus
   Windows. Yang diperiksa: Start Menu Programs (kedalaman 2), desktop, dan `User Pinned\TaskBar` / `StartMenu`.
   **Hanya shortcut yang targetnya `TermUL.exe` instalasi ini** yang diubah, dan hanya kalau ikonnya belum benar;
   target, argumen, dan isi lain tidak disentuh. Satu shortcut yang gagal dibaca dilewati.
3. **Menu klik kanan Explorer** (kalau terpasang) menulis `Icon` = file itu; tanpa file, tetap `TermUL.exe,0`.
4. Setelah ada perubahan, `SHChangeNotify` meminta Explorer memuat ulang ikon.
5. Berjalan sekali per start di thread platform latar berprioritas rendah (COM terikat thread OS, jadi bukan virtual
   thread), setelah window tampil. Semua kegagalan hanya dicatat di log: fitur ini kosmetik.

## Konsekuensi
- (+) Ganti ikon cukup lewat update menu; tidak perlu menaikkan `UpdateProtocol.GENERATION`.
- (+) Shortcut yang dibuat atau di-pin belakangan ikut diperbaiki di start berikutnya.
- (−) Ikon file `TermUL.exe` sendiri (dilihat di folder instalasi) tetap ikon lama sampai installer baru. Menulis ulang
  resource exe yang sedang berjalan sengaja tidak dilakukan: rawan dicurigai antivirus dan mengganggu perbaikan `.msi`.
- (−) Kalau folder cache dihapus, shortcut sementara tampil tanpa ikon sampai TermUL dijalankan lagi.
- macOS tidak diubah: ikon `.app` di Finder / Launchpad hanya berganti lewat `.dmg` baru (mengubah isi bundle merusak
  tanda tangan). Ikon Dock saat berjalan sudah ikut jar.

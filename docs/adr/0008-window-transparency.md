# ADR 0008: Transparansi jendela dan terminal, dengan bingkai Windows yang digambar sendiri

- Status: Diterima
- Tanggal: 2026-10-23
- Terkait: `docs/ARCHITECTURE.md` bagian "Transparansi jendela", prototipe di luar repo (dibuang)

## Konteks
User ingin dua pengaturan transparansi terpisah: **jendela** (semua bagian selain terminal, teks ikut pudar) dan
**latar terminal** (teks terminal tetap solid), seperti Windows Terminal. Keduanya harus berlaku langsung tanpa
restart, dan window harus tetap bisa di-Snap (seret ke tepi, Win+panah, Snap Layouts) dengan animasi docking.

Batasan yang ditemukan lewat prototipe:

1. Transparansi per bagian butuh **alpha per piksel**. Java hanya mengizinkannya di window **tanpa dekorasi native**;
   window berdekorasi menolak `setOpacity`/latar beralpha.
2. Window tanpa dekorasi adalah popup (`WS_POPUP`) bagi Windows: Snap tidak berfungsi.
3. Trik DWM (`DwmExtendFrameIntoClientArea`, backdrop acrylic) tidak membuat piksel Java tembus: pipeline GDI maupun
   D3D Java selalu mengirim piksel solid. Windows Terminal bisa karena menggambar lewat DirectComposition.
4. Mengganti window berdekorasi menjadi tanpa dekorasi harus membuat HWND baru (`dispose` + tampil lagi). Itu terlihat
   sebagai kedipan dan efek "minimize", dan **ikon taskbar hilang sesaat**. Penutup berupa tangkapan layar menutupi
   kedipan, tetapi tidak menutupi taskbar.
5. Alpha native untuk seluruh window (`WS_EX_LAYERED` + `SetLayeredWindowAttributes`) mempertahankan bingkai native dan
   Snap, tetapi semua bagian ikut pudar, termasuk terminal.

## Keputusan
1. **Windows: window utama selalu tanpa dekorasi native**, dengan title bar FlatLaf (`JRootPane.FRAME`), ditambah
   `WinCustomFrame` yang membuatnya berperilaku seperti window berbingkai native:
   - window procedure AWT di-subclass lewat JNA; gaya window diberi `WS_CAPTION | WS_THICKFRAME | WS_SYSMENU |
     WS_MINIMIZEBOX | WS_MAXIMIZEBOX` (bukan popup lagi);
   - `WM_NCCALCSIZE`: tanpa area non-client. Saat maximize, area isi dibatasi ke area kerja monitor, karena Windows
     menaruh window berbingkai tebal 8 px melewati tiap sisi layar (`WM_GETMINMAXINFO` diabaikan untuk window ini);
   - `WM_NCHITTEST`: tepi 5 px untuk resize, title bar untuk seret (`HTCAPTION`), tombol maximize FlatLaf sebagai
     `HTMAXBUTTON` (Snap Layouts Windows 11). Komponen yang menerima mouse (menu, tombol, input, tooltip) tetap `HTCLIENT`;
   - sudut membulat Windows 11 (`DWMWA_WINDOW_CORNER_PREFERENCE`) dan bayangan (`DwmExtendFrameIntoClientArea`).
2. Karena window sejak awal tanpa dekorasi, **berpindah solid ↔ tembus cukup `Frame.setBackground`** (latar beralpha
   atau tidak). HWND tetap sama, sehingga tidak ada kedipan dan ikon taskbar tidak hilang. Saat solid, window tidak
   *layered*, jadi animasi docking/maximize tetap ada.
3. **Pudar bagian selain terminal**: `TranslucentRootPane` menjadi *painting origin* saat opasitas < 100%, menggambar isi
   window ke buffer, menempelnya dengan `AlphaComposite.Src` × alpha (mengganti piksel, bukan menumpuk), lalu menempel
   ulang area terminal dan popup ringan dengan alpha apa adanya.
4. **Latar terminal**: `TerminalTranslucency` (persen global + daftar panel yang tampil). `ZoomableTermWidget` mengisi
   latar dengan `AlphaComposite.Src` × alpha (gambar latar tema ikut), lalu teks digambar lewat `FillFilterGraphics2D`.
5. Saat transparansi aktif, `Toolkit.setDynamicLayout(false)`: tata letak disusun setelah resize selesai, karena window
   beralpha per piksel digambar ulang penuh setiap langkah resize.
6. **OS lain** (macOS): tidak ada bingkai sendiri. Window dibuat tanpa dekorasi hanya kalau transparansi aktif saat start;
   dari 100% ke bawahnya berlaku setelah restart. Perubahan di antara nilai < 100% tetap langsung.
7. Config: `windowOpacity` (30–100) kini berarti bagian selain terminal; field baru `terminalOpacity` (10–100). Config lama
   tanpa field = solid.

8. **Blur (tambahan 2026-11-02)**, dua opsi yang bisa dipakai bersama:
   - **Blur di belakang window** (`windowBlur`, opt-in, Windows saja): `SetWindowCompositionAttribute` dengan
     `ACCENT_ENABLE_BLURBEHIND`. Berbeda dengan backdrop DWM (batasan 3), accent ini *terbukti* mem-blur isi di belakang
     piksel tembus window layered milik Java (prototipe dengan screenshot `Robot`, teks di depan tetap tajam). Hanya
     menyala selama transparansi aktif; kekuatan blur diatur Windows. `ACCENT_ENABLE_ACRYLICBLURBEHIND` tidak dipakai
     karena dikenal patah-patah saat window diseret. Animasi maximize/restore/Snap DWM tetap menyala: selama animasi
     window tampil tembus tanpa blur dengan latar yang benar, lalu blur muncul di tempat barunya (diukur, 2026-11-03).
     Selama blur menyala, `TranslucentRootPane` menaruh lapisan warna latar tema (alpha 45%; di tema gelap warnanya
     dikali 0,5) di bawah isi window, supaya bagian yang tembus tidak tampak terang/abu-abu; blur tetap terlihat.
   - **Backdrop sistem acrylic dicoba lalu ditinggalkan (2026-11-02 s.d. 11-03).**
     `DWMWA_SYSTEMBACKDROP_TYPE = DWMSBT_TRANSIENTWINDOW` (Windows 11 22H2+) juga terlihat di piksel tembus window
     layered, tetapi (semua diukur dengan tangkapan layar beruntun):
     - selama animasi maximize/restore/Snap (~250 ms) DWM menampilkan potret backdrop dari ukuran/posisi lama yang
       diregangkan: blur salah tempat, terasa kaku seperti gambar tersisa;
     - mematikan animasi (`DWMWA_TRANSITIONS_FORCEDISABLED`) menghilangkan sisa gambar, tetapi window melompat dan
       berkedip (ditolak user);
     - melepas acrylic sebelum animasi lalu memasangnya lagi menyisakan satu frame (5–20 ms) potret lama saat acrylic
       dipasang kembali di posisi baru: kedip saat dock/undock;
     - acrylic yang dinyalakan selagi window tidak aktif (pratinjau slider dari dialog, mulai dari 100%) tampil
       abu-abu rata sampai maximize/restore; selama window tidak aktif backdrop sistem juga menampilkan warna solid.
     Accent tidak punya satu pun masalah ini, jadi blur kembali hanya memakai accent.
   - **Blur gambar latar terminal** (`imageBlur` di palet tema, 0..100): dihitung di Java saat gambar disesuaikan ke
     ukuran panel, jadi jalan di semua OS dan tanpa transparansi.

9. **Pindah monitor beda skala (tambahan 2026-11-02).**
   - Painter D3D milik JDK untuk window layered memakai ulang buffer berskala lama setelah window pindah ke monitor
     dengan skala lain (ukuran logisnya sama): isi window tampil berulang/bergaris. `TermULApp` mengeset
     `sun.java2d.twp.forcesw=true` sebelum AWT dimuat, sehingga dipakai painter software yang menghitung buffernya dari
     skala saat ini. Installer sudah tanpa D3D (`-Dsun.java2d.d3d=false`), jadi painter software memang sudah dipakai di
     sana; properti ini untuk cara jalan lain (IDE, JAR portable). Melepas lalu memasang lagi latar beralpha juga
     memperbaikinya, tetapi terlihat sebagai kedipan (window kosong lalu putih), jadi tidak dipakai.
   - Setelah skala berubah, gambar ulang pertama harus penuh: painter membuat buffer baru yang kosong, dan gambar
     sebagian (jam, animasi) membuat sisa window tampil kosong sesaat. `MainFrame.relayoutForScreen` (langsung di EDT
     saat `graphicsConfiguration` berubah) menandai semua komponen tidak valid dan seluruh root pane kotor. Penyusunan
     dan gambarnya dikerjakan RepaintManager dalam satu langkah, setelah semua komponen memakai skala baru (sekitar
     25–50 ms setelah `WM_DPICHANGED`). Diukur dengan tangkapan layar beruntun.
   - `TranslucentRootPane` menempel buffer dalam piksel perangkat yang dibulatkan keluar: di skala pecahan (125%) tepi
     area yang digambar ulang sebelumnya tertinggal transparan dan tampak sebagai garis tipis.

10. **Teks tetap solid (tambahan 2026-11-02, opt-in `windowSolidText`).** Slider Jendela memudarkan teks juga
    (keputusan 3), sehingga di 85% teks tampak lebih redup daripada saat solid (diukur: 191 vs 221). Opsi ini membuat
    hanya latarnya yang tembus, seperti Windows Terminal: area yang digambar ulang digambar sekali lagi lewat
    `TextMaskGraphics2D` (hanya `drawString`/`drawGlyphVector`, putih pekat) menjadi mask, lalu alpha tiap piksel =
    max(alpha jendela, cakupan teks). Ikon, garis, dan teks yang sudah berupa gambar tetap ikut pudar. Biaya: area
    yang digambar ulang digambar dua kali selama opsi aktif.
    Perbaikan (2026-11-03): rumus `warna × max(alpha jendela, cakupan)` ikut memudarkan bagian teks di tepi huruf
    (cakupan 50% hanya menyisakan 25% teks), sehingga huruf tampak tipis dan bergerigi dibanding saat solid. Sekarang
    area itu digambar sekali lagi tanpa teks (`TextMaskGraphics2D.withoutText`) sebagai latar di bawah teks, dan yang
    dipudarkan hanya bagian latar: `p − (1 − alpha) × (1 − cakupan) × latar`, alpha `alpha + (1 − alpha) × cakupan`.
    Warna subpiksel ClearType di tepi huruf tetap (diukur: piksel ClearType 665 → 747, solid 948). Biaya: digambar
    sekali lagi selama opsi aktif dan bagian selain terminal dipudarkan.

11. **Teks tetap ClearType saat transparan (tambahan 2026-11-02).** Java hanya menggambar teks LCD (subpiksel) ke
    permukaan pekat; di buffer beralpha teks jatuh ke antialiasing abu-abu dan tampak berpiksel dibanding saat solid.
    Selama window beralpha per piksel, `TranslucentRootPane` selalu menjadi painting origin dan menggambar area yang
    sama dua kali: ke buffer beralpha (sumber alpha) dan ke buffer pekat berlatar hitam (sumber warna, teks ClearType).
    Piksel pekat di bagian selain terminal memakai warna buffer pekat apa adanya; di terminal dan popup, warnanya
    dikalikan alpha (latar terminal digambar dengan `Src` beralpha, yang di buffer pekat tersimpan sebagai warna penuh).
    Teks terminal sendiri tetap abu-abu, karena JediTerm selalu memakai antialiasing abu-abu, juga saat solid.
    Biaya: setiap gambar ulang dikerjakan dua kali selama transparan aktif.

## Konsekuensi
- (+) Dua slider transparansi, berlaku langsung tanpa restart dan tanpa kedipan, dengan Snap dan animasi docking tetap
  berfungsi (diuji manual oleh user).
- (+) Window solid berperilaku seperti sebelumnya (title bar FlatLaf yang sama, Snap, maximize pas layar).
- (−) Window utama di Windows bergantung pada subclass window procedure lewat JNA. Kalau JNA gagal dimuat, window tetap
  jalan sebagai window tanpa dekorasi biasa (tanpa Snap), dan error dicatat di log.
- (−) Saat transparansi aktif, window *layered*: resize dan docking kurang mulus (seluruh window digambar ulang lewat CPU),
  dan CPU sedikit lebih tinggi saat output terminal deras. Karena itu getar layar (BEL) di window transparan menggeser
  satu tangkapan isi window, bukan menggambar ulang seluruh window di tiap langkah (diukur 28–41 ms per langkah,
  sementara timer getar 16 ms, sehingga getarannya patah-patah; dengan tangkapan 2–7 ms).
- (−) Bagian title bar yang menerima mouse dikenali dari tipe dan listener-nya. Komponen baru di title bar yang bisa diklik
  tetapi tanpa listener perlu client property `JComponent.titleBarCaption = false`.
- (−) macOS belum diuji. Multi-monitor beda DPI di Windows (100% ↔ 125%) diuji 2026-11-02 setelah keputusan 9.
- (−) Blur di belakang window memakai API Windows yang tidak terdokumentasi: perilakunya bisa berubah di update Windows.
  Kalau gagal, error dicatat sekali di log dan transparansi tetap jalan tanpa blur.

# Plan rilis: Floating button

Floating button (tombol mengambang) di atas beranda, terminal, dan panel SFTP, dirilis bertahap dalam tiga rilis.
Desain awal dan contoh interaktifnya ada di artifact *Floating Button TermUL*. File ini mencatat apa yang sudah dan
belum selesai.

**Status:** ✅ Rilis A, B, dan C selesai · terakhir diperbarui 2026-10-13

## Prinsip yang berlaku untuk semua tombol

- **Semua tema.** Warna, border, sudut, dan bayangan dibaca dari tema yang aktif (`FloatingButton.*` di
  `UIManager`). Tema Neobrutalism mengisi gaya border tebal dan bayangan tegas; tema lain memakai turunan warna aksen.
- **Tidak mengambil fokus.** Tombol tidak bisa difokus (`setFocusable(false)`), supaya ketikan dan shortcut terminal
  tidak pernah berpindah ke tombol.
- **Muncul saat dibutuhkan.** Tombol di area terminal hanya tampil pada kondisi tertentu (sesi putus, sedang
  membaca history), supaya tidak menutupi output.
- **Keamanan tidak berubah.** Tombol hanya memanggil aksi yang sudah ada (reconnect, inject sudo manual, copy), dengan
  guard yang sama seperti menu dan hotkey.

## Rilis A: komponen dasar + tombol yang paling berguna

| | Item | Detail |
|---|---|---|
| ✅ | Komponen `FloatingButton` | Bulat atau pil, bayangan tegas digambar sendiri, key `FloatingButton.*` per tema, label bisa menyusut (animasi) |
| ✅ | Komponen `FloatingOverlay` | Panel yang menumpuk tombol di atas konten (beranda, terminal) di sudut/tengah bawah |
| ✅ | Gaya per tema | Nilai `FloatingButton.*` di 3 tema Neobrutalism + Flat Illustration; tema lain memakai nilai turunan |
| ✅ | #1 FAB **Host baru** (beranda) | Pojok kanan bawah beranda, sama dengan File → Host baru |
| ✅ | #2 Extended FAB **Sambung ulang** (terminal) | Muncul saat sesi berakhir/putus; label menyusut saat Anda scroll ke atas membaca history |
| ✅ | #3 Mini FAB **Ke bawah** (terminal) | Muncul saat Anda scroll ke atas; menampilkan jumlah baris baru, klik untuk lompat ke baris terakhir |
| ✅ | Unit test | `FloatingStyleTest`, `FloatingOverlayTest`, `NewLinesCounterTest`; ikon baru `PLUS` dan `ARROW_DOWN` di kelima set ikon |
| ✅ | Rilis | [v0.1.168](https://github.com/egateza/TermUL/releases/tag/v0.1.168) |

## Rilis B: status pekerjaan + aksi terminal

| | Item | Detail |
|---|---|---|
| ✅ | #5 FAB + badge **Transfer** (SFTP) | Jumlah transfer berjalan + antre di atas daftar file; klik menampilkan daftar dan progresnya (diperbarui langsung) |
| ✅ | #6 Status **Upload remote edit** | Tombol status di kanan bawah terminal. Mengunggah/berhasil hilang sendiri; konflik (kuning) dan gagal (merah) tetap tampil sampai diklik, lalu membuka daftar file yang diedit |
| ✅ | #7 Floating toolbar **Cari / Salin / Sudo** (terminal) | Cari + Salin saat ada seleksi; Password sudo/root saat baris kursor berupa prompt password. Inject manual yang sama dengan Ctrl+Shift+P |
| ✅ | Unit test | `TransferListTest`, `EditStatusFloatTest`, `PromptResponderTest.passwordPrompt…`, gaya peringatan/gagal; ikon baru `SEARCH` dan `COPY` |
| ✅ | Rilis | [v0.1.169](https://github.com/egateza/TermUL/releases/tag/v0.1.169) |

## Rilis C: snippet

Keputusan (2026-10-13): snippet berupa teks tetap tanpa variabel, diketik ke prompt **tanpa Enter**, berlaku global
atau per grup host (termasuk subgrup), dan speed dial tidak dibuat.

| | Item | Detail |
|---|---|---|
| ➖ | #4 Speed dial **Buat baru** | Dilewati: isinya sama dengan menu, dan beranda sudah punya tombol **+** Host baru |
| ✅ | Fitur **Snippet** | `snippets.json` di config. Satu baris tanpa karakter kontrol (Enter/Tab/ESC ditolak), jadi tidak pernah berjalan sendiri. Kelola lewat **Terminal → Snippet…** |
| ✅ | #8 Draggable FAB **Snippet cepat** | Tombol petir di atas terminal; seret ke sudut mana pun (disimpan di `config.json` untuk semua tab). Bisa disembunyikan lewat **Pengaturan → Tampilkan tombol snippet** |
| ✅ | Unit test | `SnippetTest`, `SnippetStoreTest`, `AppConfigSnippetTest`, `SnippetButtonTest`, sudut & seret di `FloatingOverlayTest`; ikon baru `BOLT` |
| ✅ | Rilis | [v0.1.171](https://github.com/egateza/TermUL/releases/tag/v0.1.171) |

**Catatan:** snippet yang terikat ke grup tidak ikut berpindah kalau grupnya di-rename; ubah grupnya lewat dialog
snippet.

## Riwayat

| Tanggal | Rilis | Versi | Catatan |
|---|---|---|---|
| 2026-10-13 | — | — | Plan dibuat |
| 2026-10-13 | A | 0.1.168 | Host baru, Sambung ulang, Ke bawah |
| 2026-10-13 | B | 0.1.169 | Badge transfer, status remote edit, toolbar Cari/Salin/Sudo |
| 2026-10-13 | C | 0.1.171 | Snippet + tombol snippet yang bisa diseret |

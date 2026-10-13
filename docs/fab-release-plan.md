# Plan rilis: Floating button

Floating button (tombol mengambang) di atas beranda, terminal, dan panel SFTP, dirilis bertahap dalam tiga rilis.
Desain awal dan contoh interaktifnya ada di artifact *Floating Button TermUL*. File ini mencatat apa yang sudah dan
belum selesai.

**Status:** 🟡 berjalan · terakhir diperbarui 2026-10-13

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
| ⬜ | Rilis | v0.1.168 |

## Rilis B: status pekerjaan + aksi terminal

| | Item | Detail |
|---|---|---|
| ⬜ | #5 FAB + badge **Transfer** (SFTP) | Jumlah transfer berjalan; klik menampilkan daftar dan progresnya |
| ⬜ | #6 Status **Upload remote edit** | Tombol status (mengunggah → berhasil → konflik/gagal) alih-alih cincin progres, karena file config biasanya kecil |
| ⬜ | #7 Floating toolbar **Cari / Salin / Sudo** (terminal) | Muncul saat ada seleksi teks atau prompt password; Sudo memakai inject manual yang sama dengan Ctrl+Shift+P |
| ⬜ | Unit test | — |
| ⬜ | Rilis | — |

## Rilis C: menunggu keputusan

| | Item | Detail |
|---|---|---|
| ⬜ | #4 Speed dial **Buat baru** | Sesi SSH / tab SFTP / distro WSL. Nilainya kecil di desktop karena sama dengan menu |
| ⬜ | Fitur **Snippet** | Perlu diputuskan dulu: tempat penyimpanan, boleh berisi variabel atau tidak, kirim dengan atau tanpa Enter |
| ⬜ | #8 Draggable FAB **Snippet cepat** | Bergantung pada fitur Snippet; posisi sudut disimpan di `config.json` |
| ⬜ | Rilis | — |

## Riwayat

| Tanggal | Rilis | Versi | Catatan |
|---|---|---|---|
| 2026-10-13 | — | — | Plan dibuat |

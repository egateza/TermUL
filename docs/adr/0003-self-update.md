# ADR 0003: Update aplikasi lewat GitHub Releases: delta per jar, manifest bertanda tangan Ed25519

- Status: Diterima
- Tanggal: 2026-10-05

## Konteks
Selama ini setiap versi baru dibagikan sebagai zip/app-image penuh (~120 MB). Isinya: `runtime/` hasil jlink 81 MB, library pihak ketiga ~20 MB, dan jar TermUL sendiri ~750 KB. Hampir semua update hanya mengubah jar TermUL. Pengguna lain perlu cara memperbarui aplikasi lewat menu, tanpa mengunduh dan mengekstrak ulang semuanya.

Batasan:
- Mekanisme update adalah jalur untuk menjalankan kode baru di mesin yang memegang credential server produksi. Integritas dan keaslian update **wajib** diverifikasi.
- Hasil instal `.msi` berada di `Program Files` (butuh admin untuk ditulis), dan di Windows jar yang sedang dipakai terkunci.
- Repo `egateza/TermUL` publik, jadi rilis bisa diunduh tanpa token.

## Keputusan
1. **Hosting di GitHub Releases.** Setiap rilis (tag `v<versi>`, mis. `v0.1.130`) memuat aset `manifest.json`, `manifest.json.sig`, semua jar aplikasi (flat), dan opsional installer `.msi` / zip portable untuk instalasi baru. Aplikasi mengambil `releases/latest/download/manifest.json`, lalu `.sig` dan jar dari `releases/download/v<versi>/` (bukan "latest", supaya tidak tercampur kalau ada rilis baru di tengah proses).
2. **Manifest** (JSON): `format`, `version`, `generation`, `mainClass`, `notes`, dan `files[]` berisi `name`, `sha256`, `size`. Aplikasi hanya mengunduh jar yang hash-nya tidak ada di lokal. Jar dengan hash sama disalin dari folder instalasi atau dari versi update yang sedang berjalan. Update biasa cukup ~750 KB.
3. **Tanda tangan Ed25519** atas byte persis `manifest.json`. Public key ditanam di kode (`UpdateKeys`). Private key hanya ada di mesin rilis (`~/.termul-release/update-signing.key`, di luar repo). Manifest dengan tanda tangan tidak valid ditolak. Setiap jar diverifikasi ukuran dan SHA-256-nya.
4. **Tidak ada downgrade.** Update hanya dipasang kalau versinya lebih tinggi dari versi yang berjalan. Saat start, folder update dipakai hanya kalau versinya lebih tinggi dari versi bawaan installer (jadi memasang `.msi` yang lebih baru otomatis mengalahkan update lama).
5. **File instalasi tidak pernah ditimpa.** Update dipasang ke `<cacheDir>/updates/<versi>/` (Windows `%LOCALAPPDATA%\TermUL\updates`), ditulis ke folder staging lalu di-rename secara atomic. Pointer `updates/current` ditulis secara atomic. Folder versi yang sedang berjalan tidak dihapus.
6. **Bootstrap** (`dev.egateza.termul.update.Bootstrap`) menjadi main class app-image dan fat jar. Ia berjalan dari jar bawaan (system classloader), lalu:
   - memverifikasi ulang tanda tangan manifest dan SHA-256 semua jar di folder update **setiap start** (public key dari kode bawaan, bukan dari update),
   - memuat jar update dengan `URLClassLoader` ber-parent **platform classloader** (bukan system), supaya class lama di classpath bawaan tidak ikut termuat,
   - kalau tidak ada update yang valid, menjalankan `TermULApp` bawaan secara langsung, persis seperti sebelum fitur ini ada.
   Bootstrap tidak boleh memakai SLF4J/Logback, JNA, atau AWT, karena library itu harus pertama kali dimuat oleh classloader aplikasi. Catatan bootstrap diteruskan lewat system property dan di-log oleh aplikasi.
7. **Rollback otomatis.** Bootstrap mencatat percobaan start per versi. Aplikasi menandai versi "sehat" setelah window utama tampil. Kalau sebuah versi gagal mencapai itu 3 kali berturut-turut, bootstrap kembali ke versi bawaan.
8. **Generation.** `UpdateProtocol.GENERATION` dinaikkan setiap kali runtime jlink (modul JDK, versi JDK) atau kode Bootstrap berubah. Rilis dengan `generation` lebih tinggi dari bootstrap yang terpasang tidak bisa dipasang lewat menu. Pengguna diarahkan ke halaman rilis untuk installer baru.
9. **Pemasangan selalu manual.** Pengecekan otomatis (revisi 2026-10-05) berjalan 20 detik setelah start lalu setiap 12 jam, memverifikasi tanda tangan manifest, dan hanya menampilkan **badge** "Update x.y.z" di menu bar. Tidak ada yang diunduh atau dipasang tanpa klik user. Setelah dipasang, badge menjadi tombol "Restart untuk update", karena jar baru baru dimuat saat JVM start ulang. Opsi Pengaturan → Periksa update otomatis (default aktif) mematikan pengecekan. Dijalankan dari IDE (versi `dev`) tidak ada badge, dan tanpa Bootstrap dialog hanya menampilkan versi terbaru dan tautan halaman rilis.

## Konsekuensi
- (+) Update biasa ~750 KB, dan tidak butuh hak admin walau terpasang di `Program Files`.
- (+) Update yang dimodifikasi (di GitHub, jaringan, atau disk lokal) ditolak, karena butuh private key.
- (+) Update rusak tidak membuat aplikasi mati permanen (rollback ke versi bawaan setelah 3 kali gagal).
- (−) Malware yang berjalan sebagai user yang sama tetap bisa mengganggu folder update. Ini **tidak** memperburuk threat model: malware seperti itu sudah bisa membuka DPAPI dan keylog (lihat ADR 0002). Verifikasi setiap start membuat modifikasi seperti itu terdeteksi, bukan dijalankan.
- (−) Private key adalah aset kritis. Kalau bocor, siapa pun bisa membuat update yang diterima semua instalasi. Kalau hilang, update via menu berhenti dan rotasi key butuh installer baru (generation naik). Simpan cadangan offline, dan jangan pernah commit.
- (−) Versi = `major.minor.<jumlah commit>`, jadi rilis harus dibuat dari branch utama yang sudah di-commit (ReleaseTool menolak build `-dirty`). Rilis dari branch dengan commit lebih sedikit akan terlihat sebagai downgrade dan ditolak.
- (−) Verifikasi SHA-256 ~21 MB jar menambah sekitar puluhan milidetik ke waktu start saat berjalan dari folder update.
- Fitur ini butuh modul `java.net.http` di runtime jlink. Instalasi lama (tanpa Bootstrap) perlu sekali pasang installer baru.

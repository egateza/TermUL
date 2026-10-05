# Menerbitkan Rilis Baru

Panduan untuk menerbitkan versi baru TermUL ke [GitHub Releases](https://github.com/egateza/TermUL/releases), supaya
pengguna bisa memperbarui lewat **Bantuan → Periksa update**. Desain dan alasan keamanannya ada di
[`adr/0003-self-update.md`](adr/0003-self-update.md).

## Ringkasan

```
commit + push ke master  →  tulis notes.txt  →  .\tools\release.ps1 -NotesFile notes.txt  →  cek di GitHub
```

Skrip `tools/release.ps1` menjalankan semuanya. Ia build aplikasi, membuat dan menandatangani `manifest.json`, lalu
menerbitkan rilis `v<versi>` beserta semua aset. Pengguna yang sudah memasang v0.1.127 atau lebih baru melihat versi
baru lewat menu. Mulai versi dengan badge update, aplikasi juga memeriksa otomatis (20 detik setelah start, lalu setiap
12 jam) dan menampilkan badge **Update x.y.z** di menu bar. Memasang tetap manual. Biasanya yang diunduh hanya jar
TermUL yang berubah (ratusan KB).

## Persiapan sekali per mesin

| Kebutuhan | Cara cek / pasang |
|---|---|
| JDK 25 di `PATH` | `java -version` → `25.x` |
| GitHub CLI | `gh --version`. Kalau belum ada: `winget install GitHub.cli` (buka terminal baru setelahnya) |
| Login GitHub | `gh auth status` → *Logged in to github.com account egateza*. Kalau belum: `gh auth login` (GitHub.com → HTTPS → browser) |
| Private key penandatangan | File `%USERPROFILE%\.termul-release\update-signing.key` ada (lihat [Private key](#private-key)) |
| WiX Toolset (opsional) | Hanya untuk `-Msi`. Tanpa WiX, rilis tetap berisi zip app-image Windows |

## Langkah rilis

### 1. Siapkan kode di `master`

```powershell
git checkout master
git pull
.\mvnw.cmd verify          # semua test lulus (integration test butuh Docker)
git status                 # harus bersih
git push
```

Rilis **wajib** dari commit yang sudah di-commit dan di-push. Skrip menolak working tree yang belum bersih dan commit
yang belum ada di remote.

### 2. Tulis catatan rilis

Buat file teks di luar repo, atau di dalam repo tapi jangan di-commit, misalnya `C:\tmp\notes.txt`:

```text
- Perbaikan: koneksi lewat jump host tidak lagi menggantung saat tujuan mati.
- Baru: tombol salin path di panel SFTP.
```

Isi file ini tampil di dialog **Periksa update** dan di halaman rilis GitHub. Isinya ikut ditandatangani, jadi tidak
bisa diubah setelah rilis terbit. Tulis untuk pengguna: apa yang berubah dan apa yang perlu mereka lakukan, kalau ada.

### 3. Jalankan skrip rilis

```powershell
.\tools\release.ps1 -NotesFile C:\tmp\notes.txt
```

| Opsi | Fungsi |
|---|---|
| `-Draft` | Rilis dibuat sebagai draft: belum terlihat oleh aplikasi sampai Anda klik **Publish release** di GitHub. Dianjurkan kalau ingin memeriksa aset dulu |
| `-Msi` | Membuat installer `.msi` (butuh WiX). Dengan opsi ini zip app-image Windows tidak dibuat |
| `-AllowDirty` | Hanya untuk uji. Mengizinkan working tree yang belum di-commit. **Jangan dipakai untuk rilis sungguhan** |

Hasil yang benar di akhir output:

```
Rilis 0.1.130: 27 file, 20,4 MB, generation 1
version=0.1.130
Menerbitkan v0.1.130 (31 aset) dari commit <hash>
https://github.com/egateza/TermUL/releases/tag/v0.1.130
Selesai: v0.1.130
```

### 4. Periksa hasilnya

1. Buka halaman rilis dari output. Pastikan ada aset `manifest.json`, `manifest.json.sig`, semua `*.jar`, dan zip
   instalasi.
2. Pastikan rilis ini yang ditandai **Latest**:
   ```powershell
   curl.exe -sIL https://github.com/egateza/TermUL/releases/latest/download/manifest.json | Select-String "^Location"
   ```
   URL pertama harus mengarah ke `.../download/v<versi baru>/manifest.json`.
3. Dari TermUL versi sebelumnya: **Bantuan → Periksa update** harus menampilkan versi baru, lalu
   **Unduh & pasang** → **Restart sekarang**. Setelah restart, **Bantuan → Tentang TermUL** menunjukkan versi baru.

## Aset yang diterbitkan

| Aset | Untuk |
|---|---|
| `manifest.json`, `manifest.json.sig` | Update lewat menu: daftar jar + hash, ditandatangani Ed25519 |
| `termul-*.jar` dan library (`*.jar`) | Update lewat menu: diunduh hanya yang hash-nya berubah |
| `TermUL-<versi>-windows.zip` | Instalasi baru di Windows: ekstrak, jalankan `TermUL\TermUL.exe` (runtime Java sudah termasuk) |
| `TermUL-<versi>-jar.zip` | Instalasi baru di macOS/Linux: butuh JDK 25 terpasang |
| `TermUL-<versi>.msi` | Instalasi baru di Windows lewat installer (hanya dengan `-Msi`) |

## Penomoran versi

Versi = `major.minor.<jumlah commit>`, misalnya `0.1.130`. `major.minor` diatur di property `termul.versionBase` di
`app/pom.xml`, dan jumlah commit dihitung otomatis dari git.

- Setiap rilis butuh minimal satu commit baru sejak rilis sebelumnya. Tanpa itu versinya sama dan tag-nya sudah ada.
- Rilis dari branch selain `master` bisa menghasilkan angka yang **lebih kecil** dari rilis terakhir. Aplikasi menolak
  memasangnya karena terlihat sebagai downgrade.
- Menaikkan `termul.versionBase` (misalnya ke `0.2`) membuat versi berikutnya `0.2.<n>`, selalu lebih baru dari `0.1.x`.
- Jangan menulis ulang riwayat `master` (rebase, squash) setelah rilis, karena jumlah commit bisa turun.

## Kapan pengguna butuh installer baru

Update lewat menu hanya mengganti jar. Rilis berikut **tidak bisa** dipasang lewat menu, jadi pengguna harus mengunduh
zip atau `.msi` baru:

- daftar modul JDK di `jpackage.modules` (`app/pom.xml`) berubah, misalnya library baru butuh modul JDK lain,
- versi JDK runtime naik,
- kode `update.Bootstrap` (atau class yang dipanggilnya saat start) berubah,
- public key penandatangan diganti.

Untuk perubahan seperti itu, **naikkan `UpdateProtocol.GENERATION`**
(`update/src/main/java/dev/egateza/termul/update/UpdateProtocol.java`) di commit yang sama. Instalasi lama akan
menampilkan *"butuh installer baru"* beserta tombol ke halaman rilis, bukan memasang update yang tidak cocok. Tulis juga
di catatan rilis bahwa installer baru diperlukan.

Menambah atau menaikkan versi library biasa **tidak** butuh installer baru, selama modul JDK yang dipakai tetap sama.
Cek dengan perintah `jdeps` di [`SETUP.md`](SETUP.md#distribusi-profile-package-win).

## Menarik rilis yang bermasalah

```powershell
gh release delete v0.1.130 --yes --cleanup-tag
```

- Setelah dihapus, **Latest** kembali ke rilis sebelumnya, jadi pengguna yang belum update tidak akan ditawari versi itu.
- Pengguna yang **sudah** memasang versi itu tetap menjalankannya, karena aplikasi tidak pernah downgrade lewat menu.
  Perbaikannya: commit fix lalu terbitkan versi yang lebih tinggi.
- Kalau versi itu crash sebelum window utama tampil, aplikasi otomatis kembali ke versi bawaan installer setelah 3 kali
  gagal start.
- Jangan menerbitkan ulang nomor versi yang sama dengan isi berbeda.

## Private key

Lokasi: `%USERPROFILE%\.termul-release\update-signing.key`. Public key pasangannya ada di
`update/src/main/java/dev/egateza/termul/update/UpdateKeys.java`.

- **Cadangkan secara offline** (flash disk terenkripsi, password manager). Kalau file ini hilang, rilis baru tidak bisa
  ditandatangani, dan semua instalasi yang ada tidak bisa update lewat menu lagi.
- **Jangan pernah di-commit** atau dikirim lewat chat/email. Siapa pun yang memegangnya bisa membuat update yang diterima
  semua instalasi.
- Merilis dari mesin lain: salin file key ke lokasi yang sama, atau set environment variable
  `TERMUL_UPDATE_KEY=<path file key>`.
- Kalau key bocor atau hilang:
  ```powershell
  java -cp "app\target\jpackage-input\libs\*" dev.egateza.termul.update.release.ReleaseTool keygen
  ```
  Perintah ini menolak menimpa key lama (pindahkan dulu file lama). Salin public key baru ke `UpdateKeys.PUBLIC_KEY`,
  naikkan `UpdateProtocol.GENERATION`, lalu terbitkan rilis. Semua pengguna perlu memasang installer baru.

## Troubleshooting

| Pesan | Penyebab dan solusi |
|---|---|
| `Working tree belum bersih` | Ada perubahan yang belum di-commit. Commit atau `git stash`, lalu ulangi |
| `Commit ... belum ada di remote` | Jalankan `git push` dulu |
| `GitHub CLI (gh) belum terpasang` | `winget install GitHub.cli`, buka terminal baru, lalu `gh auth login` |
| `Private key tidak ada` | File key tidak ditemukan. Salin dari cadangan atau set `TERMUL_UPDATE_KEY` |
| `Private key tidak cocok dengan UpdateKeys.PUBLIC_KEY` | Key yang dipakai bukan pasangan public key di kode. Pakai key yang benar; jangan ganti `UpdateKeys` kecuali sedang rotasi key |
| `Build dari working tree yang belum di-commit (...-dirty)` | Sama dengan poin pertama. Jar membawa penanda `-dirty` |
| `bukan build rilis (versi: ${...})` | Build tidak lewat Maven. Jalankan skrip dari root project |
| `gh release create gagal`: *tag already exists* | Versi ini sudah dirilis. Buat commit baru (versi naik), lalu ulangi |
| Pengguna melihat *"sudah versi terbaru"* padahal rilis baru ada | Rilis masih draft, atau versinya tidak lebih tinggi dari versi pengguna (lihat [Penomoran versi](#penomoran-versi)) |
| Pengguna melihat *"belum mendukung update lewat menu"* | Instalasinya dari sebelum v0.1.127. Minta pasang zip/msi terbaru sekali |

## Uji lokal tanpa menerbitkan

Untuk menguji perubahan pada mekanisme update tanpa menyentuh GitHub, pakai data uji terpisah:

1. `.\mvnw.cmd -Ppackage-win -DskipTests package`
2. Buat rilis palsu:
   ```powershell
   java -cp "app\target\jpackage-input\libs\*" dev.egateza.termul.update.release.ReleaseTool publish `
       --input app\target\jpackage-input --out C:\tmp\rel --allow-dirty
   ```
   Versinya harus lebih tinggi dari versi bawaan app-image. Ubah `version` di `build.properties` dalam jar
   `termul-app` sebelum langkah ini.
3. Salin `C:\tmp\rel` ke `C:\tmp\termul-home\cache\updates\<versi>\`, lalu tulis `<versi>` ke
   `C:\tmp\termul-home\cache\updates\current`.
4. Jalankan dengan runtime app-image:
   ```powershell
   $d = "app\target\dist\TermUL"
   & "$d\runtime\bin\java.exe" --enable-native-access=ALL-UNNAMED "-Dtermul.home=C:\tmp\termul-home" `
       -cp "$d\app\termul-app-0.1.0-SNAPSHOT.jar;$d\app\libs\*" dev.egateza.termul.update.Bootstrap
   ```
   Log (`C:\tmp\termul-home\config\logs`) harus berisi `Bootstrap: Menjalankan update <versi>`.

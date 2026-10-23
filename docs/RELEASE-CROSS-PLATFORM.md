# Rilis Windows + macOS dalam Satu Tag

Panduan menerbitkan satu rilis GitHub (`v<versi>`) yang berisi aset untuk Windows **dan** macOS, dari dua mesin. Dasar
rilisnya (persiapan, catatan rilis, aset, penomoran versi) ada di [`RELEASE.md`](RELEASE.md).

## Prinsip

1. **Satu tag, dibuat sekali.** Mesin pertama membuat rilis sebagai **draft** dan menandatangani manifest. Mesin kedua
   hanya **melampirkan** aset untuk OS-nya ke draft yang sama, lalu draft dipublikasikan.
2. **Satu commit.** Versi = jumlah commit, jadi kedua mesin wajib build dari commit yang sama. Kedua skrip menolak
   melampirkan aset kalau `HEAD` bukan commit rilis.
3. **Satu private key, satu tanda tangan.** Hanya mesin pertama yang memakai `update-signing.key`. Mesin kedua tidak
   butuh key dan tidak menyentuh `manifest.json`.
4. **Draft dulu.** Aplikasi pengguna tidak melihat draft. Rilis setengah jadi tidak pernah terunduh.

| Urutan | Mesin pertama (draft + tanda tangan, butuh key) | Mesin kedua (aset saja, tanpa key) |
|---|---|---|
| Windows → Mac | `.\tools\release.ps1 -NotesFile <file> -Draft` | `tools/release-mac.sh --dmg-only` |
| Mac → Windows | `tools/release-mac.sh --notes <file> --draft` | `.\tools\release.ps1 -AssetsOnly` |

## Aset per mesin

| Aset | Windows → Mac | Mac → Windows |
|---|---|---|
| jar + `manifest.json` + `manifest.json.sig` (update lewat menu, semua OS) | Windows | Mac |
| `TermUL-<versi>-jar.zip` (JAR portable) | Windows | Mac |
| `TermUL-<versi>-windows.zip` (app-image portable Windows) | Windows | Windows (`-AssetsOnly`) |
| `TermUL-<versi>.msi` (opsional, `-Msi`, butuh WiX) | Windows | Windows (`-AssetsOnly -Msi`) |
| `TermUL-<versi>-macos.dmg` | Mac (`--dmg-only`) | Mac |

Jar sama persis di Windows dan Mac (dependensi tidak bergantung OS, build reproducible), jadi manifest dari mesin mana
pun berlaku untuk pengguna di kedua OS. Yang hanya bisa dibuat di OS-nya sendiri adalah paket `jpackage`: zip/msi
Windows dan DMG.

## Alur A: Windows dulu, lalu Mac

Disarankan sebagai alur utama: zip/msi Windows dibuat di mesin yang sama dengan manifest.

```powershell
# 1. PC Windows (PowerShell, jangan pakai 2>&1)
git checkout master; git pull
git status                                   # harus bersih
.\tools\release.ps1 -NotesFile docs\release-notes\v0.2.md -Draft
# di akhir output: "Untuk DMG macOS ...: git checkout <commit> lalu tools/release-mac.sh --dmg-only"
```

```bash
# 2. MacBook
git checkout master && git pull
git log --oneline -1                          # harus commit yang disebut skrip Windows
tools/release-mac.sh --dmg-only               # DMG masuk ke draft
```

3. Periksa draft (lihat [Sebelum publish](#sebelum-publish)), lalu **Publish release** di GitHub, atau:

```bash
gh release edit v<versi> --draft=false --latest
```

## Alur B: Mac dulu, lalu Windows

```bash
# 1. MacBook (key di ~/.termul-release/update-signing.key, mode 600)
git checkout master && git pull
tools/release-mac.sh --notes docs/release-notes/v0.2.md --draft
# di akhir output: "Zip Windows untuk rilis yang sama: git checkout <commit> lalu .\tools\release.ps1 -AssetsOnly"
```

```powershell
# 2. PC Windows
git checkout master; git pull
git log --oneline -1                          # harus commit yang disebut skrip Mac
.\tools\release.ps1 -AssetsOnly               # zip Windows masuk ke draft (tambah -Msi untuk installer)
```

3. Periksa draft, lalu publish (sama dengan alur A).

Di alur ini **jangan** menjalankan `release.ps1` tanpa `-AssetsOnly` di Windows: skrip akan menolak karena tag sudah
ada, supaya manifest tidak dibuat dua kali.

## Pengecualian: rilis yang butuh installer baru

Kalau rilis menaikkan `UpdateProtocol.GENERATION` (lihat "Kapan pengguna butuh installer baru" di
[`RELEASE.md`](RELEASE.md)), pengguna Windows wajib memasang zip/msi baru. Rilis seperti itu **harus memakai alur A**.
`release-mac.sh` menolak menerbitkannya karena generation-nya lebih tinggi dari rilis Latest.

## Sebelum publish

- Halaman draft berisi semua aset di tabel atas, untuk urutan yang dipakai.
- `manifest.json` di draft berisi versi yang benar dan `generation` yang diharapkan.
- Setelah publish, rilis ini yang menjadi Latest:

```bash
curl -sIL https://github.com/egateza/TermUL/releases/latest/download/manifest.json | grep -i '^location'
# → .../download/v<versi>/manifest.json
```

## Melengkapi rilis yang sudah dipublikasikan

Kedua mode "aset saja" juga bekerja pada rilis yang sudah publik, misalnya rilis dari Mac yang belum punya zip Windows.
Checkout commit rilisnya, lalu jalankan `.\tools\release.ps1 -AssetsOnly` (Windows) atau
`tools/release-mac.sh --dmg-only` (Mac). Aset dengan nama sama ditimpa (`--clobber`); manifest dan tanda tangan tidak
berubah, jadi pengguna yang sudah update tidak terpengaruh.

## Pesan galat yang umum

| Pesan | Penyebab dan solusi |
|---|---|
| `Rilis vX (juga draft) belum ada` | Mesin pertama belum menerbitkan, atau versi build berbeda (commit lain). Cek `git log --oneline -1` di kedua mesin |
| `Rilis vX dibuat dari commit abc1234, sedangkan HEAD def5678` | Mesin kedua tidak di commit rilis. Jalankan `git checkout abc1234`, ulangi, lalu `git checkout master` |
| `Rilis vX sudah ada (mungkin dibuat dari Mac)` | Windows menjalankan rilis penuh untuk tag yang sudah dibuat. Pakai `-AssetsOnly` |
| `Generation naik ... Terbitkan rilis ini dengan tools/release.ps1` | Rilis butuh installer baru. Pakai alur A |
| `Private key bisa dibaca user lain (mode 644)` | `chmod 600 ~/.termul-release/update-signing.key` |
| `Private key tidak cocok dengan UpdateKeys.PUBLIC_KEY` | Key yang dipakai bukan key rilis. Salin key yang sama dari mesin rilis lewat jalur aman |
| `Working tree belum bersih` / `Commit ... belum ada di remote` | Commit dan push dulu. Kedua mesin harus memakai commit yang sudah ada di GitHub |

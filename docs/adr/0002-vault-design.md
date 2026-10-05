# ADR 0002: Desain vault: master password (Argon2id) + AES-256-GCM, opsi "ingat di PC ini" via DPAPI

- Status: Diterima
- Tanggal: 2026-09-30

## Konteks
Vault menyimpan password login, passphrase key, password sudo, dan password root untuk server produksi. `docs/SECURITY.md` memberi dua opsi (DPAPI saja, atau master password) dan merekomendasikan kombinasi.

## Keputusan
**Kombinasi**, dengan envelope encryption:

1. Saat vault dibuat, dibangkitkan **data key (DEK)** acak 256-bit.
2. **Master password → Argon2id** (default m=64 MiB, t=3, p=1, salt 16 byte) menghasilkan **KEK** 256-bit. KEK membungkus DEK dengan AES-256-GCM. AAD = header file (magic, versi, parameter Argon2, salt), sehingga header yang diubah terdeteksi.
3. Setiap secret dienkripsi dengan DEK memakai AES-256-GCM, nonce 12 byte acak per entry, **AAD = `profileId|secretType`**, supaya ciphertext tidak bisa dipindah antar entry.
4. **"Ingat di PC ini" (opsional)**: DEK dibungkus Windows DPAPI (scope CurrentUser, dengan entropy aplikasi) dan disimpan di `vault.bin.dpapi`. Kalau file ini ada dan bisa dibuka, vault ter-unlock tanpa master password. Mematikan opsi = file dihapus.
5. Ganti master password cukup membungkus ulang DEK (entry tidak perlu dienkripsi ulang).

Format `vault.bin` (big-endian):

```
"MYTV" | u8 version=1 | i32 memKiB | i32 iterations | i32 parallelism | salt[16]
       | wrapNonce[12] | i32 len | wrappedDek[len]            (AAD = header)
       | checkNonce[12] | i32 len | keyCheck[len]              (GCM(DEK, "MYTERM-VAULT-OK"), AAD = "key-check")
       | i32 count | { uuid[16] | u8 secretType | nonce[12] | i32 len | ciphertext[len] } * count
```

Blok key-check dipakai untuk memastikan DEK dari DPAPI memang milik vault ini. File ditulis secara atomic (temp lalu rename); key DPAPI disimpan di `vault.bin.dpapi`.

## Konsekuensi
- (+) Tanpa DPAPI: file vault yang dicuri tetap butuh master password (Argon2id memperlambat brute force).
- (+) Dengan DPAPI: nyaman di laptop kerja sendiri, tetap terikat ke akun Windows.
- (−) Dengan DPAPI aktif, malware yang berjalan sebagai user yang sama bisa membuka vault. Ini trade-off yang disadari dan opsi ini default **OFF**.
- (−) Lupa master password tanpa DPAPI = semua secret hilang (harus diisi ulang). Profil tetap aman karena tidak berisi secret.
  Menu **Vault → Lupa master password...** mengganti vault yang sedang dipakai dengan vault baru: file lama di-rename ke
  `vault.bin.forgotten-<waktu>` (ceklis backup, default aktif; tanpa backup = dihapus setelah konfirmasi kedua), key DPAPI
  dihapus, lalu langsung membuat master password baru. Tidak ada jalur recovery: backup hanya berguna kalau password lama teringat.
- DEK dan secret hasil dekripsi hanya berada di memory sebagai `byte[]`/`char[]` dan di-zero saat lock atau setelah dipakai.

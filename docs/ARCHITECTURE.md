# Arsitektur

## Gambaran komponen

```
┌──────────────────────────── app (Swing + FlatLaf) ────────────────────────────┐
│  HostTreePanel   │   TerminalTabs (JediTerm)      │   SftpPanel / EditTracker  │
└────────┬─────────┴───────────────┬────────────────┴─────────────┬─────────────┘
         │                         │                              │
   ProfileStore (core)     TerminalSession (terminal)     RemoteFileService (sftp)
         │                   ├─ SshTtyConnector             ├─ RemoteEditSession
         │                   └─ PromptResponder             └─ SudoWriter (exec)
         │                         │                              │
         │                 SessionManager (ssh) ◄─────────────────┘
         │                   ├─ SshClient (MINA, singleton)
         │                   ├─ ConnectionHandle (ref-counted per host)
         │                   ├─ HostKeyVerifier (known_hosts + TOFU)
         │                   └─ AuthProvider (key / agent / password)
         │                         │
   CredentialVault (vault) ◄───────┘
```

## Konsep utama

### ConnectionHandle (satu koneksi, banyak channel)
- `SessionManager.acquire(profile)` mengembalikan `ConnectionHandle`, yaitu `ClientSession` yang sudah terautentikasi dengan **reference count**.
- Pemakai (tab terminal, panel SFTP, remote edit) membuka channel sendiri: `ChannelShell`, `SftpClient`, `ChannelExec`.
- `release()` pada pemakai terakhir menutup session (dengan grace period ±30 detik, supaya reopen tab tidak perlu auth ulang).
- Koneksi putus memicu event `ConnectionLost` ke semua pemakai. UI menampilkan banner reconnect.

### TerminalSession
- `SshTtyConnector implements com.jediterm.terminal.TtyConnector`: `read()` dari stdout channel, `write()` ke stdin channel, `resize()` mengirim `window-change`.
- PTY request: `xterm-256color`, dengan ukuran awal dari JediTerm.
- Output channel dilewatkan ke `PromptResponder` (tap/tee) sebelum ke JediTerm. Responder tidak boleh memblokir stream.

### PromptResponder (auto sudo)
- Input: event Enter dari UI (baris kursor dari `TerminalTextBuffer`) dan chunk output dari reader thread.
- State: `armed`, `armedUntil`, `fired`, rolling tail buffer (≤ 512 char, ANSI di-strip).
- Output: bytes password + `\r` ke channel. Password diambil dari vault per kejadian, lalu di-zero.
- Detail guard ada di `SECURITY.md`.

### RemoteEditSession
State machine per file:

```
OPENING ─download+stat─► EDITING ─(file saved, debounce)─► UPLOADING ─ok─► EDITING
                             │                                 │
                             │                           conflict/err
                             │                                 ▼
                             │                          NEEDS_ATTENTION (dialog / retry)
                             └─ close (user) ─► CLOSED (cache dihapus setelah sinkron)
```

Upload normal (user punya akses tulis):
1. `stat` remote. Kalau `mtime/size` ≠ baseline, lempar `RemoteConflictException`.
2. Tulis ke `<dir>/.<name>.termul-<rand>.tmp`.
3. `setstat` mode = mode asal.
4. `posix-rename@openssh.com` (atomic replace). Fallback: rename biasa setelah remove.
5. Perbarui baseline (`stat` baru).

Upload file root (`SudoWriter`):
1. Upload ke `/tmp/termul-<rand>` (mode 600).
2. `ChannelExec`: `sudo -S -p '' install -m <mode> -o <uid> -g <gid> /tmp/termul-<rand> <target> && rm -f /tmp/termul-<rand>`. Password dikirim via stdin.
3. Opsional: jalankan validation hook. Kalau gagal, restore backup (`<target>.termul-bak`).

File watching:
- Satu `WatchService` untuk direktori cache (`%LOCALAPPDATA%\TermUL\edit\`), dengan thread watcher tunggal.
- Event dikumpulkan per file dan di-debounce 300–500 ms (editor melakukan atomic save: tulis temp lalu rename, sehingga muncul beberapa event).
- Bandingkan hash (SHA-256) konten lokal dengan hash terakhir yang di-upload supaya tidak upload tanpa perubahan.

## Threading model

| Thread | Tanggung jawab |
|---|---|
| EDT | Semua komponen Swing. Tidak ada I/O. |
| MINA NIO workers | I/O SSH internal (dikelola MINA) |
| `term-reader-<id>` | Loop baca stdout channel lalu JediTerm (JediTerm memiliki emulator thread sendiri yang memanggil `read()`) |
| `ssh-ops` executor (bounded) | Connect, auth, exec, operasi SFTP dari UI (mengembalikan `CompletableFuture`) |
| `edit-watcher` | WatchService + scheduler debounce |
| `transfer` executor | Upload/download besar, dengan progress callback ke EDT |

Aturan: callback ke UI selalu lewat `invokeLater`. `CompletableFuture` dari operasi SSH tidak boleh di-`join()` di EDT.

## Layout storage (Windows)

```
%APPDATA%\TermUL\
  config.json         preferensi (font, tema, editor mapping, hotkey)
  profiles.json       host tree + profil (TANPA secret)
  known_hosts         host key terverifikasi (format OpenSSH)
  vault.bin           secret terenkripsi
  logs\               log aplikasi (tanpa secret)
%LOCALAPPDATA%\TermUL\
  edit\<profileId>\<remote-path>   cache file yang sedang diedit
```

## Alur utama

1. **Connect**: double-click host, lalu `ssh-ops` menjalankan `acquire()`: TCP connect, verifikasi host key (dialog TOFU di EDT kalau host baru), dan auth (agent, key, lalu password dari vault/prompt). Setelah itu buka `ChannelShell` + PTY, pasang connector ke JediTerm, dan tab aktif.
2. **Sudo inject (hotkey)**: EDT → vault.get → write bytes ke channel (di `ssh-ops`) → zero buffer.
3. **Edit file**: double-click di SFTP panel, download ke cache + stat baseline, jalankan editor (`ProcessBuilder`), watcher mendeteksi perubahan, debounce, lalu upload (normal/sudo) dan status di EditTracker diperbarui.

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

   LocalTerminalTab (app) ── LocalTtyConnector (localterm) ── pty4j (PTY lokal, ADR 0005)
```

Tab di `MainFrame` berisi `SplitPanes<TermPane>`. `TermPane` adalah `sealed` (`TerminalTab` untuk SSH,
`LocalTerminalTab` untuk shell lokal). Fitur khusus SSH (SFTP, inject password, remote edit, profil host) hanya
diakses lewat pattern matching ke `TerminalTab`, sehingga compiler menandai `switch` yang lupa menangani tab lokal.

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
1. Upload ke `/tmp/termul-<rand>/content` (direktori mode 700 dibuat dulu, file 600).
2. `ChannelExec` (`RemoteExec`): `env LC_ALL=C sudo -S -k -p '' sh -c <script> termul <target> <src> <hook> <bakdir> <keep>`. Password via stdin. Script: `readlink -f`, backup `cp -p` ke `/var/backups/termul`, `install -m/-o/-g` (mode/owner lama) ke temp di direktori target, `mv -f`.
3. Hook validasi pertama yang cocok (`ValidationHooks`, mis. `/etc/nginx/** = nginx -t`) dijalankan di sudo yang sama. Gagal → backup dipasang lagi (exit 10; 11 = rollback gagal). Direktori `/tmp/termul-*` dihapus di `finally`.
4. App menawarkan sudo otomatis kalau `test -w` gagal saat file dibuka, atau lewat klik kanan *Edit sebagai root*.

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
| `io` executor (tab lokal) | Menjalankan shell lokal (start proses + memuat library native pty4j saat pertama kali) |
| `local-writer-<id>` (virtual) | Write + resize ke PTY lokal secara berurutan (write bisa blocking) |
| `local-wait-<id>` / `local-close-<id>` (virtual) | Menunggu shell lokal berakhir (kode keluar); mematikan pohon proses saat tab ditutup |
| `termul-highlight` (satu, bersama) | Highlight teks: mencocokkan keyword baris yang berubah (tanpa lock) lalu menulis style ke buffer JediTerm (memegang lock buffer sebentar) |

Aturan: callback ke UI selalu lewat `invokeLater`. `CompletableFuture` dari operasi SSH tidak boleh di-`join()` di EDT.

## Layout storage

Lokasi per OS (`AppPaths`): Windows `%APPDATA%` / `%LOCALAPPDATA%`; macOS `~/Library/Application Support` /
`~/Library/Caches`; OS lain `$XDG_CONFIG_HOME` / `$XDG_CACHE_HOME` (fallback `~/.config` / `~/.cache`). Isinya sama:

```
%APPDATA%\TermUL\
  config.json         preferensi (font, tema, editor mapping, hotkey)
  profiles.json       host tree + profil (TANPA secret)
  known_hosts         host key terverifikasi (format OpenSSH)
  ftps_known_certs    sertifikat FTPS yang dipercaya user (TOFU): "host port SHA256:..." (ADR 0007)
  vault.bin           secret terenkripsi
  vault.bin.dpapi     (Windows, opsional) DEK dibungkus DPAPI untuk "Ingat di PC ini"
  vault.bin.keychain  (macOS .app, opsional) DEK dibungkus kunci di Keychain untuk "Ingat di Mac ini" (ADR 0006)
  update-history.json 5 update terakhir yang sudah berjalan + catatan rilisnya (Bantuan → Yang baru)
  local-profiles.json profil terminal lokal + pilihan default (ADR 0005; teks biasa, tanpa secret); file rusak disisihkan ke .corrupt-<waktu>
  highlight.json      profil highlight teks (keyword format Tabby) + penugasan host/grup/lokal/global; file rusak disisihkan ke .corrupt-<waktu>
  shortcuts.json      shortcut yang diganti user (id aksi → "mod+shift+T", kosong = tanpa shortcut); sisanya default; file rusak disisihkan ke .corrupt-<waktu>
  snippets.json       snippet perintah (teks biasa, satu baris, tanpa secret); file rusak disisihkan ke .corrupt-<waktu>
  streak.json         streak api harian (jumlah hari + tanggal terakhir dihitung); file rusak disisihkan ke .corrupt-<waktu>
  instance.lock       FileLock single instance (dipegang selama TermUL berjalan)
  instance.port       port loopback + token acak untuk instance kedua ("tampilkan window"); dihapus saat keluar
  logs\               log aplikasi (tanpa secret)
%LOCALAPPDATA%\TermUL\
  edit\<profileId>\<remote-path>   cache file yang sedang diedit
  updates\current                  versi update aktif (pointer, ditulis atomic)
  updates\<versi>\                 jar update + manifest.json(.sig), .healthy/.attempts
```

## Update aplikasi (ADR 0003)

Main class app-image dan fat jar adalah `update.Bootstrap`, bukan `TermULApp`. Setiap start, Bootstrap:

1. membaca versi bawaan dari `build.properties` di jar aplikasi (build dev → langsung `TermULApp`),
2. memilih `updates/<current>` kalau versinya lebih baru dari versi bawaan, tanda tangan manifest valid (public key dari
   kode bawaan), semua SHA-256 cocok, `generation` didukung, dan belum gagal start 3 kali,
3. memuat jar update dengan `URLClassLoader` ber-parent platform classloader (class lama di classpath bawaan tidak
   terlihat), set context classloader, lalu memanggil `main`. Kalau syarat tidak terpenuhi, `TermULApp` bawaan dipanggil
   langsung.

Bootstrap tidak memakai SLF4J/JNA/AWT; catatannya diteruskan lewat system property `termul.update.notes` dan di-log
aplikasi. Aplikasi menandai versi sehat (`.healthy`) setelah window utama tampil. Menu Bantuan → Periksa update
(`app.update.UpdateDialog` → `update.Updater`) mengambil `manifest.json` rilis terbaru di GitHub, menyalin jar lokal yang
hash-nya sama, mengunduh sisanya, memverifikasi, lalu me-rename folder staging dan memindah pointer secara atomic.
Restart memakai `--wait-pid=<pid>` supaya proses baru menunggu proses lama selesai.

`app.update.UpdateNotifier` memeriksa rilis terbaru di background (20 detik setelah window tampil, lalu setiap 12 jam,
thread scheduler sendiri) lewat `Updater.newerVersion()` (tanpa hash jar lokal) dan menampilkan badge di menu bar.
Badge tidak pernah memasang apa pun; setelah pemasangan lewat dialog, badge menjadi tombol "Restart untuk update".
Bisa dimatikan lewat `AppConfig.autoUpdateCheck`.

## Terminal lokal (ADR 0005)

- Modul `localterm` (`localterm → core`, tanpa Swing): `LocalShells` (shell default per OS), `LocalTerminalFactory`
  (pty4j; ConPTY di Windows), `LocalTtyConnector` (TtyConnector JediTerm). `PtyHandle` membungkus `PtyProcess`
  supaya connector bisa di-unit test tanpa native.
- Siklus hidup: tab ditutup → `closeByUser()` → pohon proses dicatat (`ProcessHandle.of(pid)`; `toHandle()` pty4j
  tidak didukung) → PTY ditutup (SIGHUP; shell interaktif mengabaikan SIGTERM) → setelah 2 dtk sisa proses dimatikan
  paksa. Kalau JVM keluar/crash, OS menutup master PTY sehingga shell tetap mendapat SIGHUP.
- Di macOS, shell yang keluar menunggu output-nya di PTY dibaca; JediTerm selalu membaca, jadi ini hanya relevan
  untuk test (lihat `LocalTtyConnectorIT`).
- Library native pty4j ikut jar dan diekstrak ke folder temp saat dipakai.

## Highlight teks

- `core.highlight`: `HighlightKeyword`/`HighlightProfile` (field JSON sama dengan plugin Tabby), `HighlightSettings`
  (saklar, profil, penugasan target → profil; resolusi host → grup terdekat → global, lokal → semua lokal → global),
  `HighlightMatcher` (keyword dikompilasi; keyword di atas menang; batas waktu 25 ms dan 8192 karakter per baris supaya
  regex lambat tidak menggantung), `HighlightStore` (`highlight.json`), `TabbyHighlightJson` (import/export).
- `app.terminal.TextHighlighter`: satu per widget terminal. Listener buffer hanya menjadwalkan; di thread
  `termul-highlight` baris logis (soft-wrap digabung, penanda karakter lebar dibuang) dicocokkan, lalu style karakter
  diganti dengan `HighlightStyle` yang menyimpan style asli. Teks buffer tidak berubah. Baris yang sudah diproses
  diingat (teks + generasi pengaturan) dan dilewati; pengaturan baru memproses ulang seluruh scrollback.
  API `addCustomHighlighting` JediTerm tidak dipakai karena hanya merender satu highlight per baris tanpa bold/italic.
- `MainFrame` mengompilasi profil sekali per perubahan pengaturan dan memilih profil per `TermPane` (pattern matching
  `TerminalTab`/`LocalTerminalTab`); dipanggil ulang saat host diedit (pindah grup).

## WSL (opsional, ADR 0004)

Aktif hanya kalau `AppConfig.wslManager` dinyalakan dan OS-nya Windows. Distro WSL dimasuki lewat SSH biasa ke sshd di
distro (`localhost:<port>`). Profilnya `HostProfile` dengan field `wslDistro`, jadi tidak ada PTY lokal.

- `core.wsl.WslManager` menjalankan `wsl.exe` (lewat `WslRunner`, yang di-fake di test): `--list --quiet [--running]`,
  start dengan keep-alive (`--exec sleep infinity`, karena WSL mematikan distro tanpa sesi `wsl.exe`), `--terminate`,
  `--shutdown`, dan sshd sebagai root. Semua method blocking.
- `app.wsl.WslController` (EDT) membaca ulang daftar distro setiap 15 detik di `ssh-ops`, mengisi grup bawaan WSL di
  `HostTreePanel`, dan mengelola `WslManagerDialog`. `prepare()` dipasang sebagai `TerminalTab.BeforeConnect`: sebelum
  connect ke profil WSL, distro dan sshd dijalankan dulu. Auto-reconnect tidak menyalakan distro yang berhenti.
- Keep-alive dilepas saat aplikasi ditutup (`TermULApp.shutdown`); distro tidak dihentikan paksa.

## Alur utama

1. **Connect**: double-click host, lalu `ssh-ops` menjalankan `acquire()`: TCP connect, verifikasi host key (dialog TOFU di EDT kalau host baru), dan auth (agent, key, lalu password dari vault/prompt). Setelah itu buka `ChannelShell` + PTY, pasang connector ke JediTerm, dan tab aktif.
2. **Sudo inject (hotkey)**: EDT → vault.get → write bytes ke channel (di `ssh-ops`) → zero buffer.
3. **Edit file**: double-click di SFTP panel, download ke cache + stat baseline, jalankan editor (`ProcessBuilder`), watcher mendeteksi perubahan, debounce, lalu upload (normal/sudo) dan status di EditTracker diperbarui.

## FTP/FTPS (ADR 0007)

- `RemoteFileService` (modul `sftp`) adalah interface: `SftpFileService` (SFTP di atas lease SSH) dan
  `ftp.FtpFileService` (Apache Commons Net). `SftpLinks.acquire` memilih opener dari `HostProfile.protocol`; profil FTP
  tidak punya gate terminal, jadi `SftpConnection` langsung membuka service.
- `FtpConnector` membuka koneksi: TLS (`FtpsTrust`: CA + hostname, lalu TOFU `FtpsCertStore`), login `char[]`
  (`FtpClients`), `PBSZ 0`/`PROT P`, mode biner, passive. Satu `SSLContext` (cache sesi) per koneksi kontrol, dan
  socket data melapor port kontrol (`SessionKeySocket`), supaya kanal data me-resume sesi TLS kanal kontrolnya sendiri.
- `FtpFileService`: operasi metadata lewat satu koneksi kontrol (`ReentrantLock`, dibuka ulang otomatis setelah idle
  timeout, dicek `NOOP` setelah 30 detik menganggur); setiap download/upload memakai koneksi transfer sendiri (pool kecil,
  maks. 2 menganggur), sehingga listing tetap jalan selama transfer. Stat memakai `MLST` kalau ada, kalau tidak listing
  parent + `MDTM`.
- UI: profil FTP selalu dibuka sebagai tab file saja (`MainFrame.openTab`), tanpa deteksi OS dan tanpa "Edit sebagai root".

## Upload antar-panel SFTP

- `SftpPanel` mendaftar dirinya di registry statis (EDT) dan keluar saat `dispose()`. Menu "Upload ke ▸" memuat panel lain
  yang tersambung dan tampil di tab, panel dalam satu grup split dulu (komponen tab yang sama), lalu urut posisi tab.
- Drag dari tabel SFTP membawa `RemoteSelection` (objek lokal JVM); panel SFTP lain menerimanya sebagai upload antar-server.
- Transfer berjalan di antrean panel **tujuan**: `link.execute` tujuan → `source.link.ensureLive()` → file:
  `RemoteFileService.copyFrom(source, path, target)`, folder: `TreeTransfer.copy(...)`. Upload biasa dan salin antar-server
  memakai jalur tulis yang sama (`write`: temp → chmod → rename atomic).

## Shortcut yang bisa diganti

- `ShortcutAction` (app.ui) mendaftar semua aksi: id stabil (disimpan di file), kategori, default Windows/Mac dalam notasi
  `mod+shift+T` (`mod` = Ctrl / ⌘). `KeyBindings` = default + override (immutable); `Shortcuts.use/get/bindings` memegang
  binding aktif (ditulis di EDT, `volatile`).
- `ShortcutStore` (core, `shortcuts.json`) hanya menyimpan override, jadi default baru di versi berikutnya ikut terpakai.
  Listener store → `MainFrame.applyShortcuts`: accelerator menu, tooltip, dan teks sambutan diperbarui tanpa restart.
- Pengambilan sebelum terminal (`Shortcuts.isAppCombo`): hanya kombinasi yang dipakai suatu aksi, dan salah satu dari
  modifier utama + Shift, aksi yang default-nya memang diambil tanpa Shift (Ctrl+F5/⌘R, ⌘W, ⌘T, Ctrl+Tab, Ctrl/⌘+1..9),
  atau shortcut yang diganti user. Default tanpa Shift lain (Ctrl+N, Ctrl+F, Ctrl+G, zoom) tetap milik shell di Windows.
- `ShortcutRules`: tolak tanpa Ctrl/⌘, Alt/Option, tombol Windows, shortcut sistem (⌘Q, ⌘Space, ⌘⇧4, Ctrl+Esc, Cmd+Ctrl),
  dan inject password tanpa modifier utama; peringatkan kombinasi milik shell / copy-paste terminal.

## Transparansi jendela (ADR 0008)

- Dua opasitas di config: `windowOpacity` (bagian selain terminal, teks ikut pudar) dan `terminalOpacity` (latar terminal,
  teks tetap solid). Dialog Pengaturan > Transparansi jendela memberi pratinjau langsung; `WindowOpacity.apply` adalah
  satu-satunya titik yang menerapkannya.
- Windows: `MainFrame` selalu tanpa dekorasi native (title bar FlatLaf) + `WinCustomFrame` (subclass window procedure
  lewat JNA): gaya window berbingkai, `WM_NCCALCSIZE` (maximize = area kerja monitor), `WM_NCHITTEST` (tepi resize,
  title bar = seret, tombol maximize FlatLaf = `HTMAXBUTTON`). Dipasang di `MainFrame.addNotify`, sebelum window tampil.
  Geometri title bar dihitung di EDT dan dibaca window procedure (thread toolkit) lewat field volatile.
- Solid ↔ tembus: `Frame.setBackground` beralpha atau tidak, tanpa membuat HWND baru. Saat tembus, `Toolkit.setDynamicLayout(false)`.
- `TranslucentRootPane` (root pane `MainFrame`): opasitas jendela < 100% → painting origin, isi window digambar ke buffer,
  ditempel dengan `AlphaComposite.Src` × alpha, lalu area terminal (`TerminalTranslucency.visibleAreasIn`) dan popup
  ringan ditempel ulang tanpa dipudarkan.
- Latar terminal: `ZoomableTermWidget` mengisi latar (warna default JediTerm + gambar latar tema) dengan
  `AlphaComposite.Src` × `TerminalTranslucency.percent()`, lalu teks lewat `FillFilterGraphics2D`.
- macOS: window tanpa dekorasi hanya kalau transparansi aktif saat start; dari 100% butuh restart.

## Menu klik kanan Explorer (ADR 0009)
- `ExplorerMenu` / `WindowsExplorerMenu` (app, `startup`) menulis cascading menu "TermUL" di
  `HKCU\Software\Classes\{Directory\Background, Directory, Drive}\shell\TermUL`. Ada satu item per pilihan terminal
  lokal, dan tiap item menjalankan `TermUL.exe --open-local=<id> --cwd "%V"`. `MainFrame.syncExplorerMenu` menulis ulang
  daftarnya di io saat daftar terminal lokal berubah, kalau menu terpasang.
- `OpenLocalRequest.parse` membaca argumen tersebut di `TermULApp.main`, sebelum `SingleInstance.acquire`.
  - Kalau TermUL sudah berjalan, permintaan dikirim sebagai `SingleInstance.Request("open-local", "<id>\n<folder>")`.
    Proses baru langsung keluar, dan instance utama memanggil `MainFrame.openLocalIn` di EDT.
  - Kalau TermUL belum berjalan, tab dibuka setelah window tampil.
- `openLocalIn` menyalin `LocalProfiles.Choice` dengan `directory` = folder, ditambah `CHERE_INVOKING=1` di
  environment.

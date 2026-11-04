# ADR 0012: Port forwarding (local, remote, dynamic)

- Status: Diterima
- Tanggal: 2026-11-04
- Terkait: ADR 0001 (MINA SSHD), prototipe "TermUL Desktop" artboard `Tunnels.dc.html`

## Konteks
Port forwarding ada di backlog sejak awal (REQUIREMENTS F6). Prototipe menambahkan dialog Port forwarding (chip panah
di panel bawah, `Ctrl/Cmd+Shift+U`): daftar tunnel tersimpan dengan saklar, filter status/jenis, detail berisi alur,
alamat untuk aplikasi, jumlah koneksi dan byte, error dengan perbaikan ("Use port N instead"), auto-start saat host
tersambung, dan perintah `ssh` setara.

MINA punya tracker siap pakai untuk ketiga jenis (`createLocal/Remote/DynamicPortForwardingTracker`), tapi tidak
menyediakan jumlah byte per tunnel, dan bind error-nya terbungkus sehingga "port sudah dipakai" sulit dibedakan.

## Keputusan
1. **Model di `core.tunnel`**: `Tunnel` (record, tanpa secret) per profil host, `TunnelStore` di `tunnels.json`
   (ditulis atomic, entri tidak valid dilewati, file rusak disisihkan). Status jalan tidak disimpan: tunnel hanya
   jalan selama TermUL terbuka. `TunnelChecks` memegang validasi port, bentrok port, dan perintah `ssh` setara.
2. **`ssh.tunnel.TunnelManager`** menjalankan tunnel lewat `SessionManager` yang sama dengan tab. Setiap tunnel yang
   jalan memegang `SshLease` sendiri, jadi koneksinya tetap hidup walau tab host itu ditutup.
   - **Local**: `ServerSocket` sendiri di `127.0.0.1` (atau `0.0.0.0` kalau dibagi), setiap koneksi menjadi channel
     `direct-tcpip` dan disalurkan `Relay` di virtual thread (byte dan koneksi terhitung, `BindException` jelas).
   - **Dynamic**: sama, ditambah handshake SOCKS5 (tanpa auth) dan SOCKS4/4a, hanya CONNECT. Nama host diteruskan ke
     server (DNS dari server), seperti `ssh -D`.
   - **Remote**: `tcpip-forward` MINA di loopback server (`localhost:<port>`, sama dengan default `ssh -R`). Koneksi dari
     server diteruskan MINA ke relay `127.0.0.1:<acak>` milik TermUL, lalu ke layanan tujuan, supaya byte juga
     terhitung.
3. **Auto-start dan putus**: `SessionManager.addConnectionListener` memberi tahu setiap koneksi baru yang selesai login.
   Tunnel `autoStart` host itu, dan tunnel yang tadi berhenti karena koneksi putus (`Failed(DISCONNECTED)`), dijalankan
   lagi. TermUL tidak reconnect sendiri di background; yang memicu adalah koneksi baru (tab, SFTP, atau Start).
4. **Forwarding filter client** (`ClientForwardingFilter`): sebelumnya default MINA (tolak semua). Sekarang server hanya
   boleh membuka `forwarded-tcpip` ke relay loopback tunnel yang diminta TermUL. Agent forwarding, X11, dan listen
   tetap ditolak.
5. **UI mengikuti prototipe**: `TunnelManagerDialog`, `TunnelFormDialog`, `TunnelChip`, memakai palet dan komponen
   `SnippetUi` supaya sama dengan Snippet manager.

## Konsekuensi
- (+) Statistik koneksi/byte dan pesan error akurat untuk ketiga jenis, dengan saran port kosong.
- (+) Tunnel tidak bergantung pada tab terminal; tidak ada proses `ssh` eksternal.
- (−) Satu hop tambahan (relay loopback) untuk tunnel remote. Proses lain di PC yang sama secara teori bisa tersambung
  ke relay itu, tapi hanya sampai ke layanan tujuan yang memang ada di PC ini.
- (−) SOCKS tanpa autentikasi. Default hanya loopback; kalau dibagi ke jaringan, UI memperingatkan bahwa siapa pun di
  jaringan bisa menjangkau server lewat port itu.
- (−) Tidak ada reconnect otomatis di background saat koneksi putus dan tidak ada tab yang tersambung lagi.

package dev.egateza.termul.ssh.tunnel;

import java.util.List;

/**
 * Keadaan tunnel saat ini (immutable).
 *
 * @param connections koneksi yang sedang terbuka lewat tunnel
 * @param bytes       total byte yang lewat (dua arah) sejak tunnel terakhir di-start
 * @param issue       masalah koneksi terakhir yang lewat tunnel (mis. server menolak membuka tujuan), atau null;
 *                    hilang lagi saat ada koneksi yang berhasil
 * @param clients     alamat asal setiap koneksi yang sedang terbuka (local/dynamic: program yang tersambung ke port di
 *                    PC ini, mis. {@code 127.0.0.1} atau IP perangkat lain kalau port dibagi; remote: {@code ""}, koneksi
 *                    datang dari server). Satu entri per koneksi
 */
public record TunnelState(TunnelStatus status, int connections, long bytes, String issue, List<String> clients) {

    public static final TunnelState STOPPED = new TunnelState(TunnelStatus.STOPPED, 0, 0, null, List.of());

    public TunnelState {
        clients = List.copyOf(clients);
    }
}

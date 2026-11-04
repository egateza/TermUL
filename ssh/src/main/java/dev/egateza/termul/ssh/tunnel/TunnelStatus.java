package dev.egateza.termul.ssh.tunnel;

/** Status jalan sebuah tunnel. Tidak disimpan: tunnel hanya jalan selama TermUL terbuka. */
public sealed interface TunnelStatus {

    record Stopped() implements TunnelStatus {
    }

    /** Sedang connect ke host / membuka port. */
    record Starting() implements TunnelStatus {
    }

    record Active() implements TunnelStatus {
    }

    /**
     * Gagal start, atau berhenti karena koneksi putus.
     *
     * @param detail        keterangan teknis (pesan koneksi), boleh kosong; tidak pernah berisi secret
     * @param suggestedPort port lain yang kosong di PC ini untuk {@link Reason#PORT_IN_USE}, 0 kalau tidak ada
     */
    record Failed(Reason reason, int port, String detail, int suggestedPort) implements TunnelStatus {
        public Failed {
            detail = detail == null ? "" : detail;
        }
    }

    enum Reason {
        /** Port di PC ini sudah dipakai program lain. */
        PORT_IN_USE,
        /** Sistem menolak membuka port (mis. port di bawah 1024 tanpa hak admin). */
        PORT_DENIED,
        /** Tujuan tunnel local tidak bisa dibuka dari server ({@code detail} = {@code host:port — pesan}). */
        DESTINATION_UNREACHABLE,
        /** Layanan tujuan tunnel remote tidak bisa dihubungi dari PC ini ({@code detail} seperti di atas). */
        SERVICE_UNREACHABLE,
        /** Port remote sudah didengarkan program lain di server (dicek sebelum meminta forward). */
        REMOTE_PORT_IN_USE,
        /** Server menolak membuka port remote (forwarding dimatikan, port dicadangkan, dll.). */
        REMOTE_REFUSED,
        /** Profil host sudah dihapus atau bukan SSH. */
        HOST_MISSING,
        /** Connect / login ke host gagal ({@code detail} = pesan koneksi). */
        CONNECT_FAILED,
        /** Koneksi ke host putus; tunnel jalan lagi saat host tersambung. */
        DISCONNECTED,
        OTHER
    }

    Stopped STOPPED = new Stopped();
    Starting STARTING = new Starting();
    Active ACTIVE = new Active();

    /** Active atau Starting: switch di daftar menyala. */
    default boolean isRunning() {
        return this instanceof Active || this instanceof Starting;
    }
}

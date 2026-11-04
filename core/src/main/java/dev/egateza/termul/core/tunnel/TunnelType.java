package dev.egateza.termul.core.tunnel;

/** Jenis port forwarding, sama dengan opsi {@code ssh -L}, {@code -R}, dan {@code -D}. */
public enum TunnelType {
    /** Port di PC ini → layanan yang dijangkau dari server ({@code -L}). */
    LOCAL,
    /** Port di server → layanan yang dijangkau dari PC ini ({@code -R}). */
    REMOTE,
    /** Proxy SOCKS di PC ini; tujuan dipilih aplikasi, koneksinya keluar dari server ({@code -D}). */
    DYNAMIC;

    /** Port sumbernya dibuka di PC ini (local/dynamic), bukan di server. */
    public boolean listensOnThisPc() {
        return this != REMOTE;
    }

    /** Punya host + port tujuan tetap (dynamic tidak: tujuannya diminta aplikasi lewat SOCKS). */
    public boolean hasDestination() {
        return this != DYNAMIC;
    }
}

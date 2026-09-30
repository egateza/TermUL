package dev.egateza.termul.sftp;

/**
 * Status sesi terminal sebuah host. Sesi terminal adalah pemilik koneksi: SFTP hanya boleh dipakai saat terminal
 * tersambung, dan menyambung ulang (bertahap) adalah tugas terminal, bukan SFTP.
 */
public enum TerminalState {
    /** Sedang menyambung pertama kali. */
    CONNECTING,
    CONNECTED,
    /** Koneksi terputus; terminal sedang menyambung ulang secara bertahap. */
    RECONNECTING,
    /** Terminal tidak tersambung dan tidak sedang mencoba (gagal, sesi berakhir); menunggu user Reconnect. */
    DOWN;

    /** true kalau terminal sedang berusaha tersambung, jadi SFTP sebaiknya menunggu. */
    public boolean isPending() {
        return this == CONNECTING || this == RECONNECTING;
    }
}

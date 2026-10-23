package dev.egateza.termul.core.profile;

/**
 * Protokol koneksi sebuah profil. {@link #SSH} = terminal + SFTP; sisanya hanya browser file (tanpa shell).
 *
 * <p>{@link #FTP} mengirim username, password, dan isi file tanpa enkripsi: hanya untuk server yang tidak menyediakan
 * SFTP/FTPS (lihat {@code docs/adr/0007-ftp.md}).
 */
public enum Protocol {
    /** SSH (terminal) + SFTP. */
    SSH(22),
    /** FTP dengan TLS eksplisit ({@code AUTH TLS} di port 21). */
    FTPS(21),
    /** FTP dengan TLS implisit (TLS sejak awal, biasanya port 990). */
    FTPS_IMPLICIT(990),
    /** FTP tanpa enkripsi. */
    FTP(21);

    private final int defaultPort;

    Protocol(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public int defaultPort() {
        return defaultPort;
    }

    /** true untuk keluarga FTP (browser file saja, auth password, tanpa jump host/sudo/WSL). */
    public boolean isFtp() {
        return this != SSH;
    }

    /** true kalau koneksi dienkripsi (SSH atau FTPS). */
    public boolean isEncrypted() {
        return this != FTP;
    }
}

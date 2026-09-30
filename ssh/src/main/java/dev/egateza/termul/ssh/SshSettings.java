package dev.egateza.termul.ssh;

import java.time.Duration;
import java.util.Objects;

/**
 * Parameter koneksi SSH.
 *
 * @param connectTimeout     batas waktu TCP connect + key exchange awal
 * @param authTimeout        batas waktu autentikasi (termasuk waktu user mengetik password/konfirmasi TOFU)
 * @param channelOpenTimeout batas waktu membuka channel (shell/exec/sftp)
 * @param heartbeatInterval  interval keep-alive; {@link Duration#ZERO} = mati
 * @param heartbeatMaxMissed jumlah keep-alive tanpa balasan sebelum koneksi dianggap putus
 * @param releaseGrace       jeda sebelum koneksi tanpa pemakai ditutup (reopen tab tidak perlu auth ulang)
 */
public record SshSettings(Duration connectTimeout, Duration authTimeout, Duration channelOpenTimeout,
                          Duration heartbeatInterval, int heartbeatMaxMissed, Duration releaseGrace) {

    public SshSettings {
        Objects.requireNonNull(connectTimeout);
        Objects.requireNonNull(authTimeout);
        Objects.requireNonNull(channelOpenTimeout);
        Objects.requireNonNull(heartbeatInterval);
        Objects.requireNonNull(releaseGrace);
        if (heartbeatMaxMissed < 0) {
            throw new IllegalArgumentException("heartbeatMaxMissed < 0");
        }
    }

    public static SshSettings defaults() {
        return new SshSettings(Duration.ofSeconds(15), Duration.ofMinutes(2), Duration.ofSeconds(15),
                Duration.ofSeconds(30), 3, Duration.ofSeconds(30));
    }
}

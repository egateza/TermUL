package dev.egateza.termul.sftp.ftp;

import java.time.Instant;

/**
 * Callback ke UI saat sertifikat FTPS server tidak lolos validasi CA/hostname dan belum pernah dipercaya. Dipanggil
 * dari thread koneksi (di tengah handshake TLS); implementasi UI harus pindah ke EDT sendiri dan boleh blocking.
 */
@FunctionalInterface
public interface FtpsCertPrompt {

    /**
     * Info sertifikat untuk ditampilkan ke user.
     *
     * @param hostLabel   {@code host:port}
     * @param fingerprint {@code SHA256:...} sertifikat leaf
     * @param reason      kenapa validasi CA gagal (mis. self-signed, hostname tidak cocok, kedaluwarsa)
     */
    record CertInfo(String hostLabel, String fingerprint, String subject, String issuer, Instant notBefore,
                    Instant notAfter, String reason) {
    }

    /** @return true kalau user memercayai sertifikat ini (disimpan, TOFU) */
    boolean confirmUntrusted(CertInfo info);

    /** Menolak semua sertifikat yang tidak lolos validasi CA (dipakai kalau tidak ada UI). */
    FtpsCertPrompt REJECT = info -> false;
}

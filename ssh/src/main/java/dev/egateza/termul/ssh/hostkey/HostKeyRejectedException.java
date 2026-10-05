package dev.egateza.termul.ssh.hostkey;

import dev.egateza.termul.ssh.SshConnectException;

/** Koneksi ditolak karena host key tidak diterima (host key berubah atau user menolak host baru). */
public final class HostKeyRejectedException extends SshConnectException {

    private final HostKeyVerdict verdict;

    public HostKeyRejectedException(HostKeyVerdict verdict) {
        super(message(verdict));
        this.verdict = verdict;
    }

    public HostKeyVerdict verdict() {
        return verdict;
    }

    private static String message(HostKeyVerdict verdict) {
        return switch (verdict) {
            case HostKeyVerdict.Changed c -> """
                    PERINGATAN: HOST KEY %s BERUBAH!
                    Kemungkinan ada serangan man-in-the-middle, atau server di-reinstall.
                    Fingerprint baru: %s
                    Fingerprint tersimpan: %s
                    Koneksi ditolak. Kalau perubahan ini memang sah (sudah dicek ke server), hapus entry lama lewat klik kanan
                    host > Lupakan host key, atau menu Pengaturan > Known hosts."""
                    .formatted(c.presented().hostLabel(), c.presented().fingerprint(), String.join(", ", c.knownFingerprints()));
            case HostKeyVerdict.RejectedByUser r -> "Host key " + r.presented().hostLabel() + " tidak dipercaya. Koneksi dibatalkan.";
            case HostKeyVerdict.Trusted t -> "Host key diterima";
        };
    }
}

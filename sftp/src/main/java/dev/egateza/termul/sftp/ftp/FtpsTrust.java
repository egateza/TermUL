package dev.egateza.termul.sftp.ftp;

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Verifikasi sertifikat FTPS untuk satu server (satu {@link FtpFileService}, semua koneksinya):
 * <ol>
 *   <li>lolos validasi CA (truststore JDK) + hostname: diterima;</li>
 *   <li>tidak lolos, tetapi fingerprint-nya sudah dipercaya user ({@link FtpsCertStore}): diterima;</li>
 *   <li>sudah ada fingerprint lain yang dipercaya untuk host:port ini: <b>ditolak</b> (sertifikat berubah);</li>
 *   <li>belum dikenal: user ditanya (TOFU); diterima = disimpan.</li>
 * </ol>
 * Setelah satu sertifikat diterima, koneksi lain (kanal data, koneksi transfer) wajib memakai sertifikat yang sama.
 *
 * <p>Dipanggil dari thread handshake TLS. Thread-safe.
 */
final class FtpsTrust extends X509ExtendedTrustManager {

    /** Kenapa sertifikat ditolak (untuk pesan error setelah handshake gagal). */
    sealed interface Rejection {
        record Changed(String hostLabel, String presented, String known) implements Rejection {
        }

        record NotTrusted(String hostLabel, String fingerprint, String reason) implements Rejection {
        }

        record Mismatch(String hostLabel) implements Rejection {
        }
    }

    private final String host;
    private final int port;
    private final X509ExtendedTrustManager system;
    private final FtpsCertStore store;
    private final FtpsCertPrompt prompt;
    private volatile String accepted;     // fingerprint yang sudah diterima untuk server ini
    private volatile Rejection rejection; // alasan penolakan terakhir

    FtpsTrust(String host, int port, X509ExtendedTrustManager system, FtpsCertStore store, FtpsCertPrompt prompt) {
        this.host = Objects.requireNonNull(host);
        this.port = port;
        this.system = Objects.requireNonNull(system);
        this.store = Objects.requireNonNull(store);
        this.prompt = Objects.requireNonNull(prompt);
    }

    /** Trust manager PKIX bawaan JDK (cacerts). */
    static X509ExtendedTrustManager systemTrustManager() {
        try {
            var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            for (TrustManager tm : factory.getTrustManagers()) {
                if (tm instanceof X509ExtendedTrustManager x) {
                    return x;
                }
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Truststore TLS tidak tersedia", e);
        }
        throw new IllegalStateException("Truststore TLS tidak tersedia");
    }

    /** Alasan penolakan terakhir, atau null. */
    Rejection rejection() {
        return rejection;
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        verify(chain, () -> system.checkServerTrusted(chain, authType, socket));
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        verify(chain, () -> system.checkServerTrusted(chain, authType, engine));
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        verify(chain, () -> system.checkServerTrusted(chain, authType));
    }

    @FunctionalInterface
    private interface Check {
        void run() throws CertificateException;
    }

    private void verify(X509Certificate[] chain, Check systemCheck) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new CertificateException("Server tidak mengirim sertifikat");
        }
        String label = FtpsCertStore.Entry.hostLabel(host.toLowerCase(java.util.Locale.ROOT), port);
        String fp = FtpsCertStore.fingerprint(chain[0]);
        synchronized (this) { // satu prompt pada satu waktu; koneksi paralel menunggu keputusan yang sama
            String already = accepted;
            if (already != null) {
                if (already.equals(fp)) {
                    return;
                }
                rejection = new Rejection.Mismatch(label);
                throw new CertificateException("Sertifikat koneksi ini berbeda dengan sertifikat koneksi kontrol");
            }
            String reason;
            try {
                systemCheck.run(); // CA + hostname (endpoint identification diaktifkan di socket)
                accepted = fp;
                return;
            } catch (CertificateException e) {
                reason = e.getMessage();
            }
            var known = store.find(host, port);
            if (known.isPresent()) {
                if (known.get().equals(fp)) {
                    accepted = fp;
                    return;
                }
                rejection = new Rejection.Changed(label, fp, known.get());
                throw new CertificateException("Sertifikat FTPS " + label + " berubah");
            }
            X509Certificate leaf = chain[0];
            var info = new FtpsCertPrompt.CertInfo(label, fp, leaf.getSubjectX500Principal().getName(),
                    leaf.getIssuerX500Principal().getName(), leaf.getNotBefore().toInstant(),
                    leaf.getNotAfter().toInstant(), reason);
            if (!prompt.confirmUntrusted(info)) {
                rejection = new Rejection.NotTrusted(label, fp, reason);
                throw new CertificateException("Sertifikat FTPS " + label + " tidak dipercaya");
            }
            store.trust(host, port, fp);
            accepted = fp;
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        throw new CertificateException("Mode server tidak didukung");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        throw new CertificateException("Mode server tidak didukung");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new CertificateException("Mode server tidak didukung");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return system.getAcceptedIssuers();
    }
}

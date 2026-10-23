package dev.egateza.termul.sftp.ftp;

import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.Protocol;
import dev.egateza.termul.sftp.RemoteFileException;
import dev.egateza.termul.ssh.ConnectCancel;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Membuka {@link FtpFileService} untuk profil FTP/FTPS: connect, TLS (FTPS), login dengan password dari
 * {@link CredentialProvider} (vault lalu prompt), lalu mode biner + passive.
 *
 * <p>Password disalin ke dalam service selama koneksinya hidup, karena koneksi transfer tambahan dan reconnect setelah
 * idle timeout butuh login ulang tanpa mengganggu user. Salinan itu di-zero saat service ditutup.
 */
public final class FtpConnector {

    private static final Logger log = LoggerFactory.getLogger(FtpConnector.class);

    static final int MAX_ATTEMPTS = 3;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(60);

    private final CredentialProvider credentials;
    private final FtpsCertStore certs;
    private final FtpsCertPrompt prompt;
    private final X509ExtendedTrustManager system;

    public FtpConnector(CredentialProvider credentials, FtpsCertStore certs, FtpsCertPrompt prompt) {
        this(credentials, certs, prompt, FtpsTrust.systemTrustManager());
    }

    /** @param system trust manager CA (test memakai truststore sendiri) */
    FtpConnector(CredentialProvider credentials, FtpsCertStore certs, FtpsCertPrompt prompt,
                 X509ExtendedTrustManager system) {
        this.credentials = Objects.requireNonNull(credentials);
        this.certs = Objects.requireNonNull(certs);
        this.prompt = Objects.requireNonNull(prompt);
        this.system = Objects.requireNonNull(system);
    }

    /**
     * Connect + login. Password yang ditolak server ditanyakan lagi (maks. {@value #MAX_ATTEMPTS} kali).
     *
     * @param cancel dibatalkan = koneksi yang sedang dibuka diputus
     */
    public FtpFileService open(HostProfile profile, ConnectCancel cancel) throws RemoteFileException {
        if (!profile.isFtp()) {
            throw new IllegalArgumentException("Bukan profil FTP: " + profile.address());
        }
        Tls tls = profile.protocol() == Protocol.FTP ? null : tlsContext(profile);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            char[] password = credentials.password(profile, attempt);
            if (password == null) {
                throw new RemoteFileException("Login FTP dibatalkan.");
            }
            FTPClient client;
            try {
                client = connect(profile, tls, password, cancel);
            } catch (LoginRejected e) {
                Arrays.fill(password, '\0');
                log.info("Login FTP {} ditolak (percobaan {})", profile.address(), attempt);
                continue;
            } catch (RemoteFileException e) {
                Arrays.fill(password, '\0');
                throw e;
            }
            char[] kept = password; // milik service sampai ditutup
            try {
                return new FtpFileService(profile.address(), () -> connect(profile, tls, kept, null), client,
                        () -> Arrays.fill(kept, '\0'));
            } catch (RemoteFileException | RuntimeException e) {
                Arrays.fill(kept, '\0');
                throw e;
            }
        }
        throw new RemoteFileException("Login FTP " + profile.address() + " gagal: username atau password salah.");
    }

    /** Password ditolak server (530). */
    static final class LoginRejected extends RemoteFileException {
        LoginRejected(String reply) {
            super("Login FTP ditolak: " + reply);
        }
    }

    /**
     * TLS satu service: semua koneksinya berbagi sertifikat yang diterima ({@link FtpsTrust}), tetapi tiap koneksi
     * kontrol punya {@link SSLContext} (cache sesi) sendiri. Kanal data harus me-resume sesi kontrolnya sendiri: cache
     * JDK berkunci host:port, sehingga cache bersama bisa memberi tiket koneksi kontrol lain, dan server seperti vsftpd
     * (satu proses + kunci tiket per koneksi) menolaknya ({@code 522 session reuse required}).
     */
    private record Tls(FtpsTrust trust) {
        SSLContext newContext() throws RemoteFileException {
            try {
                var context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[] {trust}, null);
                return context;
            } catch (GeneralSecurityException e) {
                throw new RemoteFileException("TLS tidak tersedia: " + e.getMessage(), e);
            }
        }
    }

    private Tls tlsContext(HostProfile profile) {
        return new Tls(new FtpsTrust(profile.host(), profile.port(), system, certs, prompt));
    }

    /** Satu koneksi kontrol baru yang sudah login. Dipakai juga oleh service (koneksi transfer, reconnect). */
    private FTPClient connect(HostProfile profile, Tls tls, char[] password, ConnectCancel cancel)
            throws RemoteFileException {
        FTPClient client = switch (profile.protocol()) {
            case FTP -> new FtpClients.Plain();
            case FTPS -> new FtpClients.Secure(false, tls.newContext());
            case FTPS_IMPLICIT -> new FtpClients.Secure(true, tls.newContext());
            case SSH -> throw new IllegalArgumentException("Bukan profil FTP");
        };
        client.setControlEncoding("UTF-8");
        client.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        client.setDefaultTimeout((int) SOCKET_TIMEOUT.toMillis());
        client.setDataTimeout(SOCKET_TIMEOUT);
        client.setListHiddenFiles(true);
        if (cancel != null) {
            cancel.onCancel(() -> FtpFileService.disconnectQuietly(client));
        }
        boolean ok = false;
        try {
            client.connect(profile.host(), profile.port());
            if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
                throw new RemoteFileException("Server FTP menolak koneksi: " + reply(client));
            }
            int reply = ((FtpClients.PasswordLogin) client).login(profile.username(), password);
            if (reply == FTPReply.NOT_LOGGED_IN) {
                throw new LoginRejected(reply(client));
            }
            if (!FTPReply.isPositiveCompletion(reply)) {
                throw new RemoteFileException("Login FTP gagal: " + reply(client));
            }
            if (client instanceof FtpClients.Secure secure) {
                secure.protectData();
            }
            if (!client.setFileType(FTP.BINARY_FILE_TYPE)) {
                throw new RemoteFileException("Server FTP menolak mode biner: " + reply(client));
            }
            client.enterLocalPassiveMode();
            ok = true;
            return client;
        } catch (RemoteFileException e) {
            throw e;
        } catch (IOException e) {
            if (cancel != null && cancel.isCancelled()) {
                throw new RemoteFileException("Koneksi FTP dibatalkan.", e);
            }
            String rejected = tls == null ? null : rejectionMessage(tls.trust());
            if (rejected != null) {
                throw new FtpsCertRejectedException(rejected, e);
            }
            throw new RemoteFileException("Gagal connect FTP " + profile.address() + ": " + e.getMessage(), e);
        } finally {
            if (!ok) {
                FtpFileService.disconnectQuietly(client);
            }
        }
    }

    private static String rejectionMessage(FtpsTrust trust) {
        var rejection = trust.rejection();
        if (rejection == null) {
            return null;
        }
        return switch (rejection) {
            case FtpsTrust.Rejection.Changed c -> """
                    PERINGATAN: SERTIFIKAT FTPS %s BERUBAH!
                    Kemungkinan ada serangan man-in-the-middle, atau sertifikat server memang diganti.
                    Fingerprint baru: %s
                    Fingerprint tersimpan: %s
                    Koneksi ditolak. Kalau perubahan ini memang sah (sudah dicek ke server), hapus sertifikat lama lewat
                    klik kanan host > Lupakan sertifikat FTPS.""".formatted(c.hostLabel(), c.presented(), c.known());
            case FtpsTrust.Rejection.NotTrusted n ->
                    "Sertifikat FTPS " + n.hostLabel() + " tidak dipercaya. Koneksi dibatalkan.";
            case FtpsTrust.Rejection.Mismatch m ->
                    "Sertifikat FTPS " + m.hostLabel() + " berbeda antar-koneksi. Koneksi ditolak.";
        };
    }

    /** Balasan server (sudah dipangkas, tanpa karakter kontrol) untuk pesan error. */
    static String reply(FTPClient client) {
        String r = client.getReplyString();
        if (r == null || r.isBlank()) {
            return "tanpa balasan";
        }
        String clean = r.strip().replaceAll("\\p{Cntrl}+", " ");
        return clean.length() > 200 ? clean.substring(0, 200) + "..." : clean;
    }
}

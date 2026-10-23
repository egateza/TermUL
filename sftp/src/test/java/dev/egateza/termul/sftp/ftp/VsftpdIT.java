package dev.egateza.termul.sftp.ftp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.Protocol;
import dev.egateza.termul.sftp.RemoteEntry;
import dev.egateza.termul.sftp.TransferListener;
import dev.egateza.termul.ssh.ConnectCancel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * FTPS terhadap vsftpd asli dengan {@code require_ssl_reuse=YES} (default vsftpd): kanal data wajib memakai ulang sesi
 * TLS kanal kontrol, plus {@code SITE CHMOD} dan rename yang menimpa file (butuh Docker).
 */
@Testcontainers(disabledWithoutDocker = true)
class VsftpdIT {

    private static final String USER = "dev";
    private static final String PASSWORD = "devpass";
    /** Port passive dipetakan sama persis di host: PASV memberi port container. */
    private static final int PASV_MIN = 30_100;
    private static final int PASV_MAX = 30_103;

    static final class VsftpdContainer extends GenericContainer<VsftpdContainer> {
        VsftpdContainer() {
            super(new ImageFromDockerfile("termul-vsftpd-it", false).withDockerfileFromBuilder(b -> b
                    .from("alpine:3.20")
                    .run("apk add --no-cache vsftpd openssl tini"
                            + " && adduser -D " + USER + " && echo '" + USER + ":" + PASSWORD + "' | chpasswd"
                            + " && openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj /CN=vsftpd-it"
                            + " -keyout /etc/ssl/private/vsftpd.key -out /etc/ssl/certs/vsftpd.crt"
                            + " && printf '%s\\n' listen=YES background=NO seccomp_sandbox=NO anonymous_enable=NO"
                            + " local_enable=YES write_enable=YES chmod_enable=YES local_umask=022"
                            + " pasv_enable=YES pasv_min_port=" + PASV_MIN + " pasv_max_port=" + PASV_MAX
                            + " ssl_enable=YES force_local_logins_ssl=YES force_local_data_ssl=YES"
                            + " require_ssl_reuse=YES"
                            + " rsa_cert_file=/etc/ssl/certs/vsftpd.crt rsa_private_key_file=/etc/ssl/private/vsftpd.key"
                            + " > /etc/vsftpd/vsftpd.conf")
                    // vsftpd sebagai PID 1 ikut mewarisi proses sesi yatim lalu crash (SIGSEGV); tini jadi init-nya
                    .entryPoint("/sbin/tini", "--")
                    .cmd("vsftpd", "/etc/vsftpd/vsftpd.conf")
                    .build()));
            withExposedPorts(21);
            for (int port = PASV_MIN; port <= PASV_MAX; port++) {
                addFixedExposedPort(port, port);
            }
            waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
        }
    }

    @Container
    static final VsftpdContainer VSFTPD = new VsftpdContainer();

    @TempDir
    Path tmp;

    private FtpFileService files;
    private String work;

    @BeforeEach
    void setUp() throws Exception {
        var profile = new HostProfile(UUID.randomUUID(), "vsftpd", "", VSFTPD.getHost(), VSFTPD.getMappedPort(21), USER,
                null, null, null, EnvironmentTag.DEV, null, null, false, null, null, null, Protocol.FTPS);
        var connector = new FtpConnector(new FtpFileServiceTest.Passwords(PASSWORD),
                new FtpsCertStore(tmp.resolve("ftps_known_certs")), info -> true);
        files = connector.open(profile, new ConnectCancel());
        work = files.home() + "/it-" + UUID.randomUUID();
        files.mkdir(work);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (files != null) {
            files.delete(work, true);
            files.close();
        }
    }

    @Test
    void transferLewatKanalDataYangMemakaiUlangSesiTls() throws Exception {
        Path local = Files.writeString(tmp.resolve("a.txt"), "halo vsftpd");
        files.upload(local, work + "/a.txt", 0640, TransferListener.NONE);
        assertThat(files.list(work)).extracting(RemoteEntry::name).containsExactly("a.txt");
        Path back = tmp.resolve("back.txt");
        files.download(work + "/a.txt", back, TransferListener.NONE);
        assertThat(Files.readString(back)).isEqualTo("halo vsftpd");
    }

    @Test
    void uploadMenimpaFileDanMempertahankanMode() throws Exception {
        Path local = Files.writeString(tmp.resolve("app.conf"), "v1");
        files.upload(local, work + "/app.conf", null, TransferListener.NONE);
        files.chmod(work + "/app.conf", 0600);

        Files.writeString(local, "v2 lebih panjang");
        files.upload(local, work + "/app.conf", null, TransferListener.NONE);

        RemoteEntry entry = files.stat(work + "/app.conf");
        assertThat(entry.size()).isEqualTo("v2 lebih panjang".length());
        assertThat(entry.mode()).isEqualTo(0600);
        assertThat(files.list(work)).extracting(RemoteEntry::name).containsExactly("app.conf"); // tanpa file temp
    }
}

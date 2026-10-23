package dev.egateza.termul.sftp.ftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.core.profile.Protocol;
import dev.egateza.termul.sftp.RemoteEntry;
import dev.egateza.termul.sftp.TransferListener;
import dev.egateza.termul.ssh.ConnectCancel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FTPS eksplisit dengan sertifikat self-signed: TOFU, sertifikat berubah, dan kanal data terenkripsi. */
class FtpsTest {

    @TempDir
    Path tmp;

    private FtpTestServer server;
    private FtpsCertStore store;
    private final List<FtpsCertPrompt.CertInfo> prompted = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        server = new FtpTestServer(tmp, FtpTestServer.selfSignedKeystore(tmp, "server"));
        store = new FtpsCertStore(tmp.resolve("ftps_known_certs"));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private FtpConnector connector(boolean accept) {
        return new FtpConnector(new FtpFileServiceTest.Passwords(FtpTestServer.PASSWORD), store, info -> {
            prompted.add(info);
            return accept;
        });
    }

    @Test
    void sertifikatSelfSignedDitolakKalauUserTidakPercaya() {
        assertThatThrownBy(() -> connector(false).open(server.profile(Protocol.FTPS), new ConnectCancel()))
                .isInstanceOf(FtpsCertRejectedException.class)
                .hasMessageContaining("tidak dipercaya");
        assertThat(prompted).hasSize(1);
        assertThat(prompted.getFirst().fingerprint()).startsWith("SHA256:");
        assertThat(prompted.getFirst().hostLabel()).isEqualTo("127.0.0.1:" + server.port);
        assertThat(store.entries()).isEmpty();
    }

    @Test
    void tofuSekaliLaluTransferTerenkripsiTanpaDitanyaLagi() throws Exception {
        try (var files = connector(true).open(server.profile(Protocol.FTPS), new ConnectCancel())) {
            Path local = Files.writeString(tmp.resolve("rahasia.txt"), "isi terenkripsi");
            files.upload(local, "/rahasia.txt", null, TransferListener.NONE); // PROT P: kanal data TLS
            assertThat(files.list("/")).extracting(RemoteEntry::name).containsExactly("rahasia.txt");
            Path back = tmp.resolve("back.txt");
            files.download("/rahasia.txt", back, TransferListener.NONE);
            assertThat(Files.readString(back)).isEqualTo("isi terenkripsi");
        }
        assertThat(prompted).hasSize(1); // kanal data & koneksi transfer memakai sertifikat yang sudah diterima
        assertThat(store.find("127.0.0.1", server.port)).contains(prompted.getFirst().fingerprint());

        try (var again = connector(false).open(server.profile(Protocol.FTPS), new ConnectCancel())) {
            assertThat(again.exists("/rahasia.txt")).isTrue();
        }
        assertThat(prompted).hasSize(1); // sudah dipercaya: tidak ditanya lagi
    }

    @Test
    void sertifikatBerubahSelaluDitolak() {
        store.trust("127.0.0.1", server.port, "SHA256:fingerprintLamaYangBerbeda");
        assertThatThrownBy(() -> connector(true).open(server.profile(Protocol.FTPS), new ConnectCancel()))
                .isInstanceOf(FtpsCertRejectedException.class)
                .hasMessageContaining("BERUBAH")
                .hasMessageContaining("SHA256:fingerprintLamaYangBerbeda");
        assertThat(prompted).isEmpty(); // tidak ditawari "percaya saja"
        assertThat(store.find("127.0.0.1", server.port)).contains("SHA256:fingerprintLamaYangBerbeda");
    }

    @Test
    void ftpBiasaKeServerFtpsTetapBisaTanpaTls() throws Exception {
        // server ini tidak mewajibkan TLS; profil FTP biasa tidak pernah memakai sertifikat
        try (var files = connector(false).open(server.profile(Protocol.FTP), new ConnectCancel())) {
            assertThat(files.list("/")).isEmpty();
        }
        assertThat(prompted).isEmpty();
    }
}

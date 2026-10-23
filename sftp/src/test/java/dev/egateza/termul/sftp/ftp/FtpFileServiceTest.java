package dev.egateza.termul.sftp.ftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.Protocol;
import dev.egateza.termul.sftp.RemoteEntry;
import dev.egateza.termul.sftp.RemoteFileException;
import dev.egateza.termul.sftp.SftpFixture;
import dev.egateza.termul.sftp.TransferListener;
import dev.egateza.termul.sftp.TreeTransfer;
import dev.egateza.termul.sftp.edit.EditCache;
import dev.egateza.termul.sftp.edit.RemoteEditSession;
import dev.egateza.termul.ssh.ConnectCancel;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FtpFileServiceTest {

    @TempDir
    Path tmp;

    private FtpTestServer server;
    private FtpFileService files;

    /** Memberi password berurutan per percobaan; null = user membatalkan. */
    static final class Passwords implements CredentialProvider {
        final List<String> answers;
        final List<Integer> attempts = new ArrayList<>();

        Passwords(String... answers) {
            this.answers = List.of(answers);
        }

        @Override
        public char[] password(HostProfile profile, int attempt) {
            attempts.add(attempt);
            return attempt <= answers.size() ? answers.get(attempt - 1).toCharArray() : null;
        }

        @Override
        public char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt) {
            return null;
        }
    }

    private FtpConnector connector(CredentialProvider creds) {
        return new FtpConnector(creds, new FtpsCertStore(tmp.resolve("ftps_known_certs")), FtpsCertPrompt.REJECT);
    }

    @BeforeEach
    void setUp() throws Exception {
        server = new FtpTestServer(tmp, null);
        files = connector(new Passwords(FtpTestServer.PASSWORD)).open(server.profile(Protocol.FTP), new ConnectCancel());
    }

    @AfterEach
    void tearDown() {
        if (files != null) {
            files.close();
        }
        server.close();
    }

    @Test
    void homeListMkdirUploadDownloadRenameDelete() throws Exception {
        assertThat(files.home()).isEqualTo("/");
        files.mkdir("/app");
        Path local = Files.writeString(tmp.resolve("a.txt"), "halo ftp\n");
        files.upload(local, "/app/a.txt", null, TransferListener.NONE);

        assertThat(Files.readString(server.root.resolve("app/a.txt"))).isEqualTo("halo ftp\n");
        assertThat(files.list("/")).extracting(RemoteEntry::name).containsExactly("app");
        RemoteEntry entry = files.stat("/app/a.txt");
        assertThat(entry.type()).isEqualTo(RemoteEntry.Type.FILE);
        assertThat(entry.size()).isEqualTo(9);
        assertThat(files.list("/app")).extracting(RemoteEntry::name).containsExactly("a.txt"); // tanpa file temp

        Path back = tmp.resolve("back/a.txt");
        files.download("/app/a.txt", back, TransferListener.NONE);
        assertThat(Files.readString(back)).isEqualTo("halo ftp\n");

        files.rename("/app/a.txt", "/app/b.txt");
        assertThat(files.exists("/app/a.txt")).isFalse();
        assertThat(files.exists("/app/b.txt")).isTrue();
        Files.writeString(server.root.resolve("app/c.txt"), "x");
        assertThatThrownBy(() -> files.rename("/app/b.txt", "/app/c.txt"))
                .isInstanceOf(RemoteFileException.class).hasMessageContaining("sudah ada");

        files.delete("/app", true);
        assertThat(Files.exists(server.root.resolve("app"))).isFalse();
    }

    @Test
    void uploadMenimpaFileLama() throws Exception {
        Files.writeString(server.root.resolve("config.ini"), "lama");
        Path local = Files.writeString(tmp.resolve("config.ini"), "baru dan lebih panjang");
        files.upload(local, "/config.ini", null, TransferListener.NONE);
        assertThat(Files.readString(server.root.resolve("config.ini"))).isEqualTo("baru dan lebih panjang");
        assertThat(files.list("/")).extracting(RemoteEntry::name).containsExactly("config.ini");
    }

    @Test
    void canonicalizeDanPathTidakAda() throws Exception {
        Files.createDirectories(server.root.resolve("var/www"));
        assertThat(files.canonicalize("/var/./www/../www")).isEqualTo("/var/www");
        assertThat(files.canonicalize("var")).isEqualTo("/var");
        assertThatThrownBy(() -> files.canonicalize("/tidak/ada")).isInstanceOf(RemoteFileException.class);
        assertThat(files.exists("/tidak/ada.txt")).isFalse();
        assertThatThrownBy(() -> files.stat("/tidak/ada.txt")).hasMessageContaining("tidak ditemukan");
    }

    @Test
    void listingTetapBisaSelamaDownloadBerjalan() throws Exception {
        Files.write(server.root.resolve("besar.bin"), new byte[512 * 1024]);
        Files.createDirectories(server.root.resolve("lain"));
        try (InputStream in = files.openRead("/besar.bin")) {
            assertThat(in.read(new byte[1024])).isPositive();
            // koneksi transfer terpisah: koneksi kontrol bebas untuk listing
            assertThat(files.list("/")).extracting(RemoteEntry::name).containsExactly("lain", "besar.bin");
            in.transferTo(java.io.OutputStream.nullOutputStream());
        }
        assertThat(files.readSmallText("/besar.bin", 4)).hasSize(4);
    }

    @Test
    void transferDibatalkanMenghapusFileTemp() throws Exception {
        Path local = Files.write(tmp.resolve("besar.bin"), new byte[256 * 1024]);
        var progress = new AtomicInteger();
        var cancelAfterFirstChunk = new TransferListener() {
            @Override
            public void progress(long transferred, long total) {
                progress.incrementAndGet();
            }

            @Override
            public boolean isCancelled() {
                return progress.get() > 1;
            }
        };
        assertThatThrownBy(() -> files.upload(local, "/besar.bin", null, cancelAfterFirstChunk))
                .isInstanceOf(RemoteFileException.Cancelled.class);
        assertThat(files.list("/")).isEmpty();
    }

    @Test
    void remoteEditLewatFtpMendeteksiKonflik() throws Exception {
        Path remote = Files.writeString(server.root.resolve("app.conf"), "port=80\n");
        var cache = new EditCache(tmp.resolve("edit-cache"));
        var session = RemoteEditSession.open(files, cache, java.util.UUID.randomUUID(), "/app.conf",
                RemoteEditSession.SFTP_UPLOADER);
        Files.writeString(session.localFile(), "port=8080\n");
        assertThat(session.sync(RemoteEditSession.SyncOptions.DEFAULT))
                .isInstanceOf(RemoteEditSession.SyncResult.Uploaded.class);
        assertThat(Files.readString(remote)).isEqualTo("port=8080\n");

        Files.writeString(remote, "diubah orang lain di server\n");
        Files.setLastModifiedTime(remote, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 120_000));
        Files.writeString(session.localFile(), "port=9090\n");
        assertThat(session.sync(RemoteEditSession.SyncOptions.DEFAULT))
                .isInstanceOf(RemoteEditSession.SyncResult.Conflict.class);
        session.close();
    }

    @Test
    void salinFolderDariSftpKeFtp() throws Exception {
        try (var sftp = new SftpFixture(tmp.resolve("sftp"))) {
            Files.createDirectories(sftp.remoteRoot.resolve("site/css"));
            Files.writeString(sftp.remoteRoot.resolve("site/index.html"), "<h1>hai</h1>");
            Files.writeString(sftp.remoteRoot.resolve("site/css/app.css"), "body{}");
            var result = TreeTransfer.copy(sftp.files, "/site", files, "/", existing -> true,
                    TransferListener.NONE);
            assertThat(result).isNotNull();
        }
        assertThat(Files.readString(server.root.resolve("site/index.html"), StandardCharsets.UTF_8))
                .isEqualTo("<h1>hai</h1>");
        assertThat(Files.readString(server.root.resolve("site/css/app.css"))).isEqualTo("body{}");
    }

    @Test
    void passwordSalahDitanyaLagiLaluBerhasil() throws Exception {
        var creds = new Passwords("salah", FtpTestServer.PASSWORD);
        try (var second = connector(creds).open(server.profile(Protocol.FTP), new ConnectCancel())) {
            assertThat(second.list("/")).isEmpty();
        }
        assertThat(creds.attempts).containsExactly(1, 2);
    }

    @Test
    void loginDibatalkanAtauSelaluSalah() {
        assertThatThrownBy(() -> connector(new Passwords()).open(server.profile(Protocol.FTP), new ConnectCancel()))
                .isInstanceOf(RemoteFileException.class).hasMessageContaining("dibatalkan");
        assertThatThrownBy(() -> connector(new Passwords("a", "b", "c", "d"))
                .open(server.profile(Protocol.FTP), new ConnectCancel()))
                .isInstanceOf(RemoteFileException.class).hasMessageContaining("username atau password salah");
    }

    @Test
    void koneksiKontrolYangPutusDibukaLagi() throws Exception {
        Files.writeString(server.root.resolve("x.txt"), "x");
        files.close();
        files = connector(new Passwords(FtpTestServer.PASSWORD)).open(server.profile(Protocol.FTP), new ConnectCancel());
        // putus paksa koneksi kontrol (seperti idle timeout server)
        var field = FtpFileService.class.getDeclaredField("control");
        field.setAccessible(true);
        FtpFileService.disconnectQuietly((org.apache.commons.net.ftp.FTPClient) field.get(files));
        assertThat(files.list("/")).extracting(RemoteEntry::name).containsExactly("x.txt");
        assertThat(files.isOpen()).isTrue();
    }

    @Test
    void serviceTertutupMenolakOperasi() {
        files.close();
        assertThat(files.isOpen()).isFalse();
        assertThatThrownBy(() -> files.list("/")).hasMessageContaining("ditutup");
    }

    @Test
    void jalurListTanpaMlst() throws Exception {
        files.useListOnly(); // seperti vsftpd: LIST + MDTM
        Files.createDirectories(server.root.resolve("www"));
        Path local = Files.writeString(tmp.resolve("index.html"), "lama");
        files.upload(local, "/www/index.html", null, TransferListener.NONE);
        Files.writeString(local, "baru");
        files.upload(local, "/www/index.html", null, TransferListener.NONE);

        assertThat(files.list("/www")).extracting(RemoteEntry::name).containsExactly("index.html");
        RemoteEntry entry = files.stat("/www/index.html");
        assertThat(entry.type()).isEqualTo(RemoteEntry.Type.FILE);
        assertThat(entry.size()).isEqualTo(4);
        assertThat(entry.modified()).isCloseTo( // MDTM: presisi detik atau lebih, bukan menit dari LIST
                Files.getLastModifiedTime(server.root.resolve("www/index.html")).toInstant(),
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(files.stat("/www").type()).isEqualTo(RemoteEntry.Type.DIRECTORY);
        assertThat(files.exists("/www/tidak-ada")).isFalse();
        files.delete("/www", true);
        assertThat(files.list("/")).isEmpty();
    }

    @Test
    void normalizePath() {
        assertThat(FtpFileService.normalize("/a/./b/../c//d/")).isEqualTo("/a/c/d");
        assertThat(FtpFileService.normalize("/../..")).isEqualTo("/");
    }
}

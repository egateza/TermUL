package dev.egateza.myterm.sftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.myterm.core.profile.AuthMethod;
import dev.egateza.myterm.core.profile.EnvironmentTag;
import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.ssh.SessionManager;
import dev.egateza.myterm.ssh.SshSettings;
import dev.egateza.myterm.ssh.auth.CredentialProvider;
import dev.egateza.myterm.ssh.hostkey.HostKeyInfo;
import dev.egateza.myterm.ssh.hostkey.HostKeyPrompt;
import dev.egateza.myterm.ssh.hostkey.KnownHostsStore;
import dev.egateza.myterm.ssh.testing.TestSshServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteFileServiceTest {

    @TempDir
    Path tmp;

    private Path remoteRoot;
    private Path local;
    private TestSshServer server;
    private SessionManager sessions;
    private RemoteFileService files;

    @BeforeEach
    void setUp() throws Exception {
        remoteRoot = Files.createDirectories(tmp.resolve("remote"));
        local = Files.createDirectories(tmp.resolve("local"));
        server = new TestSshServer(tmp.resolve("hostkey.ser"), s -> {
            s.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
            s.setFileSystemFactory(new VirtualFileSystemFactory(remoteRoot));
        });
        var trust = new HostKeyPrompt() {
            @Override
            public boolean confirmUnknownHost(HostKeyInfo info) {
                return true;
            }

            @Override
            public void hostKeyChanged(HostKeyInfo presented, List<String> knownFingerprints) {
            }
        };
        var creds = new CredentialProvider() {
            @Override
            public char[] password(HostProfile p, int attempt) {
                return TestSshServer.PASSWORD.toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile p, Path keyFile, int attempt) {
                return null;
            }
        };
        sessions = new SessionManager(new KnownHostsStore(tmp.resolve("known_hosts")), trust, creds,
                new SshSettings(Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(10),
                        Duration.ZERO, 0, Duration.ZERO));
        var profile = new HostProfile(UUID.randomUUID(), "sftp", "", "127.0.0.1", server.port(), TestSshServer.USER,
                AuthMethod.PASSWORD, null, null, EnvironmentTag.DEV, null, null, false);
        files = RemoteFileService.open(sessions.acquire(profile));
    }

    @AfterEach
    void tearDown() throws Exception {
        files.close();
        sessions.close();
        server.close();
    }

    @Test
    void listMkdirRenameDelete() throws Exception {
        Files.writeString(remoteRoot.resolve("b.txt"), "hello");
        Files.createDirectories(remoteRoot.resolve("zdir"));
        Files.createDirectories(remoteRoot.resolve("adir"));

        assertThat(files.home()).isEqualTo("/");
        assertThat(files.list("/")).extracting(RemoteEntry::name).containsExactly("adir", "zdir", "b.txt");
        var b = files.stat("/b.txt");
        assertThat(b.type()).isEqualTo(RemoteEntry.Type.FILE);
        assertThat(b.size()).isEqualTo(5);

        files.mkdir("/adir/sub");
        Files.writeString(remoteRoot.resolve("adir/sub/x"), "x");
        files.rename("/b.txt", "/zdir/c.txt");
        assertThat(remoteRoot.resolve("zdir/c.txt")).hasContent("hello");
        assertThatThrownBy(() -> files.rename("/zdir/c.txt", "/adir/sub/x")).hasMessageContaining("sudah ada");

        assertThatThrownBy(() -> files.delete("/adir", false)).isInstanceOf(RemoteFileException.class);
        files.delete("/adir", true);
        assertThat(remoteRoot.resolve("adir")).doesNotExist();
        assertThat(files.exists("/adir")).isFalse();
    }

    @Test
    void uploadDownloadRoundTripDenganProgress() throws Exception {
        byte[] data = new byte[1_234_567];
        new Random(42).nextBytes(data);
        Path src = Files.write(local.resolve("big.bin"), data);
        var last = new AtomicLong();

        files.upload(src, "/big.bin", null, (done, total) -> last.set(done));

        assertThat(last).hasValue(data.length);
        assertThat(Files.readAllBytes(remoteRoot.resolve("big.bin"))).isEqualTo(data);
        assertThat(remoteRoot).isDirectoryNotContaining("glob:**.tmp");

        Path dst = local.resolve("copy/big.bin");
        files.download("/big.bin", dst, TransferListener.NONE);
        assertThat(Files.readAllBytes(dst)).isEqualTo(data);
        assertThat(dst.getParent()).isDirectoryNotContaining("glob:**.part");
    }

    @Test
    void uploadMenimpaFileLamaSecaraUtuh() throws Exception {
        Files.writeString(remoteRoot.resolve("config.yml"), "lama: 1\n");
        Path src = Files.writeString(local.resolve("config.yml"), "baru: 2\n");

        files.upload(src, "/config.yml", null, TransferListener.NONE);

        assertThat(remoteRoot.resolve("config.yml")).hasContent("baru: 2\n");
        try (var s = Files.list(remoteRoot)) {
            assertThat(s.map(p -> p.getFileName().toString())).containsExactly("config.yml");
        }
    }

    @Test
    void uploadDibatalkanTidakMeninggalkanSampahDanFileLamaUtuh() throws Exception {
        Files.writeString(remoteRoot.resolve("data.bin"), "asli");
        byte[] data = new byte[2_000_000];
        Path src = Files.write(local.resolve("data.bin"), data);
        var cancelAfter = new TransferListener() {
            long seen;

            @Override
            public void progress(long done, long total) {
                seen = done;
            }

            @Override
            public boolean isCancelled() {
                return seen > 100_000;
            }
        };

        assertThatThrownBy(() -> files.upload(src, "/data.bin", null, cancelAfter))
                .isInstanceOf(RemoteFileException.Cancelled.class);

        assertThat(remoteRoot.resolve("data.bin")).hasContent("asli");
        try (var s = Files.list(remoteRoot)) {
            assertThat(s.map(p -> p.getFileName().toString())).containsExactly("data.bin");
        }
    }

    @Test
    void downloadDibatalkanTidakMenimpaFileLokal() throws Exception {
        Files.write(remoteRoot.resolve("x.bin"), new byte[1_000_000]);
        Path dst = Files.writeString(local.resolve("x.bin"), "lokal");
        var cancel = new TransferListener() {
            @Override
            public void progress(long done, long total) {
            }

            @Override
            public boolean isCancelled() {
                return true;
            }
        };

        assertThatThrownBy(() -> files.download("/x.bin", dst, cancel)).isInstanceOf(RemoteFileException.Cancelled.class);
        assertThat(dst).hasContent("lokal");
        assertThat(local).isDirectoryNotContaining("glob:**.part");
    }

    @Test
    void fileTidakAdaMemberiPesanJelas() {
        assertThatThrownBy(() -> files.stat("/nope.txt")).hasMessageContaining("tidak ditemukan");
        assertThatThrownBy(() -> files.list("/nope")).hasMessageContaining("/nope");
    }
}

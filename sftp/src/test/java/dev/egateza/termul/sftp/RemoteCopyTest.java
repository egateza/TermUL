package dev.egateza.termul.sftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Salin antar-server: dua server SFTP in-process, isi tidak pernah lewat disk lokal. */
class RemoteCopyTest {

    @TempDir
    Path tmpA;
    @TempDir
    Path tmpB;

    private SftpFixture a;
    private SftpFixture b;

    @BeforeEach
    void setUp() throws Exception {
        a = new SftpFixture(tmpA);
        b = new SftpFixture(tmpB);
    }

    @AfterEach
    void tearDown() throws Exception {
        a.close();
        b.close();
    }

    @Test
    void salinFileKeServerLain() throws Exception {
        Files.writeString(a.remoteRoot.resolve("dump.sql"), "x".repeat(100_000));
        Files.createDirectories(b.remoteRoot.resolve("backup"));
        var last = new AtomicLong();

        b.files.copyFrom(a.files, "/dump.sql", "/backup/dump.sql", (done, total) -> last.set(done));

        assertThat(b.remoteRoot.resolve("backup/dump.sql")).hasContent("x".repeat(100_000));
        assertThat(last.get()).isEqualTo(100_000);
        try (var files = Files.list(b.remoteRoot.resolve("backup"))) {
            assertThat(files).hasSize(1); // file temp sudah di-rename
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void fileBaruMemakaiModeSumberFileLamaTetap() throws Exception {
        Path script = Files.writeString(a.remoteRoot.resolve("run.sh"), "#!/bin/sh\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-x---"));
        Path existing = Files.writeString(b.remoteRoot.resolve("lama.sh"), "lama");
        Files.setPosixFilePermissions(existing, PosixFilePermissions.fromString("rw-------"));

        b.files.copyFrom(a.files, "/run.sh", "/run.sh", (d, t) -> { });
        b.files.copyFrom(a.files, "/run.sh", "/lama.sh", (d, t) -> { });

        assertThat(Files.getPosixFilePermissions(b.remoteRoot.resolve("run.sh")))
                .isEqualTo(PosixFilePermissions.fromString("rwxr-x---"));
        assertThat(Files.getPosixFilePermissions(b.remoteRoot.resolve("lama.sh")))
                .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        assertThat(b.remoteRoot.resolve("lama.sh")).hasContent("#!/bin/sh\n");
    }

    @Test
    void salinFolderBesertaIsinya() throws Exception {
        Path src = Files.createDirectories(a.remoteRoot.resolve("app/conf"));
        Files.writeString(src.resolve("app.yml"), "port: 80");
        Files.writeString(a.remoteRoot.resolve("app/readme.txt"), "halo");
        Files.createDirectories(a.remoteRoot.resolve("app/empty"));
        Files.createDirectories(b.remoteRoot.resolve("opt"));

        var r = TreeTransfer.copy(a.files, "/app", b.files, "/opt", t -> false, (d, t) -> { });

        assertThat(r.files()).isEqualTo(2);
        assertThat(r.directories()).isEqualTo(3);
        assertThat(b.remoteRoot.resolve("opt/app/conf/app.yml")).hasContent("port: 80");
        assertThat(b.remoteRoot.resolve("opt/app/readme.txt")).hasContent("halo");
        assertThat(b.remoteRoot.resolve("opt/app/empty")).isDirectory();
    }

    @Test
    void folderTujuanSudahAdaMintaKonfirmasi() throws Exception {
        Files.createDirectories(a.remoteRoot.resolve("app"));
        Files.writeString(a.remoteRoot.resolve("app/baru.txt"), "baru");
        Files.createDirectories(b.remoteRoot.resolve("app"));
        Files.writeString(b.remoteRoot.resolve("app/lama.txt"), "lama");

        assertThat(TreeTransfer.copy(a.files, "/app", b.files, "/", t -> false, (d, t) -> { })).isNull();
        assertThat(b.remoteRoot.resolve("app/baru.txt")).doesNotExist();

        TreeTransfer.copy(a.files, "/app", b.files, "/", t -> true, (d, t) -> { });
        assertThat(b.remoteRoot.resolve("app/baru.txt")).hasContent("baru");
        assertThat(b.remoteRoot.resolve("app/lama.txt")).hasContent("lama");
    }

    @Test
    void salinDiServerYangSama() throws Exception {
        Files.writeString(a.remoteRoot.resolve("a.txt"), "isi");
        Files.createDirectories(a.remoteRoot.resolve("dst"));

        a.files.copyFrom(a.files, "/a.txt", "/dst/a.txt", (d, t) -> { });

        assertThat(a.remoteRoot.resolve("dst/a.txt")).hasContent("isi");
        assertThat(a.remoteRoot.resolve("a.txt")).hasContent("isi");
    }

    @Test
    void batalDiTengahTidakMeninggalkanFileSetengahJadi() throws Exception {
        Files.writeString(a.remoteRoot.resolve("big.bin"), "y".repeat(500_000));
        var listener = new TransferListener() {
            @Override
            public void progress(long done, long total) {
            }

            @Override
            public boolean isCancelled() {
                return true;
            }
        };

        assertThatThrownBy(() -> b.files.copyFrom(a.files, "/big.bin", "/big.bin", listener))
                .isInstanceOf(RemoteFileException.Cancelled.class);
        try (var files = Files.list(b.remoteRoot)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void bukanFileDitolak() throws Exception {
        Files.createDirectories(a.remoteRoot.resolve("dir"));
        assertThatThrownBy(() -> b.files.copyFrom(a.files, "/dir", "/dir", (d, t) -> { }))
                .isInstanceOf(RemoteFileException.class).hasMessageContaining("Bukan file");
    }
}

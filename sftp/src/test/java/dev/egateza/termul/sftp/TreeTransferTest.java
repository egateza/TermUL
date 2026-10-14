package dev.egateza.termul.sftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TreeTransferTest {

    @TempDir
    Path tmp;

    private SftpFixture fx;
    private Path local;

    @BeforeEach
    void setUp() throws Exception {
        fx = new SftpFixture(tmp);
        local = Files.createDirectories(tmp.resolve("local"));
    }

    @AfterEach
    void tearDown() throws Exception {
        fx.close();
    }

    @Test
    void uploadFolderBesertaSubfolderDanFolderKosong() throws Exception {
        Path src = Files.createDirectories(local.resolve("app/controllers"));
        Files.writeString(src.resolve("a.php"), "<?php echo 1;");
        Files.writeString(local.resolve("app/readme.txt"), "halo");
        Files.createDirectories(local.resolve("app/empty"));
        Files.createDirectories(fx.remoteRoot.resolve("www"));
        var last = new AtomicLong();

        var result = TreeTransfer.upload(fx.files, local.resolve("app"), "/www", t -> false,
                (done, total) -> last.set(done));

        assertThat(result.files()).isEqualTo(2);
        assertThat(result.directories()).isEqualTo(3); // app, app/controllers, app/empty
        assertThat(result.bytes()).isEqualTo(13 + 4);
        assertThat(last.get()).isEqualTo(17);
        assertThat(fx.remoteRoot.resolve("www/app/controllers/a.php")).hasContent("<?php echo 1;");
        assertThat(fx.remoteRoot.resolve("www/app/readme.txt")).hasContent("halo");
        assertThat(fx.remoteRoot.resolve("www/app/empty")).isDirectory();
    }

    @Test
    void downloadFolderBesertaSubfolder() throws Exception {
        Path src = Files.createDirectories(fx.remoteRoot.resolve("logs/2026/10"));
        Files.writeString(src.resolve("app.log"), "baris 1\n");
        Files.writeString(fx.remoteRoot.resolve("logs/index.txt"), "x");

        var result = TreeTransfer.download(fx.files, "/logs", local, t -> false, TransferListener.NONE);

        assertThat(result.files()).isEqualTo(2);
        assertThat(local.resolve("logs/2026/10/app.log")).hasContent("baris 1\n");
        assertThat(local.resolve("logs/index.txt")).hasContent("x");
        assertThat(result.skipped()).isEmpty();
    }

    @Test
    void tujuanSudahAdaDitanyakanSekaliDanBisaDibatalkan() throws Exception {
        Files.createDirectories(fx.remoteRoot.resolve("conf"));
        Files.writeString(fx.remoteRoot.resolve("conf/a.ini"), "baru");
        Files.createDirectories(local.resolve("conf"));
        Files.writeString(local.resolve("conf/a.ini"), "lama");
        var asked = new ArrayList<String>();

        assertThat(TreeTransfer.download(fx.files, "/conf", local, t -> {
            asked.add(t);
            return false;
        }, TransferListener.NONE)).isNull();
        assertThat(local.resolve("conf/a.ini")).hasContent("lama");

        var result = TreeTransfer.download(fx.files, "/conf", local, t -> {
            asked.add(t);
            return true;
        }, TransferListener.NONE);
        assertThat(result.files()).isEqualTo(1);
        assertThat(local.resolve("conf/a.ini")).hasContent("baru");
        assertThat(asked).hasSize(2);
    }

    @Test
    void batalDiTengahJalan() throws Exception {
        Path src = Files.createDirectories(local.resolve("many"));
        for (int i = 0; i < 5; i++) {
            Files.writeString(src.resolve("f" + i + ".txt"), "isi " + i);
        }
        var progressCalls = new AtomicInteger();
        var listener = new TransferListener() {
            @Override
            public void progress(long transferred, long total) {
                progressCalls.incrementAndGet();
            }

            @Override
            public boolean isCancelled() {
                return progressCalls.get() > 0; // batal setelah file pertama mulai
            }
        };

        assertThatThrownBy(() -> TreeTransfer.upload(fx.files, src, "/", t -> true, listener))
                .isInstanceOf(RemoteFileException.Cancelled.class);
        try (var remaining = Files.list(fx.remoteRoot.resolve("many"))) {
            assertThat(remaining.filter(p -> !p.getFileName().toString().startsWith("."))).hasSizeLessThan(5);
        }
    }

    @Test
    void namaRemoteBerbahayaDitolak() {
        for (String bad : List.of("..", ".", "", "a/b", "a\\b")) {
            assertThatThrownBy(() -> TreeTransfer.safeLocalName(bad)).isInstanceOf(RemoteFileException.class);
        }
    }
}

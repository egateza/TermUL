package dev.egateza.termul.app.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.egateza.termul.core.config.EditorConfig;
import dev.egateza.termul.sftp.SftpFixture;
import dev.egateza.termul.sftp.SftpLinks;
import dev.egateza.termul.sftp.edit.EditCache;
import dev.egateza.termul.sftp.edit.RemoteEditSession.State;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Alur Fase 4 tanpa UI: buka → "editor" (langsung keluar) → ubah file lokal → auto-upload → tutup. */
@EnabledOnOs(OS.WINDOWS)
class EditManagerTest {

    @TempDir
    Path tmp;

    private SftpFixture fx;
    private ExecutorService sshOps;
    private EditManager edits;
    private SftpLinks links;

    @BeforeEach
    void setUp() throws Exception {
        fx = new SftpFixture(tmp);
        sshOps = Executors.newVirtualThreadPerTaskExecutor();
        var fakeEditor = new EditorConfig("cmd.exe /c exit 0", Map.of());
        links = new SftpLinks(fx.sessions);
        edits = new EditManager(links, new EditCache(tmp.resolve("cache")), () -> fakeEditor, sshOps, () -> null);
    }

    @AfterEach
    void tearDown() throws Exception {
        edits.close();
        sshOps.shutdownNow();
        fx.close();
    }

    @Test
    void simpanFileLokalLangsungTerupload() throws Exception {
        Files.writeString(fx.remoteRoot.resolve("config.yml"), "port: 80\n");

        edits.open(fx.profile, "/config.yml");
        await().atMost(Duration.ofSeconds(10)).until(() -> edits.entries().size() == 1);
        var entry = edits.entries().getFirst();
        Path local = entry.session().localFile();
        assertThat(local).hasContent("port: 80\n");

        Files.writeString(local, "port: 8080\n");

        await().atMost(Duration.ofSeconds(10))
                .until(() -> Files.readString(fx.remoteRoot.resolve("config.yml")).equals("port: 8080\n"));
        await().atMost(Duration.ofSeconds(5)).until(() -> entry.session().state() == State.EDITING
                && entry.session().lastUpload() != null);

        // membuka file yang sama tidak membuat sesi kedua
        edits.open(fx.profile, "/config.yml");
        Thread.sleep(500);
        assertThat(edits.entries()).hasSize(1);

        edits.close(entry);
        assertThat(edits.entries()).isEmpty();
        assertThat(local).doesNotExist();
    }

    @Test
    void koneksiPutusSaatEditLaluSimpanSambungUlangDanTerupload() throws Exception {
        Files.writeString(fx.remoteRoot.resolve("app.conf"), "a=1\n");
        edits.open(fx.profile, "/app.conf");
        await().atMost(Duration.ofSeconds(10)).until(() -> edits.entries().size() == 1);
        var entry = edits.entries().getFirst();
        var oldFiles = entry.session().files();

        oldFiles.close(); // koneksi SFTP terputus (timeout) sementara file masih terbuka di editor
        assertThat(oldFiles.isOpen()).isFalse();
        Files.writeString(entry.session().localFile(), "a=2\n");

        await().atMost(Duration.ofSeconds(15))
                .until(() -> Files.readString(fx.remoteRoot.resolve("app.conf")).equals("a=2\n"));
        assertThat(entry.session().files()).isNotSameAs(oldFiles);
        assertThat(entry.session().files().isOpen()).isTrue();
    }

    @Test
    void aktivitasEditTampilUntukPanelSftpProfilYangSama() throws Exception {
        Files.writeString(fx.remoteRoot.resolve("x.txt"), "1");
        var messages = new java.util.concurrent.CopyOnWriteArrayList<String>();
        edits.addActivityListener(fx.profile.id(), (level, text) -> messages.add(level + ": " + text));
        edits.addActivityListener(java.util.UUID.randomUUID(), (level, text) -> messages.add("SALAH: " + text));

        edits.open(fx.profile, "/x.txt");
        await().atMost(Duration.ofSeconds(10)).until(() -> edits.entries().size() == 1);
        Files.writeString(edits.entries().getFirst().session().localFile(), "2");

        await().atMost(Duration.ofSeconds(10)).until(() -> messages.stream().anyMatch(m -> m.startsWith("SUCCESS")
                && m.contains("/x.txt berhasil diupload")));
        assertThat(messages).anyMatch(m -> m.startsWith("INFO") && m.contains("Mengupload /x.txt"));
        assertThat(messages).noneMatch(m -> m.startsWith("SALAH"));
    }
}

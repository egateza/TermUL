package dev.egateza.termul.sftp.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.sftp.RemoteFileException;
import dev.egateza.termul.sftp.SftpFixture;
import dev.egateza.termul.sftp.edit.RemoteEditSession.LineEndingPolicy;
import dev.egateza.termul.sftp.edit.RemoteEditSession.State;
import dev.egateza.termul.sftp.edit.RemoteEditSession.SyncOptions;
import dev.egateza.termul.sftp.edit.RemoteEditSession.SyncResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteEditSessionTest {

    @TempDir
    Path tmp;

    private SftpFixture fx;
    private EditCache cache;

    @BeforeEach
    void setUp() throws Exception {
        fx = new SftpFixture(tmp);
        cache = new EditCache(tmp.resolve("cache"));
    }

    @AfterEach
    void tearDown() throws Exception {
        fx.close();
    }

    private RemoteEditSession open(String name, String content) throws Exception {
        Files.writeString(fx.remoteRoot.resolve(name), content);
        return RemoteEditSession.open(fx.files, cache, fx.profile.id(), "/" + name,
                RemoteEditSession.SFTP_UPLOADER);
    }

    @Test
    void editLaluSyncMenguploadSekaliSaja() throws Exception {
        var session = open("config.yml", "a: 1\n");
        var states = new ArrayList<State>();
        session.addListener(s -> states.add(s.state()));
        assertThat(session.localFile()).hasContent("a: 1\n");
        assertThat(session.localFile().getFileName().toString()).isEqualTo("config.yml");

        assertThat(session.sync(SyncOptions.DEFAULT)).isInstanceOf(SyncResult.Unchanged.class);

        Files.writeString(session.localFile(), "a: 2\n");
        assertThat(session.sync(SyncOptions.DEFAULT)).isInstanceOf(SyncResult.Uploaded.class);
        assertThat(fx.remoteRoot.resolve("config.yml")).hasContent("a: 2\n");
        assertThat(states).containsExactly(State.UPLOADING, State.EDITING);

        // save ulang tanpa perubahan (editor atomic save) tidak mengupload lagi
        Files.writeString(session.localFile(), "a: 2\n");
        assertThat(session.sync(SyncOptions.DEFAULT)).isInstanceOf(SyncResult.Unchanged.class);
    }

    @Test
    void konflikMenahanUploadSampaiUserMemilihOverwrite() throws Exception {
        var session = open("app.conf", "x=1\n");
        Path remote = fx.remoteRoot.resolve("app.conf");
        Files.writeString(remote, "x=diubah-orang-lain\n");
        Files.setLastModifiedTime(remote, FileTime.from(Instant.now().plusSeconds(60)));
        Files.writeString(session.localFile(), "x=2\n");

        var result = session.sync(SyncOptions.DEFAULT);

        assertThat(result).isInstanceOf(SyncResult.Conflict.class);
        assertThat(session.state()).isEqualTo(State.NEEDS_ATTENTION);
        assertThat(remote).hasContent("x=diubah-orang-lain\n");

        assertThat(session.sync(new SyncOptions(true, LineEndingPolicy.ASK))).isInstanceOf(SyncResult.Uploaded.class);
        assertThat(remote).hasContent("x=2\n");
        assertThat(session.state()).isEqualTo(State.EDITING);
    }

    @Test
    void crlfDariEditorWindowsDitahanLaluDikonversi() throws Exception {
        var session = open("run.sh", "#!/bin/sh\necho hi\n");
        Files.writeString(session.localFile(), "#!/bin/sh\r\necho halo\r\n");

        var result = session.sync(SyncOptions.DEFAULT);
        assertThat(result).isInstanceOfSatisfying(SyncResult.LineEndingChanged.class,
                r -> assertThat(r.now()).isEqualTo(LineEndings.Style.CRLF));
        assertThat(fx.remoteRoot.resolve("run.sh")).hasContent("#!/bin/sh\necho hi\n");

        session.sync(new SyncOptions(false, LineEndingPolicy.CONVERT_TO_LF));
        assertThat(Files.readString(fx.remoteRoot.resolve("run.sh"))).isEqualTo("#!/bin/sh\necho halo\n");
        assertThat(Files.readString(session.localFile())).isEqualTo("#!/bin/sh\necho halo\n");
    }

    @Test
    void crlfBisaDiuploadApaAdanya() throws Exception {
        var session = open("a.txt", "1\n");
        Files.writeString(session.localFile(), "2\r\n");

        session.sync(new SyncOptions(false, LineEndingPolicy.KEEP));

        assertThat(Files.readString(fx.remoteRoot.resolve("a.txt"))).isEqualTo("2\r\n");
    }

    @Test
    void closeMenghapusCacheHanyaKalauTersinkron() throws Exception {
        var synced = open("s.txt", "s");
        Path syncedLocal = synced.localFile();
        assertThat(synced.close()).isTrue();
        assertThat(syncedLocal).doesNotExist();

        var pending = open("p.txt", "p");
        Files.writeString(pending.localFile(), "belum diupload");
        assertThat(pending.close()).isFalse();
        assertThat(pending.localFile()).hasContent("belum diupload");
        assertThat(pending.state()).isEqualTo(State.CLOSED);
    }

    @Test
    void direktoriDanFileHilangDitolak() throws Exception {
        Files.createDirectories(fx.remoteRoot.resolve("dir"));
        assertThatThrownBy(() -> RemoteEditSession.open(fx.files, cache, fx.profile.id(), "/dir",
                RemoteEditSession.SFTP_UPLOADER)).hasMessageContaining("Bukan file");
        assertThatThrownBy(() -> RemoteEditSession.open(fx.files, cache, fx.profile.id(), "/nope",
                RemoteEditSession.SFTP_UPLOADER)).isInstanceOf(RemoteFileException.class);
    }

    @Test
    void uploadGagalMenjadiNeedsAttention() throws Exception {
        var failing = new RemoteEditSession.Uploader() {
            @Override
            public void upload(dev.egateza.termul.sftp.RemoteFileService files, Path local, String remotePath)
                    throws RemoteFileException {
                throw new RemoteFileException("akses ditolak");
            }
        };
        Files.writeString(fx.remoteRoot.resolve("root.conf"), "a");
        var session = RemoteEditSession.open(fx.files, cache, fx.profile.id(), "/root.conf", failing);
        Files.writeString(session.localFile(), "b");

        assertThatThrownBy(() -> session.sync(SyncOptions.DEFAULT)).hasMessageContaining("akses ditolak");
        assertThat(session.state()).isEqualTo(State.NEEDS_ATTENTION);
        assertThat(session.hasPendingChanges()).isTrue();
    }

    @Test
    void namaCacheDisanitasi() {
        assertThat(EditCache.safeName("/srv/a&calc.exe b.yml")).isEqualTo("a_calc.exe_b.yml");
        assertThat(EditCache.safeName("/etc/nginx/sites-available/default")).isEqualTo("default");
        assertThat(EditCache.safeName("/x/CON.txt")).isEqualTo("_CON.txt");
        assertThat(EditCache.safeName("/x/..")).isEqualTo("file");
        assertThat(EditCache.safeName("/x/%(id).conf")).isEqualTo("__id_.conf");
        assertThat(EditCache.safeName("/x/" + "a".repeat(300) + ".json")).hasSize(100).endsWith(".json");
    }

    @Test
    void fileBerbedaDenganNamaSamaTidakBentrok() {
        var id = fx.profile.id();
        assertThat(cache.pathFor(id, "/a/config.yml")).isNotEqualTo(cache.pathFor(id, "/b/config.yml"));
        assertThat(cache.pathFor(id, "/a/config.yml")).isEqualTo(cache.pathFor(id, "/a/config.yml"));
    }

    @Test
    void deteksiLineEnding() {
        assertThat(LineEndings.detect("a\nb\n".getBytes())).isEqualTo(LineEndings.Style.LF);
        assertThat(LineEndings.detect("a\r\nb\r\n".getBytes())).isEqualTo(LineEndings.Style.CRLF);
        assertThat(LineEndings.detect("a\r\nb\n".getBytes())).isEqualTo(LineEndings.Style.MIXED);
        assertThat(LineEndings.detect("ab".getBytes())).isEqualTo(LineEndings.Style.NONE);
        assertThat(new String(LineEndings.toLf("a\r\nb\rc\n".getBytes()))).isEqualTo("a\nb\rc\n");
        assertThat(LineEndings.crlfIntroduced(LineEndings.Style.CRLF, LineEndings.Style.CRLF)).isFalse();
        assertThat(List.of(LineEndings.Style.values())).hasSize(4);
    }
}

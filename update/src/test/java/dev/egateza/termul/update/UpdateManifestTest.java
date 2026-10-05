package dev.egateza.termul.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdateManifestTest {

    private static final String SHA = "a".repeat(64);

    @Test
    void jsonBolakBalik() throws Exception {
        var m = new Fixtures.Release().jar("termul-app-0.1.0.jar", "app").jar("libs-x-1.0.jar", "x").manifest();

        var parsed = UpdateManifest.parse(m.toJson());

        assertThat(parsed).isEqualTo(m);
        assertThat(parsed.totalSize()).isEqualTo(4);
    }

    @Test
    void namaFileDenganFolderDitolak() {
        for (String name : List.of("../evil.jar", "libs/x.jar", "..\\x.jar", "x.exe", ".hidden.jar", "C:x.jar")) {
            assertThatThrownBy(() -> new UpdateManifest.FileEntry(name, SHA, 1))
                    .as(name).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void ukuranDanHashDivalidasi() {
        assertThatThrownBy(() -> new UpdateManifest.FileEntry("a.jar", "ABC", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UpdateManifest.FileEntry("a.jar", SHA, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UpdateManifest.FileEntry("a.jar", SHA, UpdateManifest.MAX_FILE_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namaGandaBedaKapitalDitolak() {
        var files = List.of(new UpdateManifest.FileEntry("a.jar", SHA, 1), new UpdateManifest.FileEntry("A.jar", SHA, 1));
        assertThatThrownBy(() -> new UpdateManifest(ReleaseVersion.parse("0.1.1"), 1, Fixtures.MAIN, "", files))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatTidakDikenalMintaInstallerBaru() {
        byte[] json = "{\"format\":2,\"version\":\"0.1.1\"}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> UpdateManifest.parse(json))
                .isInstanceOf(UpdateException.class).hasMessageContaining("installer");
    }

    @Test
    void jsonRusakJadiUpdateException() {
        assertThatThrownBy(() -> UpdateManifest.parse("{bukan json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UpdateException.class);
        assertThatThrownBy(() -> UpdateManifest.parse("[]".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UpdateException.class);
    }
}

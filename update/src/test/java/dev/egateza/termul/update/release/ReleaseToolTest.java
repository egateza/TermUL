package dev.egateza.termul.update.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.update.ManifestSignature;
import dev.egateza.termul.update.UpdateException;
import dev.egateza.termul.update.UpdateProtocol;
import dev.egateza.termul.update.UpdateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseToolTest {

    @TempDir
    Path tmp;

    @Test
    void publishMenghasilkanFolderRilisYangTerverifikasi() throws Exception {
        Path key = tmp.resolve("keys").resolve("signing.key");
        var publicKey = ManifestSignature.publicKey(ReleaseTool.keygen(key));
        Path input = input("version=0.1.130\ncommit=abc1234\n");
        Path out = tmp.resolve("out");

        var manifest = ReleaseTool.publish(input, out, ReleaseTool.loadPrivateKey(key), publicKey, "Catatan", false);

        assertThat(manifest.version()).hasToString("0.1.130");
        assertThat(manifest.generation()).isEqualTo(UpdateProtocol.GENERATION);
        assertThat(manifest.notes()).isEqualTo("Catatan");
        assertThat(manifest.files()).extracting(f -> f.name())
                .containsExactly("termul-app-0.1.0.jar", "a-lib-1.0.jar", "z-lib-2.0.jar");
        // folder rilis bisa diverifikasi persis seperti folder update di sisi user
        assertThat(UpdateStore.verify(out, publicKey).manifest()).isEqualTo(manifest);
    }

    @Test
    void keyTidakCocokDenganPublicKeyAplikasiDitolak() throws Exception {
        Path key = tmp.resolve("signing.key");
        ReleaseTool.keygen(key);
        var other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();

        assertThatThrownBy(() -> ReleaseTool.publish(input("version=0.1.130\ncommit=abc\n"), tmp.resolve("out"),
                ReleaseTool.loadPrivateKey(key), other, "", false))
                .isInstanceOf(UpdateException.class).hasMessageContaining("tidak cocok");
        assertThat(tmp.resolve("out")).doesNotExist();
    }

    @Test
    void buildDirtyDitolakKecualiDiizinkan() throws Exception {
        Path key = tmp.resolve("signing.key");
        var publicKey = ManifestSignature.publicKey(ReleaseTool.keygen(key));
        Path input = input("version=0.1.130\ncommit=abc-dirty\n");

        assertThatThrownBy(() -> ReleaseTool.publish(input, tmp.resolve("out1"), ReleaseTool.loadPrivateKey(key),
                publicKey, "", false)).isInstanceOf(UpdateException.class).hasMessageContaining("commit");
        assertThat(ReleaseTool.publish(input, tmp.resolve("out2"), ReleaseTool.loadPrivateKey(key), publicKey, "",
                true).version()).hasToString("0.1.130");
    }

    @Test
    void buildDevDitolak() throws Exception {
        Path key = tmp.resolve("signing.key");
        var publicKey = ManifestSignature.publicKey(ReleaseTool.keygen(key));

        assertThatThrownBy(() -> ReleaseTool.publish(input("version=${termul.versionBase}.${x}\n"), tmp.resolve("o"),
                ReleaseTool.loadPrivateKey(key), publicKey, "", false))
                .isInstanceOf(UpdateException.class).hasMessageContaining("bukan build rilis");
    }

    @Test
    void keygenTidakMenimpaKeyLama() throws Exception {
        Path key = tmp.resolve("signing.key");
        ReleaseTool.keygen(key);
        byte[] before = Files.readAllBytes(key);

        assertThatThrownBy(() -> ReleaseTool.keygen(key)).isInstanceOf(UpdateException.class);
        assertThat(Files.readAllBytes(key)).isEqualTo(before);
    }

    /** Folder seperti app/target/jpackage-input: jar utama berisi build.properties + libs/. */
    private Path input(String buildProperties) throws IOException {
        Path input = tmp.resolve("input-" + System.nanoTime());
        Files.createDirectories(input.resolve("libs"));
        try (var zip = new ZipOutputStream(Files.newOutputStream(input.resolve("termul-app-0.1.0.jar")))) {
            zip.putNextEntry(new ZipEntry(UpdateProtocol.BUILD_INFO_RESOURCE));
            zip.write(buildProperties.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Files.writeString(input.resolve("libs").resolve("z-lib-2.0.jar"), "z");
        Files.writeString(input.resolve("libs").resolve("a-lib-1.0.jar"), "a");
        Files.writeString(input.resolve("libs").resolve("README.txt"), "bukan jar");
        return input;
    }
}

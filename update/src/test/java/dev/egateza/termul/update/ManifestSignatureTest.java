package dev.egateza.termul.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ManifestSignatureTest {

    private final java.security.KeyPair key = Fixtures.keyPair();
    private final byte[] json = new Fixtures.Release().jar("a.jar", "isi").manifest().toJson();

    @Test
    void tandaTanganValidDiterima() throws Exception {
        byte[] sig = ManifestSignature.sign(json, key.getPrivate());

        assertThat(ManifestSignature.verify(json, sig, key.getPublic()).version()).hasToString("0.1.200");
    }

    @Test
    void manifestDiubahSatuByteDitolak() throws Exception {
        byte[] sig = ManifestSignature.sign(json, key.getPrivate());
        byte[] tampered = new String(json, StandardCharsets.UTF_8).replace("0.1.200", "0.1.900")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ManifestSignature.verify(tampered, sig, key.getPublic()))
                .isInstanceOf(UpdateException.class).hasMessageContaining("Tanda tangan");
    }

    @Test
    void keyLainDitolak() throws Exception {
        byte[] sig = ManifestSignature.sign(json, Fixtures.keyPair().getPrivate());

        assertThat(ManifestSignature.isValid(json, sig, key.getPublic())).isFalse();
    }

    @Test
    void signatureSampahDitolakTanpaException() {
        assertThat(ManifestSignature.isValid(json, "bukan base64!!".getBytes(StandardCharsets.US_ASCII),
                key.getPublic())).isFalse();
        assertThat(ManifestSignature.isValid(json, new byte[0], key.getPublic())).isFalse();
        assertThat(ManifestSignature.isValid(json, null, key.getPublic())).isFalse();
        assertThat(ManifestSignature.isValid(json, new byte[ManifestSignature.MAX_SIGNATURE_TEXT + 1],
                key.getPublic())).isFalse();
    }

    @Test
    void publicKeyBawaanBisaDibaca() {
        assertThat(UpdateKeys.publicKey().getAlgorithm()).isIn("Ed25519", "EdDSA");
    }
}

package dev.egateza.termul.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Memakai login keychain sungguhan dengan layanan khusus test; item selalu dihapus lagi. */
@EnabledOnOs(OS.MAC)
class KeychainKeyProtectorIT {

    private final KeychainKeyProtector protector =
            new KeychainKeyProtector("TermUL-test", "it-" + UUID.randomUUID());

    @AfterEach
    void cleanUp() {
        protector.discard();
    }

    @Test
    void bungkusDanBukaLewatKeychain() {
        byte[] dek = "0123456789abcdef0123456789abcdef".getBytes();
        byte[] blob = protector.protect(dek);

        assertThat(new String(blob, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(new String(dek));
        assertThat(protector.unprotect(blob)).isEqualTo(dek);
    }

    @Test
    void protectUlangMenggantiKunciSehinggaBlobLamaTidakBerlaku() {
        byte[] lama = protector.protect(new byte[32]);
        byte[] baru = protector.protect(new byte[32]);

        assertThat(protector.unprotect(baru)).isEqualTo(new byte[32]);
        assertThatThrownBy(() -> protector.unprotect(lama)).isInstanceOf(VaultException.class);
    }

    @Test
    void setelahDiscardBlobTidakBisaDibuka() {
        byte[] blob = protector.protect(new byte[32]);
        protector.discard();
        protector.discard(); // item sudah tidak ada: bukan error

        assertThatThrownBy(() -> protector.unprotect(blob)).hasMessageContaining("tidak ditemukan");
    }
}

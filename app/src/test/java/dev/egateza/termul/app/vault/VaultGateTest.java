package dev.egateza.termul.app.vault;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.vault.Argon2Params;
import dev.egateza.termul.vault.FileCredentialVault;
import dev.egateza.termul.vault.KeyProtector;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultGateTest {

    /** Pengganti DPAPI untuk test: blob = data di-XOR, selalu bisa dibuka. */
    private static final KeyProtector FAKE_OS_KEY = new KeyProtector() {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public byte[] protect(byte[] data) {
            return xor(data);
        }

        @Override
        public byte[] unprotect(byte[] blob) {
            return xor(blob);
        }

        private static byte[] xor(byte[] in) {
            var out = in.clone();
            for (int i = 0; i < out.length; i++) {
                out[i] ^= 0x5A;
            }
            return out;
        }
    };

    @TempDir
    Path dir;

    private FileCredentialVault vault;
    private VaultGate gate;

    @BeforeEach
    void setUp() {
        vault = new FileCredentialVault(dir.resolve("vault.bin"), new Argon2Params(64, 1, 1), FAKE_OS_KEY);
        vault.create("master-123".toCharArray());
        vault.setRememberOnThisPc(true);
        gate = new VaultGate(vault, () -> null, Duration.ofMinutes(15));
    }

    @AfterEach
    void tearDown() {
        gate.close();
    }

    @Test
    void kunciManualTidakDibukaDiamDiamDenganKeyOs() {
        gate.lock();

        assertThat(gate.isLockedByUser()).isTrue();
        assertThat(gate.tryOsKey()).isFalse();
        assertThat(vault.isUnlocked()).isFalse();
    }

    @Test
    void bukaLewatMenuBolehMemakaiKeyOsLalu() {
        gate.lock();

        assertThat(gate.unlockExplicitly()).isTrue();

        assertThat(vault.isUnlocked()).isTrue();
        assertThat(gate.isLockedByUser()).isFalse();
        vault.lock(); // kunci tanpa lewat menu (mis. idle): key OS boleh dipakai lagi
        assertThat(gate.tryOsKey()).isTrue();
    }

    @Test
    void autoLockIdleTidakLengket() {
        var idle = new VaultGate(vault, () -> null, Duration.ZERO);
        try {
            idle.autoLockIfIdle();

            assertThat(vault.isUnlocked()).isFalse();
            assertThat(idle.isLockedByUser()).isFalse();
            assertThat(idle.tryOsKey()).isTrue();
        } finally {
            idle.close();
        }
    }
}

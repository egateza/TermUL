package dev.egateza.myterm.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class FileCredentialVaultTest {

    /** Parameter ringan supaya test cepat; produksi memakai Argon2Params.defaults(). */
    private static final Argon2Params FAST = new Argon2Params(64, 1, 1);

    /** Protector palsu (XOR) untuk menguji alur "ingat di PC ini" tanpa DPAPI. */
    private static final KeyProtector FAKE_OS = new KeyProtector() {
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

        private byte[] xor(byte[] in) {
            byte[] out = in.clone();
            for (int i = 0; i < out.length; i++) {
                out[i] ^= 0x5A;
            }
            return out;
        }
    };

    @TempDir
    Path dir;

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    private FileCredentialVault vault(KeyProtector protector) {
        return new FileCredentialVault(dir.resolve("vault.bin"), FAST, protector);
    }

    private static char[] pw(String s) {
        return s.toCharArray();
    }

    @Test
    void roundTripAntarInstance() {
        var v = vault(KeyProtector.UNAVAILABLE);
        assertThat(v.exists()).isFalse();
        v.create(pw("master-123"));
        v.put(a, SecretType.SUDO_PASSWORD, pw("sudo-pässwörd"));
        v.put(a, SecretType.ROOT_PASSWORD, pw("root!"));
        v.put(b, SecretType.SUDO_PASSWORD, pw("lain"));

        var reopened = vault(KeyProtector.UNAVAILABLE);
        assertThat(reopened.exists()).isTrue();
        assertThat(reopened.isUnlocked()).isFalse();
        assertThat(reopened.has(a, SecretType.SUDO_PASSWORD)).isTrue(); // metadata terbaca walau terkunci
        assertThatThrownBy(() -> reopened.get(a, SecretType.SUDO_PASSWORD)).isInstanceOf(VaultException.Locked.class);
        reopened.unlock(pw("master-123"));

        assertThat(reopened.get(a, SecretType.SUDO_PASSWORD)).containsExactly(pw("sudo-pässwörd"));
        assertThat(reopened.get(a, SecretType.ROOT_PASSWORD)).containsExactly(pw("root!"));
        assertThat(reopened.get(b, SecretType.SUDO_PASSWORD)).containsExactly(pw("lain"));
        assertThat(reopened.get(b, SecretType.ROOT_PASSWORD)).isNull();
        assertThat(reopened.has(a, SecretType.LOGIN_PASSWORD)).isFalse();
    }

    @Test
    void passwordSalahDitolak() {
        vault(KeyProtector.UNAVAILABLE).create(pw("master-123"));

        var v = vault(KeyProtector.UNAVAILABLE);
        assertThatThrownBy(() -> v.unlock(pw("salah-salah"))).isInstanceOf(VaultException.WrongPassword.class);
        assertThat(v.isUnlocked()).isFalse();
    }

    @Test
    void masterPasswordTerlaluPendekDitolak() {
        assertThatThrownBy(() -> vault(KeyProtector.UNAVAILABLE).create(pw("pendek")))
                .hasMessageContaining("minimal 8");
    }

    @Test
    void inputDanOutputBisaDiZeroDanTidakAdaPlaintextDiFile() throws Exception {
        var v = vault(KeyProtector.UNAVAILABLE);
        char[] master = pw("master-123");
        v.create(master);
        char[] secret = pw("RahasiaSekali");
        v.put(a, SecretType.SUDO_PASSWORD, secret);

        assertThat(master).containsOnly('\0');
        assertThat(secret).containsOnly('\0');
        String raw = new String(Files.readAllBytes(dir.resolve("vault.bin")), StandardCharsets.ISO_8859_1);
        assertThat(raw).doesNotContain("RahasiaSekali").doesNotContain("master-123");
    }

    @Test
    void lockMenghapusAkses() {
        var v = vault(KeyProtector.UNAVAILABLE);
        v.create(pw("master-123"));
        v.put(a, SecretType.SUDO_PASSWORD, pw("x"));
        v.lock();

        assertThat(v.isUnlocked()).isFalse();
        assertThatThrownBy(() -> v.get(a, SecretType.SUDO_PASSWORD)).isInstanceOf(VaultException.Locked.class);
        assertThatThrownBy(() -> v.put(a, SecretType.SUDO_PASSWORD, pw("y"))).isInstanceOf(VaultException.Locked.class);
    }

    @Test
    void ciphertextYangDiubahTerdeteksi() throws Exception {
        var v = vault(KeyProtector.UNAVAILABLE);
        v.create(pw("master-123"));
        v.put(a, SecretType.SUDO_PASSWORD, pw("abcd"));
        Path file = dir.resolve("vault.bin");
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 1] ^= 0x01; // byte terakhir = tag GCM entry
        Files.write(file, bytes);

        var reopened = vault(KeyProtector.UNAVAILABLE);
        reopened.unlock(pw("master-123"));
        assertThatThrownBy(() -> reopened.get(a, SecretType.SUDO_PASSWORD)).hasMessageContaining("rusak");
    }

    @Test
    void headerYangDiubahTerdeteksi() throws Exception {
        vault(KeyProtector.UNAVAILABLE).create(pw("master-123"));
        Path file = dir.resolve("vault.bin");
        byte[] bytes = Files.readAllBytes(file);
        bytes[20] ^= 0x01; // di dalam salt → KEK beda + AAD beda
        Files.write(file, bytes);

        assertThatThrownBy(() -> vault(KeyProtector.UNAVAILABLE).unlock(pw("master-123")))
                .isInstanceOf(VaultException.WrongPassword.class);
    }

    @Test
    void entryTidakBisaDipindahAntarProfil() throws Exception {
        var v = vault(KeyProtector.UNAVAILABLE);
        v.create(pw("master-123"));
        v.put(a, SecretType.SUDO_PASSWORD, pw("aaaa"));
        v.put(b, SecretType.SUDO_PASSWORD, pw("bbbb"));
        Path file = dir.resolve("vault.bin");
        byte[] bytes = Files.readAllBytes(file);
        // header 33 + wrappedDek 64 + keyCheck 47 + count 4 = 148; tiap entry = 16+1+12+4+20 = 53
        int first = 148;
        int second = first + 53;
        for (int i = 0; i < 16; i++) {
            byte t = bytes[first + i];
            bytes[first + i] = bytes[second + i];
            bytes[second + i] = t;
        }
        Files.write(file, bytes);

        var reopened = vault(KeyProtector.UNAVAILABLE);
        reopened.unlock(pw("master-123"));
        assertThatThrownBy(() -> reopened.get(a, SecretType.SUDO_PASSWORD)).hasMessageContaining("rusak");
        assertThatThrownBy(() -> reopened.get(b, SecretType.SUDO_PASSWORD)).hasMessageContaining("rusak");
    }

    @Test
    void fileTerpotongDitolakDenganPesanJelas() throws Exception {
        vault(KeyProtector.UNAVAILABLE).create(pw("master-123"));
        Path file = dir.resolve("vault.bin");
        byte[] bytes = Files.readAllBytes(file);
        Files.write(file, java.util.Arrays.copyOf(bytes, 50));

        assertThatThrownBy(() -> vault(KeyProtector.UNAVAILABLE).unlock(pw("master-123")))
                .isInstanceOf(VaultException.class).hasMessageContaining("rusak");
    }

    @Test
    void gantiMasterPasswordTanpaKehilanganSecret() {
        var v = vault(KeyProtector.UNAVAILABLE);
        v.create(pw("master-123"));
        v.put(a, SecretType.LOGIN_PASSWORD, pw("login"));

        assertThatThrownBy(() -> v.changeMasterPassword(pw("bukan-ini"), pw("baru-12345")))
                .isInstanceOf(VaultException.WrongPassword.class);
        v.changeMasterPassword(pw("master-123"), pw("baru-12345"));

        var reopened = vault(KeyProtector.UNAVAILABLE);
        assertThatThrownBy(() -> reopened.unlock(pw("master-123"))).isInstanceOf(VaultException.WrongPassword.class);
        reopened.unlock(pw("baru-12345"));
        assertThat(reopened.get(a, SecretType.LOGIN_PASSWORD)).containsExactly(pw("login"));
    }

    @Test
    void removeDanRemoveProfile() {
        var v = vault(KeyProtector.UNAVAILABLE);
        v.create(pw("master-123"));
        v.put(a, SecretType.SUDO_PASSWORD, pw("1"));
        v.put(a, SecretType.ROOT_PASSWORD, pw("2"));
        v.put(b, SecretType.SUDO_PASSWORD, pw("3"));

        v.remove(a, SecretType.SUDO_PASSWORD);
        assertThat(v.has(a, SecretType.SUDO_PASSWORD)).isFalse();
        assertThat(v.has(a, SecretType.ROOT_PASSWORD)).isTrue();

        v.removeProfile(a);
        var reopened = vault(KeyProtector.UNAVAILABLE);
        reopened.unlock(pw("master-123"));
        assertThat(reopened.has(a, SecretType.ROOT_PASSWORD)).isFalse();
        assertThat(reopened.get(b, SecretType.SUDO_PASSWORD)).containsExactly(pw("3"));
    }

    @Test
    void ingatDiPcIniMembukaTanpaMasterPassword() {
        var v = vault(FAKE_OS);
        v.create(pw("master-123"));
        v.put(a, SecretType.SUDO_PASSWORD, pw("s"));
        assertThat(v.isRememberedOnThisPc()).isFalse();
        v.setRememberOnThisPc(true);
        assertThat(v.isRememberedOnThisPc()).isTrue();

        var reopened = vault(FAKE_OS);
        assertThat(reopened.unlockWithOsKey()).isTrue();
        assertThat(reopened.get(a, SecretType.SUDO_PASSWORD)).containsExactly(pw("s"));

        // ganti master password tidak merusak key DPAPI (DEK sama)
        reopened.changeMasterPassword(pw("master-123"), pw("baru-12345"));
        assertThat(vault(FAKE_OS).unlockWithOsKey()).isTrue();

        reopened.setRememberOnThisPc(false);
        assertThat(vault(FAKE_OS).unlockWithOsKey()).isFalse();
    }

    @Test
    void keyOsDariVaultLainDitolak() throws Exception {
        var v = vault(FAKE_OS);
        v.create(pw("master-123"));
        v.setRememberOnThisPc(true);
        Path dpapi = dir.resolve("vault.bin.dpapi");
        byte[] blob = Files.readAllBytes(dpapi);
        blob[0] ^= 0x01;
        Files.write(dpapi, blob);

        assertThat(vault(FAKE_OS).unlockWithOsKey()).isFalse();
    }

    @Test
    void tanpaProtectorOpsiIngatTidakBisaDiaktifkan() {
        var v = vault(KeyProtector.UNAVAILABLE);
        v.create(pw("master-123"));
        assertThatThrownBy(() -> v.setRememberOnThisPc(true)).hasMessageContaining("Windows");
        assertThat(v.unlockWithOsKey()).isFalse();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void dpapiAsliRoundTrip() {
        var dpapi = new DpapiKeyProtector();
        byte[] data = "kunci-data-32-byte-xxxxxxxxxxxxx".getBytes(StandardCharsets.US_ASCII);

        byte[] blob = dpapi.protect(data);

        assertThat(blob).isNotEqualTo(data);
        assertThat(dpapi.unprotect(blob)).isEqualTo(data);
    }

    @Test
    void argon2DefaultSesuaiAdr() {
        assertThat(Argon2Params.defaults()).isEqualTo(new Argon2Params(64 * 1024, 3, 1));
    }
}

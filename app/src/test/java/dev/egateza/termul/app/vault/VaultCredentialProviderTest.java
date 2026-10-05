package dev.egateza.termul.app.vault;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import dev.egateza.termul.vault.Argon2Params;
import dev.egateza.termul.vault.FileCredentialVault;
import dev.egateza.termul.vault.KeyProtector;
import dev.egateza.termul.vault.SecretType;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultCredentialProviderTest {

    @TempDir
    Path dir;

    private FileCredentialVault vault;
    private VaultGate gate;
    private final List<String> fallbackCalls = new ArrayList<>();
    private VaultCredentialProvider provider;
    private final HostProfile profile = HostProfile.create("web", "10.0.0.1", "dev");

    @BeforeEach
    void setUp() {
        vault = new FileCredentialVault(dir.resolve("vault.bin"), new Argon2Params(64, 1, 1), KeyProtector.UNAVAILABLE);
        vault.create("master-123".toCharArray());
        gate = new VaultGate(vault, () -> null, Duration.ofMinutes(15));
        provider = new VaultCredentialProvider(gate, new CredentialProvider() {
            @Override
            public char[] password(HostProfile p, int attempt) {
                fallbackCalls.add("password#" + attempt);
                return "manual".toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile p, Path keyFile, int attempt) {
                fallbackCalls.add("passphrase#" + attempt);
                return "manual-pp".toCharArray();
            }
        });
    }

    @AfterEach
    void tearDown() {
        gate.close();
    }

    @Test
    void percobaanPertamaDariVaultBerikutnyaManual() {
        vault.put(profile.id(), SecretType.LOGIN_PASSWORD, "dari-vault".toCharArray());

        assertThat(provider.password(profile, 1)).containsExactly("dari-vault".toCharArray());
        assertThat(fallbackCalls).isEmpty();
        assertThat(provider.password(profile, 2)).containsExactly("manual".toCharArray());
        assertThat(fallbackCalls).containsExactly("password#2");
    }

    @Test
    void tidakAdaDiVaultLangsungManual() {
        assertThat(provider.password(profile, 1)).containsExactly("manual".toCharArray());
        assertThat(provider.keyPassphrase(profile, Path.of("id_ed25519"), 1)).containsExactly("manual-pp".toCharArray());
        assertThat(fallbackCalls).containsExactly("password#1", "passphrase#1");
    }

    @Test
    void passphraseDariVault() {
        vault.put(profile.id(), SecretType.KEY_PASSPHRASE, "pp".toCharArray());

        assertThat(provider.keyPassphrase(profile, Path.of("id_ed25519"), 1)).containsExactly("pp".toCharArray());
        assertThat(fallbackCalls).isEmpty();
    }
}

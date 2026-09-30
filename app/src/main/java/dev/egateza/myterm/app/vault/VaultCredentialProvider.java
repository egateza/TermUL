package dev.egateza.myterm.app.vault;

import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.ssh.auth.CredentialProvider;
import dev.egateza.myterm.vault.SecretType;
import dev.egateza.myterm.vault.VaultException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Percobaan pertama memakai secret dari vault (kalau tersimpan); kalau tidak ada, vault batal dibuka,
 * atau secret vault ditolak server, jatuh ke {@code fallback} (prompt manual).
 */
public final class VaultCredentialProvider implements CredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(VaultCredentialProvider.class);

    private final VaultGate gate;
    private final CredentialProvider fallback;

    public VaultCredentialProvider(VaultGate gate, CredentialProvider fallback) {
        this.gate = gate;
        this.fallback = fallback;
    }

    @Override
    public char[] password(HostProfile profile, int attempt) {
        char[] fromVault = attempt == 1 ? fromVault(profile, SecretType.LOGIN_PASSWORD) : null;
        return fromVault != null ? fromVault : fallback.password(profile, attempt);
    }

    @Override
    public char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt) {
        char[] fromVault = attempt == 1 ? fromVault(profile, SecretType.KEY_PASSPHRASE) : null;
        return fromVault != null ? fromVault : fallback.keyPassphrase(profile, keyFile, attempt);
    }

    private char[] fromVault(HostProfile profile, SecretType type) {
        try {
            if (!gate.vault().has(profile.id(), type) || !gate.ensureUnlocked()) {
                return null;
            }
            return gate.vault().get(profile.id(), type);
        } catch (VaultException e) {
            log.warn("Secret {} untuk {} tidak bisa dibaca dari vault: {}", type, profile.address(), e.getMessage());
            return null;
        }
    }
}

package dev.egateza.termul.app.vault;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ssh.SwingCredentialProvider;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.sftp.RemoteFileException;
import dev.egateza.termul.sftp.edit.SudoWriter;
import dev.egateza.termul.vault.SecretType;
import dev.egateza.termul.vault.VaultException;
import java.awt.Component;
import java.util.function.Supplier;

/**
 * Password sudo untuk edit file root: dari vault ({@link SecretType#SUDO_PASSWORD}, unlock on-demand), atau diminta
 * ke user tanpa disimpan kalau tidak ada. Dipanggil di luar EDT (unlock dan dialog blocking).
 */
public final class VaultSudoPassword implements SudoWriter.SudoPassword {

    private final VaultGate gate;
    private final HostProfile profile;
    private final Supplier<Component> parent;

    public VaultSudoPassword(VaultGate gate, HostProfile profile, Supplier<Component> parent) {
        this.gate = gate;
        this.profile = profile;
        this.parent = parent;
    }

    @Override
    public char[] stored() throws RemoteFileException {
        try {
            if (!gate.vault().has(profile.id(), SecretType.SUDO_PASSWORD) || !gate.ensureUnlocked()) {
                return null;
            }
            return gate.vault().get(profile.id(), SecretType.SUDO_PASSWORD);
        } catch (VaultException e) {
            throw new RemoteFileException(e.getMessage(), e);
        }
    }

    @Override
    public char[] prompt() {
        return SwingCredentialProvider.askPassword(parent, I18n.t("edit.sudo.passwordTitle"),
                I18n.t("edit.sudo.passwordPrompt", profile.address()));
    }
}

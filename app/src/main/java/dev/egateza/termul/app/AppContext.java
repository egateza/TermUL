package dev.egateza.termul.app;

import dev.egateza.termul.app.edit.EditManager;
import dev.egateza.termul.app.terminal.TerminalSettings;
import dev.egateza.termul.app.vault.VaultGate;
import dev.egateza.termul.core.AppPaths;
import dev.egateza.termul.core.config.ConfigStore;
import dev.egateza.termul.core.profile.ProfileStore;
import dev.egateza.termul.sftp.SftpLinks;
import dev.egateza.termul.ssh.SessionManager;
import dev.egateza.termul.terminal.SshTerminalFactory;
import java.util.concurrent.ExecutorService;

/**
 * Service yang di-wiring di {@link TermULApp} dan diteruskan ke UI (constructor injection manual).
 *
 * @param io         executor single-thread untuk disk I/O (profil, config)
 * @param sshOps     executor untuk operasi SSH blocking (connect, exec, SFTP)
 */
public record AppContext(AppPaths paths, ConfigStore config, ProfileStore profiles, ExecutorService io,
                         ExecutorService sshOps,
                         SessionManager sessions, SshTerminalFactory terminals, TerminalSettings terminalSettings,
                         VaultGate vault, EditManager edits, SftpLinks sftpLinks) {
}

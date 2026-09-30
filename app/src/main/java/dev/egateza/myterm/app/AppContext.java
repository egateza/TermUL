package dev.egateza.myterm.app;

import dev.egateza.myterm.app.terminal.TerminalSettings;
import dev.egateza.myterm.app.vault.VaultGate;
import dev.egateza.myterm.core.AppPaths;
import dev.egateza.myterm.core.config.ConfigStore;
import dev.egateza.myterm.core.profile.ProfileStore;
import dev.egateza.myterm.ssh.SessionManager;
import dev.egateza.myterm.terminal.SshTerminalFactory;
import java.util.concurrent.ExecutorService;

/**
 * Service yang di-wiring di {@link MyTermApp} dan diteruskan ke UI (constructor injection manual).
 *
 * @param io         executor single-thread untuk disk I/O (profil, config)
 * @param sshOps     executor untuk operasi SSH blocking (connect, exec, SFTP)
 */
public record AppContext(AppPaths paths, ConfigStore config, ProfileStore profiles, ExecutorService io,
                         ExecutorService sshOps,
                         SessionManager sessions, SshTerminalFactory terminals, TerminalSettings terminalSettings,
                         VaultGate vault) {
}

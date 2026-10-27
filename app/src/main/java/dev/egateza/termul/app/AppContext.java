package dev.egateza.termul.app;

import dev.egateza.termul.app.edit.EditManager;
import dev.egateza.termul.app.terminal.TerminalSettings;
import dev.egateza.termul.app.vault.VaultGate;
import dev.egateza.termul.core.AppPaths;
import dev.egateza.termul.core.config.ConfigStore;
import dev.egateza.termul.core.snippet.SnippetStore;
import dev.egateza.termul.core.profile.ProfileStore;
import dev.egateza.termul.core.theme.ThemeStore;
import dev.egateza.termul.core.wsl.WslManager;
import dev.egateza.termul.sftp.SftpLinks;
import dev.egateza.termul.sftp.ftp.FtpsCertStore;
import dev.egateza.termul.ssh.SessionManager;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.terminal.SshTerminalFactory;
import java.util.concurrent.ExecutorService;

/**
 * Service yang di-wiring di {@link TermULApp} dan diteruskan ke UI (constructor injection manual).
 *
 * @param io         executor single-thread untuk disk I/O (profil, config)
 * @param sshOps     executor untuk operasi SSH blocking (connect, exec, SFTP)
 * @param knownHosts known_hosts aplikasi (instance yang sama dengan verifier di {@code sessions})
 * @param ftpsCerts  sertifikat FTPS yang dipercaya user (instance yang sama dengan yang dipakai {@code sftpLinks})
 * @param wsl        start/stop distro WSL; hanya dipakai kalau manajemen WSL aktif di Windows
 * @param snippets   snippet perintah untuk tombol snippet di terminal
 * @param locals     shell terdeteksi + profil terminal lokal (ADR 0005)
 * @param highlights profil highlight teks dan penugasannya ke host/grup/terminal lokal
 * @param shortcuts  shortcut yang diganti user (sisanya default)
 * @param bookmarks  bookmark folder panel SFTP/FTP (remote & lokal)
 */
public record AppContext(AppPaths paths, ConfigStore config, ProfileStore profiles, ExecutorService io,
                         ExecutorService sshOps,
                         SessionManager sessions, SshTerminalFactory terminals, TerminalSettings terminalSettings,
                         VaultGate vault, EditManager edits, SftpLinks sftpLinks, ThemeStore themes,
                         KnownHostsStore knownHosts, FtpsCertStore ftpsCerts, WslManager wsl, SnippetStore snippets,
                         dev.egateza.termul.app.terminal.LocalProfiles locals,
                         dev.egateza.termul.core.highlight.HighlightStore highlights,
                         dev.egateza.termul.core.shortcut.ShortcutStore shortcuts,
                         dev.egateza.termul.core.bookmark.BookmarkStore bookmarks) {
}

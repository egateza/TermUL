package dev.egateza.termul.sftp.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.core.config.ValidationHooks;
import dev.egateza.termul.core.config.ValidationHooks.Hook;
import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.sftp.RemoteFileService;
import dev.egateza.termul.ssh.SessionManager;
import dev.egateza.termul.ssh.SshSettings;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import dev.egateza.termul.ssh.hostkey.HostKeyInfo;
import dev.egateza.termul.ssh.hostkey.HostKeyPrompt;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.ssh.testing.OpenSshContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Edit file milik root lewat sudo terhadap OpenSSH + sudo asli (butuh Docker). */
@Testcontainers(disabledWithoutDocker = true)
class SudoWriterIT {

    @Container
    static final OpenSshContainer SSHD = new OpenSshContainer();

    private static final ValidationHooks HOOKS = new ValidationHooks(List.of(
            new Hook("/etc/termul-it/**", "grep -q '^valid' \"$TERMUL_FILE\"")));

    @TempDir
    Path tmp;

    private SessionManager sessions;
    private RemoteFileService files;
    private String dir;
    private String target;
    private final AtomicInteger prompts = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        dir = "/etc/termul-it/" + UUID.randomUUID();
        target = dir + "/app.conf";
        root("mkdir -p " + dir + " && printf 'valid lama\\n' > " + target
                + " && chown root:root " + target + " && chmod 644 " + target
                + " && ln -s app.conf " + dir + "/link.conf");
        var trust = new HostKeyPrompt() {
            @Override
            public boolean confirmUnknownHost(HostKeyInfo info) {
                return true;
            }

            @Override
            public void hostKeyChanged(HostKeyInfo presented, List<String> knownFingerprints) {
            }
        };
        var creds = new CredentialProvider() {
            @Override
            public char[] password(HostProfile p, int attempt) {
                return OpenSshContainer.PASSWORD.toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile p, Path keyFile, int attempt) {
                return null;
            }
        };
        sessions = new SessionManager(new KnownHostsStore(tmp.resolve("known_hosts")), trust, creds, SshSettings.defaults());
        var profile = new HostProfile(UUID.randomUUID(), "it", "", SSHD.getHost(), SSHD.sshPort(), OpenSshContainer.USER,
                AuthMethod.PASSWORD, null, null, EnvironmentTag.DEV, null, null, false);
        files = RemoteFileService.open(sessions.acquire(profile));
    }

    @AfterEach
    void tearDown() throws Exception {
        files.close();
        sessions.close();
        root("rm -rf " + dir);
    }

    private SudoWriter writer(String stored, String typed) {
        return new SudoWriter(new SudoWriter.SudoPassword() {
            @Override
            public char[] stored() {
                return stored == null ? null : stored.toCharArray();
            }

            @Override
            public char[] prompt() {
                prompts.incrementAndGet();
                return typed == null ? null : typed.toCharArray();
            }
        }, () -> HOOKS);
    }

    @Test
    void fileRootTerpasangDenganOwnerDanModeTetapPlusBackup() throws Exception {
        root("chmod 640 " + target + " && chgrp 1000 " + target); // group dev: masih bisa dibaca
        assertThat(files.canWrite(target)).isFalse();
        var writer = writer(OpenSshContainer.PASSWORD, null);

        writer.upload(files, local("valid baru\n"), target);

        assertThat(root("cat " + target)).isEqualTo("valid baru\n");
        assertThat(root("stat -c '%u:%g %a' " + target)).isEqualTo("0:1000 640\n");
        String backup = writer.lastInstalled().backupPath();
        assertThat(backup).startsWith(SudoWriter.BACKUP_DIR + "/");
        assertThat(root("cat '" + backup + "'")).isEqualTo("valid lama\n");
        assertThat(root("stat -c %a " + SudoWriter.BACKUP_DIR)).isEqualTo("700\n");
        assertThat(root("ls -d /tmp/termul-* 2>/dev/null; true")).isEmpty(); // direktori sementara dihapus
        assertThat(prompts).hasValue(0);
    }

    @Test
    void validasiGagalMengembalikanIsiLama() throws Exception {
        String before = root("stat -c '%s %Y' " + target);

        assertThatThrownBy(() -> writer(OpenSshContainer.PASSWORD, null).upload(files, local("rusak\n"), target))
                .isInstanceOfSatisfying(RemoteValidationException.class, e -> assertThat(e.rolledBack()).isTrue());

        assertThat(root("cat " + target)).isEqualTo("valid lama\n");
        assertThat(root("stat -c '%s %Y' " + target)).isEqualTo(before); // baseline edit tetap cocok
        assertThat(root("stat -c '%U %a' " + target)).isEqualTo("root 644\n");
    }

    @Test
    void passwordSalahTidakMengubahFileDanTidakDiulang() throws Exception {
        assertThatThrownBy(() -> writer("salah", "juga-salah").upload(files, local("valid baru\n"), target))
                .isInstanceOf(SudoAuthException.class)
                .hasMessageContaining("vault");

        assertThat(root("cat " + target)).isEqualTo("valid lama\n");
        assertThat(prompts).hasValue(0); // password vault salah tidak memicu prompt / percobaan kedua
    }

    @Test
    void tanpaPasswordTersimpanUserDimintaPassword() throws Exception {
        writer(null, OpenSshContainer.PASSWORD).upload(files, local("valid dari prompt\n"), target);

        assertThat(prompts).hasValue(1);
        assertThat(root("cat " + target)).isEqualTo("valid dari prompt\n");
    }

    @Test
    void symlinkTetapSymlinkDanFileTujuannyaYangDiganti() throws Exception {
        writer(OpenSshContainer.PASSWORD, null).upload(files, local("valid lewat link\n"), dir + "/link.conf");

        assertThat(root("readlink " + dir + "/link.conf")).isEqualTo("app.conf\n");
        assertThat(root("cat " + target)).isEqualTo("valid lewat link\n");
    }

    private Path local(String content) throws Exception {
        return Files.writeString(Files.createTempFile(tmp, "edit", ".conf"), content);
    }

    /** Menjalankan command sebagai root di container (setup/verifikasi test, di luar aplikasi). */
    private static String root(String command) throws Exception {
        var result = SSHD.execInContainer("sh", "-c", command);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(command + " gagal: " + result.getStderr());
        }
        return result.getStdout();
    }
}

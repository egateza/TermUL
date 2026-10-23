package dev.egateza.termul.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.ssh.PtySize;
import dev.egateza.termul.ssh.SessionManager;
import dev.egateza.termul.ssh.SshSettings;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import dev.egateza.termul.ssh.hostkey.HostKeyInfo;
import dev.egateza.termul.ssh.hostkey.HostKeyPrompt;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.ssh.testing.OpenSshContainer;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** "Ikuti folder terminal" terhadap OpenSSH asli (butuh Docker; otomatis skip kalau Docker tidak ada). */
@Testcontainers(disabledWithoutDocker = true)
class ShellCwdIT {

    @Container
    static final OpenSshContainer SSHD = new OpenSshContainer();

    @TempDir
    Path dir;

    private SessionManager manager;
    private SshTtyConnector tty;

    /** Seperti default Debian/Ubuntu: server menerima variabel LC_* dari client. */
    @BeforeAll
    static void acceptLcEnv() throws Exception {
        var result = SSHD.execInContainer("sh", "-c",
                "c=/config/sshd/sshd_config; [ -f $c ] || c=/etc/ssh/sshd_config; "
                        + "{ echo 'AcceptEnv LC_*'; cat $c; } > /tmp/sshd_config && cat /tmp/sshd_config > $c "
                        + "&& pkill -HUP -o sshd");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        Thread.sleep(1000); // sshd membaca ulang konfigurasi (re-exec)
    }

    @BeforeEach
    void setUp() {
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
            public char[] password(HostProfile profile, int attempt) {
                return OpenSshContainer.PASSWORD.toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt) {
                return null;
            }
        };
        manager = new SessionManager(new KnownHostsStore(dir.resolve("known_hosts")), trust, creds,
                SshSettings.defaults());
    }

    @AfterEach
    void tearDown() {
        if (tty != null) {
            tty.closeByUser();
        }
        manager.close();
    }

    private SshTtyConnector openShell() throws Exception {
        var profile = new HostProfile(UUID.randomUUID(), "it", "", SSHD.getHost(), SSHD.sshPort(),
                OpenSshContainer.USER, AuthMethod.PASSWORD, null, null, EnvironmentTag.DEV, null, null, false);
        tty = new SshTerminalFactory(manager).open(profile, new PtySize(80, 24));
        drain(tty);
        return tty;
    }

    /** Output shell harus dibaca (di aplikasi oleh JediTerm), supaya SSH window tidak penuh. */
    private static void drain(SshTtyConnector tty) {
        Thread.ofVirtual().start(() -> {
            char[] buf = new char[4096];
            try {
                while (tty.read(buf, 0, buf.length) >= 0) {
                    // dibuang
                }
            } catch (Exception e) {
                // shell ditutup
            }
        });
    }

    private ShellCwd.Result awaitResult(SshTtyConnector tty, ShellCwd.Result wanted) throws Exception {
        ShellCwd.Result last = null;
        for (int i = 0; i < 50; i++) {
            last = ShellCwd.probe(tty.lease().connection(), tty.shellToken());
            if (last.equals(wanted)) {
                return last;
            }
            Thread.sleep(200);
        }
        return last;
    }

    @Test
    void mengikutiCdKeFolderBerspasi() throws Exception {
        var shell = openShell();
        shell.write("mkdir -p '/tmp/ikut folder' && cd '/tmp/ikut folder'\r");

        var wanted = new ShellCwd.Result.Found("/tmp/ikut folder");
        assertThat(awaitResult(shell, wanted)).isEqualTo(wanted);
    }

    @Test
    void folderProgramDiDepanBukanFolderShell() throws Exception {
        var shell = openShell();
        shell.write("cd /tmp && (cd /etc && exec sleep 30)\r");

        var wanted = new ShellCwd.Result.Found("/etc");
        assertThat(awaitResult(shell, wanted)).isEqualTo(wanted);
    }

    @Test
    void tokenTanpaShellTidakDidukung() throws Exception {
        var shell = openShell();

        var result = ShellCwd.probe(shell.lease().connection(), ShellCwd.newToken());

        assertThat(result).isInstanceOf(ShellCwd.Result.Unsupported.class);
    }
}

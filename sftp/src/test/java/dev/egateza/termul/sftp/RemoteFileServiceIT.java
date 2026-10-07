package dev.egateza.termul.sftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** SFTP terhadap OpenSSH asli: mode POSIX, owner, posix-rename (butuh Docker). */
@Testcontainers(disabledWithoutDocker = true)
class RemoteFileServiceIT {

    @Container
    static final OpenSshContainer SSHD = new OpenSshContainer();

    @TempDir
    Path tmp;

    private SessionManager sessions;
    private RemoteFileService files;
    private String home;

    @BeforeEach
    void setUp() throws Exception {
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
        home = files.home();
        String work = home + "/it-" + UUID.randomUUID();
        files.mkdir(work);
        home = work;
    }

    @AfterEach
    void tearDown() throws Exception {
        files.delete(home, true);
        files.close();
        sessions.close();
    }

    @Test
    void uploadMempertahankanModeFileLama() throws Exception {
        String target = home + "/secret.env";
        Path v1 = Files.writeString(tmp.resolve("v1"), "A=1\n");
        files.upload(v1, target, 0600, TransferListener.NONE);
        assertThat(files.stat(target).mode()).isEqualTo(0600);

        files.chmod(target, 0640);
        Path v2 = Files.writeString(tmp.resolve("v2"), "A=2\n");
        files.upload(v2, target, 0644, TransferListener.NONE);

        var entry = files.stat(target);
        assertThat(entry.mode()).isEqualTo(0640);
        assertThat(entry.size()).isEqualTo(4);
        Path back = tmp.resolve("back");
        files.download(target, back, TransferListener.NONE);
        assertThat(back).hasContent("A=2\n");
        assertThat(files.list(home)).extracting(RemoteEntry::name).containsExactly("secret.env");
    }

    @Test
    void uploadMenolakFileRootWalauDirektorinyaWritable() throws Exception {
        String target = home + "/root.conf";
        root("printf 'asli\\n' > " + target + " && chown root:root " + target + " && chmod 644 " + target);
        Path v2 = Files.writeString(tmp.resolve("v2"), "diubah\n");

        assertThatThrownBy(() -> files.upload(v2, target, null, TransferListener.NONE))
                .isInstanceOf(RemoteFileException.class)
                .hasMessageContaining("tidak bisa ditulis");

        assertThat(root("cat " + target)).isEqualTo("asli\n");
        assertThat(root("stat -c '%U %a' " + target)).isEqualTo("root 644\n");
        assertThat(files.list(home)).extracting(RemoteEntry::name).containsExactly("root.conf"); // tanpa sisa temp
    }

    @Test
    void uploadMenolakFileRootWalauGroupWritable() throws Exception {
        String target = home + "/shared.conf";
        root("printf 'asli\\n' > " + target + " && chown root:1000 " + target + " && chmod 664 " + target);
        Path v2 = Files.writeString(tmp.resolve("v2"), "diubah\n");

        assertThatThrownBy(() -> files.upload(v2, target, null, TransferListener.NONE))
                .isInstanceOf(RemoteFileException.class)
                .hasMessageContaining("bukan user login");

        assertThat(root("cat " + target)).isEqualTo("asli\n");
        assertThat(root("stat -c '%u:%g %a' " + target)).isEqualTo("0:1000 664\n");
        assertThat(files.list(home)).extracting(RemoteEntry::name).containsExactly("shared.conf");
    }

    @Test
    void uploadLewatSymlinkMenggantiTargetDanLinkTetapUtuh() throws Exception {
        // seperti /etc/apache2/sites-enabled/x.conf -> ../sites-available/x.conf
        files.mkdir(home + "/sites-available");
        files.mkdir(home + "/sites-enabled");
        String target = home + "/sites-available/site.conf";
        String link = home + "/sites-enabled/site.conf";
        files.upload(Files.writeString(tmp.resolve("v1"), "v1\n"), target, 0640, TransferListener.NONE);
        root("ln -s ../sites-available/site.conf " + link);

        files.upload(Files.writeString(tmp.resolve("v2"), "v2\n"), link, null, TransferListener.NONE);

        assertThat(root("readlink " + link)).isEqualTo("../sites-available/site.conf\n");
        assertThat(root("cat " + target)).isEqualTo("v2\n");
        assertThat(files.stat(target).mode()).isEqualTo(0640);
        assertThat(files.list(home + "/sites-available")).extracting(RemoteEntry::name).containsExactly("site.conf");
        assertThat(files.list(home + "/sites-enabled")).singleElement()
                .satisfies(e -> assertThat(e.type()).isEqualTo(RemoteEntry.Type.SYMLINK));
    }

    @Test
    void resolveLinksMengikutiRantaiDanMembiarkanPathBiasa() throws Exception {
        String target = home + "/real.conf";
        files.upload(Files.writeString(tmp.resolve("v1"), "x\n"), target, null, TransferListener.NONE);
        root("ln -s real.conf " + home + "/a && ln -s a " + home + "/b");

        assertThat(files.resolveLinks(home + "/b")).isEqualTo(target);
        assertThat(files.resolveLinks(target)).isEqualTo(target);
        assertThat(files.resolveLinks(home + "/baru.conf")).isEqualTo(home + "/baru.conf");
    }

    @Test
    void listMenampilkanOwnerDanGroup() throws Exception {
        files.mkdir(home + "/d");
        var entries = files.list(home);

        assertThat(entries).singleElement().satisfies(e -> {
            assertThat(e.isDirectory()).isTrue();
            assertThat(e.owner()).isEqualTo(OpenSshContainer.USER);
            assertThat(e.modeString()).startsWith("rwx");
        });
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

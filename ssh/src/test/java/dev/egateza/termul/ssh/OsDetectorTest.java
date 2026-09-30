package dev.egateza.termul.ssh;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import dev.egateza.termul.ssh.hostkey.HostKeyInfo;
import dev.egateza.termul.ssh.hostkey.HostKeyPrompt;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.ssh.testing.TestSshServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OsDetectorTest {

    @TempDir
    Path dir;

    private TestSshServer server;
    private SessionManager sessions;
    private final List<String> commands = new CopyOnWriteArrayList<>();
    private volatile String response = "";

    private SshLease connect() throws Exception {
        server = new TestSshServer(dir.resolve("hostkey.ser"), s -> s.setCommandFactory((channel, command) -> {
            commands.add(command);
            return new FixedOutput(response);
        }));
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
                return TestSshServer.PASSWORD.toCharArray();
            }

            @Override
            public char[] keyPassphrase(HostProfile p, Path keyFile, int attempt) {
                return null;
            }
        };
        sessions = new SessionManager(new KnownHostsStore(dir.resolve("known_hosts")), trust, creds,
                new SshSettings(Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(10),
                        Duration.ZERO, 0, Duration.ZERO));
        return sessions.acquire(new HostProfile(UUID.randomUUID(), "t", "", "127.0.0.1", server.port(),
                TestSshServer.USER, AuthMethod.PASSWORD, null, null, EnvironmentTag.DEV, null, null, false));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (sessions != null) {
            sessions.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void mendeteksiUbuntuLewatExec() throws Exception {
        response = "PRETTY_NAME=\"Ubuntu 24.04.1 LTS\"\nID=ubuntu\nVERSION_ID=\"24.04\"\n";
        try (var lease = connect()) {
            var os = OsDetector.detect(lease.connection());

            assertThat(os).get().satisfies(o -> {
                assertThat(o.id()).isEqualTo("ubuntu");
                assertThat(o.label()).isEqualTo("Ubuntu 24.04.1 LTS");
            });
            assertThat(commands).containsExactly(OsDetector.COMMAND);
            assertThat(lease.connection().isOpen()).isTrue();
        }
    }

    @Test
    void outputTidakDikenalAtauRaksasaTidakError() throws Exception {
        response = "x".repeat(200_000);
        try (var lease = connect()) {
            assertThat(OsDetector.detect(lease.connection())).isEmpty();
        }
    }

    /** Command server yang menulis teks tetap lalu keluar. */
    private static final class FixedOutput implements Command {
        private final String text;
        private OutputStream out;
        private ExitCallback exit;

        FixedOutput(String text) {
            this.text = text;
        }

        @Override
        public void setInputStream(InputStream in) {
        }

        @Override
        public void setOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void setErrorStream(OutputStream err) {
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.exit = callback;
        }

        @Override
        public void start(ChannelSession channel, Environment env) {
            Thread.ofVirtual().start(() -> {
                try {
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    exit.onExit(0);
                } catch (IOException e) {
                    exit.onExit(1);
                }
            });
        }

        @Override
        public void destroy(ChannelSession channel) {
        }
    }
}

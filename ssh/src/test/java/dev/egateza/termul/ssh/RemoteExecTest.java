package dev.egateza.termul.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteExecTest {

    @TempDir
    Path dir;

    private TestSshServer server;
    private SessionManager sessions;
    private SshLease lease;
    private final List<String> commands = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        server = new TestSshServer(dir.resolve("hostkey.ser"), s -> s.setCommandFactory((channel, command) -> {
            commands.add(command);
            return command.equals("hang") ? new Hang() : new StdinEcho();
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
        lease = sessions.acquire(new HostProfile(UUID.randomUUID(), "t", "", "127.0.0.1", server.port(),
                TestSshServer.USER, AuthMethod.PASSWORD, null, null, EnvironmentTag.DEV, null, null, false));
    }

    @AfterEach
    void tearDown() throws Exception {
        lease.close();
        sessions.close();
        server.close();
    }

    @Test
    void stdinTerkirimLaluEofDanOutputSertaExitStatusTerbaca() throws Exception {
        var result = RemoteExec.run(lease.connection(), "echo-stdin",
                "rahasia\n".getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(10));

        assertThat(result.exitStatus()).isEqualTo(3);
        assertThat(result.ok()).isFalse();
        assertThat(result.stdout()).isEqualTo("rahasia\n");
        assertThat(result.stderr()).isEqualTo("selesai");
        assertThat(commands).containsExactly("echo-stdin");
    }

    @Test
    void tanpaStdinTetapMengirimEof() throws Exception {
        var result = RemoteExec.run(lease.connection(), "echo-stdin", null, Duration.ofSeconds(10));

        assertThat(result.stdout()).isEmpty();
        assertThat(result.exitStatus()).isEqualTo(3);
    }

    @Test
    void commandYangTidakSelesaiDihentikanSetelahTimeout() {
        assertThatThrownBy(() -> RemoteExec.run(lease.connection(), "hang", null, Duration.ofMillis(500)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("tidak selesai");
        assertThat(lease.connection().isOpen()).isTrue(); // hanya channel yang ditutup
    }

    /** Menyalin stdin sampai EOF ke stdout, menulis "selesai" ke stderr, exit 3. */
    private static final class StdinEcho implements Command, Runnable {
        private InputStream in;
        private OutputStream out;
        private OutputStream err;
        private ExitCallback exit;

        @Override
        public void setInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public void setOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void setErrorStream(OutputStream err) {
            this.err = err;
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.exit = callback;
        }

        @Override
        public void start(ChannelSession channel, Environment env) {
            Thread.ofVirtual().start(this);
        }

        @Override
        public void run() {
            try {
                in.transferTo(out);
                out.flush();
                err.write("selesai".getBytes(StandardCharsets.UTF_8));
                err.flush();
                exit.onExit(3);
            } catch (IOException e) {
                exit.onExit(1, e.toString());
            }
        }

        @Override
        public void destroy(ChannelSession channel) {
        }
    }

    /** Tidak pernah selesai. */
    private static final class Hang implements Command {
        @Override
        public void setInputStream(InputStream in) {
        }

        @Override
        public void setOutputStream(OutputStream out) {
        }

        @Override
        public void setErrorStream(OutputStream err) {
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
        }

        @Override
        public void start(ChannelSession channel, Environment env) {
        }

        @Override
        public void destroy(ChannelSession channel) {
        }
    }
}

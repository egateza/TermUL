package dev.egateza.myterm.ssh.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

/**
 * Server SSH in-process (MINA) untuk unit test tanpa Docker.
 * User/password: {@value #USER} / {@value #PASSWORD}. Shell: echo baris per baris, {@code exit} menutup.
 */
public final class TestSshServer implements AutoCloseable {

    public static final String USER = "dev";
    public static final String PASSWORD = "devpass";

    private final SshServer server;
    private final List<PublicKey> authorizedKeys = new CopyOnWriteArrayList<>();
    /** Environment (termasuk TERM, COLUMNS, LINES) dari shell terakhir. */
    public volatile Environment lastShellEnv;
    public final List<String> windowChanges = new CopyOnWriteArrayList<>();

    public TestSshServer(Path hostKeyFile) throws IOException {
        this(hostKeyFile, s -> { });
    }

    public TestSshServer(Path hostKeyFile, Consumer<SshServer> customizer) throws IOException {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        var keys = new SimpleGeneratorHostKeyProvider(hostKeyFile);
        keys.setAlgorithm(KeyUtils.EC_ALGORITHM);
        server.setKeyPairProvider(keys);
        server.setPasswordAuthenticator((user, pass, session) -> USER.equals(user) && PASSWORD.equals(pass));
        server.setPublickeyAuthenticator((user, key, session) -> USER.equals(user)
                && authorizedKeys.stream().anyMatch(k -> KeyUtils.compareKeys(k, key)));
        server.setShellFactory(channel -> new EchoShell());
        customizer.accept(server);
        server.start();
    }

    public int port() {
        return server.getPort();
    }

    public SshServer server() {
        return server;
    }

    public void authorize(PublicKey key) {
        authorizedKeys.add(key);
    }

    /** Memutus semua session aktif dari sisi server (simulasi koneksi putus). */
    public void dropAllSessions() {
        server.getActiveSessions().forEach(s -> s.close(true));
    }

    @Override
    public void close() throws IOException {
        server.stop(true);
    }

    private final class EchoShell implements Command, Runnable {
        private InputStream in;
        private OutputStream out;
        private ExitCallback exit;
        private Thread thread;

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
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.exit = callback;
        }

        @Override
        public void start(ChannelSession channel, Environment env) {
            lastShellEnv = env;
            env.addSignalListener((ch, signal) -> windowChanges.add(
                    env.getEnv().get(Environment.ENV_COLUMNS) + "x" + env.getEnv().get(Environment.ENV_LINES)));
            thread = Thread.ofVirtual().name("echo-shell").start(this);
        }

        @Override
        public void run() {
            try {
                out.write("welcome\r\n$ ".getBytes(StandardCharsets.UTF_8));
                out.flush();
                var line = new java.io.ByteArrayOutputStream();
                int b;
                while ((b = in.read()) >= 0) {
                    if (b == '\r' || b == '\n') {
                        String cmd = line.toString(StandardCharsets.UTF_8);
                        line.reset();
                        if (cmd.equals("exit")) {
                            break;
                        }
                        out.write(("\r\necho:" + cmd + "\r\n$ ").getBytes(StandardCharsets.UTF_8));
                    } else {
                        line.write(b);
                        out.write(b);
                    }
                    out.flush();
                }
                exit.onExit(0);
            } catch (IOException e) {
                exit.onExit(1, e.toString());
            }
        }

        @Override
        public void destroy(ChannelSession channel) {
            if (thread != null) {
                thread.interrupt();
            }
        }
    }
}

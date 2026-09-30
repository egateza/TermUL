package dev.egateza.myterm.ssh;

import dev.egateza.myterm.core.profile.HostProfile;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.PtyChannelConfiguration;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Satu {@link ClientSession} terautentikasi yang dipakai bersama (shell, SFTP, exec).
 * Jangan ditutup langsung oleh pemakai: lepaskan lewat {@link SshLease#close()}.
 */
public final class SshConnection {

    private static final Logger log = LoggerFactory.getLogger(SshConnection.class);

    /** Terminal type yang diminta ke server. */
    public static final String TERM = "xterm-256color";

    private final HostProfile profile;
    private final ClientSession session;
    private final Duration channelOpenTimeout;
    private final CopyOnWriteArrayList<Consumer<SshConnection>> closeListeners = new CopyOnWriteArrayList<>();
    private volatile boolean closedByManager;

    SshConnection(HostProfile profile, ClientSession session, Duration channelOpenTimeout) {
        this.profile = Objects.requireNonNull(profile);
        this.session = Objects.requireNonNull(session);
        this.channelOpenTimeout = channelOpenTimeout;
        session.addSessionListener(new SessionListener() {
            @Override
            public void sessionClosed(Session s) {
                if (!closedByManager) {
                    log.info("Koneksi ke {} terputus", profile.address());
                }
                closeListeners.forEach(l -> {
                    try {
                        l.accept(SshConnection.this);
                    } catch (RuntimeException e) {
                        log.warn("Listener close gagal", e);
                    }
                });
            }
        });
    }

    public HostProfile profile() {
        return profile;
    }

    /** Akses langsung untuk modul lain (mis. SFTP). Jangan di-close. */
    public ClientSession session() {
        return session;
    }

    public boolean isOpen() {
        return session.isOpen() && !session.isClosing();
    }

    /** true kalau koneksi ditutup oleh aplikasi (bukan putus). */
    public boolean isClosedByManager() {
        return closedByManager;
    }

    /**
     * Dipanggil sekali saat koneksi tertutup (putus atau ditutup). Kalau sudah tertutup,
     * listener tidak dipanggil — cek {@link #isOpen()} setelah mendaftar.
     */
    public void addCloseListener(Consumer<SshConnection> listener) {
        closeListeners.add(Objects.requireNonNull(listener));
    }

    public void removeCloseListener(Consumer<SshConnection> listener) {
        closeListeners.remove(listener);
    }

    /** Membuka shell dengan PTY {@value #TERM}. Blocking; jangan dipanggil di EDT. */
    public ChannelShell openShell(PtySize size, Map<String, String> env) throws IOException {
        var pty = new PtyChannelConfiguration();
        pty.setPtyType(TERM);
        pty.setPtyColumns(size.columns());
        pty.setPtyLines(size.rows());
        ChannelShell channel = session.createShellChannel(pty, env == null ? Map.of() : env);
        channel.setRedirectErrorStream(true);
        try {
            channel.open().verify(channelOpenTimeout);
        } catch (IOException | RuntimeException e) {
            channel.close(true);
            throw e;
        }
        return channel;
    }

    /** Membuat exec channel (belum dibuka). Blocking; jangan dipanggil di EDT. */
    public ChannelExec createExec(String command) throws IOException {
        return session.createExecChannel(command);
    }

    public Duration channelOpenTimeout() {
        return channelOpenTimeout;
    }

    void closeByManager() {
        closedByManager = true;
        session.close(false);
    }
}

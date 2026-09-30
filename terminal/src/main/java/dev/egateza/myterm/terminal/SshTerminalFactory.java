package dev.egateza.myterm.terminal;

import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.ssh.PtySize;
import dev.egateza.myterm.ssh.SessionManager;
import dev.egateza.myterm.ssh.SshConnectException;
import dev.egateza.myterm.ssh.SshLease;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.sshd.client.channel.ChannelShell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Membuka sesi terminal: acquire koneksi → buka shell PTY → {@link SshTtyConnector}. */
public final class SshTerminalFactory {

    private static final Logger log = LoggerFactory.getLogger(SshTerminalFactory.class);

    public static final PtySize DEFAULT_SIZE = new PtySize(80, 24);

    private final SessionManager sessions;
    private final AtomicInteger counter = new AtomicInteger();

    public SshTerminalFactory(SessionManager sessions) {
        this.sessions = Objects.requireNonNull(sessions);
    }

    /** Blocking (network + prompt): jangan dipanggil di EDT. */
    public SshTtyConnector open(HostProfile profile, PtySize size) throws SshConnectException {
        SshLease lease = sessions.acquire(profile);
        try {
            ChannelShell shell = lease.connection().openShell(size, Map.of());
            var connector = new SshTtyConnector(lease, shell, profile.name() + "#" + counter.incrementAndGet());
            log.info("Shell {} dibuka ke {}", connector.getName(), profile.address());
            if (profile.initialDirectory() != null) {
                connector.write("cd " + ShellQuote.quote(profile.initialDirectory()) + "\r");
            }
            return connector;
        } catch (IOException | RuntimeException e) {
            lease.close();
            throw new SshConnectException("Gagal membuka shell di " + profile.address() + ": " + e.getMessage(), e);
        }
    }
}

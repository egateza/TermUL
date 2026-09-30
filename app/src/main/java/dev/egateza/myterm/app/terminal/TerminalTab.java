package dev.egateza.myterm.app.terminal;

import com.jediterm.terminal.ui.JediTermWidget;
import dev.egateza.myterm.app.ui.UiAsync;
import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.ssh.SshConnectException;
import dev.egateza.myterm.ssh.hostkey.HostKeyRejectedException;
import dev.egateza.myterm.terminal.SshTerminalFactory;
import dev.egateza.myterm.terminal.SshTtyConnector;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Isi satu tab terminal. Semua method dipanggil di EDT; connect berjalan di executor {@code ssh-ops}.
 * State: CONNECTING → CONNECTED → (DISCONNECTED | ERROR) → reconnect → CONNECTING ...
 */
public final class TerminalTab extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(TerminalTab.class);

    private final HostProfile profile;
    private final SshTerminalFactory factory;
    private final Executor sshOps;
    private final TerminalSettings settings;
    private final JPanel banner = new JPanel(new BorderLayout());
    private JediTermWidget widget;       // hanya diakses di EDT
    private SshTtyConnector connector;   // hanya diakses di EDT
    private CompletableFuture<SshTtyConnector> pending; // hanya diakses di EDT
    private boolean disposed;            // hanya diakses di EDT

    public TerminalTab(HostProfile profile, SshTerminalFactory factory, Executor sshOps, TerminalSettings settings) {
        super(new BorderLayout());
        this.profile = profile;
        this.factory = factory;
        this.sshOps = sshOps;
        this.settings = settings;
        add(banner, BorderLayout.NORTH);
        banner.setVisible(false);
    }

    public HostProfile profile() {
        return profile;
    }

    public void connect() {
        if (disposed || pending != null) {
            return;
        }
        hideBanner();
        showCenter(centerMessage("Menghubungkan ke " + profile.address() + " ..."));
        pending = UiAsync.run(sshOps,
                () -> {
                    try {
                        return factory.open(profile, SshTerminalFactory.DEFAULT_SIZE);
                    } catch (SshConnectException e) {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                },
                this::onConnected,
                this::onConnectFailed);
    }

    private void onConnected(SshTtyConnector tty) {
        pending = null;
        if (disposed) {
            tty.close();
            return;
        }
        connector = tty;
        widget = new JediTermWidget(settings);
        widget.setTtyConnector(tty);
        tty.addCloseListener(c -> SwingUtilities.invokeLater(() -> onClosed(c)));
        showCenter(widget);
        widget.start();
        widget.requestFocusInWindow();
    }

    private void onConnectFailed(Throwable error) {
        pending = null;
        if (disposed) {
            return;
        }
        log.info("Connect ke {} gagal: {}", profile.address(), error.getMessage());
        String message = error instanceof SshConnectException ? error.getMessage()
                : "Kesalahan tak terduga: " + error;
        var text = new JTextArea(message);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        if (error instanceof HostKeyRejectedException) {
            text.setForeground(new Color(0xE5484D));
            text.setFont(text.getFont().deriveFont(java.awt.Font.BOLD));
        }
        var panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(text), BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        var retry = new JButton("Coba lagi");
        retry.addActionListener(e -> connect());
        buttons.add(retry);
        panel.add(buttons, BorderLayout.SOUTH);
        showCenter(panel);
    }

    private void onClosed(SshTtyConnector tty) {
        if (disposed || tty != connector || tty.isClosedByUser()) {
            return;
        }
        showBanner("Sesi ke " + profile.address() + " berakhir atau koneksi terputus.");
    }

    private void showBanner(String text) {
        banner.removeAll();
        banner.setBackground(new Color(0x5A2A2A));
        banner.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        var label = new JLabel(text);
        label.setForeground(Color.WHITE);
        banner.add(label, BorderLayout.CENTER);
        var reconnect = new JButton("Reconnect");
        reconnect.addActionListener(e -> reconnect());
        banner.add(reconnect, BorderLayout.EAST);
        banner.setVisible(true);
        banner.revalidate();
        banner.repaint();
    }

    private void hideBanner() {
        banner.setVisible(false);
        banner.removeAll();
    }

    public void reconnect() {
        disposeTerminal();
        connect();
    }

    public void focusTerminal() {
        if (widget != null) {
            widget.requestFocusInWindow();
        }
    }

    /** Menutup terminal (tab ditutup). Idempotent. */
    public void dispose() {
        disposed = true;
        disposeTerminal();
    }

    private void disposeTerminal() {
        if (connector != null) {
            connector.close();
            connector = null;
        }
        if (widget != null) {
            widget.close();
            widget = null;
        }
    }

    private void showCenter(java.awt.Component c) {
        var layout = (BorderLayout) getLayout();
        var old = layout.getLayoutComponent(BorderLayout.CENTER);
        if (old != null) {
            remove(old);
        }
        add(c, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private static JLabel centerMessage(String text) {
        return new JLabel(text, SwingConstants.CENTER);
    }
}

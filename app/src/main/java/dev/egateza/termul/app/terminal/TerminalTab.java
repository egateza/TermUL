package dev.egateza.termul.app.terminal;

import dev.egateza.termul.app.sftp.SftpPanel;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.app.vault.VaultGate;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.ssh.SshConnectException;
import dev.egateza.termul.ssh.hostkey.HostKeyRejectedException;
import dev.egateza.termul.terminal.ExitGuard;
import dev.egateza.termul.terminal.SshTerminalFactory;
import dev.egateza.termul.terminal.SshTtyConnector;
import dev.egateza.termul.vault.SecretType;
import dev.egateza.termul.vault.Secrets;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
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
    private ZoomableTermWidget widget;   // hanya diakses di EDT
    private SshTtyConnector connector;   // hanya diakses di EDT
    private CompletableFuture<SshTtyConnector> pending; // hanya diakses di EDT
    private boolean disposed;            // hanya diakses di EDT
    private final JPanel content = new JPanel(new BorderLayout()); // banner + terminal
    private final Function<HostProfile, SftpPanel> sftpFactory;
    private SftpPanel sftp;              // dibuat saat pertama kali dibuka
    private JSplitPane split;
    private int sftpDivider = 380;

    public TerminalTab(HostProfile profile, SshTerminalFactory factory, Executor sshOps, TerminalSettings settings,
                       Function<HostProfile, SftpPanel> sftpFactory) {
        super(new BorderLayout());
        this.profile = profile;
        this.factory = factory;
        this.sshOps = sshOps;
        this.settings = settings.copy(); // zoom per tab
        this.sftpFactory = sftpFactory;
        content.add(banner, BorderLayout.NORTH);
        banner.setVisible(false);
        add(content, BorderLayout.CENTER);
    }

    private java.util.function.Consumer<SshTtyConnector> connectedListener; // EDT
    private Runnable stateListener = () -> { }; // EDT

    /** Dipanggil (di EDT) setiap status sesi/panel SFTP berubah: connect, gagal, berakhir, toggle SFTP. */
    public void setStateListener(Runnable listener) {
        this.stateListener = listener;
    }

    /** Dipanggil (di EDT) setiap kali terminal berhasil connect, termasuk setelah reconnect. */
    public void setConnectedListener(java.util.function.Consumer<SshTtyConnector> listener) {
        this.connectedListener = listener;
    }

    /** Menampilkan/menyembunyikan panel SFTP di kiri terminal (koneksi SSH yang sama). */
    public void toggleSftp() {
        if (split != null) {
            sftpDivider = split.getDividerLocation();
            remove(split);
            split.remove(content);
            split = null;
            add(content, BorderLayout.CENTER);
            focusTerminal();
        } else {
            if (sftp == null) {
                sftp = sftpFactory.apply(profile);
            }
            remove(content);
            split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sftp, content);
            split.setContinuousLayout(true);
            split.setDividerLocation(sftpDivider);
            add(split, BorderLayout.CENTER);
            sftp.connect();
        }
        revalidate();
        repaint();
        stateListener.run();
    }

    public HostProfile profile() {
        return profile;
    }

    /** true kalau terminal sedang tersambung ke server (bukan sekadar sedang connect). */
    public boolean isConnected() {
        return connector != null && connector.isConnected();
    }

    public boolean isSftpOpen() {
        return split != null;
    }

    /** true kalau sesi sedang connect atau masih terhubung (menutup tab akan memutusnya). */
    public boolean isSessionActive() {
        return pending != null || (connector != null && connector.isConnected());
    }

    /** Jumlah transfer SFTP yang berjalan/antre di tab ini. */
    public int activeTransfers() {
        return sftp == null ? 0 : sftp.activeTransfers();
    }

    public void connect() {
        if (disposed || pending != null) {
            return;
        }
        hideBanner();
        showCenter(centerMessage("Menghubungkan ke " + profile.address() + " ..."));
        stateListener.run();
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
        widget = new ZoomableTermWidget(settings);
        widget.setTtyConnector(tty);
        tty.addCloseListener(c -> SwingUtilities.invokeLater(() -> onClosed(c)));
        showCenter(widget);
        widget.start();
        if (connectedListener != null) {
            connectedListener.accept(tty);
        }
        widget.requestFocusInWindow();
        stateListener.run();
    }

    private void onConnectFailed(Throwable error) {
        pending = null;
        stateListener.run();
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
        var retry = new JButton("Coba lagi", AppIcon.RECONNECT.icon());
        retry.addActionListener(e -> connect());
        buttons.add(retry);
        panel.add(buttons, BorderLayout.SOUTH);
        showCenter(panel);
    }

    private void onClosed(SshTtyConnector tty) {
        stateListener.run();
        log.info("Sesi terminal {} berakhir (disposed={}, current={}, byUser={})",
                tty.getName(), disposed, tty == connector, tty.isClosedByUser());
        if (disposed || tty != connector || tty.isClosedByUser()) {
            return;
        }
        sessionEnded = true;
        showBanner("Sesi ke " + profile.address() + " berakhir. Tekan Enter atau klik Reconnect untuk membuka lagi.");
    }

    private boolean sessionEnded; // EDT

    /**
     * Dipanggil dari dispatcher key global (EDT) sebelum JediTerm menerima key.
     * <ul>
     *   <li>Ctrl+D di prompt kosong / {@code exit}/{@code logout} + Enter → konfirmasi dulu</li>
     *   <li>Enter setelah sesi berakhir → reconnect</li>
     * </ul>
     *
     * @return true kalau event sudah ditangani (jangan diteruskan ke terminal)
     */
    public boolean interceptKey(KeyEvent e) {
        if (e.getID() == KeyEvent.KEY_TYPED && swallowTyped) {
            swallowTyped = false; // pasangan KEY_TYPED dari key yang sudah ditangani
            return true;
        }
        if (e.getID() != KeyEvent.KEY_PRESSED || widget == null) {
            return false;
        }
        boolean handled = handlePressed(e);
        swallowTyped = handled;
        return handled;
    }

    private boolean swallowTyped; // EDT

    private boolean handlePressed(KeyEvent e) {
        Integer zoom = zoomDirection(e);
        if (zoom != null) {
            zoom(zoom);
            return true;
        }
        boolean enter = e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiersEx() == 0;
        boolean ctrlD = e.getKeyCode() == KeyEvent.VK_D
                && e.getModifiersEx() == InputEvent.CTRL_DOWN_MASK;
        if (sessionEnded) {
            if (enter) {
                reconnect();
                return true;
            }
            return false;
        }
        if ((!enter && !ctrlD) || connector == null || !connector.isConnected()) {
            return false;
        }
        String line = cursorLine();
        if (ctrlD && ExitGuard.isEmptyPrompt(line)) {
            if (confirmExit()) {
                connector.write(new byte[] {0x04});
            }
            return true;
        }
        if (enter && ExitGuard.isExitCommand(line)) {
            // Tidak → Ctrl+U menghapus baris perintah di shell (readline)
            connector.write(confirmExit() ? new byte[] {'\r'} : new byte[] {0x15});
            return true;
        }
        return false;
    }

    /**
     * Arah zoom dari shortcut: Ctrl + (+, =, numpad +) → 1, Ctrl + (-, numpad -) → -1, Ctrl+0 → 0 (reset).
     * Shift boleh (di keyboard US, '+' = Shift+'='); Alt tidak.
     */
    static Integer zoomDirection(KeyEvent e) {
        int mods = e.getModifiersEx() & ~InputEvent.SHIFT_DOWN_MASK;
        if (mods != InputEvent.CTRL_DOWN_MASK) {
            return null;
        }
        return switch (e.getKeyCode()) {
            case KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD -> 1;
            case KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_MINUS
                    ? null // Ctrl+Shift+- = Ctrl+_ (undo readline), biarkan ke terminal
                    : -1;
            case KeyEvent.VK_0, KeyEvent.VK_NUMPAD0 -> e.isShiftDown() ? null : 0;
            default -> null;
        };
    }

    /** @param direction 1 = perbesar, -1 = perkecil, 0 = ukuran default */
    public void zoom(int direction) {
        float size = direction == 0 ? settings.defaultSize() : settings.getTerminalFontSize() + direction;
        float applied = settings.setFontSize(size);
        if (widget != null) {
            widget.refreshFont();
        }
        log.debug("Zoom terminal {}: {}pt", profile.address(), applied);
    }

    private boolean confirmExit() {
        boolean yes = JOptionPane.showConfirmDialog(this,
                "Anda akan keluar dari sesi " + profile.address() + ". Lanjutkan?",
                "Keluar dari sesi", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
        focusTerminal();
        return yes;
    }

    /** Teks baris tempat kursor berada (kosong kalau tidak bisa dibaca). */
    private String cursorLine() {
        var buffer = widget.getTerminalTextBuffer();
        buffer.lock();
        try {
            int y = widget.getTerminal().getCursorY() - 1; // cursorY 1-based
            if (y < 0 || y >= buffer.getHeight()) {
                return "";
            }
            return buffer.getLine(y).getText();
        } finally {
            buffer.unlock();
        }
    }

    private void showBanner(String text) {
        banner.removeAll();
        banner.setBackground(new Color(0x5A2A2A));
        banner.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        var label = new JLabel(text);
        label.setForeground(Color.WHITE);
        banner.add(label, BorderLayout.CENTER);
        var reconnect = new JButton("Reconnect", AppIcon.RECONNECT.icon());
        reconnect.addActionListener(e -> reconnect());
        banner.add(reconnect, BorderLayout.EAST);
        banner.setVisible(true);
        content.revalidate();
        content.repaint();
    }

    private void hideBanner() {
        sessionEnded = false;
        banner.setVisible(false);
        banner.removeAll();
    }

    public void reconnect() {
        disposeTerminal();
        connect();
    }

    /**
     * Mengirim secret dari vault + Enter ke terminal ini (hotkey). User yang menentukan kapan,
     * sehingga tidak ada prompt yang bisa di-spoof. Dipanggil di EDT; vault diakses di {@code sshOps}.
     */
    public void injectSecret(SecretType type, VaultGate gate) {
        SshTtyConnector target = connector;
        if (target == null || !target.isConnected()) {
            return;
        }
        UiAsync.run(sshOps, () -> {
            if (!gate.ensureUnlocked()) {
                return InjectResult.CANCELLED;
            }
            char[] secret = gate.vault().get(profile.id(), type);
            if (secret == null) {
                return InjectResult.MISSING;
            }
            try {
                target.writeSecret(Secrets.toUtf8WithSuffix(secret, (byte) '\r'));
            } finally {
                Secrets.zero(secret);
            }
            log.info("Password {} di-inject ke {}", type, profile.address()); // tanpa isi secret
            return InjectResult.SENT;
        }, result -> {
            if (result == InjectResult.MISSING) {
                String what = type == SecretType.ROOT_PASSWORD ? "root" : "sudo";
                Dialogs.info(this, "Inject password",
                        "Password " + what + " untuk " + profile.name() + " belum disimpan di vault.\n"
                                + "Isi lewat Edit host (F2).");
            }
            focusTerminal();
        }, err -> Dialogs.error(this, "Inject password gagal", err));
    }

    private enum InjectResult { SENT, MISSING, CANCELLED }

    public void focusTerminal() {
        if (widget != null) {
            widget.requestFocusInWindow();
        }
    }

    /** Menutup terminal (tab ditutup). Idempotent. */
    public void dispose() {
        disposed = true;
        disposeTerminal();
        if (sftp != null) {
            sftp.dispose();
        }
    }

    private void disposeTerminal() {
        if (connector != null) {
            connector.closeByUser();
            connector = null;
        }
        if (widget != null) {
            widget.close();
            widget = null;
        }
    }

    private void showCenter(java.awt.Component c) {
        var layout = (BorderLayout) content.getLayout();
        var old = layout.getLayoutComponent(BorderLayout.CENTER);
        if (old != null) {
            content.remove(old);
        }
        content.add(c, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private static JLabel centerMessage(String text) {
        return new JLabel(text, SwingConstants.CENTER);
    }
}

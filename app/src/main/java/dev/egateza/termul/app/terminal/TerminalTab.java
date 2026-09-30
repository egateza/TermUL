package dev.egateza.termul.app.terminal;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.sftp.SftpPanel;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.app.vault.VaultGate;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.sftp.RemoteFileException;
import dev.egateza.termul.sftp.SftpLinks;
import dev.egateza.termul.sftp.TerminalState;
import dev.egateza.termul.ssh.SshConnectException;
import dev.egateza.termul.ssh.hostkey.HostKeyRejectedException;
import dev.egateza.termul.terminal.ExitGuard;
import dev.egateza.termul.terminal.PromptResponder;
import dev.egateza.termul.terminal.Reconnector;
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
 *
 * <p>Tab terminal adalah pemilik sesi: kalau koneksi terputus, tab ini menyambung ulang bertahap (langsung, +15 dtk,
 * +30 dtk). Satu percobaan baru dianggap berhasil kalau shell <b>dan</b> SFTP (bila sedang dipakai) sama-sama
 * tersambung. Panel SFTP hanya mengikuti status tab ini lewat {@link SftpLinks}.
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
    private final SftpLinks links;
    private final SftpLinks.TerminalHandle gate;   // status sesi ini untuk panel SFTP & sesi edit
    private final boolean sftpOnly;               // tab berisi panel SFTP saja, tanpa shell
    private javax.swing.Timer monitor;             // EDT; hanya mode sftpOnly: mendeteksi koneksi SFTP putus
    private Reconnector<?> reconnector; // EDT; tidak null selama reconnect otomatis berjalan
    private SftpPanel sftp;              // dibuat saat pertama kali dibuka
    private JSplitPane split;
    private int sftpDivider = 380;

    public TerminalTab(HostProfile profile, SshTerminalFactory factory, Executor sshOps, TerminalSettings settings,
                       SftpLinks links, boolean sftpOnly, Function<HostProfile, SftpPanel> sftpFactory) {
        super(new BorderLayout());
        this.sftpOnly = sftpOnly;
        this.links = links;
        this.gate = links.registerTerminal(profile);
        this.profile = profile;
        this.factory = factory;
        this.sshOps = sshOps;
        this.settings = settings.copy(); // zoom per tab
        this.sftpFactory = sftpFactory;
        content.add(banner, BorderLayout.NORTH);
        banner.setVisible(false);
        add(content, BorderLayout.CENTER);
        if (sftpOnly) {
            sftp = sftpFactory.apply(profile);
            content.add(sftp, BorderLayout.CENTER);
        }
    }

    /** true kalau tab ini hanya berisi panel SFTP (tanpa terminal). */
    public boolean isSftpOnly() {
        return sftpOnly;
    }

    private java.util.function.Consumer<SshTtyConnector> connectedListener; // EDT
    private Runnable stateListener = () -> { }; // EDT

    /** Dipanggil (di EDT) setiap status sesi/panel SFTP berubah: connect, gagal, berakhir, toggle SFTP. */
    public void setStateListener(Runnable listener) {
        this.stateListener = listener;
    }

    private java.util.function.BooleanSupplier autoSudoEnabled = () -> false; // EDT
    private VaultGate autoSudoVault;     // EDT; null = auto-inject mati
    private PromptResponder responder;   // EDT; milik connector saat ini

    /**
     * Mengaktifkan auto-inject password sudo/su (guard lengkap di {@link PromptResponder}).
     *
     * @param enabled dibaca setiap Enter (toggle di profil langsung berlaku untuk tab yang sudah terbuka)
     */
    public void setAutoSudo(java.util.function.BooleanSupplier enabled, VaultGate vault) {
        this.autoSudoEnabled = enabled;
        this.autoSudoVault = vault;
    }

    /** Dipanggil (di EDT) setiap kali terminal berhasil connect, termasuk setelah reconnect. */
    public void setConnectedListener(java.util.function.Consumer<SshTtyConnector> listener) {
        this.connectedListener = listener;
    }

    /** Menampilkan/menyembunyikan panel SFTP di kiri terminal (koneksi SSH yang sama). */
    public void toggleSftp() {
        if (sftpOnly) {
            return; // panel SFTP adalah isi tab ini
        }
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
        if (sftpOnly) {
            return sftp != null && sftp.isConnected();
        }
        return pending != null || (connector != null && connector.isConnected());
    }

    /** Jumlah transfer SFTP yang berjalan/antre di tab ini. */
    public int activeTransfers() {
        return sftp == null ? 0 : sftp.activeTransfers();
    }

    public void connect() {
        if (sftpOnly) {
            connectSftpOnly();
            return;
        }
        if (disposed || pending != null) {
            return;
        }
        hideBanner();
        gate.set(TerminalState.CONNECTING);
        showCenter(centerMessage(I18n.t("tab.connecting", profile.address())));
        stateListener.run();
        pending = UiAsync.run(sshOps,
                () -> {
                    try {
                        return openSession();
                    } catch (SshConnectException e) {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                },
                this::onConnected,
                this::onConnectFailed);
    }

    /**
     * Mode "SFTP saja": tidak ada terminal yang ditunggu, jadi SFTP langsung boleh dibuka. Tab ini sendiri yang
     * memantau koneksi dan menyambung ulang bertahap (lihat {@link #startSftpReconnect}).
     */
    private void connectSftpOnly() {
        if (disposed) {
            return;
        }
        hideBanner();
        gate.set(TerminalState.CONNECTED);
        sftp.connect();
        if (monitor == null) {
            monitor = new javax.swing.Timer(1000, e -> checkSftp());
            monitor.start();
        }
        stateListener.run();
    }

    private void checkSftp() {
        if (disposed || autoReconnecting || sessionEnded || sftp == null) {
            return;
        }
        if (sftp.isChannelDead()) {
            startSftpReconnect();
        }
    }

    /**
     * Satu percobaan sambung: buka shell, lalu pulihkan SFTP kalau sedang dipakai (panel atau sesi edit). Kalau SFTP
     * gagal, shell yang baru dibuka ditutup dan percobaan dianggap gagal, supaya terminal dan SFTP selalu tersambung
     * bersama. Blocking: jalankan di {@code sshOps}.
     */
    private SshTtyConnector openSession() throws SshConnectException {
        SshTtyConnector tty = factory.open(profile, SshTerminalFactory.DEFAULT_SIZE);
        try {
            links.restoreSftp(profile);
        } catch (RemoteFileException e) {
            tty.close();
            throw new SshConnectException(I18n.t("tab.error.sftpOpen", e.getMessage()), e);
        }
        return tty;
    }

    private void onConnected(SshTtyConnector tty) {
        pending = null;
        if (disposed) {
            tty.close();
            return;
        }
        reconnector = null;
        autoReconnecting = false;
        hideBanner();
        if (widget != null) {
            widget.close(); // terminal lama yang beku setelah koneksi putus
        }
        connector = tty;
        responder = new PromptResponder(profile.username(), new PromptResponder.Listener() {
            @Override
            public void respond(PromptResponder.Kind kind) {
                SwingUtilities.invokeLater(() -> autoInject(kind, tty));
            }

            @Override
            public void rejected(PromptResponder.Kind kind) {
                SwingUtilities.invokeLater(() -> {
                    if (tty == connector) {
                        showNotice(I18n.t("tab.autoInject.rejected", kind == PromptResponder.Kind.SU ? "root" : "sudo"));
                    }
                });
            }
        });
        tty.addOutputListener(responder);
        gate.set(TerminalState.CONNECTED);
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
        gate.set(TerminalState.DOWN);
        stateListener.run();
        if (disposed) {
            return;
        }
        log.info("Connect ke {} gagal: {}", profile.address(), error.getMessage());
        String message = error instanceof SshConnectException ? error.getMessage()
                : I18n.t("tab.error.unexpected", String.valueOf(error));
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
        var retry = new JButton(I18n.t("tab.retry"), AppIcon.RECONNECT.icon());
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
        if (tty.isConnectionLost()) { // koneksi SSH putus (bukan sekadar exit/logout di shell)
            startAutoReconnect();
            return;
        }
        gate.set(TerminalState.DOWN);
        sessionEnded = true;
        showBanner(I18n.t("tab.sessionEnded", profile.address()));
    }

    private boolean sessionEnded; // EDT
    private boolean autoReconnecting; // EDT

    /** Koneksi terminal putus: sambung ulang shell dan SFTP bersama. */
    private void startAutoReconnect() {
        startReconnect(this::openSession, this::onConnected, I18n.t("tab.reconnect.what.both"));
    }

    /** Koneksi SFTP putus pada tab "SFTP saja": buka lagi kanalnya. */
    private void startSftpReconnect() {
        startReconnect(() -> {
            links.restoreSftp(profile);
            if (sftp.isChannelDead()) {
                throw new java.io.IOException(I18n.t("tab.error.sftpChannel"));
            }
            return Boolean.TRUE;
        }, ok -> {
            reconnector = null;
            autoReconnecting = false;
            hideBanner();
            gate.set(TerminalState.CONNECTED);
            stateListener.run();
        }, "SFTP");
    }

    /**
     * Sambung ulang bertahap (langsung, +15 dtk, +30 dtk) sampai berhasil. Layar lama tetap terlihat di bawah banner.
     * Selama berjalan, panel SFTP dan sesi edit menunggu ({@link TerminalState#RECONNECTING}).
     *
     * @param what    yang disambung, untuk teks banner
     * @param success dipanggil di EDT saat berhasil
     */
    private <T> void startReconnect(Reconnector.Attempt<T> attempt, java.util.function.Consumer<T> success, String what) {
        String address = profile.address();
        var r = new Reconnector<T>();
        reconnector = r;
        autoReconnecting = true;
        sessionEnded = false;
        gate.set(TerminalState.RECONNECTING);
        stateListener.run();
        log.info("Koneksi ke {} terputus, menyambung ulang {}", address, what);
        showBanner(I18n.t("tab.reconnect.lost", address, what), null, null);
        sshOps.execute(() -> r.run(attempt, new Reconnector.Listener<T>() {
            @Override
            public void attempting(int n) {
                SwingUtilities.invokeLater(() -> {
                    if (reconnector == r) {
                        showBanner(n == 1
                                ? I18n.t("tab.reconnect.lost", address, what)
                                : I18n.t("tab.reconnect.attempt", what, address, String.valueOf(n)), null, null);
                    }
                });
            }

            @Override
            public void waiting(int secondsLeft, Exception lastError) {
                SwingUtilities.invokeLater(() -> {
                    if (reconnector == r) {
                        showBanner(I18n.t("tab.reconnect.failedRetry", lastError.getMessage(), String.valueOf(secondsLeft)),
                                I18n.t("tab.reconnect.buttonWait", String.valueOf(secondsLeft)),
                                r::skipWait);
                    }
                });
            }

            @Override
            public void connected(T value) {
                SwingUtilities.invokeLater(() -> {
                    if (disposed || reconnector != r) {
                        discarded(value);
                        return;
                    }
                    log.info("Tersambung kembali ke {} ({})", address, what);
                    success.accept(value);
                });
            }

            @Override
            public void discarded(T value) {
                if (value instanceof SshTtyConnector tty) {
                    tty.close(); // shell yang sudah terlanjur dibuka tapi tidak dipakai
                }
            }

            @Override
            public void gaveUp(Exception lastError) {
                SwingUtilities.invokeLater(() -> {
                    if (disposed || reconnector != r) {
                        return;
                    }
                    reconnector = null;
                    autoReconnecting = false;
                    gate.set(TerminalState.DOWN);
                    sessionEnded = true;
                    stateListener.run();
                    showBanner(I18n.t("tab.reconnect.gaveUp", address, lastError.getMessage()),
                            I18n.t("tab.reconnect"), TerminalTab.this::reconnect);
                });
            }
        }));
    }

    private void cancelAutoReconnect() {
        if (reconnector != null) {
            reconnector.cancel();
            reconnector = null;
        }
        autoReconnecting = false;
    }

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
        if (autoReconnecting) { // Enter memotong hitung mundur
            if (enter && reconnector != null) {
                reconnector.skipWait();
            }
            return enter;
        }
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
        if (enter && responder != null) {
            // baris kosong = disarm; Enter tetap diteruskan ke terminal
            responder.onEnter(autoSudoVault != null && autoSudoEnabled.getAsBoolean() ? line : "");
        }
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

    /** Pakai family font terminal yang baru dipilih (pengaturan bersama), dengan ukuran zoom tab ini. EDT. */
    public void reloadFont() {
        settings.reloadFont();
        if (widget != null) {
            widget.refreshFont();
        }
    }

    /** Pakai warna terminal yang baru dipilih (pengaturan bersama) di tab ini. EDT. */
    public void reloadColors() {
        if (widget != null) {
            widget.refreshColors();
        }
    }

    private boolean confirmExit() {
        boolean yes = JOptionPane.showConfirmDialog(this,
                I18n.t("tab.exit.confirm", profile.address()),
                I18n.t("tab.exit.title"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
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
        showBanner(text, I18n.t("tab.reconnect"), this::reconnect);
    }

    /** @param buttonText null = tanpa tombol (sedang mencoba) */
    private void showBanner(String text, String buttonText, Runnable action) {
        banner.removeAll();
        banner.setBackground(new Color(0x5A2A2A));
        banner.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        var label = new JLabel(text);
        label.setForeground(Color.WHITE);
        banner.add(label, BorderLayout.CENTER);
        if (buttonText != null) {
            var reconnect = new JButton(buttonText, AppIcon.RECONNECT.icon());
            reconnect.addActionListener(e -> action.run());
            banner.add(reconnect, BorderLayout.EAST);
        }
        banner.setVisible(true);
        content.revalidate();
        content.repaint();
    }

    private void hideBanner() {
        sessionEnded = false;
        banner.setVisible(false);
        banner.removeAll();
    }

    /** Reconnect manual: menutup sesi sekarang lalu membuka lagi (shell + SFTP bersama). */
    public void reconnect() {
        cancelAutoReconnect();
        if (sftpOnly) {
            hideBanner();
            if (sftp.isChannelDead()) {
                startSftpReconnect();
            } else {
                connectSftpOnly(); // belum pernah tersambung: coba buka lagi
            }
            return;
        }
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
                Dialogs.info(this, I18n.t("tab.inject.title"),
                        I18n.t("tab.inject.missing", what, profile.name()));
            }
            focusTerminal();
        }, err -> Dialogs.error(this, I18n.t("tab.inject.failed"), err));
    }

    private enum InjectResult { SENT, MISSING, CANCELLED }

    /**
     * Guard {@link PromptResponder} lolos: kirim password dari vault. Tanpa dialog kalau password tidak tersimpan
     * (vault tidak di-unlock untuk profil yang memang tidak punya password sudo/root). EDT.
     */
    private void autoInject(PromptResponder.Kind kind, SshTtyConnector target) {
        VaultGate vault = autoSudoVault;
        if (vault == null || target != connector || !target.isConnected()) {
            return;
        }
        SecretType type = kind == PromptResponder.Kind.SU ? SecretType.ROOT_PASSWORD : SecretType.SUDO_PASSWORD;
        sshOps.execute(() -> {
            try {
                if (!vault.vault().has(profile.id(), type)) {
                    log.info("Auto-inject {} ke {} dilewati: password belum disimpan", type, profile.address());
                    return;
                }
                if (!vault.ensureUnlocked()) {
                    return;
                }
                char[] secret = vault.vault().get(profile.id(), type);
                if (secret == null) {
                    return;
                }
                try {
                    target.writeSecret(Secrets.toUtf8WithSuffix(secret, (byte) '\r'));
                } finally {
                    Secrets.zero(secret);
                }
                log.info("Password {} di-inject otomatis ke {}", type, profile.address()); // tanpa isi secret
            } catch (RuntimeException e) {
                log.warn("Auto-inject {} ke {} gagal: {}", type, profile.address(), e.getMessage());
            }
        });
    }

    /** Pemberitahuan di atas terminal (sesi tetap berjalan) dengan tombol tutup. EDT. */
    private void showNotice(String text) {
        banner.removeAll();
        banner.setBackground(new Color(0x5A2A2A));
        banner.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        var label = new JLabel(text);
        label.setForeground(Color.WHITE);
        banner.add(label, BorderLayout.CENTER);
        var close = new JButton(I18n.t("tab.notice.close"));
        close.addActionListener(e -> {
            hideBanner();
            focusTerminal();
        });
        banner.add(close, BorderLayout.EAST);
        banner.setVisible(true);
        content.revalidate();
        content.repaint();
    }

    public void focusTerminal() {
        if (widget != null) {
            widget.requestFocusInWindow();
        }
    }

    /** Menutup terminal (tab ditutup). Idempotent. */
    public void dispose() {
        disposed = true;
        cancelAutoReconnect();
        if (monitor != null) {
            monitor.stop();
        }
        gate.close();
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

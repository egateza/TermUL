package dev.egateza.termul.app.terminal;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.app.ui.anim.AnimationKind;
import dev.egateza.termul.app.ui.anim.LoadingPanel;
import dev.egateza.termul.app.ui.fab.FloatingOverlay;
import dev.egateza.termul.localterm.LocalShell;
import dev.egateza.termul.localterm.LocalShellException;
import dev.egateza.termul.localterm.LocalShells;
import dev.egateza.termul.localterm.LocalTerminalFactory;
import dev.egateza.termul.localterm.LocalTtyConnector;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Tab terminal lokal (ADR 0005): shell di mesin sendiri lewat PTY lokal, dengan widget, zoom, tema, pencarian, dan
 * snippet yang sama seperti tab SSH. Tidak ada fitur khusus SSH: tanpa SFTP, inject/auto-sudo, vault, atau konfirmasi
 * paste per environment. Semua method dipanggil di EDT; shell dijalankan dan dimatikan di luar EDT.
 */
public final class LocalTerminalTab extends TermPane {

    private final LocalProfiles.Choice profile;
    private final LocalShell shell;
    private final LocalTerminalFactory factory;
    private final Executor io;
    private final TerminalSettings settings;
    private final JPanel banner = new JPanel(new BorderLayout());
    private final JPanel content = new JPanel(new BorderLayout()); // banner + terminal
    private final FloatingOverlay center = new FloatingOverlay();
    private ZoomableTermWidget widget;          // EDT
    private LocalTtyConnector connector;        // EDT
    private CompletableFuture<LocalTtyConnector> pending; // EDT; shell sedang dijalankan
    private TerminalFloats floats;              // EDT
    private SnippetHost snippetHost;            // EDT
    private SnippetButton snippetButton;        // EDT
    private Runnable stateListener = () -> { }; // EDT
    private boolean disposed;                   // EDT
    private boolean sessionEnded;               // EDT

    /**
     * @param profile shell terdeteksi atau profil buatan user (perintah, direktori, environment, warna)
     * @param io      executor untuk menjalankan shell (blocking: start proses, memuat library native)
     */
    public LocalTerminalTab(LocalProfiles.Choice profile, LocalTerminalFactory factory, Executor io,
                            TerminalSettings settings) {
        this.profile = profile;
        this.shell = LocalShells.build(profile.command(), profile.directory(), profile.environment());
        this.factory = factory;
        this.io = io;
        this.settings = settings.copy(); // zoom per tab
        content.add(banner, BorderLayout.NORTH);
        banner.setVisible(false);
        center.setMargins(28, 16); // kanan lebih lebar: jangan menutupi scrollbar terminal
        content.add(center, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    /** Id profil (untuk Duplikat tab). */
    public java.util.UUID profileId() {
        return profile.id();
    }

    private java.util.function.Function<String, dev.egateza.termul.core.theme.CustomTheme.TerminalPalette> palettes =
            id -> null; // EDT; id warna terminal profil -> palet (null = ikut pengaturan)

    /** Penerjemah warna terminal khusus profil (id -> palet); dibaca setiap warna dimuat ulang. */
    public void setPalettes(java.util.function.Function<String,
            dev.egateza.termul.core.theme.CustomTheme.TerminalPalette> resolver) {
        this.palettes = resolver;
        reloadColors();
    }

    /** Dipanggil (di EDT) setiap status shell berubah: berjalan, gagal, berakhir. */
    public void setStateListener(Runnable listener) {
        this.stateListener = listener;
    }

    /** Pasang tombol snippet: snippet untuk semua host saja (tab lokal tidak punya host atau grup). */
    public void setSnippetHost(SnippetHost host) {
        this.snippetHost = host;
        if (snippetButton != null) {
            snippetButton.dispose();
        }
        snippetButton = new SnippetButton(center, host.corner(), snippetTarget(), host.sender(), host::manage,
                host::cornerChanged);
        refreshSnippetButton();
    }

    @Override
    public void openSnippetMenu() {
        if (snippetButton != null && widget != null) {
            snippetButton.showMenu();
        }
    }

    @Override
    public dev.egateza.termul.app.snippet.SnippetTarget snippetTarget() {
        return new dev.egateza.termul.app.snippet.SnippetTarget() {
            @Override
            public String name() {
                return displayName();
            }

            @Override
            public String address() {
                return displayAddress();
            }

            @Override
            public String hostKey() {
                return "local:" + profile.id();
            }

            @Override
            public String hostName() {
                return "";
            }

            @Override
            public String hostGroup() {
                return "";
            }

            @Override
            public boolean canPaste() {
                return connector != null && connector.isConnected();
            }

            @Override
            public boolean bracketedPaste() {
                return widget != null && widget.isBracketedPaste();
            }

            @Override
            public void paste(String text) {
                typeText(text);
            }

            @Override
            public void focus() {
                focusTerminal();
            }
        };
    }

    /** Kirim teks snippet apa adanya ke shell (disusun {@link dev.egateza.termul.core.snippet.SnippetPayload}). */
    public void typeText(String text) {
        if (connector == null || !connector.isConnected() || text.isEmpty()) {
            return;
        }
        connector.write(text);
        focusTerminal();
    }

    /** Jalankan shell (tab baru, atau jalankan ulang setelah berakhir). */
    public void start() {
        if (disposed || pending != null) {
            return;
        }
        hideBanner();
        showCenter(new LoadingPanel(I18n.t("local.starting", profile.name()),
                AnimationKind.promptFor(System.getProperty("user.name", ""), "localhost")));
        stateListener.run();
        pending = UiAsync.run(io, () -> {
            try {
                return factory.open(shell);
            } catch (LocalShellException e) {
                throw new CompletionException(e);
            }
        }, this::onStarted, this::onStartFailed);
    }

    private void onStarted(LocalTtyConnector tty) {
        pending = null;
        if (disposed) {
            tty.closeByUser();
            return;
        }
        if (floats != null) {
            floats.dispose();
            floats = null;
        }
        if (widget != null) {
            widget.close();
        }
        connector = tty;
        settings.setPaletteOverride(palettes.apply(profile.terminalTheme())); // null = ikut pengaturan
        widget = new ZoomableTermWidget(settings);
        widget.getTerminalPanel().setDefaultCursorShape(settings.cursorShape());
        attachHighlighter(widget);
        floats = new TerminalFloats(widget, center, this::reconnect, null); // tanpa tombol password
        if (snippetButton != null) {
            snippetButton.setShown(snippetHost.buttonVisible());
        }
        widget.setTtyConnector(tty);
        tty.addCloseListener(c -> SwingUtilities.invokeLater(() -> onClosed(c)));
        showCenter(widget);
        widget.start();
        widget.requestFocusInWindow();
        stateListener.run();
    }

    private void onStartFailed(Throwable error) {
        pending = null;
        stateListener.run();
        if (disposed) {
            return;
        }
        String message = error instanceof LocalShellException ? error.getMessage()
                : I18n.t("tab.error.unexpected", String.valueOf(error));
        var text = new JTextArea(message);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        var panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(text), BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        var retry = new JButton(I18n.t("tab.retry"), AppIcon.RECONNECT.icon());
        retry.addActionListener(e -> start());
        buttons.add(retry);
        panel.add(buttons, BorderLayout.SOUTH);
        showCenter(panel);
    }

    private void onClosed(LocalTtyConnector tty) {
        stateListener.run();
        if (disposed || tty != connector || tty.isClosedByUser()) {
            return;
        }
        setSessionEnded(true);
        String code = tty.exitCode().map(String::valueOf).orElse("?");
        showBanner(I18n.t("local.ended", profile.name(), code));
    }

    private void setSessionEnded(boolean ended) {
        sessionEnded = ended;
        if (floats != null) {
            floats.setSessionEnded(ended);
        }
    }

    @Override
    public String displayName() {
        return I18n.t("local.tab.name", profile.name());
    }

    @Override
    public String displayAddress() {
        return profile.commandLine() + " · " + shell.directory();
    }

    @Override
    public Color environmentColor() {
        return null;
    }

    @Override
    public boolean hasTerminal() {
        return true;
    }

    @Override
    public boolean showsSftp() {
        return false;
    }

    @Override
    public boolean isConnected() {
        return connector != null && connector.isConnected();
    }

    /** Aktif = shell sedang dijalankan, atau masih menjalankan perintah (shell diam di prompt boleh ditutup). */
    @Override
    public boolean isSessionActive() {
        return pending != null || !runningCommands().isEmpty();
    }

    /** Perintah yang masih berjalan di shell ini (nama proses tanpa argumen), untuk konfirmasi tutup. */
    public List<String> runningCommands() {
        return connector == null ? List.of() : connector.runningCommands();
    }

    /** Jalankan ulang shell (tombol, menu Sambung ulang, atau Enter setelah shell berakhir). */
    @Override
    public void reconnect() {
        disposeTerminal();
        start();
    }

    @Override
    public void focusTerminal() {
        if (widget != null) {
            widget.requestFocusInWindow();
        }
    }

    @Override
    public void zoom(int direction) {
        float size = direction == 0 ? settings.defaultSize() : settings.getTerminalFontSize() + direction;
        settings.setFontSize(size);
        if (widget != null) {
            widget.refreshFont();
        }
    }

    @Override
    public void reloadFont() {
        settings.reloadFont();
        if (widget != null) {
            widget.refreshFont();
        }
    }

    @Override
    public void reloadCursor() {
        if (widget != null) {
            widget.getTerminalPanel().setDefaultCursorShape(settings.cursorShape());
            widget.getTerminalPanel().repaint();
        }
    }

    @Override
    public void reloadColors() {
        settings.setPaletteOverride(palettes.apply(profile.terminalTheme()));
        if (widget != null) {
            widget.refreshColors();
        }
    }

    @Override
    public void refreshSnippetButton() {
        if (snippetButton == null) {
            return;
        }
        snippetButton.setCorner(snippetHost.corner());
        snippetButton.setShown(snippetHost.buttonVisible() && widget != null);
    }

    /** Zoom (Ctrl/⌘ +/-/0) dan Enter setelah shell berakhir (= jalankan ulang). */
    @Override
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
        Integer zoom = TerminalTab.zoomDirection(e);
        if (zoom != null) {
            zoom(zoom);
            return true;
        }
        if (sessionEnded && e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiersEx() == 0) {
            reconnect();
            return true;
        }
        return false;
    }

    private void showBanner(String text) {
        banner.removeAll();
        banner.setBackground(new Color(0x5A2A2A));
        banner.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        var label = new JLabel(text);
        label.setForeground(Color.WHITE);
        banner.add(label, BorderLayout.CENTER);
        var restart = new JButton(I18n.t("local.restart"), AppIcon.RECONNECT.icon());
        restart.addActionListener(e -> reconnect());
        banner.add(restart, BorderLayout.EAST);
        banner.setVisible(true);
        content.revalidate();
        content.repaint();
    }

    private void hideBanner() {
        setSessionEnded(false);
        banner.setVisible(false);
        banner.removeAll();
    }

    /** Menutup tab: shell dan pohon prosesnya dimatikan (di thread lain). Idempotent. */
    @Override
    public void dispose() {
        disposed = true;
        disposeTerminal();
    }

    private void disposeTerminal() {
        if (connector != null) {
            connector.closeByUser();
            connector = null;
        }
        if (floats != null) {
            floats.dispose();
            floats = null;
        }
        if (widget != null) {
            detachHighlighter();
            widget.close();
            widget = null;
        }
        if (snippetButton != null) {
            snippetButton.setShown(false);
        }
    }

    private void showCenter(java.awt.Component c) {
        center.setBase(c);
    }
}

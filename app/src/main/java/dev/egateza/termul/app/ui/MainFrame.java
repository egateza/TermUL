package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.AppContext;
import dev.egateza.termul.app.BuildInfo;
import dev.egateza.termul.app.edit.EditTrackerDialog;
import dev.egateza.termul.app.edit.EditorSettingsDialog;
import dev.egateza.termul.app.edit.ValidationHooksDialog;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.log.LogBuffer;
import dev.egateza.termul.app.log.LogPanel;
import dev.egateza.termul.app.monitor.HostStatusBar;
import dev.egateza.termul.app.monitor.ResourceMonitor;
import dev.egateza.termul.app.sftp.ActivityBar;
import dev.egateza.termul.app.sftp.SftpPanel;
import dev.egateza.termul.app.sftp.SystemFileIcons;
import dev.egateza.termul.app.terminal.BackgroundImages;
import dev.egateza.termul.app.terminal.SplitPanes;
import dev.egateza.termul.app.terminal.TerminalTab;
import dev.egateza.termul.app.ui.tree.HostTreePanel;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import dev.egateza.termul.core.config.AppConfig;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.profile.OsInfo;
import dev.egateza.termul.core.profile.ProfileStore;
import dev.egateza.termul.core.theme.CustomTheme;
import dev.egateza.termul.ssh.OsDetector;
import dev.egateza.termul.terminal.SshTtyConnector;
import dev.egateza.termul.sftp.edit.RemoteEditSession;
import dev.egateza.termul.vault.SecretType;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.ExecutorService;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.ButtonGroup;
import java.awt.Window;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Window utama: host tree di kiri, tab terminal di kanan. */
public final class MainFrame extends JFrame implements HostTreePanel.Actions {

    private final AppContext ctx;
    private final ProfileStore store;
    private final ExecutorService io;
    private final SystemFileIcons fileIcons;
    private final HostTreePanel hostTree;
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    /** Tab yang dipilih dengan Ctrl+klik untuk digabung (Ctrl+G), urutan pilih. EDT. */
    private final java.util.Set<java.awt.Component> marked = new java.util.LinkedHashSet<>();
    private final JPanel center = new JPanel(new BorderLayout());
    private final JLabel welcome = new JLabel(
            "", SwingConstants.CENTER);
    private final Runnable onExit;
    private final KeyEventDispatcher hotkeys = this::dispatchHotkey;
    private final LogPanel logPanel;
    private final JSplitPane logSplit;
    private final JCheckBoxMenuItem showLog = new JCheckBoxMenuItem(I18n.t("main.menu.help.showLog"));
    private int logHeight = 220; // EDT
    private final ResourceMonitor resourceMonitor;
    private final HostStatusBar hostStatus;
    private final JCheckBoxMenuItem showHostStatus = new JCheckBoxMenuItem(I18n.t("main.menu.settings.hostStatus"));
    private int logDividerSize; // EDT
    // item menu Terminal yang bergantung pada tab/sesi aktif (lihat updateTerminalMenu)
    private JMenuItem miDuplicate;
    private JMenuItem miReconnect;
    private JMenuItem miCloseTab;
    private JMenuItem miSftp;
    private JMenuItem miZoomIn;
    private JMenuItem miZoomOut;
    private JMenuItem miZoomReset;
    private JMenuItem miInjectSudo;
    private JMenuItem miInjectRoot;
    private JMenuItem miGroup;
    private JMenuItem miUngroup;
    private JMenuItem miClosePane;
    private JMenuItem miNextPane;
    private static final int HOST_HANDLE_WIDTH = 18;
    private static final int HOST_TOGGLE_HEIGHT = 44;
    /** Isi area utama: split (mode panel) atau area terminal saja (mode tombol melayang). */
    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel hostSide = new JPanel(new BorderLayout());
    private final JButton hostToggle = new JButton();
    private final JPanel handle = new JPanel(new java.awt.GridBagLayout());
    private final JButton modeToggle = new JButton(); // pojok kanan atas menu bar: terang/gelap
    private UiTheme theme; // EDT
    private List<CustomTheme> customThemes; // EDT; disegarkan setelah editor tema ditutup
    private long backdropRequest; // EDT; nomor permintaan muat gambar latar terakhir, untuk membuang hasil yang usang
    private String backdropPath; // EDT; path gambar latar yang sedang terpasang (atau sedang dimuat)
    private java.awt.image.BufferedImage backdropImage; // EDT; gambar untuk backdropPath, null selama belum selesai dimuat
    private ThemeMode wantedMode; // EDT
    private final HostDrawer drawer;
    private JSplitPane hostSplit; // hanya di mode panel, selain itu null. EDT
    private String hostMode = AppConfig.HOST_DOCKED; // EDT
    private int hostWidth = 260; // EDT

    public MainFrame(AppContext ctx, List<CustomTheme> customThemes, Runnable onExit) {
        super("TermUL");
        setIconImages(appIcons());
        this.ctx = ctx;
        this.store = ctx.profiles();
        this.io = ctx.io();
        this.fileIcons = new SystemFileIcons(ctx.paths().cacheDir().resolve("file-icons"), AppIcon.SIZE);
        this.onExit = onExit;
        this.hostTree = new HostTreePanel(this);
        this.customThemes = List.copyOf(customThemes);
        this.theme = UiThemes.resolve(ctx.config().current().theme(), this.customThemes);
        this.wantedMode = ThemeMode.fromId(ctx.config().current().themeMode());

        int opacity = ctx.config().current().windowOpacity();
        if (opacity < 100) {
            // Java menolak transparansi pada window berdekorasi native: pakai title bar FlatLaf
            setUndecorated(true);
            getRootPane().setWindowDecorationStyle(javax.swing.JRootPane.FRAME);
            WindowOpacity.apply(this, opacity);
        }

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });

        tabs.putClientProperty("JTabbedPane.tabClosable", true);
        tabs.putClientProperty("JTabbedPane.tabCloseToolTipText", I18n.t("main.tab.close"));
        tabs.putClientProperty("JTabbedPane.tabCloseCallback",
                (BiConsumer<JTabbedPane, Integer>) (t, index) -> closeTab(index));
        tabs.addChangeListener(e -> {
            updateCenter();
            updateTerminalMenu();
            if (tabs.getSelectedComponent() instanceof TerminalTab tab) {
                SwingUtilities.invokeLater(tab::focusTerminal);
            }
        });
        // Ctrl+klik tab = pilih/batal pilih untuk digabung (Ctrl+G); klik biasa membatalkan semua pilihan
        tabs.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int index = tabs.indexAtLocation(e.getX(), e.getY());
                if (index < 0) {
                    return;
                }
                if (e.isControlDown()) {
                    var c = tabs.getComponentAt(index);
                    if (!marked.remove(c)) {
                        marked.add(c);
                    }
                } else if (!marked.isEmpty()) {
                    marked.clear();
                }
                refreshTitles();
                updateTerminalMenu();
            }
        });
        welcome.setFont(welcome.getFont().deriveFont(Font.PLAIN, welcome.getFont().getSize2D() + 2));
        updateCenter();

        // Strip tipis di tepi kanan panel host (satu warna dengan panel, jadi terasa satu kesatuan) berisi tombol
        // laci di tengah. Strip bagian dari sisi kiri, jadi tetap terlihat saat host tree disembunyikan.
        hostToggle.setFocusable(false);
        hostToggle.putClientProperty("JButton.buttonType", "toolBarButton");
        hostToggle.putClientProperty("FlatLaf.style", "arc: 6; margin: 0,0,0,0");
        hostToggle.setPreferredSize(new Dimension(HOST_HANDLE_WIDTH, HOST_TOGGLE_HEIGHT));
        hostToggle.addActionListener(e -> setDockedVisible(!hostTree.isVisible()));
        updateHostToggle();
        handle.add(hostToggle);
        handle.setBackground(javax.swing.UIManager.getColor("Tree.background")); // satu warna dengan daftar host
        handle.setMinimumSize(new Dimension(HOST_HANDLE_WIDTH, 0)); // lebar minimum sisi kiri saat host tree tertutup
        hostSide.add(handle, BorderLayout.EAST);
        hostTree.setMinimumSize(new Dimension(160, 100));
        // mode tombol melayang: daftar host tampil di atas area terminal (terminal tidak di-resize)
        drawer = new HostDrawer(getLayeredPane(), center, hostTree,
                () -> currentTab().ifPresent(TerminalTab::focusTerminal));
        drawer.setOpacity(ctx.config().current().hostButtonOpacity());
        // panel log di bawah; disembunyikan dengan setVisible supaya tab terminal tidak di-reparent
        logPanel = new LogPanel(LogBuffer.global(), ctx.paths().logDir(), io, () -> setLogVisible(false));
        logPanel.setVisible(false);
        logPanel.setMinimumSize(new Dimension(100, 60));
        logSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, body, logPanel);
        logSplit.setResizeWeight(1.0);
        logSplit.setContinuousLayout(true);
        logDividerSize = logSplit.getDividerSize();
        logSplit.setDividerSize(0); // panel log tersembunyi: tanpa divider (tidak ada garis/titik di tepi bawah)
        getContentPane().add(logSplit, BorderLayout.CENTER);
        // monitor resource JVM: bar "Host status" paling bawah
        resourceMonitor = new ResourceMonitor(io);
        hostStatus = new HostStatusBar(resourceMonitor, () -> setHostStatusVisible(false));
        getContentPane().add(hostStatus, BorderLayout.SOUTH);
        hostStatus.setVisible(ctx.config().current().hostStatusBar());
        resourceMonitor.setActive(hostStatus.isVisible());
        applyHostMode(ctx.config().current().hostPanelMode());
        setJMenuBar(buildMenu());

        setSize(1280, 800);
        setLocationRelativeTo(null);

        store.addListener(s -> SwingUtilities.invokeLater(() -> {
            hostTree.setSnapshot(s);
            reloadTerminalColors(); // warna khusus host / environment yang diubah di Edit host
        }));
        // belum ada tab: langsung tampilkan daftar host (mode tombol melayang), menutup otomatis saat host dibuka
        SwingUtilities.invokeLater(() -> drawer.setOpen(true));
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(hotkeys);
    }

    /**
     * Shortcut aplikasi (Ctrl+Shift+…, Ctrl+F5) ditangkap sebelum JediTerm, supaya tetap jalan saat
     * fokus di terminal dan tidak ikut terkirim ke remote. Shortcut shell biasa (Ctrl+F, Ctrl+N, ...)
     * tidak disentuh.
     */
    private boolean dispatchHotkey(KeyEvent e) {
        var tab = currentGroup().flatMap(g -> g.paneOf(e.getComponent())); // panel split tempat key diketik
        if (tab.isPresent() && tab.get().interceptKey(e)) {
            e.consume();
            return true;
        }
        if (e.getID() != KeyEvent.KEY_PRESSED || !e.isControlDown() || e.isAltDown()) {
            return false;
        }
        // Ctrl+G hanya diambil kalau ada tab yang dipilih untuk digabung; selain itu tetap ^G ke shell
        if (e.getKeyCode() == KeyEvent.VK_G && !e.isShiftDown() && marked.size() >= 2
                && e.getComponent() != null && SwingUtilities.getWindowAncestor(e.getComponent()) == this) {
            groupMarkedTabs();
            e.consume();
            return true;
        }
        boolean appCombo = e.isShiftDown() || e.getKeyCode() == KeyEvent.VK_F5;
        if (!appCombo || e.getComponent() == null || SwingUtilities.getWindowAncestor(e.getComponent()) != this) {
            return false;
        }
        updateTerminalMenu(); // status bisa berubah sejak menu terakhir dibuka
        JMenuItem item = findAccelerator(getJMenuBar(), KeyStroke.getKeyStrokeForEvent(e));
        if (item == null || !item.isEnabled()) {
            return false;
        }
        item.doClick(0);
        e.consume();
        return true;
    }

    /**
     * Aktif/nonaktifkan item menu Terminal sesuai tab yang dipilih: aksi tab butuh ada tab; SFTP dan inject password
     * butuh sesi tersambung. Dipanggil saat status berubah, saat menu dibuka, dan sebelum shortcut dicari.
     */
    private void updateTerminalMenu() {
        if (miDuplicate == null) { // dipanggil sebelum menu selesai dibuat
            return;
        }
        var tab = currentTab();
        var state = TerminalMenuState.of(tab.isPresent(), tab.map(TerminalTab::isConnected).orElse(false),
                tab.map(TerminalTab::isSftpOpen).orElse(false), tab.map(t -> !t.isSftpOnly()).orElse(true));
        for (var item : new JMenuItem[] {miDuplicate, miReconnect, miCloseTab, miZoomIn, miZoomOut, miZoomReset}) {
            item.setEnabled(state.tabActions());
        }
        miSftp.setEnabled(state.sftp());
        miInjectSudo.setEnabled(state.inject());
        miInjectRoot.setEnabled(state.inject());
        boolean grouped = currentGroup().map(g -> g.panes().size() > 1).orElse(false);
        marked.removeIf(c -> tabs.indexOfComponent(c) < 0); // tab yang sudah ditutup
        miGroup.setEnabled(marked.size() >= 2);
        miUngroup.setEnabled(grouped);
        miClosePane.setEnabled(grouped);
        miNextPane.setEnabled(grouped);
    }

    private static JMenuItem findAccelerator(JMenuBar bar, KeyStroke ks) {
        for (int i = 0; i < bar.getMenuCount(); i++) {
            var menu = bar.getMenu(i);
            if (menu == null) { // komponen bar yang bukan JMenu (glue, tombol mode)
                continue;
            }
            for (int j = 0; j < menu.getItemCount(); j++) {
                var item = menu.getItem(j);
                if (item != null && ks.equals(item.getAccelerator())) {
                    return item;
                }
            }
        }
        return null;
    }

    @Override
    public void dispose() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(hotkeys);
        drawer.dispose();
        resourceMonitor.close();
        super.dispose();
    }

    public void showSnapshot(ProfileSnapshot snapshot) {
        hostTree.setSnapshot(snapshot);
    }

    public JTabbedPane tabs() {
        return tabs;
    }

    private JMenuBar buildMenu() {
        var bar = new JMenuBar();
        var file = new JMenu(I18n.t("main.menu.file"));
        file.add(menuItem(AppIcon.SERVER, I18n.t("main.menu.file.newHost"), KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK),
                () -> newHost(hostTree.selectedGroup())));
        file.add(menuItem(AppIcon.FOLDER_PLUS, I18n.t("main.menu.file.newGroup"), null, () -> newGroup(hostTree.selectedGroup())));
        file.add(menuItem(null, I18n.t("main.menu.file.importSshConfig"), null, this::importSshConfig));
        file.add(menuItem(null, I18n.t("main.menu.file.findHost"), KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
                () -> {
                    showHostList();
                    SwingUtilities.invokeLater(hostTree::focusSearch);
                }));
        file.addSeparator();
        file.add(menuItem(AppIcon.EXIT, I18n.t("main.menu.file.exit"), KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK), this::exit));
        bar.add(file);

        var terminal = new JMenu(I18n.t("main.menu.terminal"));
        int ctrlShift = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
        terminal.add(miDuplicate = menuItem(null, I18n.t("main.menu.terminal.duplicate"), KeyStroke.getKeyStroke(KeyEvent.VK_T, ctrlShift),
                () -> currentTab().ifPresent(t -> openTab(t.profile(), t.isSftpOnly()))));
        terminal.add(miReconnect = menuItem(null, I18n.t("main.menu.terminal.reconnect"), KeyStroke.getKeyStroke(KeyEvent.VK_F5, InputEvent.CTRL_DOWN_MASK),
                () -> currentTab().ifPresent(TerminalTab::reconnect)));
        terminal.add(miCloseTab = menuItem(null, I18n.t("main.tab.close"), KeyStroke.getKeyStroke(KeyEvent.VK_W, ctrlShift),
                () -> closeTab(tabs.getSelectedIndex())));
        terminal.add(miSftp = menuItem(null, I18n.t("main.menu.terminal.sftp"), KeyStroke.getKeyStroke(KeyEvent.VK_F, ctrlShift),
                () -> currentTab().ifPresent(TerminalTab::toggleSftp)));
        terminal.addSeparator();
        terminal.add(miGroup = menuItem(null, I18n.t("main.menu.terminal.group"), KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK),
                this::groupMarkedTabs));
        miGroup.setToolTipText(I18n.t("main.splitGroup.hint", String.valueOf(SplitPanes.MAX_PANES)));
        terminal.add(miUngroup = menuItem(null, I18n.t("main.menu.terminal.ungroup"), KeyStroke.getKeyStroke(KeyEvent.VK_G, ctrlShift),
                this::ungroupCurrent));
        terminal.add(buildSplitModeMenu());
        terminal.add(miNextPane = menuItem(null, I18n.t("main.menu.terminal.nextPane"), KeyStroke.getKeyStroke(KeyEvent.VK_N, ctrlShift),
                () -> currentGroup().map(SplitPanes::next).ifPresent(TerminalTab::focusTerminal)));
        terminal.add(miClosePane = menuItem(null, I18n.t("main.menu.terminal.closePane"), KeyStroke.getKeyStroke(KeyEvent.VK_X, ctrlShift),
                this::closePane));
        terminal.addSeparator();
        terminal.add(miZoomIn = menuItem(null, I18n.t("main.menu.terminal.zoomIn"), KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK),
                () -> currentTab().ifPresent(t -> t.zoom(1))));
        terminal.add(miZoomOut = menuItem(null, I18n.t("main.menu.terminal.zoomOut"), KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK),
                () -> currentTab().ifPresent(t -> t.zoom(-1))));
        terminal.add(miZoomReset = menuItem(null, I18n.t("main.menu.terminal.zoomReset"), KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK),
                () -> currentTab().ifPresent(t -> t.zoom(0))));
        terminal.addSeparator();
        terminal.add(menuItem(null, I18n.t("main.menu.terminal.editTracker"), KeyStroke.getKeyStroke(KeyEvent.VK_E, ctrlShift),
                this::showEditTracker));
        terminal.addSeparator();
        terminal.add(miInjectSudo = menuItem(null, I18n.t("main.menu.terminal.injectSudo"), KeyStroke.getKeyStroke(KeyEvent.VK_P, ctrlShift),
                () -> currentTab().ifPresent(t -> t.injectSecret(SecretType.SUDO_PASSWORD, ctx.vault()))));
        terminal.add(miInjectRoot = menuItem(null, I18n.t("main.menu.terminal.injectRoot"), KeyStroke.getKeyStroke(KeyEvent.VK_R, ctrlShift),
                () -> currentTab().ifPresent(t -> t.injectSecret(SecretType.ROOT_PASSWORD, ctx.vault()))));
        terminal.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                updateTerminalMenu();
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        updateTerminalMenu();
        bar.add(terminal);
        bar.add(buildVaultMenu());

        var settings = new JMenu(I18n.t("menu.settings"));
        settings.add(menuItem(null, I18n.t("main.menu.settings.editors"), null, this::configureEditors));
        settings.add(menuItem(null, I18n.t("main.menu.settings.validationHooks"), null, this::configureValidationHooks));
        settings.add(buildHostPanelMenu());
        settings.add(buildThemeMenu());
        settings.add(menuItem(null, I18n.t("menu.settings.fonts"), null, this::configureFonts));
        settings.add(menuItem(null, I18n.t("menu.settings.windowOpacity"), null, this::configureWindowOpacity));
        settings.add(buildLanguageMenu());
        settings.add(buildBellMenu());
        settings.add(buildIconSetMenu());
        settings.addSeparator();
        showHostStatus.setSelected(hostStatus.isVisible());
        showHostStatus.setToolTipText(I18n.t("main.menu.settings.hostStatus.tooltip"));
        showHostStatus.addActionListener(e -> setHostStatusVisible(showHostStatus.isSelected()));
        settings.add(showHostStatus);
        bar.add(settings);
        bar.add(javax.swing.Box.createHorizontalGlue());
        modeToggle.setFocusable(false);
        modeToggle.putClientProperty("JButton.buttonType", "toolBarButton");
        modeToggle.addActionListener(e -> {
            applyTheme(theme, theme.effectiveMode(wantedMode).other());
        });
        bar.add(modeToggle);
        updateModeToggle();

        var help = new JMenu(I18n.t("main.menu.help"));
        showLog.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, ctrlShift));
        showLog.setToolTipText(I18n.t("main.menu.help.showLog.tooltip"));
        showLog.addActionListener(e -> setLogVisible(showLog.isSelected()));
        help.add(showLog);
        help.add(menuItem(null, I18n.t("main.menu.help.openLogDir"), null,
                () -> UiAsync.run(io, () -> LogPanel.openFolder(ctx.paths().logDir()),
                        err -> Dialogs.error(this, I18n.t("main.error.openLogDir"), err))));
        help.addSeparator();
        help.add(menuItem(AppIcon.INFO, I18n.t("main.menu.help.about"), null, () -> Dialogs.info(this,
                I18n.t("main.about.title"),
                I18n.t("main.about.text", BuildInfo.load().display(), Runtime.version().toString(), ctx.paths().logDir().toString(), Arrays.stream(IconSet.values())
                        .map(s -> "  • " + s.attribution())
                        .collect(Collectors.joining("\n"))))));
        bar.add(help);
        return bar;
    }

    /** Tampilkan/sembunyikan bar "Host status" di bawah dan simpan pilihannya. EDT. */
    private void setHostStatusVisible(boolean visible) {
        showHostStatus.setSelected(visible);
        if (visible == hostStatus.isVisible()) {
            return;
        }
        hostStatus.setVisible(visible);
        getContentPane().revalidate();
        resourceMonitor.setActive(visible); // sampler hanya jalan selama bar terlihat
        mutate(I18n.t("error.saveSettings"), () ->
                ctx.config().save(ctx.config().current().withHostStatusBar(visible)));
    }

    private void configureEditors() {
        EditorSettingsDialog.show(this, ctx.config().current().editors()).ifPresent(editors ->
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withEditors(editors))));
    }

    /** Ikon aplikasi (dibuat dengan {@code tools/MakeAppIcon.java}); ukuran yang tidak ada dilewati. */
    private static List<java.awt.Image> appIcons() {
        var images = new java.util.ArrayList<java.awt.Image>();
        for (int size : new int[] {16, 32, 48, 64, 128, 256}) {
            var url = MainFrame.class.getResource("/dev/egateza/termul/app/icons/app-" + size + ".png");
            if (url != null) {
                images.add(new javax.swing.ImageIcon(url).getImage());
            }
        }
        return images;
    }

    /** Impor host dari {@code ~/.ssh/config}: baca file di io, pilih di dialog (EDT), simpan di io. */
    private void importSshConfig() {
        java.nio.file.Path home = java.nio.file.Path.of(System.getProperty("user.home"));
        java.nio.file.Path sshDir = home.resolve(".ssh");
        java.nio.file.Path file = sshDir.resolve("config");
        UiAsync.run(io, () -> {
            if (!java.nio.file.Files.isRegularFile(file)) {
                return List.<dev.egateza.termul.core.sshconfig.SshConfigImport.Candidate>of();
            }
            List<dev.egateza.termul.core.sshconfig.SshConfigHost> hosts;
            try {
                hosts = dev.egateza.termul.core.sshconfig.SshConfigParser.parse(file, sshDir, home);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(I18n.t("sshconfig.readFailed", file.toString()), e);
            }
            return dev.egateza.termul.core.sshconfig.SshConfigImport.plan(hosts, store.snapshot(),
                    System.getProperty("user.name"), I18n.t("sshconfig.group"));
        }, plan -> {
            var toSave = SshConfigImportDialog.show(this, file.toString(), plan);
            if (!toSave.isEmpty()) {
                mutate(I18n.t("main.error.saveProfile"), () -> toSave.forEach(store::save));
            }
        }, err -> Dialogs.error(this, I18n.t("sshconfig.title"), err));
    }

    private void configureValidationHooks() {
        ValidationHooksDialog.show(this, ctx.config().current().validationHooks()).ifPresent(hooks ->
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withValidationHooks(hooks))));
    }

    private boolean floatingMode() {
        return AppConfig.HOST_FLOATING.equals(hostMode);
    }

    /** Pasang panel host sesuai mode: panel di samping (split) atau tombol melayang. EDT. */
    private void applyHostMode(String mode) {
        hostMode = AppConfig.HOST_FLOATING.equals(mode) ? AppConfig.HOST_FLOATING : AppConfig.HOST_DOCKED;
        body.removeAll();
        if (floatingMode()) {
            hostSide.remove(hostTree);
            hostSplit = null;
            hostTree.setVisible(true);
            body.add(center, BorderLayout.CENTER);
            drawer.activate();
        } else {
            drawer.deactivate();
            hostSide.add(hostTree, BorderLayout.CENTER);
            hostTree.setVisible(true);
            hostSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, hostSide, center);
            hostSplit.setContinuousLayout(true);
            hostSplit.putClientProperty("FlatLaf.style", "gripDotCount: 0"); // divider tetap bisa digeser, tanpa titik
            hostSplit.setDividerLocation(hostWidth);
            body.add(hostSplit, BorderLayout.CENTER);
            updateHostToggle();
        }
        welcome.setText("<html><center><b>TermUL</b><br><br>" + I18n.t("main.welcome.open") + "<br>"
                + I18n.t("main.welcome.shortcuts")
                + (floatingMode() ? "<br><br>" + I18n.t("main.welcome.floatingHint") : "")
                + "</center></html>");
        body.revalidate();
        body.repaint();
        SwingUtilities.invokeLater(drawer::layout);
    }

    /** Pastikan daftar host terlihat (untuk Ctrl+F): membuka laci atau panel di samping. */
    private void showHostList() {
        if (floatingMode()) {
            drawer.setOpen(true);
        } else {
            setDockedVisible(true);
        }
    }

    /** Mode panel: buka/tutup panel host di kiri (tombol pada strip), dengan lebar terakhir yang dipakai. EDT. */
    private void setDockedVisible(boolean visible) {
        if (hostSplit == null || visible == hostTree.isVisible()) {
            return;
        }
        if (!visible) {
            hostWidth = Math.max(160 + HOST_HANDLE_WIDTH, hostSplit.getDividerLocation());
            var owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (owner != null && SwingUtilities.isDescendingFrom(owner, hostTree)) {
                currentTab().ifPresent(TerminalTab::focusTerminal);
            }
        }
        hostTree.setVisible(visible);
        hostSplit.setEnabled(visible); // divider tidak bisa digeser saat hanya strip yang tersisa
        hostSplit.revalidate();
        hostSplit.setDividerLocation(visible ? hostWidth : hostSide.getMinimumSize().width);
        updateHostToggle();
    }

    private void updateHostToggle() {
        boolean open = hostTree.isVisible();
        hostToggle.setIcon((open ? AppIcon.ANGLES_LEFT : AppIcon.ANGLES_RIGHT).icon(16));
        hostToggle.setToolTipText(I18n.t(open ? "host.toggle.hide" : "host.toggle.show"));
    }

    /** Submenu: mode panel host (di samping / tombol melayang) dan transparansi tombol melayang. */
    private JMenu buildHostPanelMenu() {
        var menu = new JMenu(I18n.t("menu.settings.hostPanel"));
        var modes = new ButtonGroup();
        var config = ctx.config().current();
        record Mode(String id, String label) {
        }
        for (var mode : new Mode[] {
                new Mode(AppConfig.HOST_DOCKED, I18n.t("main.menu.hostPanel.docked")),
                new Mode(AppConfig.HOST_FLOATING, I18n.t("main.menu.hostPanel.floating"))}) {
            var item = new JRadioButtonMenuItem(mode.label(), mode.id().equals(config.hostPanelMode()));
            item.addActionListener(e -> {
                if (mode.id().equals(hostMode)) {
                    return;
                }
                applyHostMode(mode.id());
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withHostPanelMode(mode.id())));
            });
            modes.add(item);
            menu.add(item);
        }
        menu.addSeparator();
        var opacity = new JMenu(I18n.t("main.menu.hostPanel.opacity"));
        opacity.setToolTipText(I18n.t("main.menu.hostPanel.opacity.tooltip"));
        var levels = new ButtonGroup();
        for (int percent : new int[] {100, 70, 40, 20}) {
            var item = new JRadioButtonMenuItem(percent == 100 ? I18n.t("main.menu.hostPanel.opacity.solid") : percent + "%",
                    percent == config.hostButtonOpacity());
            item.addActionListener(e -> {
                drawer.setOpacity(percent);
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withHostButtonOpacity(percent)));
            });
            levels.add(item);
            opacity.add(item);
        }
        menu.add(opacity);
        return menu;
    }

    /** Submenu reaksi terhadap BEL dari server: suara sistem dan getaran layar, masing-masing bisa dimatikan. */
    private JMenu buildBellMenu() {
        var menu = new JMenu(I18n.t("menu.settings.bell"));
        menu.setToolTipText(I18n.t("menu.settings.bell.tooltip"));
        var bell = ctx.terminalSettings().bell();
        var sound = new javax.swing.JCheckBoxMenuItem(I18n.t("menu.settings.bell.sound"), bell.sound());
        sound.addActionListener(e -> {
            bell.setSound(sound.isSelected());
            mutate(I18n.t("error.saveSettings"), () ->
                    ctx.config().save(ctx.config().current().withBellSound(sound.isSelected())));
            if (sound.isSelected()) {
                java.awt.Toolkit.getDefaultToolkit().beep(); // contoh suara
            }
        });
        var shake = new javax.swing.JCheckBoxMenuItem(I18n.t("menu.settings.bell.shake"), bell.shake());
        shake.addActionListener(e -> {
            bell.setShake(shake.isSelected());
            mutate(I18n.t("error.saveSettings"), () ->
                    ctx.config().save(ctx.config().current().withBellShake(shake.isSelected())));
            if (shake.isSelected()) {
                SwingUtilities.invokeLater(() -> ShakeEffect.shake(getRootPane())); // contoh getaran
            }
        });
        menu.add(sound);
        menu.add(shake);
        return menu;
    }

    /** Submenu pilihan bahasa UI; disimpan di config.json dan berlaku penuh setelah restart. */
    private JMenu buildLanguageMenu() {
        var menu = new JMenu(I18n.t("menu.settings.language"));
        menu.setToolTipText(I18n.t("menu.settings.language.tooltip"));
        var group = new ButtonGroup();
        for (var language : I18n.SUPPORTED) {
            var item = new JRadioButtonMenuItem(language.label(),
                    language.tag().equals(ctx.config().current().language()));
            item.addActionListener(e -> {
                if (language.tag().equals(ctx.config().current().language())) {
                    return;
                }
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withLanguage(language.tag())));
                // pesan memakai bahasa baru, supaya jelas apa yang akan terjadi setelah restart
                JOptionPane.showMessageDialog(this,
                        I18n.tIn(language.tag(), "menu.settings.language.restart", language.label()),
                        I18n.tIn(language.tag(), "menu.settings.language.restartTitle"),
                        JOptionPane.INFORMATION_MESSAGE);
            });
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    /**
     * Submenu tema: pilihan tema UI (bawaan dan custom), opsi menerapkan tema ke terminal, pilihan warna terminal
     * terpisah, dan editor tema. Isinya dibangun ulang tiap menu dibuka karena daftar tema custom bisa berubah.
     */
    private JMenu buildThemeMenu() {
        var menu = new JMenu(I18n.t("menu.settings.theme"));
        fillThemeMenu(menu);
        menu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                fillThemeMenu(menu);
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        return menu;
    }

    private void fillThemeMenu(JMenu menu) {
        menu.removeAll();
        var config = ctx.config().current();
        var group = new ButtonGroup();
        for (UiTheme t : UiThemes.all(customThemes)) {
            var item = new JRadioButtonMenuItem(t.label(), t.id().equals(theme.id()));
            item.setToolTipText(I18n.t("main.menu.theme.modes", t.modes().stream().map(ThemeMode::label).collect(Collectors.joining(", "))));
            item.addActionListener(e -> {
                if (!t.id().equals(theme.id())) {
                    applyTheme(t, wantedMode);
                }
            });
            group.add(item);
            menu.add(item);
        }
        menu.addSeparator();

        var linked = new JCheckBoxMenuItem(I18n.t("menu.settings.theme.linked"), config.terminalThemeLinked());
        linked.addActionListener(e -> {
            var next = ctx.config().current().withTerminalThemeLinked(linked.isSelected());
            applyTerminalColors(next);
            mutate(I18n.t("error.saveSettings"), () -> ctx.config().save(next));
        });
        menu.add(linked);

        var terminalMenu = new JMenu(I18n.t("menu.settings.theme.terminalColors"));
        terminalMenu.setEnabled(!config.terminalThemeLinked());
        var terminalGroup = new ButtonGroup();
        String chosen = UiThemes.find(config.terminalTheme(), customThemes).map(CustomTheme::id).orElse(null);
        terminalMenu.add(terminalColorItem(terminalGroup, I18n.t("menu.settings.theme.terminalDefault"), null,
                chosen == null));
        for (CustomTheme t : customThemes) {
            terminalMenu.add(terminalColorItem(terminalGroup, t.name(), t.id(), t.id().equals(chosen)));
        }
        menu.add(terminalMenu);
        menu.addSeparator();
        menu.add(menuItem(null, I18n.t("menu.settings.theme.editor"), null, this::openThemeEditor));
    }

    private JRadioButtonMenuItem terminalColorItem(ButtonGroup group, String label, String themeId, boolean selected) {
        var item = new JRadioButtonMenuItem(label, selected);
        item.addActionListener(e -> {
            var next = ctx.config().current().withTerminalTheme(themeId);
            applyTerminalColors(next);
            mutate(I18n.t("error.saveSettings"), () -> ctx.config().save(next));
        });
        group.add(item);
        return item;
    }

    /**
     * Pakai warna terminal dan gambar latar menurut {@code config} ke semua tab yang terbuka dan tab yang dibuka
     * berikutnya. Warna langsung berlaku; gambar dimuat di thread lain (file bisa besar) lalu dipasang. EDT.
     */
    private void applyTerminalColors(AppConfig config) {
        var settings = ctx.terminalSettings();
        var palette = UiThemes.terminalPalette(config, customThemes, theme.effectiveMode(wantedMode));
        settings.setPalette(palette);
        String path = BackgroundImages.key(palette);
        long request = ++backdropRequest;
        if (path == null) {
            backdropPath = null;
            backdropImage = null;
            settings.setBackgroundImage(null, 0);
        } else if (path.equals(backdropPath) && backdropImage != null) {
            // gambar yang sama sudah di memori: hanya keterlihatannya yang mungkin berubah
            settings.setBackgroundImage(backdropImage, palette.imageVisibility());
        } else {
            backdropPath = path;
            backdropImage = null;
            settings.setBackgroundImage(null, 0); // jangan tampilkan gambar lama selama yang baru dimuat
            int visibility = palette.imageVisibility();
            UiAsync.run(ctx.sshOps(), () -> BackgroundImages.loadFor(palette), image -> {
                if (request == backdropRequest && image != null) {
                    backdropImage = image;
                    settings.setBackgroundImage(image, visibility);
                    reloadTerminalColors();
                }
            }, err -> { });
        }
        reloadTerminalColors();
    }

    private void reloadTerminalColors() {
        allPanes().forEach(TerminalTab::reloadColors);
    }

    /**
     * Dialog transparansi seluruh jendela. Perubahan antar nilai di bawah 100% langsung terlihat; dari 100% ke bawahnya
     * butuh restart karena window harus dibuat tanpa dekorasi native.
     */
    private void configureWindowOpacity() {
        int original = ctx.config().current().windowOpacity();
        var slider = new javax.swing.JSlider(AppConfig.MIN_WINDOW_OPACITY, 100, original);
        var label = new JLabel(I18n.t("window.opacity.label", original));
        slider.addChangeListener(e -> {
            label.setText(I18n.t("window.opacity.label", slider.getValue()));
            WindowOpacity.apply(this, slider.getValue()); // pratinjau langsung; diam-diam gagal di window berdekorasi
        });
        var hint = new JLabel("<html>" + I18n.t("window.opacity.hint") + "</html>");
        hint.setPreferredSize(new Dimension(380, 70));
        var panel = new JPanel(new BorderLayout(0, 6));
        panel.add(label, BorderLayout.NORTH);
        panel.add(slider, BorderLayout.CENTER);
        panel.add(hint, BorderLayout.SOUTH);
        int answer = JOptionPane.showConfirmDialog(this, panel, I18n.t("window.opacity.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        int chosen = answer == JOptionPane.OK_OPTION ? slider.getValue() : original;
        WindowOpacity.apply(this, chosen); // batal: kembali ke nilai semula
        if (chosen == original) {
            return;
        }
        mutate(I18n.t("error.saveSettings"), () ->
                ctx.config().save(ctx.config().current().withWindowOpacity(chosen)));
        if (!isUndecorated() && chosen < 100) {
            JOptionPane.showMessageDialog(this, I18n.t("window.opacity.restart"),
                    I18n.t("window.opacity.title"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Buka editor tema; setelah ditutup, daftar tema dibaca ulang dan tema yang terpengaruh diterapkan ulang. */
    private void openThemeEditor() {
        var result = ThemeEditorDialog.show(this, ctx.themes(), ctx.terminalSettings().getTerminalFont(),
                UiThemes.builtinIds(), theme.id());
        customThemes = List.copyOf(ctx.themes().list()); // file kecil; dibaca sekali setelah dialog ditutup
        if (result.applyId() != null) {
            applyTheme(UiThemes.resolve(result.applyId(), customThemes), wantedMode);
            return;
        }
        // tema yang sedang dipakai mungkin baru diubah atau dihapus (kembali ke bawaan)
        var current = UiThemes.resolve(theme.id(), customThemes);
        if (current instanceof CustomUiTheme || !current.id().equals(theme.id())) {
            applyTheme(current, wantedMode);
        } else {
            applyTerminalColors(ctx.config().current());
        }
    }

    /**
     * Pasang tema+mode ke seluruh window yang terbuka lalu simpan. Mode yang diminta disimpan apa adanya (bukan
     * mode efektif), supaya kembali ke mode favorit kalau user pindah ke tema yang mendukung keduanya. EDT.
     */
    private void applyTheme(UiTheme newTheme, ThemeMode wanted) {
        theme = newTheme;
        wantedMode = wanted;
        theme.install(wanted);
        // warna yang di-set manual tidak ikut berubah lewat updateUI
        handle.setBackground(javax.swing.UIManager.getColor("Tree.background"));
        refreshLaf();
        updateModeToggle();
        var next = ctx.config().current().withTheme(newTheme.id()).withThemeMode(wanted.id());
        applyTerminalColors(next);
        mutate(I18n.t("error.saveSettings"), () -> ctx.config().save(next));
    }

    /** Terapkan ulang LaF (tema, font aplikasi) ke semua komponen, termasuk yang sedang terlepas dari window. EDT. */
    private void refreshLaf() {
        com.formdev.flatlaf.FlatLaf.updateUI();
        // FlatLaf.updateUI hanya menjangkau komponen yang sedang ada di window; yang terlepas dari hierarki
        // (tabs saat belum ada tab, host tree di mode melayang, dst.) harus diperbarui sendiri
        for (var detached : new java.awt.Component[] {tabs, welcome, hostTree, hostSide, logPanel, editTracker}) {
            if (detached != null) {
                SwingUtilities.updateComponentTreeUI(detached);
            }
        }
        // ukuran menu bar dan tab bergantung pada font; hitung ulang tata letaknya
        getRootPane().revalidate();
        getRootPane().repaint();
    }

    /** Dialog font aplikasi dan font terminal; langsung berlaku ke seluruh tampilan dan tab yang terbuka, lalu disimpan. */
    private void configureFonts() {
        var config = ctx.config().current();
        FontSettingsDialog.show(this, config.uiFontFamily(), config.terminalFontFamily()).ifPresent(choice -> {
            if (!java.util.Objects.equals(choice.uiFamily(), config.uiFontFamily())) {
                UiFont.apply(choice.uiFamily());
                refreshLaf();
            }
            if (!java.util.Objects.equals(choice.terminalFamily(), config.terminalFontFamily())) {
                ctx.terminalSettings().setFamily(choice.terminalFamily());
                allPanes().forEach(TerminalTab::reloadFont);
            }
            mutate(I18n.t("error.saveSettings"), () ->
                    ctx.config().save(ctx.config().current().withUiFontFamily(choice.uiFamily())
                            .withTerminalFontFamily(choice.terminalFamily())));
        });
    }

    private void updateModeToggle() {
        var mode = theme.effectiveMode(wantedMode);
        boolean both = theme.modes().size() > 1;
        // ikon menunjukkan mode tujuan: di mode gelap tampil matahari, di mode terang tampil bulan
        modeToggle.setText(mode == ThemeMode.DARK ? "☀" : "☾");
        modeToggle.setEnabled(both);
        modeToggle.setToolTipText(both
                ? I18n.t("main.modeToggle.switch", mode.other().label().toLowerCase(java.util.Locale.ROOT))
                : I18n.t("main.modeToggle.single", theme.label(), mode.label().toLowerCase(java.util.Locale.ROOT)));
    }

    /** Submenu pilihan set ikon; ganti langsung terlihat (repaint) dan disimpan di config.json. */
    private JMenu buildIconSetMenu() {
        var menu = new JMenu(I18n.t("main.menu.settings.iconSet"));
        var group = new ButtonGroup();
        for (IconSet set : IconSet.values()) {
            var item = new JRadioButtonMenuItem(set.label(), set == AppIcon.current());
            item.addActionListener(e -> {
                if (set == AppIcon.current()) {
                    return;
                }
                AppIcon.use(set);
                for (Window w : Window.getWindows()) {
                    w.repaint();
                }
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withIconSet(set.id())));
            });
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    /** Tampilkan/sembunyikan panel log di bawah, dengan tinggi terakhir yang dipakai. EDT. */
    private void setLogVisible(boolean visible) {
        showLog.setSelected(visible);
        if (visible == logPanel.isVisible()) {
            return;
        }
        if (!visible) {
            logHeight = Math.max(80, logSplit.getHeight() - logSplit.getDividerLocation());
        }
        logPanel.setVisible(visible);
        logSplit.setDividerSize(visible ? logDividerSize : 0);
        if (visible) {
            logSplit.setDividerLocation(Math.max(100, logSplit.getHeight() - logHeight));
        }
        logSplit.revalidate();
        if (!visible) {
            currentTab().ifPresent(TerminalTab::focusTerminal);
        }
    }

    private JMenu buildVaultMenu() {
        var gate = ctx.vault();
        var menu = new JMenu(I18n.t("main.menu.vault"));
        menu.add(menuItem(null, I18n.t("main.menu.vault.unlock"), null, () -> ctx.sshOps().execute(gate::ensureUnlocked)));
        menu.add(menuItem(null, I18n.t("main.menu.vault.lock"), null, () -> ctx.sshOps().execute(gate::lock)));
        menu.add(menuItem(null, I18n.t("main.menu.vault.changePassword"), null,
                () -> ctx.sshOps().execute(gate::changeMasterPasswordInteractive)));
        if (!gate.vault().isRememberSupported()) {
            return menu; // mis. macOS: belum ada proteksi key OS, vault hanya dibuka dengan master password
        }
        menu.addSeparator();
        var remember = new JCheckBoxMenuItem(I18n.t("main.menu.vault.remember"));
        remember.setToolTipText(I18n.t("main.menu.vault.remember.tooltip"));
        remember.addActionListener(e -> {
            boolean wanted = remember.isSelected();
            UiAsync.run(ctx.sshOps(), () -> {
                if (wanted && !gate.ensureUnlocked()) {
                    return false;
                }
                gate.vault().setRememberOnThisPc(wanted);
                return true;
            }, ok -> remember.setSelected(ok ? wanted : !wanted), err -> {
                remember.setSelected(!wanted);
                Dialogs.error(this, I18n.t("main.vault.title"), err);
            });
        });
        menu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                remember.setSelected(gate.vault().isRememberedOnThisPc());
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        menu.add(remember);
        return menu;
    }

    /** Profil yang OS-nya sudah dideteksi pada run ini (sekali per profil per run). EDT. */
    private final Set<java.util.UUID> osDetected = new java.util.HashSet<>();

    /** Deteksi OS di background setelah connect; hasil disimpan ke profil (untuk ikon tree & tab). */
    private void detectOs(java.util.UUID profileId, SshTtyConnector tty) {
        if (!osDetected.add(profileId)) {
            return;
        }
        UiAsync.run(ctx.sshOps(), () -> {
            try {
                return OsDetector.detect(tty.lease().connection());
            } catch (IllegalStateException e) { // lease sudah dilepas (tab keburu ditutup)
                return java.util.Optional.<OsInfo>empty();
            }
        }, os -> os.ifPresentOrElse(detected -> {
            updateTabIcons(profileId, detected);
            mutate(I18n.t("main.error.saveOsInfo"), () -> store.snapshot().find(profileId)
                    .filter(current -> !detected.equals(current.os()))
                    .ifPresent(current -> store.save(current.withOs(detected))));
        }, () -> osDetected.remove(profileId)), err -> osDetected.remove(profileId));
    }

    private void updateTabIcons(java.util.UUID profileId, OsInfo os) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (groupAt(i).panes().getFirst().profile().id().equals(profileId)) { // ikon grup = host panel pertama
                tabs.setIconAt(i, OsIcons.of(os));
            }
        }
    }

    private EditTrackerDialog editTracker;

    private void showEditTracker() {
        if (editTracker == null) {
            editTracker = new EditTrackerDialog(this, ctx.edits());
        }
        editTracker.setVisible(true);
        editTracker.toFront();
    }

    /** Isi tab ke-{@code index}: selalu {@link SplitPanes} berisi panel terminal (satu, atau beberapa kalau di-split). */
    @SuppressWarnings("unchecked")
    private SplitPanes<TerminalTab> groupAt(int index) {
        return (SplitPanes<TerminalTab>) tabs.getComponentAt(index);
    }

    private Optional<SplitPanes<TerminalTab>> currentGroup() {
        int index = tabs.getSelectedIndex();
        return index < 0 ? Optional.empty() : Optional.of(groupAt(index));
    }

    /** Panel terminal aktif (yang terakhir difokus) di tab yang dipilih. */
    private Optional<TerminalTab> currentTab() {
        return currentGroup().map(SplitPanes::active);
    }

    /** Semua panel terminal di semua tab. */
    private List<TerminalTab> allPanes() {
        var result = new ArrayList<TerminalTab>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            result.addAll(groupAt(i).panes());
        }
        return result;
    }

    /** Tutup tab dari tombol ✕ / menu (semua panel split-nya): konfirmasi dulu kalau ada sesi yang masih aktif. */
    private void closeTab(int index) {
        if (index < 0 || index >= tabs.getTabCount()) {
            return;
        }
        var panes = groupAt(index).panes();
        if (!confirmClose(panes, I18n.t("main.tab.close"), () -> tabs.setSelectedIndex(index))) {
            return;
        }
        removeTab(index, true);
    }

    /**
     * Konfirmasi menutup panel-panel terminal kalau masih ada sesi/transfer aktif, atau sesi edit host yang ikut
     * ditutup (panel terakhir host itu). Tanpa hal aktif langsung true.
     *
     * @param reveal dipanggil sebelum dialog, supaya yang akan ditutup terlihat
     */
    private boolean confirmClose(List<TerminalTab> closing, String title, Runnable reveal) {
        int transfers = closing.stream().mapToInt(TerminalTab::activeTransfers).sum();
        int edits = lastProfilesOf(closing).stream().mapToInt(id -> ctx.edits().openCount(id)).sum();
        if (closing.stream().noneMatch(TerminalTab::isSessionActive) && transfers == 0 && edits == 0) {
            return true;
        }
        String transferNote = transfers > 0 ? I18n.t("main.tab.close.transfers", String.valueOf(transfers)) : "";
        String editNote = edits > 0 ? I18n.t("main.tab.close.edits", String.valueOf(edits)) : "";
        String question;
        if (closing.size() == 1) {
            var p = closing.getFirst().profile();
            question = I18n.t("main.tab.close.confirm", p.name(), p.address());
        } else {
            question = I18n.t("main.tab.close.confirmGroup", String.valueOf(closing.size()), closing.stream()
                    .map(t -> t.profile().name() + " (" + t.profile().address() + ")")
                    .collect(Collectors.joining(", ")));
        }
        reveal.run();
        boolean yes = JOptionPane.showConfirmDialog(this, question + transferNote + editNote,
                title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
        if (!yes) {
            currentTab().ifPresent(TerminalTab::focusTerminal);
        }
        return yes;
    }

    /** Profil yang tidak punya panel terminal lagi (di luar {@code closing}) setelah panel-panel ini ditutup. */
    private Set<java.util.UUID> lastProfilesOf(List<TerminalTab> closing) {
        var ids = new java.util.LinkedHashSet<java.util.UUID>();
        closing.forEach(t -> ids.add(t.profile().id()));
        for (var other : allPanes()) {
            if (!closing.contains(other)) {
                ids.remove(other.profile().id());
            }
        }
        return ids;
    }

    /** @param closeEdits true = tab terakhir host ini ikut menutup sesi edit-nya (saat keluar: lewat EditManager.close) */
    private void removeTab(int index, boolean closeEdits) {
        var group = groupAt(index);
        var panes = group.panes();
        if (closeEdits) {
            lastProfilesOf(panes).forEach(id -> ctx.edits().closeProfile(id));
        }
        panes.forEach(TerminalTab::dispose);
        marked.remove(group);
        tabs.removeTabAt(index);
        updateCenter();
        updateTerminalMenu();
    }

    /** Arah split menurut pengaturan: menyamping (default) atau atas-bawah. */
    private int splitOrientation() {
        return AppConfig.SPLIT_VERTICAL.equals(ctx.config().current().splitMode())
                ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
    }

    /**
     * Gabungkan tab yang dipilih (Ctrl+klik) menjadi satu tab split, urut sesuai posisi tab, di posisi tab pertama.
     * Tab yang sudah berupa grup ikut dilebur; total maksimal {@value SplitPanes#MAX_PANES} terminal.
     */
    private void groupMarkedTabs() {
        marked.removeIf(c -> tabs.indexOfComponent(c) < 0);
        if (marked.size() < 2) {
            Dialogs.info(this, I18n.t("main.splitGroup.title"), I18n.t("main.splitGroup.hint", String.valueOf(SplitPanes.MAX_PANES)));
            return;
        }
        var indexes = marked.stream().mapToInt(tabs::indexOfComponent).sorted().toArray();
        var panes = new ArrayList<TerminalTab>();
        for (int i : indexes) {
            panes.addAll(groupAt(i).panes());
        }
        if (panes.size() > SplitPanes.MAX_PANES) {
            Dialogs.info(this, I18n.t("main.splitGroup.title"),
                    I18n.t("main.splitGroup.max", String.valueOf(SplitPanes.MAX_PANES), String.valueOf(panes.size())));
            return;
        }
        var focus = currentTab().filter(panes::contains).orElse(panes.getFirst());
        marked.clear();
        for (int k = indexes.length - 1; k >= 0; k--) { // dari belakang supaya indeks di depannya tetap
            groupAt(indexes[k]).detachAll();
            tabs.removeTabAt(indexes[k]);
        }
        var group = new SplitPanes<>(TerminalTab.class, panes, splitOrientation());
        insertGroupTab(group, indexes[0]);
        group.setActive(focus);
        refreshTitles();
        SwingUtilities.invokeLater(focus::focusTerminal);
    }

    /** Pisahkan tab grup yang dipilih: setiap panel kembali menjadi tab sendiri (sesi tetap berjalan). */
    private void ungroupCurrent() {
        int index = tabs.getSelectedIndex();
        if (index < 0 || groupAt(index).panes().size() < 2) {
            return;
        }
        var group = groupAt(index);
        var focus = group.active();
        var panes = group.detachAll();
        marked.remove(group);
        tabs.removeTabAt(index);
        SplitPanes<TerminalTab> selected = null;
        for (int k = 0; k < panes.size(); k++) {
            var single = new SplitPanes<>(TerminalTab.class, panes.get(k));
            insertGroupTab(single, index + k);
            if (panes.get(k) == focus) {
                selected = single;
            }
        }
        tabs.setSelectedComponent(selected);
        SwingUtilities.invokeLater(focus::focusTerminal);
    }

    /** Pasang tab (tunggal atau grup) di posisi {@code index} dan pilih tab itu. */
    private void insertGroupTab(SplitPanes<TerminalTab> group, int index) {
        group.setActiveListener(this::updateTerminalMenu);
        var first = group.panes().getFirst().profile();
        tabs.insertTab(tabTitle(group), OsIcons.of(first.os()), group, tabTooltip(group), index);
        tabs.setSelectedIndex(index);
        updateCenter();
        updateTerminalMenu();
    }

    /** Tutup panel split yang aktif; panel terakhir = tutup tab. */
    private void closePane() {
        int index = tabs.getSelectedIndex();
        if (index < 0) {
            return;
        }
        var group = groupAt(index);
        if (group.panes().size() < 2) {
            closeTab(index);
            return;
        }
        var pane = group.active();
        if (!confirmClose(List.of(pane), I18n.t("main.menu.terminal.closePane"), () -> { })) {
            return;
        }
        lastProfilesOf(List.of(pane)).forEach(id -> ctx.edits().closeProfile(id));
        pane.dispose();
        group.removePane(pane);
        refreshTitles();
        updateTerminalMenu();
        group.active().focusTerminal();
    }

    /** Ringkasan hal yang masih aktif (untuk konfirmasi keluar). Dipanggil di EDT, tanpa I/O. */
    private List<String> activeWork() {
        var items = new ArrayList<String>();
        for (var tab : allPanes()) {
            if (tab.isSessionActive()) {
                items.add(I18n.t("main.active.session", tab.profile().name(), tab.profile().address()));
            }
            if (tab.activeTransfers() > 0) {
                items.add(I18n.t("main.active.transfers", tab.profile().name(), String.valueOf(tab.activeTransfers())));
            }
        }
        for (var edit : ctx.edits().entries()) {
            var state = edit.session().state();
            String status = I18n.t(state == RemoteEditSession.State.EDITING
                    ? "main.active.edit.synced" : "main.active.edit.unsynced");
            items.add(I18n.t("main.active.edit", status, edit.profile().name(), String.valueOf(edit.session().remotePath())));
        }
        return items;
    }

    private int firstActiveTab() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (groupAt(i).panes().stream().anyMatch(tab -> tab.isSessionActive() || tab.activeTransfers() > 0)) {
                return i;
            }
        }
        return -1;
    }

    /** Judul tab: nama host (titik warna environment), digabung "|" untuk grup, tanda centang kalau dipilih. */
    private String tabTitle(SplitPanes<TerminalTab> group) {
        var panes = group.panes();
        boolean isMarked = marked.contains(group);
        var parts = new ArrayList<String>();
        boolean html = isMarked || panes.size() > 1;
        for (var pane : panes) {
            var p = pane.profile();
            String name = (p.name() + (pane.isSftpOnly() ? " (SFTP)" : "")).replace("&", "&amp;").replace("<", "&lt;");
            Color color = EnvColors.of(p.environment());
            html |= color != null;
            parts.add(color == null ? name
                    : "<font color='#%06x'>&#9679;</font> %s".formatted(color.getRGB() & 0xFFFFFF, name));
        }
        if (!html) {
            var only = panes.getFirst();
            return only.profile().name() + (only.isSftpOnly() ? " (SFTP)" : "");
        }
        return "<html>" + (isMarked ? "<b>&#10004;</b> " : "") + String.join(" &nbsp;|&nbsp; ", parts) + "</html>";
    }

    private static String tabTooltip(SplitPanes<TerminalTab> group) {
        return group.panes().stream().map(t -> t.profile().address()).collect(Collectors.joining(" | "));
    }

    /** Perbarui judul semua tab (tanda pilih, isi grup). EDT. */
    private void refreshTitles() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            tabs.setTitleAt(i, tabTitle(groupAt(i)));
            tabs.setToolTipTextAt(i, tabTooltip(groupAt(i)));
        }
    }

    /** Submenu arah split tab yang digabung; berlaku langsung ke semua grup dan disimpan di config.json. */
    private JMenu buildSplitModeMenu() {
        var menu = new JMenu(I18n.t("main.menu.terminal.splitMode"));
        var group = new ButtonGroup();
        record Mode(String id, String label, int orientation) {
        }
        for (var mode : new Mode[] {
                new Mode(AppConfig.SPLIT_HORIZONTAL, I18n.t("main.menu.terminal.splitMode.horizontal"), JSplitPane.HORIZONTAL_SPLIT),
                new Mode(AppConfig.SPLIT_VERTICAL, I18n.t("main.menu.terminal.splitMode.vertical"), JSplitPane.VERTICAL_SPLIT)}) {
            var item = new JRadioButtonMenuItem(mode.label(), mode.id().equals(ctx.config().current().splitMode()));
            item.addActionListener(e -> {
                for (int i = 0; i < tabs.getTabCount(); i++) {
                    groupAt(i).setOrientation(mode.orientation());
                }
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withSplitMode(mode.id())));
            });
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    /** @param icon null = tanpa ikon (ikon hanya untuk item utama: host/grup baru, Tentang) */
    private static JMenuItem menuItem(AppIcon icon, String label, KeyStroke key, Runnable action) {
        var item = icon == null ? new JMenuItem(label) : new JMenuItem(label, icon.icon());
        if (key != null) {
            item.setAccelerator(key);
        }
        item.addActionListener(e -> action.run());
        return item;
    }

    private void updateCenter() {
        var wanted = tabs.getTabCount() == 0 ? welcome : tabs;
        if (center.getComponentCount() == 0 || center.getComponent(0) != wanted) {
            center.removeAll();
            center.add(wanted, BorderLayout.CENTER);
            center.revalidate();
            center.repaint();
        }
    }

    /** Keluar aplikasi: selalu konfirmasi; kalau masih ada sesi/transfer/edit, tampilkan daftarnya. */
    private void exit() {
        List<String> active = activeWork();
        String message;
        if (active.isEmpty()) {
            message = I18n.t("exit.confirm");
        } else {
            var sb = new StringBuilder(I18n.t("exit.activeHeader")).append("\n\n");
            active.forEach(a -> sb.append("  • ").append(a).append('\n'));
            sb.append('\n').append(I18n.t("exit.activeFooter"));
            message = sb.toString();
        }
        int choice = JOptionPane.showConfirmDialog(this, message, I18n.t("exit.title"), JOptionPane.YES_NO_OPTION,
                active.isEmpty() ? JOptionPane.QUESTION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            int first = firstActiveTab();
            if (first >= 0) {
                tabs.setSelectedIndex(first);
            } else if (!ctx.edits().entries().isEmpty()) {
                showEditTracker();
            }
            return;
        }
        while (tabs.getTabCount() > 0) {
            removeTab(0, false);
        }
        dispose();
        onExit.run();
    }

    /** Menjalankan mutasi store di thread I/O; error ditampilkan di EDT. */
    private void mutate(String errorTitle, Runnable change) {
        UiAsync.run(io, change, err -> Dialogs.error(this, errorTitle, err));
    }

    // --- HostTreePanel.Actions ---

    @Override
    public void open(HostProfile profile) {
        openTab(profile, false);
    }

    @Override
    public void openSftp(HostProfile profile) {
        openTab(profile, true);
    }

    private void openTab(HostProfile profile, boolean sftpOnly) {
        drawer.setOpen(false); // mode tombol melayang: beri ruang penuh untuk terminal
        mutate(I18n.t("main.error.recent"), () -> store.markUsed(profile.id()));
        var tab = createPane(profile, sftpOnly);
        insertGroupTab(new SplitPanes<>(TerminalTab.class, tab), tabs.getTabCount());
        tab.connect();
    }

    /** Panel terminal baru (belum connect) untuk tab baru atau split. */
    private TerminalTab createPane(HostProfile profile, boolean sftpOnly) {
        var tab = new TerminalTab(profile, ctx.terminals(), ctx.sshOps(), ctx.terminalSettings(), ctx.sftpLinks(),
                sftpOnly, p -> new SftpPanel(p, ctx.sftpLinks(), ctx.sshOps(), new SftpPanel.EditActions() {
                    @Override
                    public void edit(HostProfile profile, String remotePath, String command) {
                        ctx.edits().open(profile, remotePath, command);
                    }

                    @Override
                    public void editAsRoot(HostProfile profile, String remotePath) {
                        ctx.edits().openAsRoot(profile, remotePath);
                    }

                    @Override
                    public List<dev.egateza.termul.core.config.EditorConfig.NamedEditor> editors() {
                        return ctx.config().current().editors().editors();
                    }

                    @Override
                    public void configure() {
                        configureEditors();
                    }

                    @Override
                    public Runnable onActivity(java.util.UUID profileId,
                                               java.util.function.BiConsumer<ActivityBar.Level, String> sink) {
                        return ctx.edits().addActivityListener(profileId, sink);
                    }
                }, fileIcons));
        tab.setConnectedListener(tty -> detectOs(profile.id(), tty));
        tab.setStateListener(this::updateTerminalMenu);
        tab.setCurrentProfile(() -> store.snapshot().find(profile.id()).orElse(null));
        tab.setAutoSudoVault(ctx.vault());
        tab.setHostPalette(id -> HostTerminalColors.palette(id, customThemes));
        return tab;
    }

    @Override
    public void newHost(String group) {
        ProfileDialog.create(this, group, store.snapshot(), HostTerminalColors.choices(customThemes)).ifPresent(this::saveProfile);
    }

    @Override
    public void edit(HostProfile profile) {
        // baca metadata vault (disk I/O) di luar EDT, lalu buka dialog
        UiAsync.run(io, () -> storedSecrets(profile), stored ->
                        ProfileDialog.edit(this, profile, store.snapshot(), stored, HostTerminalColors.choices(customThemes)).ifPresent(this::saveProfile),
                err -> Dialogs.error(this, I18n.t("main.error.readVault"), err));
    }

    private Set<SecretType> storedSecrets(HostProfile profile) {
        var result = EnumSet.noneOf(SecretType.class);
        for (SecretType t : SecretType.values()) {
            if (ctx.vault().vault().has(profile.id(), t)) {
                result.add(t);
            }
        }
        return result;
    }

    private void saveProfile(ProfileDialog.Result result) {
        mutate(I18n.t("main.error.saveProfile"), () -> store.save(result.profile()));
        if (result.secrets().isEmpty()) {
            return;
        }
        UiAsync.run(ctx.sshOps(), () -> {
            if (!ctx.vault().ensureUnlocked()) {
                SecretChange.discardAll(result.secrets());
                return false;
            }
            SecretChange.applyAll(ctx.vault().vault(), result.profile().id(), result.secrets());
            return true;
        }, saved -> {
            if (!saved) {
                Dialogs.info(this, I18n.t("main.vault.title"), I18n.t("main.vault.notUnlocked"));
            }
        }, err -> {
            SecretChange.discardAll(result.secrets());
            Dialogs.error(this, I18n.t("main.error.saveSecret"), err);
        });
    }

    @Override
    public void duplicate(HostProfile profile) {
        mutate(I18n.t("main.error.duplicateProfile"), () -> store.save(profile.duplicate()));
    }

    @Override
    public void delete(HostProfile profile) {
        if (Dialogs.confirm(this, I18n.t("main.profile.delete.title"), I18n.t("main.profile.delete.confirm", profile.name()))) {
            mutate(I18n.t("main.error.deleteProfile"), () -> store.delete(profile.id()));
            UiAsync.run(ctx.sshOps(), () -> {
                if (!storedSecrets(profile).isEmpty() && ctx.vault().ensureUnlocked()) {
                    ctx.vault().vault().removeProfile(profile.id());
                }
            }, err -> Dialogs.error(this, I18n.t("main.error.deleteSecret"), err));
        }
    }

    @Override
    public void newGroup(String parent) {
        String name = Dialogs.input(this, I18n.t("main.group.new.title"),
                parent.isEmpty() ? I18n.t("main.group.new.name") : I18n.t("main.group.new.subName", parent), "");
        if (name != null) {
            String path = parent.isEmpty() ? name : parent + "/" + name;
            mutate(I18n.t("main.error.createGroup"), () -> store.addGroup(path));
        }
    }

    @Override
    public void renameGroup(String group) {
        String target = Dialogs.input(this, I18n.t("main.group.rename.title"), I18n.t("main.group.rename.prompt"), group);
        if (target != null && !target.equals(group)) {
            mutate(I18n.t("main.error.renameGroup"), () -> store.renameGroup(group, target));
        }
    }

    @Override
    public void deleteGroup(String group) {
        if (Dialogs.confirm(this, I18n.t("main.group.delete.title"), I18n.t("main.group.delete.confirm", group))) {
            mutate(I18n.t("main.error.deleteGroup"), () -> store.deleteGroup(group));
        }
    }

    @Override
    public void setFavorite(HostProfile profile, boolean favorite) {
        mutate(I18n.t("main.error.favorite"), () -> store.setFavorite(profile.id(), favorite));
    }

    @Override
    public void clearRecent() {
        mutate(I18n.t("main.error.recent"), store::clearRecent);
    }
}

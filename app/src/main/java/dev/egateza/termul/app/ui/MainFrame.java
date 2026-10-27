package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.AppContext;
import dev.egateza.termul.app.BuildInfo;
import dev.egateza.termul.app.bugreport.BugReportDialog;
import dev.egateza.termul.app.edit.EditTrackerDialog;
import dev.egateza.termul.app.edit.EditorSettingsDialog;
import dev.egateza.termul.app.edit.ValidationHooksDialog;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.log.LogBuffer;
import dev.egateza.termul.app.log.LogPanel;
import dev.egateza.termul.app.ssh.KnownHostsDialog;
import dev.egateza.termul.app.monitor.HostStatusPanel;
import dev.egateza.termul.app.monitor.MemoryTuning;
import dev.egateza.termul.app.monitor.ResourceMonitor;
import dev.egateza.termul.app.sftp.ActivityBar;
import dev.egateza.termul.app.sftp.SftpColumns;
import dev.egateza.termul.app.sftp.SftpPanel;
import dev.egateza.termul.app.sftp.SystemFileIcons;
import dev.egateza.termul.app.startup.AutoStart;
import dev.egateza.termul.app.startup.ExplorerMenu;
import dev.egateza.termul.app.terminal.BackgroundImages;
import dev.egateza.termul.app.terminal.SplitPanes;
import dev.egateza.termul.app.terminal.TerminalSettings;
import dev.egateza.termul.app.terminal.LocalTerminalTab;
import dev.egateza.termul.localterm.LocalTerminalFactory;
import dev.egateza.termul.app.terminal.TermPane;
import dev.egateza.termul.app.terminal.TerminalTab;
import dev.egateza.termul.app.ui.anim.AnimationChoice;
import dev.egateza.termul.app.ui.anim.LoadingPanel;
import dev.egateza.termul.app.ui.tree.HostTreePanel;
import dev.egateza.termul.app.ui.fab.FloatingButton;
import dev.egateza.termul.app.ui.fab.FloatingOverlay;
import dev.egateza.termul.app.ui.fab.FloatingStyle;
import dev.egateza.termul.app.update.AppUpdates;
import dev.egateza.termul.app.update.BadgeDotIcon;
import dev.egateza.termul.app.update.InstallerInfo;
import dev.egateza.termul.app.update.RestartCommand;
import dev.egateza.termul.app.update.UpdateDialog;
import dev.egateza.termul.app.update.UpdateNotifier;
import dev.egateza.termul.app.update.UpdatePrompter;
import dev.egateza.termul.app.update.WhatsNewDialog;
import dev.egateza.termul.update.ReleaseVersion;
import dev.egateza.termul.app.wsl.WslController;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import dev.egateza.termul.core.Os;
import dev.egateza.termul.core.config.AppConfig;
import dev.egateza.termul.core.config.TrayConfig;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.profile.OsInfo;
import dev.egateza.termul.core.profile.ProfileStore;
import dev.egateza.termul.core.streak.StreakStore;
import dev.egateza.termul.core.theme.CustomTheme;
import dev.egateza.termul.ssh.OsDetector;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.terminal.SshTtyConnector;
import dev.egateza.termul.sftp.edit.RemoteEditSession;
import dev.egateza.termul.vault.SecretType;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.ExecutorService;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import dev.egateza.termul.app.ui.idle.HomeScreen;
import dev.egateza.termul.app.ui.streak.StreakController;
import dev.egateza.termul.app.ui.idle.IdleOverlay;
import dev.egateza.termul.app.ui.idle.IdleState;
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
    private final dev.egateza.termul.app.sftp.SftpBookmarks sftpBookmarks;
    private final HostTreePanel hostTree;
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    /** Tab yang dipilih dengan Ctrl+klik untuk digabung (Ctrl+G), urutan pilih. EDT. */
    private final java.util.Set<java.awt.Component> marked = new java.util.LinkedHashSet<>();
    private final LocalTerminalFactory localTerminals = new LocalTerminalFactory(Os.current());
    /** Tab yang dilepas ke window sendiri (klik kanan tab → Lepas ke window baru, atau seret keluar baris tab). EDT. */
    private final List<DetachedTabWindow> detached = new ArrayList<>();
    /**
     * Window tab terpisah yang terakhir aktif; null kalau yang terakhir aktif window utama. Aksi menu/shortcut Terminal
     * bekerja pada tab di window ini. Dialog (konfirmasi, dsb.) tidak mengubahnya. EDT.
     */
    private DetachedTabWindow activeDetached;
    /** Nama tab yang diganti user (klik kanan → Ganti nama), per isi tab; hanya selama aplikasi berjalan. EDT. */
    private final java.util.Map<java.awt.Component, String> customTitles = new java.util.HashMap<>();
    private final RoundedPanel center = new RoundedPanel();
    /** Isi area terminal selama belum ada tab: jam, host terakhir/favorit, animasi. */
    private final HomeScreen home = new HomeScreen(this::open);
    /** Item menu per aksi shortcut (accelerator-nya diganti saat user mengubah shortcut). EDT. */
    private final java.util.EnumMap<ShortcutAction, JMenuItem> actionItems = new java.util.EnumMap<>(ShortcutAction.class);
    private FloatingButton newHostFab; // EDT; tooltip berisi shortcut host baru
    /** Beranda dengan tombol mengambang "Host baru" di pojok kanan bawah. */
    private final FloatingOverlay homeView = new FloatingOverlay();
    /** Layar idle (glass pane): menutupi window setelah sekian menit tanpa input atau lewat shortcut. */
    private final IdleOverlay idleOverlay = new IdleOverlay(this::wakeFromIdle);
    private final IdleState idle = new IdleState(nowMillis()); // EDT
    private final javax.swing.Timer idleTimer = new javax.swing.Timer(5000, e -> checkIdle());
    /** Setiap input keyboard/mouse di aplikasi (termasuk dialog) menunda layar idle. Dipanggil di EDT. */
    private final java.awt.event.AWTEventListener activity = e -> idle.activity(nowMillis());
    private final Runnable onExit;
    private final KeyEventDispatcher hotkeys = this::dispatchHotkey;
    private final LogPanel logPanel;
    /** Pembungkus panel log (kartu bersudut membulat); yang disembunyikan/ditampilkan adalah pembungkus ini. */
    private final RoundedPanel logSide = new RoundedPanel();
    private final JSplitPane logSplit;
    private final JCheckBoxMenuItem showLog = new JCheckBoxMenuItem(I18n.t("main.menu.help.showLog"));
    private int logHeight = 220; // EDT
    private final ResourceMonitor resourceMonitor;
    private final HostStatusPanel hostStatus;
    private final BottomBar bottomBar = new BottomBar();
    private final java.util.Map<HostStatusPanel.Mode, JRadioButtonMenuItem> hostStatusModes =
            new java.util.EnumMap<>(HostStatusPanel.Mode.class);
    private final JRadioButtonMenuItem hideHostStatus = new JRadioButtonMenuItem(I18n.t("main.menu.settings.hostStatus.hide"));
    private int logDividerSize; // EDT
    // item menu Terminal yang bergantung pada tab/sesi aktif (lihat updateTerminalMenu)
    private JMenuItem miDuplicate;
    private JMenuItem miReconnect;
    private JMenuItem miCloseTab;
    private JMenuItem miDetach; // lepas tab ke window baru / kembalikan ke window utama
    private JMenuItem miNextTab;
    private JMenuItem miPreviousTab;
    private JMenuItem miSftp;
    /** Terminal → tampilan SFTP dua panel (default panel baru, berlaku juga ke panel yang terbuka). */
    private final JCheckBoxMenuItem sftpDualToggle = new JCheckBoxMenuItem(I18n.t("main.menu.terminal.sftpDual"));
    private JMenuItem miZoomIn;
    private JMenuItem miZoomOut;
    private JMenuItem miZoomReset;
    private JMenuItem miInjectSudo;
    private JMenuItem miInjectRoot;
    private JMenuItem miGroup;
    private JMenuItem miUngroup;
    private JMenuItem miClosePane;
    private JMenuItem miNextPane;
    /** Isi area utama: split (mode panel) atau area terminal saja (mode tombol melayang). */
    private final JPanel body = new JPanel(new BorderLayout());
    private final RoundedPanel hostSide = new RoundedPanel();
    /** Mode panel: tombol buka/tutup panel host, melayang di tepi kiri area terminal (bukan bagian panel). */
    private final HostDrawer.Toggle dockToggle = new HostDrawer.Toggle();
    /** Gaya {@link HostToggleStyle#TAB_BAR}: ikon panel host di ujung kiri baris tab. */
    private final JButton tabBarToggle = new JButton(new SidebarIcon());
    private HostToggleStyle hostToggleStyle; // EDT
    private final JButton modeToggle = new JButton(); // pojok kanan atas menu bar: terang/gelap
    /** Menu bar kanan: "Update x.y.z" atau "Restart untuk update"; tersembunyi kalau tidak ada. */
    private final JButton updateBadge = new JButton(new BadgeDotIcon(true));
    /** Repaint berkala supaya halo badge update berdenyut; hanya jalan selama badge tampil. */
    private final javax.swing.Timer badgePulse = new javax.swing.Timer(BadgeDotIcon.FRAME_MS, e -> updateBadge.repaint());
    private final JCheckBoxMenuItem autoUpdate = new JCheckBoxMenuItem(I18n.t("main.menu.settings.autoUpdate"));
    private final JCheckBoxMenuItem snippetToggle = new JCheckBoxMenuItem(I18n.t("main.menu.settings.snippetButton"));
    private final JCheckBoxMenuItem streakToggle = new JCheckBoxMenuItem(I18n.t("main.menu.settings.streak"));
    private final JCheckBoxMenuItem streakWidgetToggle =
            new JCheckBoxMenuItem(I18n.t("main.menu.settings.streakWidget"));
    /** Api streak di pojok kiri panel bawah + animasinya saat aplikasi dibuka. */
    private final StreakController streak;
    private final WslController wsl;
    private JCheckBoxMenuItem miWslToggle; // EDT; Pengaturan → Manajemen WSL (null di luar Windows)
    /** Ikon tray (Pengaturan → Tray &amp; startup); opt-in. */
    private final TrayController tray;
    /** Run at startup; null di OS yang tidak didukung (opsinya disembunyikan). */
    private final AutoStart autoStart = AutoStart.forCurrentOs();
    private JCheckBoxMenuItem miRunAtStartup; // EDT; null kalau autoStart null
    /** Menu klik kanan Explorer; null di luar Windows (opsinya disembunyikan). */
    private final ExplorerMenu explorerMenu = ExplorerMenu.forCurrentOs();
    private JCheckBoxMenuItem miExplorerMenu; // EDT; null kalau explorerMenu null
    /** Daftar shell terakhir yang dikirim ke registry, supaya tidak ditulis ulang tanpa perubahan. EDT. */
    private List<ExplorerMenu.Entry> explorerEntriesSynced;
    private boolean trayHintShown; // EDT; notifikasi "tetap berjalan di tray" sekali per sesi
    /** Start tersembunyi ({@code --tray}): streak baru dihitung saat window pertama kali dibuka user. EDT. */
    private boolean streakPending;
    private dev.egateza.termul.update.ReleaseVersion trayNotifiedVersion; // EDT; versi update terakhir yang diberitahukan lewat tray
    private JMenuItem checkUpdateItem; // EDT
    private final UpdateNotifier updateNotifier;
    /** Modal update otomatis saat badge menemukan versi baru. */
    private final UpdatePrompter updatePrompter;
    private UpdateDialog updateDialog; // EDT; dialog update yang sedang tampil, null kalau tidak ada
    private WhatsNewDialog whatsNew; // EDT; Bantuan → Yang baru, satu jendela saja
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
    private int hostDividerSize; // EDT; ukuran divider bawaan LaF, dipakai lagi saat panel host dibuka
    private boolean roundedPanels; // EDT; panel host, area terminal, dan panel log tampil sebagai kartu membulat
    private WinCustomFrame customFrame; // Windows: bingkai sendiri yang berperilaku native; null di OS lain

    /** Root pane yang bisa memudarkan bagian selain terminal (transparansi jendela). */
    @Override
    protected javax.swing.JRootPane createRootPane() {
        var root = new TranslucentRootPane();
        root.setOpaque(true);
        return root;
    }

    /** HWND baru dibuat di sini: bingkai Windows dipasang sebelum window tampil supaya tidak "melompat". */
    @Override
    public void addNotify() {
        super.addNotify();
        if (customFrame != null) {
            customFrame.install();
        }
    }

    public MainFrame(AppContext ctx, List<CustomTheme> customThemes, Runnable onExit) {
        super("TermUL");
        setIconImages(appIcons());
        this.ctx = ctx;
        this.store = ctx.profiles();
        this.io = ctx.io();
        this.fileIcons = new SystemFileIcons(ctx.paths().cacheDir().resolve("file-icons"), AppIcon.SIZE);
        this.sftpBookmarks = new dev.egateza.termul.app.sftp.SftpBookmarks(ctx.bookmarks(), io);
        this.onExit = onExit;
        this.updateNotifier = UpdateNotifier.forGitHub(ctx.paths(), () -> ctx.config().current().autoUpdateCheck(),
                this::showUpdateState);
        // window tersembunyi di tray: tawaran update menunggu sampai window dibuka lagi
        this.updatePrompter = new UpdatePrompter(() -> modalDialogShowing() || !isShowing(),
                () -> updateDialog != null && updateDialog.isShowing(), version -> openUpdateDialog(true));
        this.hostTree = new HostTreePanel(this);
        hostTree.setCollapsedGroups(ctx.config().current().collapsedHostGroups());
        hostTree.setLocalChoices(localChoices());
        ctx.locals().store().addListener(s -> SwingUtilities.invokeLater(this::refreshLocalChoices));
        syncExplorerMenu(); // label ikut bahasa & daftar shell saat ini
        this.highlightSettings = ctx.highlights().snapshot();
        ctx.highlights().addListener(s -> SwingUtilities.invokeLater(() -> highlightChanged(s)));
        ctx.shortcuts().addListener(o -> SwingUtilities.invokeLater(
                () -> applyShortcuts(KeyBindings.of(o, Os.current().isMac()))));
        this.wsl = new WslController(this, ctx.wsl(),
                () -> Os.current().isWindows() && ctx.config().current().wslManager(), store::snapshot, ctx.sshOps(),
                new WslController.Host() {
                    @Override
                    public void wslRunningChanged(List<String> running) {
                        hostTree.setWslRunning(running);
                        refreshLocalChoices(); // distro WSL terpasang ikut jadi pilihan terminal lokal
                    }

                    @Override
                    public Optional<HostProfile> createProfile(java.awt.Component parent, HostProfile template) {
                        return ProfileDialog.createFrom(parent, template, store.snapshot(),
                                HostTerminalColors.choices(customThemes), wsl.installedNames()).map(result -> {
                                    MainFrame.this.saveProfile(result);
                                    return result.profile();
                                });
                    }

                    @Override
                    public void saveProfile(HostProfile profile) {
                        mutate(I18n.t("main.error.saveProfile"), () -> store.save(profile));
                    }
                });
        this.tray = new TrayController(new TrayController.Actions() {
            @Override
            public void showWindow() {
                MainFrame.this.showWindow();
            }

            @Override
            public void connect(HostProfile profile) {
                MainFrame.this.showWindow();
                open(profile);
            }

            @Override
            public void setStreakWidget(boolean on) {
                MainFrame.this.setStreakWidget(on);
            }

            @Override
            public void exit() {
                MainFrame.this.showWindow(); // konfirmasi keluar tampil di atas window, bukan melayang sendiri
                MainFrame.this.exit();
            }
        });
        this.customThemes = List.copyOf(customThemes);
        this.theme = UiThemes.resolve(ctx.config().current().theme(), this.customThemes);
        this.wantedMode = ThemeMode.fromId(ctx.config().current().themeMode());

        var startConfig = ctx.config().current();
        if (WindowOpacity.undecorated(startConfig)) {
            // alpha per piksel hanya bisa di window tanpa dekorasi native: title bar FlatLaf. Di Windows selalu,
            // dengan bingkai yang berperilaku native (Snap) supaya transparansi bisa diubah tanpa membuat window ulang
            setUndecorated(true);
            getRootPane().setWindowDecorationStyle(javax.swing.JRootPane.FRAME);
            if (WindowOpacity.ownFrame()) {
                customFrame = new WinCustomFrame(this);
            }
            WindowOpacity.apply(this, startConfig.windowOpacity(), startConfig.terminalOpacity());
        }

        // pindah ke monitor dengan scaling lain: lebar teks ikut berubah (hinting per skala), tapi ukuran logis window
        // tetap sehingga Swing tidak menyusun ulang; label yang tadinya pas jadi terpotong ("16:…"). Bisa datang dari
        // thread toolkit.
        addPropertyChangeListener("graphicsConfiguration", e -> SwingUtilities.invokeLater(this::relayoutForScreen));

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (tray.active() && ctx.config().current().tray().hidesOnClose()) {
                    hideToTray();
                } else {
                    exit();
                }
            }

            @Override
            public void windowIconified(WindowEvent e) {
                if (tray.active() && ctx.config().current().tray().hidesOnMinimize()) {
                    hideToTray();
                }
            }

            @Override
            public void windowActivated(WindowEvent e) {
                if (activeDetached != null) {
                    activeDetached = null;
                    updateTerminalMenu();
                }
                if (streakPending) {
                    streakPending = false;
                    streak.start();
                }
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                idle.focusLost(); // lepas tombol pembangun tidak akan sampai ke window ini lagi
            }
        });

        tabs.putClientProperty("JTabbedPane.tabClosable", true);
        applyTabStyle(ctx.config().current().tabStyle());
        tabs.putClientProperty("JTabbedPane.tabCloseToolTipText", I18n.t("main.tab.close"));
        tabs.putClientProperty("JTabbedPane.tabCloseCallback",
                (BiConsumer<JTabbedPane, Integer>) (t, index) -> closeTab(index));
        tabs.addChangeListener(e -> {
            updateCenter();
            updateTerminalMenu();
            // isi tab selalu SplitPanes: fokuskan panel aktifnya (juga dasar kunci profil sensitif saat kembali ke tab)
            currentTab().ifPresent(pane -> SwingUtilities.invokeLater(pane::focusTerminal));
        });
        // Ctrl+klik tab (Cmd+klik di Mac) = pilih/batal pilih untuk digabung (Ctrl+G); klik biasa membatalkan semua
        // pilihan; klik kanan = menu tab (popup trigger: saat ditekan di Mac, saat dilepas di Windows)
        tabs.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (maybeTabMenu(e) || !SwingUtilities.isLeftMouseButton(e)) {
                    return; // ⌃klik di Mac = klik kanan, jangan sampai membatalkan tanda gabung
                }
                int index = tabs.indexAtLocation(e.getX(), e.getY());
                if (index < 0) {
                    return;
                }
                if (Shortcuts.isMenuDown(e)) {
                    toggleMark(tabs.getComponentAt(index));
                } else if (!marked.isEmpty()) {
                    marked.clear();
                    refreshTitles();
                    updateTerminalMenu();
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                maybeTabMenu(e);
            }
        });
        TabDragSupport.install(tabs, this::moveTab, this::detachTab);
        var idleAnimation = AnimationChoice.fromId(ctx.config().current().idleAnimation());
        home.setAnimation(idleAnimation);
        homeView.setBase(home);
        newHostFab = new FloatingButton(AppIcon.PLUS, null, FloatingStyle.Kind.PRIMARY, FloatingButton.Size.NORMAL);
        newHostFab.setToolTipText(I18n.t("fab.newHost", Shortcuts.text(ShortcutAction.NEW_HOST)));
        newHostFab.getAccessibleContext().setAccessibleName(I18n.t("main.menu.file.newHost"));
        newHostFab.addActionListener(e -> newHost(hostTree.selectedGroup()));
        homeView.addFloating(newHostFab, FloatingOverlay.Anchor.BOTTOM_RIGHT);
        home.setSnapshot(store.snapshot());
        idleOverlay.setAnimation(idleAnimation);
        setGlassPane(idleOverlay);
        updateCenter();

        // Tombol melayang di tepi kiri area terminal, satu bentuk dengan tombol laci: panel host tidak punya strip
        // sendiri, jadi saat disembunyikan yang tersisa hanya divider tipis.
        hostToggleStyle = HostToggleStyle.fromId(ctx.config().current().hostToggleStyle());
        dockToggle.onClick = () -> setDockedVisible(!hostTree.isVisible());
        dockToggle.setStyle(hostToggleStyle);
        dockToggle.setOpacity(ctx.config().current().hostButtonOpacity());
        tabBarToggle.setFocusable(false);
        tabBarToggle.putClientProperty("JButton.buttonType", "toolBarButton");
        tabBarToggle.setToolTipText(I18n.t("hostToggle.tabBar.button"));
        tabBarToggle.addActionListener(e -> toggleHostList());
        dockToggle.setVisible(false);
        getLayeredPane().add(dockToggle, Integer.valueOf(javax.swing.JLayeredPane.PALETTE_LAYER + 10));
        var relayoutToggle = new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                layoutDockToggle();
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                layoutDockToggle();
            }
        };
        center.addComponentListener(relayoutToggle); // termasuk saat divider digeser
        getLayeredPane().addComponentListener(relayoutToggle);
        updateHostToggle();
        hostTree.setMinimumSize(new Dimension(160, 100));
        // mode tombol melayang: daftar host tampil di atas area terminal (terminal tidak di-resize)
        drawer = new HostDrawer(getLayeredPane(), center, hostTree,
                () -> currentTab().ifPresent(TermPane::focusTerminal));
        drawer.setOpacity(ctx.config().current().hostButtonOpacity());
        drawer.setStyle(hostToggleStyle, false);
        // panel log di bawah; disembunyikan dengan setVisible supaya tab terminal tidak di-reparent
        logPanel = new LogPanel(LogBuffer.global(), ctx.paths().logDir(), io, () -> setLogVisible(false));
        logSide.add(logPanel, BorderLayout.CENTER);
        logSide.setVisible(false);
        logSide.setMinimumSize(new Dimension(100, 60));
        logSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, body, logSide);
        logSplit.setResizeWeight(1.0);
        logSplit.setContinuousLayout(true);
        logDividerSize = logSplit.getDividerSize();
        logSplit.setDividerSize(0); // panel log tersembunyi: tanpa divider (tidak ada garis/titik di tepi bawah)
        getContentPane().add(logSplit, BorderLayout.CENTER);
        // monitor resource JVM: "Host status" di kanan baris menu (dipasang di buildMenu)
        resourceMonitor = new ResourceMonitor(io);
        hostStatus = new HostStatusPanel(resourceMonitor, () -> setHostStatusVisible(false));
        hostStatus.setMode(HostStatusPanel.Mode.fromId(ctx.config().current().hostStatusMode()));
        hostStatus.setVisible(ctx.config().current().hostStatusBar());
        resourceMonitor.setActive(hostStatus.isVisible());
        // panel bawah selalu tampil: animasi + jam
        bottomBar.setAnimation(AnimationChoice.fromId(ctx.config().current().statusAnimation()));
        streak = new StreakController(new StreakStore(ctx.paths().streakFile()), io, getLayeredPane(),
                ctx.config().current().streak());
        bottomBar.setLeading(streak.chip());
        bottomBar.network().addStatusListener(streak::setNetworkStatus); // widget streak memakai probe yang sama
        getContentPane().add(bottomBar, BorderLayout.SOUTH);
        roundedPanels = AppConfig.PANEL_ROUNDED.equals(ctx.config().current().panelCorners());
        applyHostMode(ctx.config().current().hostPanelMode());
        // ikon tray dipasang sebelum menu dibangun: menu Tray & startup membaca tray.active()
        tray.setSnapshot(store.snapshot());
        tray.setEnabled(ctx.config().current().tray().enabled());
        setJMenuBar(buildMenu());
        if (ctx.config().current().streakWidget().visible()) {
            SwingUtilities.invokeLater(() -> setStreakWidget(true)); // setelah konstruksi window selesai
        }
        wsl.apply();

        setSize(1280, 800);
        setLocationRelativeTo(null);

        store.addListener(s -> SwingUtilities.invokeLater(() -> {
            hostTree.setSnapshot(s);
            home.setSnapshot(s);
            tray.setSnapshot(s);
            wsl.profilesChanged();
            reloadTerminalColors(); // warna khusus host / environment yang diubah di Edit host
            allPanes().forEach(this::applyHighlight); // host pindah grup: profil highlight bisa berubah
        }));
        // belum ada tab: langsung tampilkan daftar host (mode tombol melayang), menutup otomatis saat host dibuka
        SwingUtilities.invokeLater(() -> drawer.setOpen(true));
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(hotkeys);
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(activity, java.awt.AWTEvent.KEY_EVENT_MASK
                | java.awt.AWTEvent.MOUSE_EVENT_MASK | java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK
                | java.awt.AWTEvent.MOUSE_WHEEL_EVENT_MASK);
        idleTimer.start();
    }

    private static long nowMillis() {
        return System.nanoTime() / 1_000_000;
    }

    /** Timer 5 detik: tampilkan layar idle kalau tidak ada input selama waktu yang diatur. EDT. */
    private void checkIdle() {
        if (isVisible() && idle.shouldActivate(nowMillis(), ctx.config().current().idleMinutes())) {
            showIdle();
        }
    }

    /** Tutupi window dengan layar idle; sesi tetap berjalan. EDT. */
    private void showIdle() {
        if (idle.isActive()) {
            return;
        }
        javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath(); // menu terbuka tetap di atas glass pane
        idle.activate();
        idleOverlay.showIdle(tabs.getTabCount());
    }

    /** Layar idle dibangunkan dengan mouse. EDT. */
    private void wakeFromIdle() {
        idle.deactivate(nowMillis());
        hideIdle();
    }

    private void hideIdle() {
        idleOverlay.setVisible(false);
        SwingUtilities.invokeLater(() -> currentTab().ifPresent(TermPane::focusTerminal));
    }

    /**
     * Keyboard selama layar idle: tombol pertama hanya membangunkan layar, dan event tombol itu (termasuk karakter
     * dan auto-repeat) ditelan sampai dilepas, supaya Enter/Ctrl+C tidak terkirim ke server. Dialog lain tidak
     * disentuh. @return true kalau event ditelan
     */
    private boolean dispatchIdleKey(KeyEvent e) {
        var c = e.getComponent();
        var window = c instanceof Window w ? w : c == null ? null : SwingUtilities.getWindowAncestor(c);
        if (window != this) {
            return false;
        }
        var kind = switch (e.getID()) {
            case KeyEvent.KEY_PRESSED -> IdleState.Key.PRESSED;
            case KeyEvent.KEY_TYPED -> IdleState.Key.TYPED;
            default -> IdleState.Key.RELEASED;
        };
        return switch (idle.onKey(kind, e.getKeyCode(), nowMillis())) {
            case PASS -> false;
            case SWALLOW -> {
                e.consume();
                yield true;
            }
            case WAKE -> {
                e.consume();
                hideIdle();
                yield true;
            }
        };
    }

    /**
     * Shortcut aplikasi (Ctrl+Shift+…, Ctrl+F5; di Mac Cmd+Shift+…, Cmd+R, Cmd+W, plus yang diganti user) ditangkap
     * sebelum JediTerm, supaya tetap jalan saat fokus di terminal dan tidak ikut terkirim ke remote. Shortcut shell biasa
     * (Ctrl+F, Ctrl+N, ...) tidak disentuh. Lihat {@link Shortcuts#isAppCombo}.
     */
    /** KEY_PRESSED shortcut keluar (Alt+F4; tidak ada di Mac, Cmd+Q ditangani menu aplikasi macOS). */
    static boolean isExitKey(KeyEvent e) {
        var exit = Shortcuts.exit();
        return exit != null && e.getID() == KeyEvent.KEY_PRESSED && KeyStroke.getKeyStrokeForEvent(e).equals(exit);
    }

    private boolean dispatchHotkey(KeyEvent e) {
        if (dispatchIdleKey(e)) {
            return true;
        }
        var tab = currentGroup().flatMap(g -> g.paneOf(e.getComponent())); // panel split tempat key diketik
        if (tab.isPresent() && isExitKey(e)) {
            // Alt+F4 di terminal: JediTerm mengirimnya ke server sebagai escape sequence (bell, jendela tidak
            // tertutup) dan accelerator menu Keluar tidak pernah terpicu. Tutup seperti tombol X (ikut aturan tray).
            // Di window tab terpisah, yang ditutup window itu saja, bukan aplikasi.
            e.consume();
            var window = SwingUtilities.getWindowAncestor(e.getComponent());
            var target = window instanceof DetachedTabWindow ? window : this;
            SwingUtilities.invokeLater(() -> target.dispatchEvent(new WindowEvent(target, WindowEvent.WINDOW_CLOSING)));
            return true;
        }
        if (tab.isPresent() && tab.get().interceptKey(e)) {
            e.consume();
            return true;
        }
        if (e.getID() != KeyEvent.KEY_PRESSED || e.isAltDown()) {
            return false;
        }
        var ks = KeyStroke.getKeyStrokeForEvent(e);
        var action = Shortcuts.bindings().actionFor(ks).orElse(null);
        if (action == null || e.getComponent() == null || !isTabWindow(SwingUtilities.getWindowAncestor(e.getComponent()))) {
            return false;
        }
        // Gabung (Ctrl+G) diambil kalau ada tab yang dipilih untuk digabung; selain itu tetap ^G ke shell
        if (action == ShortcutAction.GROUP && marked.size() >= 2) {
            groupMarkedTabs();
            e.consume();
            return true;
        }
        if (!Shortcuts.isAppCombo(ks)) {
            return false;
        }
        if (action.tabNumber() > 0) { // Ctrl/⌘+1..9, tanpa item menu
            if (activeDetached == null && action.tabNumber() <= tabs.getTabCount()) { // window terpisah: satu tab saja
                selectTab(action.tabNumber() - 1);
            }
            e.consume(); // tab tidak ada: tetap tidak dikirim ke shell
            return true;
        }
        updateTerminalMenu(); // status bisa berubah sejak menu terakhir dibuka
        JMenuItem item = actionItems.get(action);
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
        var state = tab.orElse(null) instanceof LocalTerminalTab ? TerminalMenuState.LOCAL
                : TerminalMenuState.of(tab.isPresent(), tab.map(TermPane::isConnected).orElse(false),
                        currentSsh().map(TerminalTab::isSftpOpen).orElse(false), tab.map(TermPane::hasTerminal).orElse(true));
        for (var item : new JMenuItem[] {miDuplicate, miReconnect, miCloseTab, miZoomIn, miZoomOut, miZoomReset}) {
            item.setEnabled(state.tabActions());
        }
        boolean inDetached = activeDetached != null;
        miNextTab.setEnabled(!inDetached && tabs.getTabCount() > 1);
        miPreviousTab.setEnabled(!inDetached && tabs.getTabCount() > 1);
        miDetach.setText(I18n.t(inDetached ? "main.menu.terminal.reattach" : "main.menu.terminal.detach"));
        miDetach.setEnabled(tab.isPresent());
        miSftp.setEnabled(state.sftp());
        miInjectSudo.setEnabled(state.inject());
        miInjectRoot.setEnabled(state.inject());
        boolean grouped = currentGroup().map(g -> g.panes().size() > 1).orElse(false);
        marked.removeIf(c -> tabs.indexOfComponent(c) < 0); // tab yang sudah ditutup
        miGroup.setEnabled(marked.size() >= 2);
        miUngroup.setEnabled(grouped && !inDetached);
        miClosePane.setEnabled(grouped);
        miNextPane.setEnabled(grouped);
    }

    private static JMenuItem findAccelerator(JMenuBar bar, KeyStroke ks) {
        for (int i = 0; i < bar.getMenuCount(); i++) {
            var menu = bar.getMenu(i);
            if (menu == null) { // komponen bar yang bukan JMenu (glue, tombol mode)
                continue;
            }
            var item = findAccelerator(menu, ks);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    /** Termasuk submenu (mis. Pengaturan → Tampilan → Layar idle). */
    private static JMenuItem findAccelerator(JMenu menu, KeyStroke ks) {
        for (int j = 0; j < menu.getItemCount(); j++) {
            var item = menu.getItem(j);
            if (item instanceof JMenu sub) {
                var found = findAccelerator(sub, ks);
                if (found != null) {
                    return found;
                }
            } else if (item != null && ks.equals(item.getAccelerator())) {
                return item;
            }
        }
        return null;
    }

    @Override
    public void dispose() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(hotkeys);
        java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(activity);
        idleTimer.stop();
        badgePulse.stop();
        updatePrompter.stop();
        drawer.dispose();
        resourceMonitor.close();
        wsl.close();
        tray.close();
        streak.stop(); // termasuk menutup widget mengambang
        super.dispose();
    }

    /** Hasil load awal: store.load() tidak memanggil listener, jadi beranda juga diisi di sini. EDT. */
    public void showSnapshot(ProfileSnapshot snapshot) {
        hostTree.setSnapshot(snapshot);
        home.setSnapshot(snapshot);
        tray.setSnapshot(snapshot);
    }

    /**
     * Tampilkan window dan bawa ke depan: dari tray, dari instance kedua (shortcut dibuka lagi), atau setelah start
     * tersembunyi. EDT.
     */
    public void showWindow() {
        if (!isVisible()) {
            setVisible(true);
        }
        if ((getExtendedState() & ICONIFIED) != 0) {
            setExtendedState(getExtendedState() & ~ICONIFIED);
        }
        toFront();
        requestFocus();
    }

    /**
     * Start dari run at startup ({@code --tray}): tetap tersembunyi di tray kalau ikon tray aktif, selain itu window
     * tampil ter-minimize. Streak baru dihitung saat user membuka window, supaya login otomatis tidak menambah
     * streak. EDT.
     */
    public void startHidden() {
        streakPending = true;
        if (tray.active()) {
            // peer dibuat tanpa menampilkan window: komponen jadi displayable, jadi indikator internet (dan widget
            // streak yang memakai hasilnya) tetap memeriksa selama TermUL di tray
            addNotify();
            return;
        }
        setExtendedState(ICONIFIED);
        setVisible(true);
    }

    /**
     * Susun ulang seluruh isi window untuk skala layar yang baru. {@code revalidate()} saja tidak cukup: validasi hanya
     * turun ke container yang tidak valid, jadi semua komponen ditandai tidak valid dulu. EDT.
     */
    private void relayoutForScreen() {
        invalidateTree(getRootPane());
        validate();
        repaint();
    }

    private static void invalidateTree(java.awt.Component c) {
        c.invalidate();
        if (c instanceof java.awt.Container parent) {
            for (java.awt.Component child : parent.getComponents()) {
                invalidateTree(child);
            }
        }
    }

    /** Sembunyikan window ke tray; sesi SSH dan transfer tetap berjalan. EDT. */
    private void hideToTray() {
        setVisible(false);
        setExtendedState(getExtendedState() & ~ICONIFIED); // dibuka lagi dalam ukuran sebelum di-minimize
        if (!trayHintShown) {
            trayHintShown = true;
            tray.notify("TermUL", I18n.t("tray.hiddenHint"));
        }
    }

    public JTabbedPane tabs() {
        return tabs;
    }

    private JMenuBar buildMenu() {
        var bar = new JMenuBar();
        var file = new JMenu(I18n.t("main.menu.file"));
        file.add(actionItem(AppIcon.PLUS, I18n.t("main.menu.file.newTab"), ShortcutAction.NEW_TAB, this::showQuickOpen));
        file.add(actionItem(AppIcon.SERVER, I18n.t("main.menu.file.newHost"), ShortcutAction.NEW_HOST,
                () -> newHost(hostTree.selectedGroup())));
        file.add(actionItem(AppIcon.TERMINAL, I18n.t("main.menu.file.newLocal"), ShortcutAction.NEW_LOCAL,
                this::openLocalTerminal));
        file.add(menuItem(AppIcon.FOLDER_PLUS, I18n.t("main.menu.file.newGroup"), null, () -> newGroup(hostTree.selectedGroup())));
        file.add(menuItem(null, I18n.t("main.menu.file.importSshConfig"), null, this::importSshConfig));
        file.add(menuItem(null, I18n.t("main.menu.file.exportProfiles"), null, this::exportProfiles));
        if (Os.current().isWindows()) {
            // selalu terlihat di Windows; kalau manajemen WSL mati, user ditawari untuk menyalakannya
            file.add(menuItem(AppIcon.TERMINAL, I18n.t("main.menu.file.wslManager"), null, this::openWslManager));
        }
        file.add(actionItem(null, I18n.t("main.menu.file.findHost"), ShortcutAction.FIND_HOST,
                () -> {
                    showHostList();
                    SwingUtilities.invokeLater(hostTree::focusSearch);
                }));
        file.add(actionItem(null, I18n.t("main.menu.file.toggleHostList"), ShortcutAction.TOGGLE_HOST_LIST,
                this::toggleHostList));
        file.addSeparator();
        file.add(menuItem(AppIcon.EXIT, I18n.t("main.menu.file.exit"), Shortcuts.exit(), this::exit));
        bar.add(file);

        var terminal = new JMenu(I18n.t("main.menu.terminal"));
        terminal.add(miDuplicate = actionItem(null, I18n.t("main.menu.terminal.duplicate"), ShortcutAction.DUPLICATE_TAB,
                () -> currentTab().ifPresent(this::duplicate)));
        terminal.add(miReconnect = actionItem(null, I18n.t("main.menu.terminal.reconnect"), ShortcutAction.RECONNECT,
                () -> currentTab().ifPresent(TermPane::reconnect)));
        terminal.add(miCloseTab = actionItem(null, I18n.t("main.tab.close"), ShortcutAction.CLOSE_TAB,
                this::closeCurrentTab));
        terminal.add(miDetach = menuItem(null, I18n.t("main.menu.terminal.detach"), null, this::toggleDetachCurrent));
        terminal.add(miNextTab = actionItem(null, I18n.t("main.menu.terminal.nextTab"), ShortcutAction.NEXT_TAB,
                () -> cycleTab(1)));
        terminal.add(miPreviousTab = actionItem(null, I18n.t("main.menu.terminal.previousTab"), ShortcutAction.PREVIOUS_TAB,
                () -> cycleTab(-1)));
        terminal.add(miSftp = actionItem(null, I18n.t("main.menu.terminal.sftp"), ShortcutAction.SFTP,
                () -> currentSsh().ifPresent(TerminalTab::toggleSftp)));
        sftpDualToggle.setSelected(AppConfig.SFTP_DUAL.equals(ctx.config().current().sftpLayout()));
        sftpDualToggle.setToolTipText(I18n.t("main.menu.terminal.sftpDual.tooltip"));
        sftpDualToggle.addActionListener(e -> {
            boolean on = sftpDualToggle.isSelected();
            sshPanes().forEach(t -> t.setSftpDualPane(on));
            saveSftpLayout(on);
        });
        terminal.add(sftpDualToggle);
        SftpColumns.configure(ctx.config().current().sftpHiddenColumns(), hidden -> mutate(I18n.t("error.saveSettings"),
                () -> ctx.config().save(ctx.config().current().withSftpHiddenColumns(hidden))));
        var sftpColumns = SftpColumns.menu(I18n.t("main.menu.terminal.sftpColumns"));
        sftpColumns.setToolTipText(I18n.t("main.menu.terminal.sftpColumns.tooltip"));
        terminal.add(sftpColumns);
        terminal.add(actionItem(AppIcon.BOLT, I18n.t("main.menu.terminal.snippets"), ShortcutAction.SNIPPETS,
                this::openSnippets));
        terminal.add(actionItem(null, I18n.t("main.menu.terminal.snippetQuick"), ShortcutAction.SNIPPET_QUICK,
                () -> currentTab().ifPresent(TermPane::openSnippetMenu)));
        terminal.addSeparator();
        terminal.add(miGroup = actionItem(null, I18n.t("main.menu.terminal.group"), ShortcutAction.GROUP,
                this::groupMarkedTabs));
        terminal.add(miUngroup = actionItem(null, I18n.t("main.menu.terminal.ungroup"), ShortcutAction.UNGROUP,
                this::ungroupCurrent));
        terminal.add(buildSplitModeMenu());
        terminal.add(miNextPane = actionItem(null, I18n.t("main.menu.terminal.nextPane"), ShortcutAction.NEXT_PANE,
                () -> currentGroup().map(SplitPanes::next).ifPresent(TermPane::focusTerminal)));
        terminal.add(miClosePane = actionItem(null, I18n.t("main.menu.terminal.closePane"), ShortcutAction.CLOSE_PANE,
                this::closePane));
        terminal.addSeparator();
        terminal.add(miZoomIn = actionItem(null, I18n.t("main.menu.terminal.zoomIn"), ShortcutAction.ZOOM_IN,
                () -> currentTab().ifPresent(t -> t.zoom(1))));
        terminal.add(miZoomOut = actionItem(null, I18n.t("main.menu.terminal.zoomOut"), ShortcutAction.ZOOM_OUT,
                () -> currentTab().ifPresent(t -> t.zoom(-1))));
        terminal.add(miZoomReset = actionItem(null, I18n.t("main.menu.terminal.zoomReset"), ShortcutAction.ZOOM_RESET,
                () -> currentTab().ifPresent(t -> t.zoom(0))));
        terminal.addSeparator();
        terminal.add(actionItem(null, I18n.t("main.menu.terminal.editTracker"), ShortcutAction.EDIT_TRACKER,
                this::showEditTracker));
        terminal.addSeparator();
        terminal.add(miInjectSudo = actionItem(null, I18n.t("main.menu.terminal.injectSudo"), ShortcutAction.INJECT_SUDO,
                () -> currentSsh().ifPresent(t -> t.injectSecret(SecretType.SUDO_PASSWORD, ctx.vault()))));
        terminal.add(miInjectRoot = actionItem(null, I18n.t("main.menu.terminal.injectRoot"), ShortcutAction.INJECT_ROOT,
                () -> currentSsh().ifPresent(t -> t.injectSecret(SecretType.ROOT_PASSWORD, ctx.vault()))));
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
        refreshShortcutTexts();
        bar.add(terminal);
        bar.add(buildVaultMenu());

        var settings = new JMenu(I18n.t("menu.settings"));
        settings.add(menuItem(AppIcon.TERMINAL, I18n.t("main.menu.settings.localProfiles"), null, this::manageLocalProfiles));
        settings.add(menuItem(null, I18n.t("main.menu.settings.highlight"), null, this::configureHighlight));
        settings.add(menuItem(null, I18n.t("main.menu.settings.shortcuts"), null, this::configureShortcuts));
        settings.add(menuItem(null, I18n.t("main.menu.settings.editors"), null, this::configureEditors));
        settings.add(menuItem(null, I18n.t("main.menu.settings.validationHooks"), null, this::configureValidationHooks));
        // semua yang mengubah tampilan dikumpulkan di satu submenu
        var display = new JMenu(I18n.t("menu.settings.display"));
        display.add(buildThemeMenu());
        display.add(menuItem(null, I18n.t("menu.settings.fonts"), null, this::configureFonts));
        display.add(buildIconSetMenu());
        display.add(menuItem(null, I18n.t("menu.settings.windowOpacity"), null, this::configureWindowOpacity));
        display.add(buildPanelCornersMenu());
        display.add(buildTabStyleMenu());
        display.addSeparator();
        display.add(buildHostPanelMenu());
        display.add(buildHostStatusMenu());
        display.addSeparator();
        display.add(buildAnimationMenu(I18n.t("main.menu.settings.loadingAnimation"),
                AnimationChoice.fromId(ctx.config().current().loadingAnimation()), choice -> {
                    LoadingPanel.use(choice);
                    mutate(I18n.t("error.saveSettings"), () ->
                            ctx.config().save(ctx.config().current().withLoadingAnimation(choice.id())));
                }));
        display.add(buildAnimationMenu(I18n.t("main.menu.settings.statusAnimation"),
                AnimationChoice.fromId(ctx.config().current().statusAnimation()), choice -> {
                    bottomBar.setAnimation(choice);
                    mutate(I18n.t("error.saveSettings"), () ->
                            ctx.config().save(ctx.config().current().withStatusAnimation(choice.id())));
                }));
        display.add(buildIdleMenu());
        settings.add(display);
        settings.add(buildLanguageMenu());
        settings.add(buildBellMenu());
        settings.add(buildRightClickMenu());
        settings.add(buildCursorMenu());
        settings.addSeparator();
        settings.add(buildMemoryMenu());
        autoUpdate.setSelected(ctx.config().current().autoUpdateCheck());
        autoUpdate.setToolTipText(I18n.t("main.menu.settings.autoUpdate.tooltip"));
        autoUpdate.addActionListener(e -> setAutoUpdate(autoUpdate.isSelected()));
        settings.add(autoUpdate);
        snippetToggle.setSelected(ctx.config().current().snippetButton());
        snippetToggle.setToolTipText(I18n.t("main.menu.settings.snippetButton.tooltip"));
        snippetToggle.addActionListener(e -> saveSnippetSettings(
                ctx.config().current().withSnippetButton(snippetToggle.isSelected())));
        settings.add(snippetToggle);
        streakToggle.setSelected(ctx.config().current().streak());
        streakToggle.setToolTipText(I18n.t("main.menu.settings.streak.tooltip"));
        streakToggle.addActionListener(e -> setStreakVisible(streakToggle.isSelected()));
        settings.add(streakToggle);
        streakWidgetToggle.setSelected(ctx.config().current().streakWidget().visible());
        streakWidgetToggle.setToolTipText(I18n.t("main.menu.settings.streakWidget.tooltip"));
        streakWidgetToggle.addActionListener(e -> setStreakWidget(streakWidgetToggle.isSelected()));
        settings.add(streakWidgetToggle);
        if (Os.current().isWindows()) {
            miWslToggle = new JCheckBoxMenuItem(I18n.t("main.menu.settings.wsl"), ctx.config().current().wslManager());
            miWslToggle.setToolTipText(I18n.t("main.menu.settings.wsl.tooltip"));
            miWslToggle.addActionListener(e -> setWslManager(miWslToggle.isSelected()));
            settings.add(miWslToggle);
        }
        settings.add(buildTrayMenu());
        settings.addSeparator();
        settings.add(menuItem(null, I18n.t("main.menu.settings.knownHosts"), null,
                () -> new KnownHostsDialog(this, ctx.knownHosts(), io).setVisible(true)));
        var openConfigDir = menuItem(AppIcon.FOLDER_OPEN, I18n.t("main.menu.settings.openConfigDir"), null,
                () -> UiAsync.run(io, () -> LogPanel.openFolder(ctx.paths().configDir()),
                        err -> Dialogs.error(this, I18n.t("main.error.openConfigDir"), err)));
        openConfigDir.setToolTipText(ctx.paths().configDir().toString());
        settings.add(openConfigDir);
        bar.add(settings);
        bar.add(javax.swing.Box.createHorizontalGlue());
        bar.add(hostStatus);
        modeToggle.setFocusable(false);
        modeToggle.putClientProperty("JButton.buttonType", "toolBarButton");
        modeToggle.addActionListener(e -> {
            applyTheme(theme, theme.effectiveMode(wantedMode).other());
        });
        updateBadge.setVisible(false);
        updateBadge.setFocusable(false);
        updateBadge.putClientProperty("JButton.buttonType", "toolBarButton");
        updateBadge.addActionListener(e -> onUpdateBadge());
        bar.add(updateBadge);
        bar.add(modeToggle);
        updateModeToggle();

        var help = new JMenu(I18n.t("main.menu.help"));
        showLog.setAccelerator(Shortcuts.get(ShortcutAction.SHOW_LOG));
        actionItems.put(ShortcutAction.SHOW_LOG, showLog);
        showLog.setToolTipText(I18n.t("main.menu.help.showLog.tooltip"));
        showLog.addActionListener(e -> setLogVisible(showLog.isSelected()));
        help.add(showLog);
        help.add(menuItem(null, I18n.t("main.menu.help.openLogDir"), null,
                () -> UiAsync.run(io, () -> LogPanel.openFolder(ctx.paths().logDir()),
                        err -> Dialogs.error(this, I18n.t("main.error.openLogDir"), err))));
        help.addSeparator();
        checkUpdateItem = menuItem(null, I18n.t("main.menu.help.checkUpdate"), null, this::openUpdateDialog);
        help.add(checkUpdateItem);
        help.add(menuItem(null, I18n.t("main.menu.help.whatsNew"), null, this::openWhatsNew));
        help.add(menuItem(null, I18n.t("main.menu.help.reportBug"), null,
                () -> BugReportDialog.show(this, ctx.paths().logDir(), io)));
        help.add(menuItem(AppIcon.INFO, I18n.t("main.menu.help.about"), null, this::showAbout));
        bar.add(help);
        return bar;
    }

    /**
     * Pengaturan → Host status → Tampilkan host status: tampilkan semua / grafik saja / teks saja, atau sembunyikan.
     */
    private JMenu buildHostStatusMenu() {
        var show = new JMenu(I18n.t("main.menu.settings.hostStatus"));
        show.setToolTipText(I18n.t("main.menu.settings.hostStatus.tooltip"));
        var group = new ButtonGroup();
        for (var mode : HostStatusPanel.Mode.values()) {
            var item = new JRadioButtonMenuItem(mode.label());
            item.addActionListener(e -> showHostStatus(mode));
            group.add(item);
            show.add(item);
            hostStatusModes.put(mode, item);
        }
        show.addSeparator();
        hideHostStatus.addActionListener(e -> setHostStatusVisible(false));
        group.add(hideHostStatus);
        show.add(hideHostStatus);
        syncHostStatusMenu();
        var menu = new JMenu(I18n.t("main.menu.settings.hostStatusGroup"));
        menu.add(show);
        return menu;
    }

    /**
     * Pengaturan → Tray &amp; startup: ikon tray, tombol tutup/minimize ke tray, dan run at startup. Status run at startup
     * dibaca ulang dari OS setiap menu dibuka (bisa diubah user di pengaturan OS).
     */
    private JMenu buildTrayMenu() {
        var menu = new JMenu(I18n.t("menu.settings.tray"));
        TrayConfig config = ctx.config().current().tray();
        boolean supported = TrayController.supported();
        var show = new JCheckBoxMenuItem(I18n.t("menu.settings.tray.show"), config.enabled() && supported);
        var close = new JCheckBoxMenuItem(I18n.t("menu.settings.tray.closeToTray"), config.closeToTray());
        var minimize = new JCheckBoxMenuItem(I18n.t("menu.settings.tray.minimizeToTray"), config.minimizeToTray());
        show.setEnabled(supported);
        show.setToolTipText(I18n.t(supported ? "menu.settings.tray.show.tooltip" : "menu.settings.tray.unsupported"));
        close.setToolTipText(I18n.t("menu.settings.tray.closeToTray.tooltip"));
        Runnable syncSubOptions = () -> {
            close.setEnabled(tray.active());
            minimize.setEnabled(tray.active());
        };
        syncSubOptions.run();
        show.addActionListener(e -> {
            tray.setEnabled(show.isSelected());
            show.setSelected(tray.active()); // OS bisa menolak memasang ikon
            syncSubOptions.run();
            boolean on = tray.active();
            saveTray(t -> t.withEnabled(on));
        });
        close.addActionListener(e -> {
            boolean on = close.isSelected();
            saveTray(t -> t.withCloseToTray(on));
        });
        minimize.addActionListener(e -> {
            boolean on = minimize.isSelected();
            saveTray(t -> t.withMinimizeToTray(on));
        });
        menu.add(show);
        menu.add(close);
        menu.add(minimize);
        if (autoStart != null) {
            menu.addSeparator();
            miRunAtStartup = new JCheckBoxMenuItem(I18n.t("menu.settings.tray.runAtStartup"));
            miRunAtStartup.setEnabled(false); // sampai status dari OS terbaca
            miRunAtStartup.addActionListener(e -> setRunAtStartup(miRunAtStartup.isSelected()));
            menu.add(miRunAtStartup);
            refreshRunAtStartup();
        }
        if (explorerMenu != null) {
            miExplorerMenu = new JCheckBoxMenuItem(I18n.t("menu.settings.tray.explorerMenu"));
            miExplorerMenu.setEnabled(false); // sampai status dari registry terbaca
            miExplorerMenu.addActionListener(e -> setExplorerMenu(miExplorerMenu.isSelected()));
            menu.add(miExplorerMenu);
            refreshExplorerMenu();
        }
        menu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                show.setSelected(tray.active()); // selalu sesuai ikon yang benar-benar terpasang
                syncSubOptions.run();
                if (autoStart != null) {
                    refreshRunAtStartup();
                }
                if (explorerMenu != null) {
                    refreshExplorerMenu();
                }
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

    private void saveTray(java.util.function.UnaryOperator<TrayConfig> change) {
        mutate(I18n.t("error.saveSettings"), () -> {
            var current = ctx.config().current();
            ctx.config().save(current.withTray(change.apply(current.tray())));
        });
    }

    /** Baca status run at startup dari OS (registry/LaunchAgent) di io, tampilkan di EDT. */
    private void refreshRunAtStartup() {
        UiAsync.run(io, autoStart::status, this::showRunAtStartup,
                err -> showRunAtStartup(AutoStart.Status.DISABLED));
    }

    private void setRunAtStartup(boolean on) {
        miRunAtStartup.setEnabled(false); // cegah klik ganda selama registry/file ditulis
        UiAsync.run(io, () -> {
            try {
                autoStart.setEnabled(on);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            return autoStart.status();
        }, this::showRunAtStartup, err -> {
            Dialogs.error(this, I18n.t("menu.settings.tray.runAtStartup.failed"), err);
            refreshRunAtStartup();
        });
    }

    /** Baca status menu klik kanan Explorer dari registry di io, tampilkan di EDT. */
    private void refreshExplorerMenu() {
        UiAsync.run(io, explorerMenu::status, this::showExplorerMenu,
                err -> showExplorerMenu(ExplorerMenu.Status.DISABLED));
    }

    private void setExplorerMenu(boolean on) {
        miExplorerMenu.setEnabled(false); // cegah klik ganda selama registry ditulis
        var entries = explorerEntries();
        UiAsync.run(io, () -> {
            try {
                explorerMenu.setEnabled(on, entries);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            return explorerMenu.status();
        }, status -> {
            explorerEntriesSynced = entries;
            showExplorerMenu(status);
        }, err -> {
            Dialogs.error(this, I18n.t("menu.settings.tray.explorerMenu.failed"), err);
            refreshExplorerMenu();
        });
    }

    private void showExplorerMenu(ExplorerMenu.Status status) {
        miExplorerMenu.setSelected(status == ExplorerMenu.Status.ENABLED);
        miExplorerMenu.setEnabled(status != ExplorerMenu.Status.UNSUPPORTED);
        miExplorerMenu.setToolTipText(status == ExplorerMenu.Status.UNSUPPORTED
                ? I18n.t("menu.settings.tray.runAtStartup.unsupported")
                : I18n.t("menu.settings.tray.explorerMenu.tooltip"));
    }

    /** Item submenu Explorer: satu per pilihan terminal lokal, default dulu. EDT. */
    private List<ExplorerMenu.Entry> explorerEntries() {
        return localChoices().stream()
                .map(c -> new ExplorerMenu.Entry(c.id(), I18n.t("explorerMenu.open", c.name())))
                .toList();
    }

    /** Samakan submenu Explorer dengan daftar terminal lokal (kalau menu terpasang). EDT. */
    private void syncExplorerMenu() {
        if (explorerMenu == null) {
            return;
        }
        var entries = explorerEntries();
        if (entries.equals(explorerEntriesSynced)) {
            return;
        }
        explorerEntriesSynced = entries;
        io.execute(() -> {
            try {
                explorerMenu.sync(entries);
            } catch (IOException | RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(MainFrame.class)
                        .warn("Menu klik kanan Explorer tidak bisa diperbarui: {}", e.toString());
            }
        });
    }

    private void showRunAtStartup(AutoStart.Status status) {
        miRunAtStartup.setSelected(status == AutoStart.Status.ENABLED);
        miRunAtStartup.setEnabled(status != AutoStart.Status.UNSUPPORTED);
        miRunAtStartup.setToolTipText(switch (status) {
            case ENABLED, DISABLED -> I18n.t("menu.settings.tray.runAtStartup.tooltip");
            case DISABLED_BY_SYSTEM -> I18n.t("menu.settings.tray.runAtStartup.disabledBySystem");
            case UNSUPPORTED -> I18n.t("menu.settings.tray.runAtStartup.unsupported");
        });
    }

    /** Pengaturan → Penggunaan memori: hemat / normal, langsung berlaku tanpa restart. */
    private JMenu buildMemoryMenu() {
        var menu = new JMenu(I18n.t("main.menu.settings.memory"));
        menu.setToolTipText(I18n.t("main.menu.settings.memory.tooltip"));
        var group = new ButtonGroup();
        var selected = MemoryTuning.Mode.fromId(ctx.config().current().memoryMode());
        for (var mode : MemoryTuning.Mode.values()) {
            var item = new JRadioButtonMenuItem(mode.label(), mode == selected);
            item.setToolTipText(I18n.t("main.menu.settings.memory." + mode.id() + ".tooltip"));
            item.addActionListener(e -> setMemoryMode(mode));
            group.add(item);
            menu.add(item);
        }
        if (InstallerInfo.needsReinstall()) {
            // instalasi lama: opsi JVM hemat memori hanya didapat lewat installer baru (panduan + link di dialog update)
            menu.addSeparator();
            menu.add(menuItem(null, I18n.t("main.menu.settings.memory.reinstall"), null, this::openUpdateDialog));
        }
        return menu;
    }

    private void setMemoryMode(MemoryTuning.Mode mode) {
        if (mode == MemoryTuning.Mode.fromId(ctx.config().current().memoryMode())) {
            return;
        }
        MemoryTuning.platform().apply(mode);
        if (mode == MemoryTuning.Mode.SAVER) {
            resourceMonitor.runGc(); // kembalikan cadangan heap sekarang, tidak menunggu GC berikutnya
        }
        mutate(I18n.t("error.saveSettings"), () ->
                ctx.config().save(ctx.config().current().withMemoryMode(mode.id())));
    }

    private void syncHostStatusMenu() {
        (hostStatus.isVisible() ? hostStatusModes.get(hostStatus.mode()) : hideHostStatus).setSelected(true);
    }

    /** Tampilkan "Host status" dengan isi {@code mode} dan simpan pilihannya. EDT. */
    private void showHostStatus(HostStatusPanel.Mode mode) {
        if (mode == hostStatus.mode() && hostStatus.isVisible()) {
            return;
        }
        hostStatus.setMode(mode);
        applyHostStatusVisible(true);
        mutate(I18n.t("error.saveSettings"), () -> ctx.config().save(
                ctx.config().current().withHostStatusMode(mode.id()).withHostStatusBar(true)));
    }

    /** Tampilkan/sembunyikan "Host status" di baris menu dan simpan pilihannya. EDT. */
    private void setHostStatusVisible(boolean visible) {
        if (visible == hostStatus.isVisible()) {
            syncHostStatusMenu();
            return;
        }
        applyHostStatusVisible(visible);
        mutate(I18n.t("error.saveSettings"), () ->
                ctx.config().save(ctx.config().current().withHostStatusBar(visible)));
    }

    private void applyHostStatusVisible(boolean visible) {
        hostStatus.setVisible(visible);
        syncHostStatusMenu();
        getJMenuBar().revalidate();
        getJMenuBar().repaint();
        resourceMonitor.setActive(visible); // sampler hanya jalan selama host status terlihat
    }

    private void configureEditors() {
        EditorSettingsDialog.show(this, ctx.config().current().editors()).ifPresent(editors ->
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withEditors(editors))));
    }

    /** Dialog "Tentang TermUL" (menu Bantuan, dan menu aplikasi di macOS). EDT. */
    void showAbout() {
        Dialogs.info(this, I18n.t("main.about.title"),
                I18n.t("main.about.text", BuildInfo.load().display(), Runtime.version().toString(),
                        ctx.paths().logDir().toString(), Arrays.stream(IconSet.values())
                                .map(s -> "  • " + s.attribution())
                                .collect(Collectors.joining("\n"))));
    }

    private static String splitGroupHint() {
        return I18n.t("main.splitGroup.hint", String.valueOf(SplitPanes.MAX_PANES), Shortcuts.menuKeyName(),
                Shortcuts.text(ShortcutAction.GROUP));
    }

    private String welcomeHint() {
        return "<html><center>" + I18n.t("main.welcome.open") + "<br>"
                + I18n.t("main.welcome.shortcuts", Shortcuts.text(ShortcutAction.NEW_HOST),
                        Shortcuts.text(ShortcutAction.FIND_HOST))
                + (floatingMode() ? "<br><br>" + I18n.t("main.welcome.floatingHint") : "")
                + "</center></html>";
    }

    /** Ikon aplikasi (dibuat dengan {@code tools/MakeAppIcon.java}); ukuran yang tidak ada dilewati. */
    static List<java.awt.Image> appIcons() {
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
            } catch (IOException e) {
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

    /** Ekspor semua profil (tanpa secret) ke file JSON pilihan user: pilih file di EDT, tulis di io. */
    private void exportProfiles() {
        var chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle(I18n.t("export.title"));
        chooser.setSelectedFile(new java.io.File("termul-profiles.json"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON (*.json)", "json"));
        if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.nio.file.Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            target = target.resolveSibling(target.getFileName() + ".json");
        }
        if (java.nio.file.Files.exists(target)
                && !Dialogs.confirm(this, I18n.t("export.title"), I18n.t("export.overwrite", target.toString()))) {
            return;
        }
        java.nio.file.Path out = target;
        UiAsync.run(io, () -> store.exportTo(out),
                count -> Dialogs.info(this, I18n.t("export.title"), I18n.t("export.done", count, out.toString())),
                err -> Dialogs.error(this, I18n.t("export.title"), err));
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
            hostDividerSize = hostSplit.getDividerSize();
            body.add(hostSplit, BorderLayout.CENTER);
            updateHostToggle();
        }
        home.setHint(welcomeHint());
        syncHostToggles();
        applyPanelCorners();
        body.revalidate();
        body.repaint();
        SwingUtilities.invokeLater(() -> {
            drawer.layout();
            layoutDockToggle();
        });
    }

    /** Jarak di tepi luar window dan di tiap sisi divider saat panel membulat, px. */
    private static final int OUTER_GAP = 6;
    private static final int INNER_GAP = 3;

    /**
     * Pasang bentuk sudut ke panel host, area terminal, dan panel log. Sisi yang berbatasan dengan divider memakai
     * jarak setengah, supaya celah di kiri-kanan divider sama dan tombol panel host tetap di tengah celah. Search
     * field host ikut membulat/kotak. EDT.
     */
    private void applyPanelCorners() {
        boolean docked = !floatingMode();
        boolean logShown = logSide.isVisible();
        hostSide.setGaps(new java.awt.Insets(OUTER_GAP, OUTER_GAP, OUTER_GAP, INNER_GAP));
        center.setGaps(new java.awt.Insets(OUTER_GAP, docked ? INNER_GAP : OUTER_GAP,
                logShown ? INNER_GAP : OUTER_GAP, OUTER_GAP));
        logSide.setGaps(new java.awt.Insets(INNER_GAP, OUTER_GAP, OUTER_GAP, OUTER_GAP));
        for (var panel : new RoundedPanel[] {hostSide, center, logSide}) {
            panel.setRounded(roundedPanels);
        }
        hostTree.setSearchArc(roundedPanels ? RoundedPanel.ARC : 0);
        SwingUtilities.invokeLater(() -> {
            drawer.layout();
            layoutDockToggle();
        });
    }

    /** Gaya tab terminal: {@link AppConfig#TAB_CARD} (kotak per tab, ala VS Code) atau garis bawah. EDT. */
    private void applyTabStyle(String style) {
        tabs.putClientProperty("JTabbedPane.tabType",
                AppConfig.TAB_UNDERLINED.equals(style) ? "underlined" : "card");
    }

    private JMenu buildTabStyleMenu() {
        var menu = new JMenu(I18n.t("main.menu.settings.tabStyle"));
        var group = new ButtonGroup();
        String current = ctx.config().current().tabStyle();
        for (var style : new String[] {AppConfig.TAB_CARD, AppConfig.TAB_UNDERLINED}) {
            var item = new JRadioButtonMenuItem(I18n.t("main.menu.settings.tabStyle." + style), style.equals(current));
            item.addActionListener(e -> {
                applyTabStyle(style);
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withTabStyle(style)));
            });
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    private JMenu buildPanelCornersMenu() {
        var menu = new JMenu(I18n.t("main.menu.settings.panelCorners"));
        var group = new ButtonGroup();
        for (var rounded : new boolean[] {true, false}) {
            var item = new JRadioButtonMenuItem(I18n.t(rounded ? "main.menu.settings.panelCorners.rounded"
                    : "main.menu.settings.panelCorners.square"), rounded == roundedPanels);
            item.addActionListener(e -> {
                if (rounded == roundedPanels) {
                    return;
                }
                roundedPanels = rounded;
                applyPanelCorners();
                mutate(I18n.t("error.saveSettings"), () -> ctx.config().save(ctx.config().current()
                        .withPanelCorners(rounded ? AppConfig.PANEL_ROUNDED : AppConfig.PANEL_SQUARE)));
            });
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    /**
     * Terapkan gaya tombol panel host ke tombol melayang (mode panel), laci (mode melayang), dan ikon di tab bar.
     * Gaya tab bar memakai tombol melayang selama belum ada tab (baris tab belum tampil). EDT.
     */
    private void syncHostToggles() {
        if (drawer == null) {
            return; // dipanggil dari updateCenter() sebelum konstruktor selesai
        }
        boolean tabBar = hostToggleStyle == HostToggleStyle.TAB_BAR && tabs.getTabCount() > 0;
        if (tabs.getClientProperty("JTabbedPane.leadingComponent") != (tabBar ? tabBarToggle : null)) {
            tabs.putClientProperty("JTabbedPane.leadingComponent", tabBar ? tabBarToggle : null);
        }
        dockToggle.setStyle(hostToggleStyle);
        dockToggle.setVisible(!floatingMode() && !tabBar);
        drawer.setStyle(hostToggleStyle, tabBar);
        layoutDockToggle();
        getLayeredPane().repaint();
    }

    /** Tombol panel host di tepi kiri area terminal, posisinya sesuai gaya. EDT. */
    private void layoutDockToggle() {
        if (!dockToggle.isVisible()) {
            return;
        }
        var origin = SwingUtilities.convertPoint(center, 0, 0, getLayeredPane());
        boolean open = hostTree.isVisible();
        int line = origin.x - (open ? hostDividerSize : 1) / 2 - 1; // tengah divider di kiri area terminal
        dockToggle.place(new java.awt.Rectangle(origin.x, origin.y, center.getWidth(), center.getHeight()), line, open);
    }

    /** Ikon panel host di tab bar: buka/tutup laci (mode melayang) atau panel di samping. EDT. */
    private void toggleHostList() {
        if (floatingMode()) {
            drawer.setOpen(!drawer.isOpen());
        } else {
            setDockedVisible(!hostTree.isVisible());
        }
    }

    /** Pindah ke tab berikutnya ({@code delta} 1) atau sebelumnya (-1), berputar di ujung. EDT. */
    private void cycleTab(int delta) {
        if (activeDetached != null) {
            return; // window terpisah hanya berisi satu tab
        }
        int count = tabs.getTabCount();
        if (count > 1) {
            selectTab(Math.floorMod(Math.max(tabs.getSelectedIndex(), 0) + delta, count));
        }
    }

    /** Pilih tab dan fokus ke panel terminal aktifnya, supaya bisa langsung mengetik. EDT. */
    private void selectTab(int index) {
        tabs.setSelectedIndex(index);
        SwingUtilities.invokeLater(() -> currentTab().ifPresent(TermPane::focusTerminal));
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
            hostWidth = Math.max(160, hostSplit.getDividerLocation());
            var owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (owner != null && SwingUtilities.isDescendingFrom(owner, hostTree)) {
                currentTab().ifPresent(TermPane::focusTerminal);
            }
        }
        hostTree.setVisible(visible);
        hostSplit.setEnabled(visible); // divider tidak bisa digeser saat panel tertutup
        hostSplit.setDividerSize(visible ? hostDividerSize : 1); // tertutup: tinggal garis tipis di tepi kiri
        hostSplit.revalidate();
        hostSplit.setDividerLocation(visible ? hostWidth : 0);
        updateHostToggle();
    }

    private void updateHostToggle() {
        dockToggle.setOpen(hostTree.isVisible());
        layoutDockToggle(); // gaya lingkaran pindah dari garis pembatas ke tepi saat panel ditutup
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
        var style = new JMenu(I18n.t("main.menu.hostPanel.style"));
        var styles = new ButtonGroup();
        for (var choice : HostToggleStyle.values()) {
            var item = new JRadioButtonMenuItem(choice.label(), choice == hostToggleStyle);
            item.setToolTipText(choice.tooltip());
            item.addActionListener(e -> {
                if (choice == hostToggleStyle) {
                    return;
                }
                hostToggleStyle = choice;
                syncHostToggles();
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withHostToggleStyle(choice.id())));
            });
            styles.add(item);
            style.add(item);
        }
        menu.add(style);
        var opacity = new JMenu(I18n.t("main.menu.hostPanel.opacity"));
        opacity.setToolTipText(I18n.t("main.menu.hostPanel.opacity.tooltip"));
        var levels = new ButtonGroup();
        for (int percent : new int[] {100, 70, 40, 20}) {
            var item = new JRadioButtonMenuItem(percent == 100 ? I18n.t("main.menu.hostPanel.opacity.solid") : percent + "%",
                    percent == config.hostButtonOpacity());
            item.addActionListener(e -> {
                drawer.setOpacity(percent);
                dockToggle.setOpacity(percent);
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withHostButtonOpacity(percent)));
            });
            levels.add(item);
            opacity.add(item);
        }
        menu.add(opacity);
        return menu;
    }

    /** Submenu kursor teks terminal (bentuk + berkedip); berlaku langsung di semua tab dan disimpan di config.json. */
    private JMenu buildCursorMenu() {
        var menu = new JMenu(I18n.t("menu.settings.cursor"));
        menu.setToolTipText(I18n.t("menu.settings.cursor.tooltip"));
        var group = new ButtonGroup();
        var blink = new JCheckBoxMenuItem(I18n.t("menu.settings.cursor.blink"), ctx.config().current().cursorBlink());
        for (String shape : AppConfig.CURSOR_SHAPES) {
            var item = new JRadioButtonMenuItem(I18n.t("menu.settings.cursor." + shape),
                    shape.equals(ctx.config().current().cursorShape()));
            item.addActionListener(e -> applyCursor(ctx.config().current().withCursorShape(shape)));
            group.add(item);
            menu.add(item);
        }
        menu.addSeparator();
        blink.addActionListener(e -> applyCursor(ctx.config().current().withCursorBlink(blink.isSelected())));
        menu.add(blink);
        return menu;
    }

    private void applyCursor(AppConfig next) {
        ctx.terminalSettings().setCursorShape(TerminalSettings.cursorShapeOf(next.cursorShape(), next.cursorBlink()));
        allPanes().forEach(TermPane::reloadCursor);
        mutate(I18n.t("error.saveSettings"), () -> ctx.config().save(next));
    }

    /** Submenu perilaku klik kanan di terminal; berlaku langsung di semua tab dan disimpan di config.json. */
    private JMenu buildRightClickMenu() {
        var menu = new JMenu(I18n.t("menu.settings.rightClick"));
        var group = new ButtonGroup();
        String current = ctx.config().current().rightClick();
        for (String mode : new String[] {AppConfig.RIGHT_CLICK_COPY_PASTE, AppConfig.RIGHT_CLICK_MENU}) {
            var item = new JRadioButtonMenuItem(I18n.t("menu.settings.rightClick." + mode), mode.equals(current));
            item.setToolTipText(I18n.t("menu.settings.rightClick." + mode + ".tooltip"));
            item.addActionListener(e -> {
                ctx.terminalSettings().setRightClickCopyPaste(AppConfig.RIGHT_CLICK_COPY_PASTE.equals(mode));
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withRightClick(mode)));
            });
            group.add(item);
            menu.add(item);
        }
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
        allPanes().forEach(TermPane::reloadColors);
    }

    /**
     * Dialog transparansi, dua slider: bagian selain terminal (teks ikut pudar) dan latar terminal (teks tetap solid).
     * Perubahan langsung terlihat saat slider digeser. Di Windows selalu; di OS lain hanya kalau window sudah dibuat
     * tanpa dekorasi native (transparansi aktif saat start), selain itu berlaku setelah restart.
     */
    private void configureWindowOpacity() {
        var config = ctx.config().current();
        int originalWindow = config.windowOpacity();
        int originalTerminal = config.terminalOpacity();
        var windowSlider = new javax.swing.JSlider(AppConfig.MIN_WINDOW_OPACITY, 100, originalWindow);
        var terminalSlider = new javax.swing.JSlider(AppConfig.MIN_TERMINAL_OPACITY, 100, originalTerminal);
        var windowLabel = new JLabel(I18n.t("window.opacity.label", originalWindow));
        var terminalLabel = new JLabel(I18n.t("window.opacity.terminalLabel", originalTerminal));
        javax.swing.event.ChangeListener preview = e -> {
            windowLabel.setText(I18n.t("window.opacity.label", windowSlider.getValue()));
            terminalLabel.setText(I18n.t("window.opacity.terminalLabel", terminalSlider.getValue()));
            WindowOpacity.apply(this, windowSlider.getValue(), terminalSlider.getValue()); // pratinjau langsung
        };
        windowSlider.addChangeListener(preview);
        terminalSlider.addChangeListener(preview);
        var hint = new JLabel("<html>" + I18n.t("window.opacity.hint") + "</html>");
        hint.setPreferredSize(new Dimension(380, 70));
        var sliders = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        sliders.add(windowLabel);
        sliders.add(windowSlider);
        sliders.add(terminalLabel);
        sliders.add(terminalSlider);
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.add(sliders, BorderLayout.CENTER);
        panel.add(hint, BorderLayout.SOUTH);
        int answer = JOptionPane.showConfirmDialog(this, panel, I18n.t("window.opacity.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        boolean ok = answer == JOptionPane.OK_OPTION;
        int window = ok ? windowSlider.getValue() : originalWindow;
        int terminal = ok ? terminalSlider.getValue() : originalTerminal;
        boolean applied = WindowOpacity.apply(this, window, terminal); // batal: kembali ke nilai semula
        if (window == originalWindow && terminal == originalTerminal) {
            return;
        }
        mutate(I18n.t("error.saveSettings"), () -> ctx.config().save(
                ctx.config().current().withWindowOpacity(window).withTerminalOpacity(terminal)));
        if (!applied) {
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
        for (var detached : new java.awt.Component[] {tabs, homeView, hostTree, hostSide, logSide, editTracker}) {
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
                allPanes().forEach(TermPane::reloadFont);
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

    /**
     * Submenu radio: tanpa animasi, acak, lalu semua animasi ({@link AnimationChoice#all}); {@code onPick} hanya
     * dipanggil kalau pilihan berubah.
     */
    private static JMenu buildAnimationMenu(String title, AnimationChoice selected,
                                            java.util.function.Consumer<AnimationChoice> onPick) {
        var menu = new JMenu(title);
        var group = new ButtonGroup();
        var current = new java.util.concurrent.atomic.AtomicReference<>(selected); // EDT; hanya wadah yang bisa diubah
        for (var choice : AnimationChoice.all()) {
            var item = new JRadioButtonMenuItem(choice.label(), choice.equals(selected));
            item.addActionListener(e -> {
                if (!choice.equals(current.getAndSet(choice))) {
                    onPick.accept(choice);
                }
            });
            group.add(item);
            menu.add(item);
            if (choice.equals(AnimationChoice.RANDOM)) {
                menu.addSeparator();
            }
        }
        return menu;
    }

    /** Pilihan waktu idle di menu (menit; 0 = mati). */
    private static final int[] IDLE_MINUTES = {0, 5, 10, 15, 30, 60};

    /** Pengaturan → Tampilan → Layar idle: tampilkan sekarang, waktu idle, dan animasinya. */
    private JMenu buildIdleMenu() {
        var menu = new JMenu(I18n.t("main.menu.settings.idle"));
        menu.setToolTipText(I18n.t("main.menu.settings.idle.tooltip"));
        menu.add(actionItem(null, I18n.t("main.menu.settings.idle.now"), ShortcutAction.IDLE_NOW, this::showIdle));
        menu.addSeparator();
        var group = new ButtonGroup();
        int current = ctx.config().current().idleMinutes();
        var choices = java.util.stream.IntStream.concat(Arrays.stream(IDLE_MINUTES), java.util.stream.IntStream.of(current))
                .distinct().sorted().toArray(); // nilai lain dari config.json (mis. 45) tetap terlihat terpilih
        for (int minutes : choices) {
            var item = new JRadioButtonMenuItem(minutes == 0 ? I18n.t("main.menu.settings.idle.off")
                    : I18n.t("main.menu.settings.idle.minutes", String.valueOf(minutes)), minutes == current);
            item.addActionListener(e -> {
                idle.activity(nowMillis()); // hitung ulang dari sekarang
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withIdleMinutes(minutes)));
            });
            group.add(item);
            menu.add(item);
        }
        menu.addSeparator();
        menu.add(buildAnimationMenu(I18n.t("main.menu.settings.idleAnimation"),
                AnimationChoice.fromId(ctx.config().current().idleAnimation()), choice -> {
                    home.setAnimation(choice);
                    idleOverlay.setAnimation(choice);
                    mutate(I18n.t("error.saveSettings"), () ->
                            ctx.config().save(ctx.config().current().withIdleAnimation(choice.id())));
                }));
        return menu;
    }

    /** Tampilkan/sembunyikan panel log di bawah, dengan tinggi terakhir yang dipakai. EDT. */
    private void setLogVisible(boolean visible) {
        showLog.setSelected(visible);
        if (visible == logSide.isVisible()) {
            return;
        }
        if (!visible) {
            logHeight = Math.max(80, logSplit.getHeight() - logSplit.getDividerLocation());
        }
        logSide.setVisible(visible);
        applyPanelCorners(); // jarak bawah area terminal mengikuti ada/tidaknya panel log
        logSplit.setDividerSize(visible ? logDividerSize : 0);
        if (visible) {
            logSplit.setDividerLocation(Math.max(100, logSplit.getHeight() - logHeight));
        }
        logSplit.revalidate();
        if (!visible) {
            currentTab().ifPresent(TermPane::focusTerminal);
        }
    }

    private JMenu buildVaultMenu() {
        var gate = ctx.vault();
        var menu = new JMenu(I18n.t("main.menu.vault"));
        var unlock = menuItem(null, I18n.t("main.menu.vault.unlock"), null,
                () -> ctx.sshOps().execute(gate::unlockExplicitly));
        var lock = menuItem(null, I18n.t("main.menu.vault.lock"), null, () -> ctx.sshOps().execute(gate::lock));
        menu.add(unlock);
        menu.add(lock);
        // status kunci terlihat dari menu: hanya aksi yang berlaku yang aktif
        menu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                boolean unlocked = gate.vault().isUnlocked();
                unlock.setEnabled(!unlocked);
                lock.setEnabled(unlocked);
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        menu.add(menuItem(null, I18n.t("main.menu.vault.changePassword"), null,
                () -> ctx.sshOps().execute(gate::changeMasterPasswordInteractive)));
        menu.add(menuItem(null, I18n.t("main.menu.vault.forgetPassword"), null,
                () -> ctx.sshOps().execute(gate::forgetMasterPasswordInteractive)));
        if (!gate.vault().isRememberSupported()) {
            return menu; // mis. JAR portable di macOS atau Linux: vault hanya dibuka dengan master password
        }
        menu.addSeparator();
        String rememberKey = Os.current().isMac() ? "main.menu.vault.rememberMac" : "main.menu.vault.remember";
        var remember = new JCheckBoxMenuItem(I18n.t(rememberKey));
        remember.setToolTipText(I18n.t(rememberKey + ".tooltip"));
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
        }, os -> os.ifPresentOrElse(detected -> applyOs(profileId, detected),
                () -> osDetected.remove(profileId)), err -> osDetected.remove(profileId));
    }

    /** Panel SFTP: deteksi OS lewat SFTP saat pertama tersambung (host yang tidak pernah dibuka di terminal). */
    private SftpPanel detectOsOnConnect(HostProfile profile, SftpPanel panel) {
        if (!profile.isFtp()) { // akun FTP biasanya di-chroot; /etc/os-release bukan milik server
            panel.setConnectedListener(svc -> detectOsViaSftp(profile.id(), svc));
        }
        return panel;
    }

    /**
     * Baca {@code /etc/os-release} lewat SFTP (tanpa exec, jadi juga untuk akun yang hanya boleh SFTP). Tidak terbaca
     * (mis. akun di-chroot) = {@link OsInfo#SFTP_ONLY}: ikon SFTP, dan terminal tetap boleh mencoba deteksi lewat exec.
     */
    private void detectOsViaSftp(java.util.UUID profileId, dev.egateza.termul.sftp.RemoteFileService svc) {
        if (!osDetected.add(profileId)) {
            return;
        }
        UiAsync.run(ctx.sshOps(), () -> {
            String content = svc.readSmallText("/etc/os-release", 16 * 1024);
            if (content == null) {
                content = svc.readSmallText("/usr/lib/os-release", 16 * 1024);
            }
            return OsInfo.parseOsRelease(content).orElse(OsInfo.SFTP_ONLY);
        }, detected -> {
            if (detected.isSftpOnly()) {
                osDetected.remove(profileId);
            }
            applyOs(profileId, detected);
        }, err -> osDetected.remove(profileId));
    }

    /** Pasang OS hasil deteksi ke ikon tab dan profil. Penanda SFTP tidak menimpa OS asli yang sudah diketahui. EDT. */
    private void applyOs(java.util.UUID profileId, OsInfo detected) {
        var known = store.snapshot().find(profileId).map(HostProfile::os).orElse(null);
        if (detected.isSftpOnly() && known != null && !known.isSftpOnly()) {
            return;
        }
        updateTabIcons(profileId, detected);
        mutate(I18n.t("main.error.saveOsInfo"), () -> store.snapshot().find(profileId)
                .filter(current -> !detected.equals(current.os()))
                .filter(current -> !detected.isSftpOnly() || current.os() == null || current.os().isSftpOnly())
                .ifPresent(current -> store.save(current.withOs(detected))));
    }

    private void updateTabIcons(java.util.UUID profileId, OsInfo os) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (ssh(groupAt(i).panes().getFirst()).filter(t -> t.profile().id().equals(profileId)).isPresent()) { // ikon grup = host panel pertama
                tabs.setIconAt(i, tabIcon(groupAt(i), OsIcons.of(os)));
            }
        }
    }

    /** Tombol snippet di atas terminal (semua jenis tab): snippet global + grup host. EDT. */
    private final TermPane.SnippetHost snippetHost = new TermPane.SnippetHost() {
        private dev.egateza.termul.app.snippet.SnippetSender sender; // EDT; dibuat saat pertama dipakai

        @Override
        public dev.egateza.termul.app.snippet.SnippetSender sender() {
            if (sender == null) {
                sender = new dev.egateza.termul.app.snippet.SnippetSender(ctx.snippets(), io,
                        () -> ctx.terminalSettings().getTerminalFont());
            }
            return sender;
        }

        @Override
        public boolean buttonVisible() {
            return ctx.config().current().snippetButton();
        }

        @Override
        public String corner() {
            return ctx.config().current().snippetCorner();
        }

        @Override
        public void cornerChanged(String corner) {
            saveSnippetSettings(ctx.config().current().withSnippetCorner(corner));
        }

        @Override
        public void manage() {
            openSnippets();
        }
    };

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
    private SplitPanes<TermPane> groupAt(int index) {
        return (SplitPanes<TermPane>) tabs.getComponentAt(index);
    }

    private Optional<SplitPanes<TermPane>> currentGroup() {
        if (activeDetached != null) {
            return Optional.of(activeDetached.group());
        }
        int index = tabs.getSelectedIndex();
        return index < 0 ? Optional.empty() : Optional.of(groupAt(index));
    }

    /** Panel terminal aktif (yang terakhir difokus) di tab yang dipilih. */
    private Optional<TermPane> currentTab() {
        return currentGroup().map(SplitPanes::active);
    }

    /** Panel aktif kalau panel SSH: SFTP, inject password, remote edit, dan profil host hanya ada di sana. */
    private Optional<TerminalTab> currentSsh() {
        return currentTab().flatMap(MainFrame::ssh);
    }

    private static Optional<TerminalTab> ssh(TermPane pane) {
        return pane instanceof TerminalTab t ? Optional.of(t) : Optional.empty();
    }

    /** Semua panel SSH di semua tab. */
    private List<TerminalTab> sshPanes() {
        return allPanes().stream().flatMap(p -> ssh(p).stream()).toList();
    }

    /** Semua panel terminal di semua tab, termasuk tab yang dilepas ke window sendiri. */
    private List<TermPane> allPanes() {
        var result = new ArrayList<TermPane>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            result.addAll(groupAt(i).panes());
        }
        detached.forEach(w -> result.addAll(w.group().panes()));
        return result;
    }

    /** Window utama atau window tab terpisah: shortcut aplikasi berlaku di sana. */
    private boolean isTabWindow(Window window) {
        return window == this || (window instanceof DetachedTabWindow w && detached.contains(w));
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

    /** Tutup tab (Ctrl/⌘+W): tab terpilih di window utama, atau tab di window terpisah yang sedang aktif. */
    private void closeCurrentTab() {
        if (activeDetached != null) {
            closeDetached(activeDetached);
        } else {
            closeTab(tabs.getSelectedIndex());
        }
    }

    /** Menu Terminal → Lepas tab / Kembalikan ke window utama, untuk tab yang sedang aktif. */
    private void toggleDetachCurrent() {
        if (activeDetached != null) {
            reattach(activeDetached);
        } else if (tabs.getSelectedIndex() >= 0) {
            detachTab(tabs.getSelectedIndex(), null);
        }
    }

    /**
     * Pindahkan tab ke window sendiri (sesi tetap berjalan, tanpa reconnect). EDT.
     *
     * @param screen titik layar tempat tab dilepas (hasil seret); null = sedikit bergeser dari window utama
     */
    private void detachTab(int index, java.awt.Point screen) {
        if (index < 0 || index >= tabs.getTabCount()) {
            return;
        }
        var group = groupAt(index);
        Dimension size = group.getSize();
        if (size.width < 400 || size.height < 250) {
            size = new Dimension(900, 600);
        }
        String custom = customTitles.remove(group);
        marked.remove(group);
        tabs.removeTabAt(index); // panel tidak di-dispose: hanya pindah window
        group.setActiveListener(this::updateTerminalMenu);
        var window = new DetachedTabWindow(group, custom, buildDetachedMenu(), new DetachedTabWindow.Owner() {
            @Override
            public void closeRequested(DetachedTabWindow w) {
                closeDetached(w);
            }

            @Override
            public void activated(DetachedTabWindow w) {
                activeDetached = w;
                updateTerminalMenu();
            }
        });
        detached.add(window);
        window.getContentPane().setPreferredSize(size);
        window.pack();
        if (screen != null) {
            window.setLocation(screen.x - 60, screen.y - 20); // judul window di bawah kursor
        } else {
            window.setLocation(getX() + 48, getY() + 48);
        }
        updateCenter();
        refreshTitles();
        window.setVisible(true);
        window.toFront();
    }

    /** Kembalikan tab di window terpisah ke window utama (paling kanan). EDT. */
    private void reattach(DetachedTabWindow window) {
        if (!detached.remove(window)) {
            return;
        }
        if (activeDetached == window) {
            activeDetached = null;
        }
        String custom = window.customTitle();
        var group = window.takeGroup();
        window.dispose();
        if (custom != null) {
            customTitles.put(group, custom);
        }
        insertGroupTab(group, tabs.getTabCount());
        refreshTitles();
        showWindow();
        SwingUtilities.invokeLater(() -> group.active().focusTerminal());
    }

    /** Tombol tutup / Ctrl+W di window terpisah: seperti menutup tab, dengan konfirmasi kalau masih ada sesi. EDT. */
    private void closeDetached(DetachedTabWindow window) {
        if (!detached.contains(window)) {
            return;
        }
        activeDetached = window; // dialog konfirmasi tampil di depan window yang ditutup
        if (!confirmClose(window.group().panes(), I18n.t("main.tab.close"), window::toFront)) {
            return;
        }
        disposeDetached(window, true);
    }

    /** @param closeEdits true = host yang tidak punya panel lain ikut menutup sesi edit-nya (lihat {@link #removeTab}) */
    private void disposeDetached(DetachedTabWindow window, boolean closeEdits) {
        var panes = window.group().panes();
        if (closeEdits) {
            lastProfilesOf(panes).forEach(id -> ctx.edits().closeProfile(id));
        }
        detached.remove(window);
        if (activeDetached == window) {
            activeDetached = null;
        }
        panes.forEach(TermPane::dispose);
        window.dispose();
        updateTerminalMenu();
    }

    /**
     * Menu window terpisah: aksi menu Terminal untuk tab di window itu. Item dibuat ulang setiap menu dibuka, supaya
     * status aktif dan shortcut-nya mengikuti item aslinya di window utama.
     */
    private JMenuBar buildDetachedMenu() {
        var bar = new JMenuBar();
        var menu = new JMenu(I18n.t("main.menu.terminal"));
        menu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                updateTerminalMenu();
                menu.removeAll();
                menu.add(popupItem(miDetach));
                menu.addSeparator();
                for (var item : new JMenuItem[] {miDuplicate, miReconnect, miSftp}) {
                    menu.add(popupItem(item));
                }
                menu.addSeparator();
                menu.add(popupItem(miInjectSudo));
                menu.add(popupItem(miInjectRoot));
                menu.addSeparator();
                for (var item : new JMenuItem[] {miZoomIn, miZoomOut, miZoomReset}) {
                    menu.add(popupItem(item));
                }
                menu.addSeparator();
                menu.add(popupItem(miNextPane));
                menu.add(popupItem(miClosePane));
                menu.add(popupItem(miCloseTab));
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        bar.add(menu);
        return bar;
    }

    /** Induk dialog: window terpisah yang sedang aktif, supaya dialog muncul di depan window itu. */
    private java.awt.Component dialogParent() {
        return activeDetached != null ? activeDetached : this;
    }

    /**
     * Konfirmasi menutup panel-panel terminal kalau masih ada sesi/transfer aktif, atau sesi edit host yang ikut
     * ditutup (panel terakhir host itu). Tanpa hal aktif langsung true.
     *
     * @param reveal dipanggil sebelum dialog, supaya yang akan ditutup terlihat
     */
    private boolean confirmClose(List<TermPane> closing, String title, Runnable reveal) {
        int transfers = closing.stream().mapToInt(TermPane::activeTransfers).sum();
        int edits = lastProfilesOf(closing).stream().mapToInt(id -> ctx.edits().openCount(id)).sum();
        if (closing.stream().noneMatch(TermPane::isSessionActive) && transfers == 0 && edits == 0) {
            return true;
        }
        String transferNote = transfers > 0 ? I18n.t("main.tab.close.transfers", String.valueOf(transfers)) : "";
        String editNote = edits > 0 ? I18n.t("main.tab.close.edits", String.valueOf(edits)) : "";
        var running = closing.stream().flatMap(t -> t instanceof LocalTerminalTab l ? l.runningCommands().stream()
                : java.util.stream.Stream.<String>empty()).distinct().toList();
        String runningNote = running.isEmpty() ? "" : I18n.t("main.tab.close.running", String.join(", ", running));
        String question;
        if (closing.size() == 1) {
            var p = closing.getFirst();
            question = p instanceof LocalTerminalTab ? I18n.t("main.tab.close.confirmLocal", p.displayName())
                    : I18n.t("main.tab.close.confirm", p.displayName(), p.displayAddress());
        } else {
            question = I18n.t("main.tab.close.confirmGroup", String.valueOf(closing.size()), closing.stream()
                    .map(t -> t.displayName() + " (" + t.displayAddress() + ")")
                    .collect(Collectors.joining(", ")));
        }
        reveal.run();
        boolean yes = JOptionPane.showConfirmDialog(dialogParent(), question + runningNote + transferNote + editNote,
                title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
        if (!yes) {
            currentTab().ifPresent(TermPane::focusTerminal);
        }
        return yes;
    }

    /** Profil yang tidak punya panel terminal lagi (di luar {@code closing}) setelah panel-panel ini ditutup. */
    private Set<java.util.UUID> lastProfilesOf(List<TermPane> closing) {
        var ids = new java.util.LinkedHashSet<java.util.UUID>();
        closing.forEach(t -> ssh(t).ifPresent(s -> ids.add(s.profile().id())));
        for (var other : sshPanes()) {
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
        panes.forEach(TermPane::dispose);
        marked.remove(group);
        customTitles.remove(group);
        tabs.removeTabAt(index);
        updateCenter();
        updateTerminalMenu();
    }

    /** Tutup beberapa tab sekaligus (Tutup tab lain / di kanan) dengan satu konfirmasi untuk semuanya. EDT. */
    private void closeTabs(List<Integer> indexes, String title) {
        var groups = indexes.stream().map(this::groupAt).toList();
        var panes = groups.stream().flatMap(g -> g.panes().stream()).toList();
        if (panes.isEmpty() || !confirmClose(panes, title, () -> { })) {
            return;
        }
        for (var group : groups) { // satu per satu: sesi edit host ditutup saat panel terakhirnya ikut tertutup
            int index = tabs.indexOfComponent(group);
            if (index >= 0) {
                removeTab(index, true);
            }
        }
        currentTab().ifPresent(TermPane::focusTerminal);
    }

    /** Duplikat tab: buka sesi baru dengan sumber yang sama (host SSH, atau "SFTP saja"). */
    private void duplicate(TermPane pane) {
        if (activeDetached != null) {
            showWindow(); // tab baru selalu dibuka di window utama
        }
        switch (pane) {
            case TerminalTab t -> openTab(t.profile(), t.isSftpOnly());
            case LocalTerminalTab l -> openLocalProfile(l.profileId());
        }
    }

    /** Pindahkan tab (hasil drag) tanpa mengubah judul/ikon/tooltip-nya, lalu pilih tab itu. EDT. */
    private void moveTab(int from, int to) {
        var group = groupAt(from);
        String title = tabs.getTitleAt(from);
        var icon = tabs.getIconAt(from);
        String tooltip = tabs.getToolTipTextAt(from);
        tabs.removeTabAt(from);
        tabs.insertTab(title, icon, group, tooltip, to);
        selectTab(to);
    }

    private void toggleMark(java.awt.Component tab) {
        if (!marked.remove(tab)) {
            marked.add(tab);
        }
        refreshTitles();
        updateTerminalMenu();
    }

    /** @return true kalau event ini pemicu menu klik kanan tab (menu ditampilkan kalau mengenai tab) */
    private boolean maybeTabMenu(java.awt.event.MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return false;
        }
        int index = tabs.indexAtLocation(e.getX(), e.getY());
        if (index >= 0) {
            showTabMenu(index, e.getX(), e.getY());
        }
        return true;
    }

    /**
     * Menu klik kanan tab. Tab dipilih dulu, jadi aksi menu Terminal (yang bekerja pada tab terpilih) dipakai lewat
     * {@link #popupItem}, lengkap dengan status aktif dan shortcut-nya. EDT.
     */
    private void showTabMenu(int index, int x, int y) {
        tabs.setSelectedIndex(index);
        updateTerminalMenu();
        var group = groupAt(index);
        int count = tabs.getTabCount();
        var profile = currentSsh().flatMap(t -> store.snapshot().find(t.profile().id())); // profil terbaru; tab SSH saja
        var menu = new javax.swing.JPopupMenu();
        menu.add(popupItem(miDuplicate));
        menu.add(popupItem(miReconnect));
        menu.add(popupItem(miSftp));
        menu.add(menuItem(null, I18n.t("main.tabMenu.rename"), null, () -> renameTab(group)));
        menu.add(menuItem(null, I18n.t("main.menu.terminal.detach"), null,
                () -> detachTab(tabs.indexOfComponent(group), null)));
        menu.addSeparator();
        menu.add(menuItem(null, I18n.t(marked.contains(group) ? "main.tabMenu.unmark" : "main.tabMenu.mark"), null,
                () -> toggleMark(group)));
        menu.add(popupItem(miGroup));
        menu.add(popupItem(miUngroup));
        menu.addSeparator();
        menu.add(popupItem(miInjectSudo));
        menu.add(popupItem(miInjectRoot));
        menu.addSeparator();
        var edit = menuItem(null, I18n.t("main.tabMenu.editHost"), null, () -> profile.ifPresent(this::edit));
        edit.setEnabled(profile.isPresent());
        menu.add(edit);
        var reveal = menuItem(null, I18n.t("main.tabMenu.reveal"), null, () -> profile.ifPresent(p -> {
            showHostList();
            SwingUtilities.invokeLater(() -> hostTree.reveal(p.id())); // laci/panel baru terlihat setelah layout
        }));
        reveal.setEnabled(profile.isPresent());
        menu.add(reveal);
        menu.addSeparator();
        menu.add(popupItem(miCloseTab));
        var others = menuItem(null, I18n.t("main.tabMenu.closeOthers"), null, () -> closeTabs(
                java.util.stream.IntStream.range(0, tabs.getTabCount()).filter(i -> i != tabs.indexOfComponent(group))
                        .boxed().toList(), I18n.t("main.tabMenu.closeOthers")));
        others.setEnabled(count > 1);
        menu.add(others);
        var right = menuItem(null, I18n.t("main.tabMenu.closeRight"), null, () -> closeTabs(
                java.util.stream.IntStream.range(tabs.indexOfComponent(group) + 1, tabs.getTabCount())
                        .boxed().toList(), I18n.t("main.tabMenu.closeRight")));
        right.setEnabled(index < count - 1);
        menu.add(right);
        menu.show(tabs, x, y);
    }

    /** Salinan item menu Terminal untuk menu tab: teks, shortcut, dan status aktif sama, aksinya item aslinya. */
    private static JMenuItem popupItem(JMenuItem source) {
        var item = new JMenuItem(source.getText(), source.getIcon());
        item.setAccelerator(source.getAccelerator());
        item.setToolTipText(source.getToolTipText());
        item.setEnabled(source.isEnabled());
        item.addActionListener(e -> source.doClick(0));
        return item;
    }

    /** Ganti nama tab; nama kosong = kembali ke nama host. Hanya selama aplikasi berjalan. EDT. */
    private void renameTab(SplitPanes<TermPane> group) {
        String current = customTitles.getOrDefault(group, group.panes().stream()
                .map(TermPane::displayName).collect(Collectors.joining(" | ")));
        Object input = JOptionPane.showInputDialog(this, I18n.t("main.tabMenu.rename.prompt"),
                I18n.t("main.tabMenu.rename.title"), JOptionPane.PLAIN_MESSAGE, null, null, current);
        if (input != null) {
            String name = input.toString().strip();
            if (name.isEmpty()) {
                customTitles.remove(group);
            } else {
                customTitles.put(group, name);
            }
            refreshTitles();
        }
        currentTab().ifPresent(TermPane::focusTerminal);
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
            Dialogs.info(this, I18n.t("main.splitGroup.title"), splitGroupHint());
            return;
        }
        var indexes = marked.stream().mapToInt(tabs::indexOfComponent).sorted().toArray();
        var panes = new ArrayList<TermPane>();
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
        var group = new SplitPanes<>(TermPane.class, panes, splitOrientation());
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
        SplitPanes<TermPane> selected = null;
        for (int k = 0; k < panes.size(); k++) {
            var single = new SplitPanes<>(TermPane.class, panes.get(k));
            insertGroupTab(single, index + k);
            if (panes.get(k) == focus) {
                selected = single;
            }
        }
        tabs.setSelectedComponent(selected);
        SwingUtilities.invokeLater(focus::focusTerminal);
    }

    /** Pasang tab (tunggal atau grup) di posisi {@code index} dan pilih tab itu. */
    private void insertGroupTab(SplitPanes<TermPane> group, int index) {
        group.setActiveListener(this::updateTerminalMenu);
        tabs.insertTab(tabTitle(group), tabIcon(group, osIcon(group.panes().getFirst())), group, tabTooltip(group), index);
        tabs.setSelectedIndex(index);
        updateCenter();
        updateTerminalMenu();
    }

    /** Tutup panel split yang aktif; panel terakhir = tutup tab. */
    private void closePane() {
        var current = currentGroup();
        if (current.isEmpty()) {
            return;
        }
        var group = current.get();
        if (group.panes().size() < 2) {
            closeCurrentTab();
            return;
        }
        var pane = group.active();
        if (!confirmClose(List.of(pane), I18n.t("main.menu.terminal.closePane"), () -> { })) {
            return;
        }
        lastProfilesOf(List.of(pane)).forEach(id -> ctx.edits().closeProfile(id));
        pane.dispose();
        group.removePane(pane);
        refreshTitles(); // termasuk judul window terpisah
        updateTerminalMenu();
        group.active().focusTerminal();
    }

    /** Ringkasan hal yang masih aktif (untuk konfirmasi keluar). Dipanggil di EDT, tanpa I/O. */
    private List<String> activeWork() {
        var items = new ArrayList<String>();
        for (var tab : allPanes()) {
            if (tab instanceof LocalTerminalTab local && tab.isSessionActive()) {
                items.add(I18n.t("main.active.local", tab.displayName(), String.join(", ", local.runningCommands())));
            } else if (tab.isSessionActive()) {
                items.add(I18n.t("main.active.session", tab.displayName(), tab.displayAddress()));
            }
            if (tab.activeTransfers() > 0) {
                items.add(I18n.t("main.active.transfers", tab.displayName(), String.valueOf(tab.activeTransfers())));
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
            if (isTabActive(i)) {
                return i;
            }
        }
        return -1;
    }

    /** Tab ke-{@code index} punya sesi atau transfer yang masih berjalan; false untuk indeks di luar jangkauan. */
    private boolean isTabActive(int index) {
        return index >= 0 && index < tabs.getTabCount()
                && groupAt(index).panes().stream().anyMatch(tab -> tab.isSessionActive() || tab.activeTransfers() > 0);
    }

    /**
     * Judul tab: nama host (titik warna environment), digabung "|" untuk grup, tanda centang kalau dipilih. Nama dari
     * user (Ganti nama tab) menggantikan nama host, dengan titik warna panel pertama.
     */
    private String tabTitle(SplitPanes<TermPane> group) {
        var panes = group.panes();
        boolean isMarked = marked.contains(group);
        String custom = customTitles.get(group);
        if (custom != null) {
            Color color = panes.getFirst().environmentColor();
            return "<html>" + (isMarked ? "<b>&#10004;</b> " : "")
                    + (color == null ? "" : "<font color='#%06x'>&#9679;</font> ".formatted(color.getRGB() & 0xFFFFFF))
                    + custom.replace("&", "&amp;").replace("<", "&lt;") + "</html>";
        }
        var parts = new ArrayList<String>();
        boolean html = isMarked || panes.size() > 1;
        for (var pane : panes) {
            String name = pane.displayName().replace("&", "&amp;").replace("<", "&lt;");
            Color color = pane.environmentColor();
            html |= color != null;
            parts.add(color == null ? name
                    : "<font color='#%06x'>&#9679;</font> %s".formatted(color.getRGB() & 0xFFFFFF, name));
        }
        if (!html) {
            return panes.getFirst().displayName();
        }
        return "<html>" + (isMarked ? "<b>&#10004;</b> " : "") + String.join(" &nbsp;|&nbsp; ", parts) + "</html>";
    }

    private static String tabTooltip(SplitPanes<TermPane> group) {
        return group.panes().stream().map(TermPane::displayAddress).collect(Collectors.joining(" | "));
    }

    /** Ikon tab: logo OS host panel pertama + ikon terminal/SFTP yang sedang dipakai panel-panel di tab itu. */
    private static TabIcon tabIcon(SplitPanes<TermPane> group, javax.swing.Icon os) {
        var panes = group.panes();
        return new TabIcon(os, panes.stream().anyMatch(TermPane::hasTerminal), panes.stream().anyMatch(TermPane::showsSftp));
    }

    /** Logo OS untuk ikon tab: OS host SSH (hasil deteksi tersimpan di profil), ikon terminal untuk tab lokal. */
    private static javax.swing.Icon osIcon(TermPane pane) {
        return switch (pane) {
            case TerminalTab t -> OsIcons.forHost(t.profile());
            case LocalTerminalTab l -> OsIcons.local();
        };
    }

    /** Perbarui ikon terminal/SFTP semua tab (logo OS yang sudah tampil dipertahankan). EDT. */
    private void refreshTabIcons() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            var group = groupAt(i);
            var os = tabs.getIconAt(i) instanceof TabIcon current ? current.os()
                    : osIcon(group.panes().getFirst());
            var icon = tabIcon(group, os);
            if (!icon.equals(tabs.getIconAt(i))) {
                tabs.setIconAt(i, icon);
            }
        }
    }

    /** Perbarui judul semua tab (tanda pilih, isi grup). EDT. */
    private void refreshTitles() {
        customTitles.keySet().removeIf(c -> tabs.indexOfComponent(c) < 0); // tab yang dilebur/dipisah/ditutup
        for (int i = 0; i < tabs.getTabCount(); i++) {
            tabs.setTitleAt(i, tabTitle(groupAt(i)));
            tabs.setToolTipTextAt(i, tabTooltip(groupAt(i)));
        }
        detached.forEach(DetachedTabWindow::refreshTitle);
        refreshTabIcons();
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
                detached.forEach(w -> w.group().setOrientation(mode.orientation()));
                mutate(I18n.t("error.saveSettings"), () ->
                        ctx.config().save(ctx.config().current().withSplitMode(mode.id())));
            });
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    /** @param icon null = tanpa ikon (ikon hanya untuk item utama: host/grup baru, Tentang) */
    /** Item menu untuk aksi yang shortcut-nya bisa diganti user; shortcut-nya diperbarui oleh {@link #applyShortcuts}. */
    private JMenuItem actionItem(AppIcon icon, String label, ShortcutAction shortcut, Runnable action) {
        var item = menuItem(icon, label, Shortcuts.get(shortcut), action);
        actionItems.put(shortcut, item);
        return item;
    }

    /** Pasang shortcut baru ke menu dan teks bantuan, langsung tanpa restart. EDT. */
    private void applyShortcuts(KeyBindings bindings) {
        Shortcuts.use(bindings);
        actionItems.forEach((action, item) -> item.setAccelerator(Shortcuts.get(action)));
        refreshShortcutTexts();
        if (newHostFab != null) {
            newHostFab.setToolTipText(I18n.t("fab.newHost", Shortcuts.text(ShortcutAction.NEW_HOST)));
        }
        home.setHint(welcomeHint());
    }

    private void refreshShortcutTexts() {
        miNextTab.setToolTipText(I18n.t("main.menu.terminal.tabNumber.tooltip",
                Shortcuts.text(ShortcutAction.TAB_1), Shortcuts.text(ShortcutAction.TAB_9)));
        miPreviousTab.setToolTipText(miNextTab.getToolTipText());
        miGroup.setToolTipText(splitGroupHint());
    }

    /** Pengaturan → Shortcut…: draft di dialog, disimpan di executor I/O, lalu diterapkan lewat listener store. */
    private void configureShortcuts() {
        new ShortcutsDialog(this, Shortcuts.bindings(), overrides -> UiAsync.run(io,
                () -> ctx.shortcuts().save(overrides),
                err -> Dialogs.error(this, I18n.t("shortcut.title"), err))).setVisible(true);
    }

    private static JMenuItem menuItem(AppIcon icon, String label, KeyStroke key, Runnable action) {
        var item = icon == null ? new JMenuItem(label) : new JMenuItem(label, icon.icon());
        if (key != null) {
            item.setAccelerator(key);
        }
        item.addActionListener(e -> action.run());
        return item;
    }

    private void updateCenter() {
        java.awt.Component wanted = tabs.getTabCount() == 0 ? homeView : tabs;
        if (center.getComponentCount() == 0 || center.getComponent(0) != wanted) {
            if (wanted == homeView) {
                home.reshuffle(); // animasi acak: berganti setiap beranda tampil lagi
                // UI tab masih memegang panel tab terakhir (visibleComponent) sampai layout berikutnya, padahal tabs
                // dilepas dari layar: layout sekarang supaya tab yang sudah ditutup bisa di-GC.
                tabs.doLayout();
            }
            center.removeAll();
            center.add(wanted, BorderLayout.CENTER);
            center.revalidate();
            center.repaint();
            syncHostToggles(); // baris tab muncul/hilang: ikon panel host di tab bar ikut
        }
    }

    /**
     * Keluar aplikasi: selalu konfirmasi; kalau masih ada sesi/transfer/edit, tampilkan daftarnya. Juga dipanggil
     * Quit/Cmd+Q di macOS ({@link MacIntegration}). EDT.
     */
    void exit() {
        if (confirmQuit(I18n.t("exit.title"), I18n.t("exit.confirm"))) {
            closeAndExit();
        }
    }

    /**
     * Tutup TermUL lalu buka ulang dengan perintah {@code command} (setelah update terpasang). Proses baru menunggu
     * proses ini selesai (argumen {@code --wait-pid}). EDT.
     */
    private void restartForUpdate(List<String> command) {
        if (!confirmQuit(I18n.t("update.restart.title"), I18n.t("update.restart.confirm"))) {
            return;
        }
        try {
            new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            Dialogs.error(this, I18n.t("update.restart.failed"), e);
            return;
        }
        closeAndExit();
    }

    /**
     * Konfirmasi menutup aplikasi; kalau masih ada sesi/edit aktif, daftarnya ditampilkan. Ditolak = tetap di tab yang
     * sedang dibuka kalau tab itu aktif, selain itu tab aktif pertama (atau daftar edit) ditampilkan. EDT.
     */
    private boolean confirmQuit(String title, String idleMessage) {
        List<String> active = activeWork();
        String message;
        if (active.isEmpty()) {
            message = idleMessage;
        } else {
            var sb = new StringBuilder(I18n.t("exit.activeHeader")).append("\n\n");
            active.forEach(a -> sb.append("  • ").append(a).append('\n'));
            sb.append('\n').append(I18n.t("exit.activeFooter"));
            message = sb.toString();
        }
        int choice = JOptionPane.showConfirmDialog(this, message, title, JOptionPane.YES_NO_OPTION,
                active.isEmpty() ? JOptionPane.QUESTION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            int first = firstActiveTab();
            if (first >= 0 && isTabActive(tabs.getSelectedIndex())) {
                return false; // tab yang sedang dipakai sudah menunjukkan sesi aktif: jangan dipindah
            }
            var activeWindow = detached.stream().filter(w -> w.group().panes().stream()
                    .anyMatch(t -> t.isSessionActive() || t.activeTransfers() > 0)).findFirst();
            if (first >= 0) {
                tabs.setSelectedIndex(first);
            } else if (activeWindow.isPresent()) {
                activeWindow.get().toFront();
            } else if (!ctx.edits().entries().isEmpty()) {
                showEditTracker();
            }
            return false;
        }
        return true;
    }

    private void closeAndExit() {
        while (tabs.getTabCount() > 0) {
            removeTab(0, false);
        }
        for (var w : List.copyOf(detached)) {
            disposeDetached(w, false);
        }
        updateNotifier.close();
        dispose();
        onExit.run();
    }

    /** Mulai pemeriksaan update di background (badge); dipanggil setelah window tampil. EDT. */
    public void startUpdateChecks() {
        updateNotifier.start();
    }

    /** Hitung streak hari ini dan putar animasinya; panggil setelah window tampil. */
    public void startStreak() {
        streak.start();
    }

    private void openUpdateDialog() {
        openUpdateDialog(false);
    }

    /** @param prompted dibuka otomatis oleh {@link UpdatePrompter}, bukan dari menu/badge */
    private void openUpdateDialog(boolean prompted) {
        if (updateDialog != null && updateDialog.isShowing()) {
            updateDialog.toFront();
            return;
        }
        updateDialog = new UpdateDialog(this, ctx.paths(), updateNotifier, this::restartForUpdate, prompted);
        try {
            updateDialog.setVisible(true); // modal: kembali setelah ditutup
        } finally {
            updateDialog = null;
        }
    }

    /** @return true kalau ada dialog modal yang sedang tampil (jangan tumpuk modal update di atasnya). EDT. */
    private static boolean modalDialogShowing() {
        for (Window w : Window.getWindows()) {
            if (w instanceof java.awt.Dialog d && d.isModal() && d.isShowing()) {
                return true;
            }
        }
        return false;
    }

    /** Bantuan → Yang baru: riwayat dibaca dari disk di luar EDT. */
    private void openWhatsNew() {
        if (whatsNew != null && whatsNew.isDisplayable()) {
            whatsNew.toFront();
            return;
        }
        UiAsync.run(io, () -> AppUpdates.history(ctx.paths()).load(), history -> {
            if (whatsNew != null && whatsNew.isDisplayable()) {
                whatsNew.toFront();
                return;
            }
            whatsNew = new WhatsNewDialog(this, history, ReleaseVersion.parseOrNull(BuildInfo.load().version()));
            whatsNew.setVisible(true);
        }, err -> Dialogs.error(this, I18n.t("whatsNew.error"), err));
    }

    /** Badge update di menu bar dan titik di menu Bantuan → Periksa update. EDT. */
    private void showUpdateState(UpdateNotifier.State state) {
        switch (state) {
            case UpdateNotifier.Idle _ -> updateBadge.setVisible(false);
            case UpdateNotifier.Available(var version) -> {
                updateBadge.setText(I18n.t("update.badge.available", version.toString()));
                updateBadge.setToolTipText(I18n.t("update.badge.available.tooltip"));
                updateBadge.setVisible(true);
                if (!isShowing() && !version.equals(trayNotifiedVersion)) {
                    trayNotifiedVersion = version; // modal update menunggu window dibuka; beri tahu lewat tray
                    tray.notify("TermUL", I18n.t("tray.updateAvailable", version.toString()));
                }
            }
            case UpdateNotifier.Installed(var version) -> {
                updateBadge.setText(I18n.t("update.badge.restart"));
                updateBadge.setToolTipText(I18n.t("update.badge.restart.tooltip", version.toString()));
                updateBadge.setVisible(true);
            }
        }
        if (updateBadge.isVisible()) {
            badgePulse.start();
        } else {
            badgePulse.stop();
        }
        if (checkUpdateItem != null) {
            checkUpdateItem.setIcon(state instanceof UpdateNotifier.Available ? new BadgeDotIcon() : null);
        }
        getJMenuBar().revalidate();
        getJMenuBar().repaint();
        updatePrompter.onState(state);
    }

    /** Klik badge: buka dialog update, atau (update sudah terpasang) restart dengan konfirmasi. EDT. */
    private void onUpdateBadge() {
        if (updateNotifier.state() instanceof UpdateNotifier.Installed(var version)) {
            RestartCommand.current().ifPresentOrElse(this::restartForUpdate, () -> Dialogs.info(this,
                    I18n.t("update.title"), I18n.t("update.restartManual", version.toString())));
        } else {
            openUpdateDialog();
        }
    }

    /** Terminal → Snippet manager: snippet ditempel ke terminal tab yang aktif (kalau ada). EDT. */
    private void openSnippets() {
        var target = currentTab().filter(TermPane::hasTerminal).map(TermPane::snippetTarget).orElse(null);
        new dev.egateza.termul.app.snippet.SnippetManagerDialog(this, snippetHost.sender(), io, target,
                store.snapshot().allGroups()).setVisible(true);
        currentTab().ifPresent(TermPane::focusTerminal);
    }

    /** Simpan pengaturan tombol snippet, lalu terapkan ke semua tab. EDT. */
    private void saveSnippetSettings(dev.egateza.termul.core.config.AppConfig next) {
        UiAsync.run(io, () -> {
            ctx.config().save(next);
            return next;
        }, saved -> {
            snippetToggle.setSelected(saved.snippetButton());
            allPanes().forEach(TermPane::refreshSnippetButton);
        }, err -> Dialogs.error(this, I18n.t("error.saveSettings"), err));
    }

    private void saveSftpLayout(boolean dual) {
        mutate(I18n.t("error.saveSettings"), () -> ctx.config().save(ctx.config().current()
                .withSftpLayout(dual ? AppConfig.SFTP_DUAL : AppConfig.SFTP_SINGLE)));
    }

    /** Tampilkan/sembunyikan widget streak mengambang; dari menu Pengaturan, menu tray, atau menu widget. EDT. */
    private void setStreakWidget(boolean on) {
        streakWidgetToggle.setSelected(on);
        tray.setStreakWidgetShown(on);
        if (on) {
            var config = ctx.config().current().streakWidget();
            var saved = config.hasPosition() ? new java.awt.Point(config.x(), config.y()) : null;
            streak.showWidget(saved, new StreakController.WidgetHost() {
                @Override
                public void openApp() {
                    showWindow();
                }

                @Override
                public void hideRequested() {
                    setStreakWidget(false);
                }

                @Override
                public void checkNetwork() {
                    bottomBar.network().checkNow(); // hasilnya kembali ke widget lewat listener status
                }

                @Override
                public void moved(java.awt.Point location) {
                    mutate(I18n.t("error.saveSettings"), () -> {
                        var current = ctx.config().current();
                        ctx.config().save(current.withStreakWidget(
                                current.streakWidget().withPosition(location.x, location.y)));
                    });
                }
            });
        } else {
            streak.hideWidget();
        }
        if (ctx.config().current().streakWidget().visible() != on) {
            mutate(I18n.t("error.saveSettings"), () -> {
                var current = ctx.config().current();
                ctx.config().save(current.withStreakWidget(current.streakWidget().withVisible(on)));
            });
        }
    }

    private void setStreakVisible(boolean on) {
        streak.setEnabled(on);
        mutate(I18n.t("error.saveSettings"), () -> ctx.config().save(ctx.config().current().withStreak(on)));
    }

    private void setAutoUpdate(boolean on) {
        if (!on) {
            updateNotifier.disabled();
        }
        mutate(I18n.t("error.saveSettings"), () -> {
            ctx.config().save(ctx.config().current().withAutoUpdateCheck(on));
            if (on) {
                updateNotifier.checkSoon(); // setelah tersimpan, supaya pemeriksaan membaca status terbaru
            }
        });
    }

    /** Nyalakan/matikan manajemen WSL; saat dinyalakan, WSL Manager langsung dibuka. */
    private void setWslManager(boolean on) {
        UiAsync.run(io, () -> {
            ctx.config().save(ctx.config().current().withWslManager(on));
            return on;
        }, saved -> {
            if (miWslToggle != null) {
                miWslToggle.setSelected(on);
            }
            wsl.apply();
            if (on) {
                wsl.openManager();
            }
        }, err -> Dialogs.error(this, I18n.t("error.saveSettings"), err));
    }

    /** Menjalankan mutasi store di thread I/O; error ditampilkan di EDT. */
    private void mutate(String errorTitle, Runnable change) {
        UiAsync.run(io, change, err -> Dialogs.error(this, errorTitle, err));
    }

    /** Membuka lebih dari ini sekaligus (multi-seleksi tree) perlu konfirmasi. */
    private static final int OPEN_ALL_CONFIRM_ABOVE = 5;

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
        sftpOnly |= profile.isFtp(); // FTP tidak punya shell: selalu tab file saja
        drawer.setOpen(false); // mode tombol melayang: beri ruang penuh untuk terminal
        mutate(I18n.t("main.error.recent"), () -> store.markUsed(profile.id()));
        var tab = createPane(profile, sftpOnly);
        insertGroupTab(new SplitPanes<>(TermPane.class, tab), tabs.getTabCount());
        if (sftpOnly && profile.wslDistro() != null && wsl.enabled()) {
            // tab "SFTP saja" tidak lewat hook connect terminal: siapkan distro WSL dulu
            UiAsync.run(ctx.sshOps(), () -> {
                try {
                    wsl.prepare(profile, false);
                    return null;
                } catch (dev.egateza.termul.ssh.SshConnectException e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }, ignored -> tab.connect(), err -> {
                Dialogs.error(this, I18n.t("wsl.error.title"), err);
                tab.connect();
            });
        } else {
            tab.connect();
        }
    }

    /** Popup Tab baru: pilih host atau terminal lokal tanpa membuka sidebar. EDT. */
    private void showQuickOpen() {
        QuickOpenPopup.show(this, store.snapshot(), wsl.enabled(), localChoices(), new QuickOpenPopup.Actions() {
            @Override
            public void openLocalProfile(java.util.UUID id) {
                MainFrame.this.openLocalProfile(id);
            }

            @Override
            public void open(HostProfile profile) {
                MainFrame.this.open(profile);
            }

            @Override
            public void openSftp(HostProfile profile) {
                MainFrame.this.openSftp(profile);
            }

            @Override
            public void cancelled() {
                currentTab().ifPresent(TermPane::focusTerminal);
            }
        });
    }

    /** Pilihan terminal lokal (shell terdeteksi + profil, default dulu); tanpa I/O. EDT. */
    private List<dev.egateza.termul.app.terminal.LocalProfiles.Choice> localChoices() {
        return ctx.locals().choices(wsl == null ? List.of() : wsl.installedNames());
    }

    private void refreshLocalChoices() {
        hostTree.setLocalChoices(localChoices());
        syncExplorerMenu();
    }

    /** Tab terminal lokal baru dengan profil default (ADR 0005). EDT. */
    @Override
    public void openLocalTerminal() {
        localChoices().stream().findFirst().ifPresent(this::openLocal);
    }

    /** Tab terminal lokal dengan profil/shell tertentu; profil yang sudah dihapus = default. EDT. */
    @Override
    public void openLocalProfile(java.util.UUID id) {
        var choices = localChoices();
        choices.stream().filter(c -> c.id().equals(id)).findFirst().or(() -> choices.stream().findFirst())
                .ifPresent(this::openLocal);
    }

    /**
     * Tab terminal lokal yang dimulai di {@code directory} (menu klik kanan Explorer). Shell yang sudah tidak ada =
     * default. EDT.
     */
    public void openLocalIn(java.util.UUID shell, java.nio.file.Path directory) {
        showWindow();
        var choices = localChoices();
        choices.stream().filter(c -> c.id().equals(shell)).findFirst().or(() -> choices.stream().findFirst())
                .map(c -> startingIn(c, directory))
                .ifPresent(this::openLocal);
    }

    /**
     * Salinan pilihan dengan folder awal lain. {@code CHERE_INVOKING} mencegah login shell MSYS (Git Bash) pindah ke
     * home; shell lain mengabaikannya.
     */
    private static dev.egateza.termul.app.terminal.LocalProfiles.Choice startingIn(
            dev.egateza.termul.app.terminal.LocalProfiles.Choice c, java.nio.file.Path directory) {
        var env = new java.util.LinkedHashMap<>(c.environment());
        env.putIfAbsent("CHERE_INVOKING", "1");
        return new dev.egateza.termul.app.terminal.LocalProfiles.Choice(c.id(), c.name(), c.command(),
                directory.toString(), java.util.Map.copyOf(env), c.terminalTheme(), c.detected(), c.isDefault());
    }

    @Override
    public void setDefaultLocalProfile(java.util.UUID id) {
        mutate(I18n.t("localProfiles.saveFailed", ""), () -> ctx.locals().store().setDefault(id));
    }

    /** Sembunyikan profil/shell dari daftar terminal lokal (aktifkan lagi lewat Profil terminal lokal). EDT. */
    @Override
    public void disableLocalProfile(java.util.UUID id) {
        var choices = localChoices();
        if (choices.stream().noneMatch(c -> !c.id().equals(id))) {
            Dialogs.info(this, I18n.t("localProfiles.title"), I18n.t("localProfiles.lastEnabled"));
            return;
        }
        mutate(I18n.t("localProfiles.saveFailed", ""), () -> ctx.locals().store().setDisabled(id, true));
    }

    @Override
    public void manageLocalProfiles() {
        new LocalProfilesDialog(this, ctx.locals().store(),
                () -> ctx.locals().allChoices(wsl == null ? List.of() : wsl.installedNames()),
                HostTerminalColors.choices(customThemes), io).setVisible(true);
    }

    private void openLocal(dev.egateza.termul.app.terminal.LocalProfiles.Choice choice) {
        drawer.setOpen(false); // mode tombol melayang: beri ruang penuh untuk terminal
        var tab = new LocalTerminalTab(choice, localTerminals, io, ctx.terminalSettings());
        tab.setPalettes(id -> HostTerminalColors.palette(id, customThemes));
        tab.setStateListener(() -> {
            updateTerminalMenu();
            refreshTabIcons();
        });
        tab.setSnippetHost(snippetHost);
        applyHighlight(tab);
        insertGroupTab(new SplitPanes<>(TermPane.class, tab), tabs.getTabCount());
        tab.start();
    }

    private dev.egateza.termul.core.highlight.HighlightSettings highlightSettings; // EDT
    /** Profil highlight yang sudah dikompilasi, per id profil; dikosongkan saat pengaturan berubah. EDT. */
    private final java.util.Map<String, dev.egateza.termul.app.terminal.TextHighlighter.Rules> highlightRules =
            new java.util.HashMap<>();

    private void highlightChanged(dev.egateza.termul.core.highlight.HighlightSettings settings) {
        highlightSettings = settings;
        highlightRules.clear();
        allPanes().forEach(this::applyHighlight);
    }

    /** Pilih profil highlight untuk panel: host/profil lokal → grup/semua lokal → global. EDT. */
    private void applyHighlight(TermPane pane) {
        var s = highlightSettings;
        var profile = switch (pane) {
            case TerminalTab t -> s.forHost(t.profile().id(), t.latestHostProfile().group());
            case LocalTerminalTab l -> s.forLocal(l.profileId());
        };
        pane.setHighlight(profile.map(p -> highlightRules.computeIfAbsent(p.id(), id ->
                dev.egateza.termul.app.terminal.TextHighlighter.Rules.of(
                        dev.egateza.termul.core.highlight.HighlightMatcher.compile(p), s.alternateDisable())))
                .orElse(null));
    }

    private void configureHighlight() {
        new dev.egateza.termul.app.ui.highlight.HighlightDialog(this, ctx.highlights(), store::snapshot,
                this::localChoices, ctx.terminalSettings(), io).setVisible(true);
    }

    /** Panel terminal baru (belum connect) untuk tab baru atau split. */
    private TerminalTab createPane(HostProfile profile, boolean sftpOnly) {
        var tab = new TerminalTab(profile, ctx.terminals(), ctx.sshOps(), ctx.terminalSettings(), ctx.sftpLinks(),
                sftpOnly, p -> detectOsOnConnect(p, new SftpPanel(p, ctx.sftpLinks(), ctx.sshOps(), new SftpPanel.EditActions() {
                    @Override
                    public void edit(HostProfile profile, String remotePath, String command) {
                        ctx.edits().open(profile, remotePath, command);
                    }

                    @Override
                    public void view(HostProfile profile, String remotePath) {
                        ctx.edits().view(profile, remotePath);
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
                }, fileIcons, sftpBookmarks, AppConfig.SFTP_DUAL.equals(ctx.config().current().sftpLayout()), on -> {
                    sftpDualToggle.setSelected(on); // tombol toolbar panel juga mengganti default panel berikutnya
                    saveSftpLayout(on);
                })));
        tab.setConnectedListener(tty -> detectOs(profile.id(), tty));
        tab.setBeforeConnect(wsl::prepare);
        tab.setStateListener(() -> {
            updateTerminalMenu();
            refreshTabIcons(); // panel SFTP dibuka/ditutup
        });
        tab.setCurrentProfile(() -> store.snapshot().find(profile.id()).orElse(null));
        tab.setAutoSudoVault(ctx.vault());
        tab.setSecretInjector(type -> tab.injectSecret(type, ctx.vault())); // tombol toolbar = hotkey Ctrl+Shift+P
        tab.setEditActivity(sink -> ctx.edits().addActivityListener(profile.id(), sink), this::showEditTracker);
        tab.setSnippetHost(snippetHost);
        tab.setFollowHost(followHost);
        tab.setHostPalette(id -> HostTerminalColors.palette(id, customThemes));
        applyHighlight(tab);
        return tab;
    }

    /** "Ikuti folder terminal" (tombol di panel SFTP): satu pengaturan untuk semua tab, disimpan di config.json. */
    private final TerminalTab.FollowHost followHost = new TerminalTab.FollowHost() {
        @Override
        public boolean following() {
            return ctx.config().current().sftpFollowTerminal();
        }

        @Override
        public void setFollowing(boolean on) {
            sshPanes().forEach(t -> t.applyFollowing(on));
            mutate(I18n.t("error.saveSettings"),
                    () -> ctx.config().save(ctx.config().current().withSftpFollowTerminal(on)));
        }
    };

    @Override
    public void newHost(String group) {
        ProfileDialog.create(this, group, store.snapshot(), HostTerminalColors.choices(customThemes), wsl.installedNames())
                .ifPresent(this::saveProfile);
    }

    @Override
    public void edit(HostProfile profile) {
        // baca metadata vault (disk I/O) di luar EDT, lalu buka dialog
        UiAsync.run(io, () -> storedSecrets(profile), stored ->
                        ProfileDialog.edit(this, profile, store.snapshot(), stored, HostTerminalColors.choices(customThemes),
                                wsl.installedNames()).ifPresent(this::saveProfile),
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
    public void forgetHostKey(HostProfile profile) {
        KnownHostsStore knownHosts = ctx.knownHosts();
        String label = KnownHostsStore.hostPattern(profile.host(), profile.port());
        UiAsync.run(io, () -> knownHosts.entriesFor(profile.host(), profile.port()), entries -> {
            if (entries.isEmpty()) {
                Dialogs.info(this, I18n.t("knownHosts.remove.title"), I18n.t("knownHosts.forget.none", profile.name(), label));
                return;
            }
            if (!KnownHostsDialog.confirmRemove(this, entries)) {
                return;
            }
            UiAsync.run(io, () -> knownHosts.remove(entries),
                    removed -> Dialogs.info(this, I18n.t("knownHosts.remove.title"), I18n.t("knownHosts.forget.done", label)),
                    err -> Dialogs.error(this, I18n.t("knownHosts.error.write"), err));
        }, err -> Dialogs.error(this, I18n.t("knownHosts.error.read"), err));
    }

    @Override
    public void forgetFtpsCert(HostProfile profile) {
        var certs = ctx.ftpsCerts();
        String label = profile.host() + ":" + profile.port();
        UiAsync.run(io, () -> certs.find(profile.host(), profile.port()), known -> {
            if (known.isEmpty()) {
                Dialogs.info(this, I18n.t("ftps.forget.title"), I18n.t("ftps.forget.none", profile.name(), label));
                return;
            }
            if (!Dialogs.confirm(this, I18n.t("ftps.forget.title"), I18n.t("ftps.forget.confirm", label, known.get()))) {
                return;
            }
            UiAsync.run(io, () -> certs.remove(profile.host(), profile.port()),
                    removed -> Dialogs.info(this, I18n.t("ftps.forget.title"), I18n.t("ftps.forget.done", label)),
                    err -> Dialogs.error(this, I18n.t("ftps.forget.error"), err));
        }, err -> Dialogs.error(this, I18n.t("ftps.forget.error"), err));
    }

    @Override
    public void duplicate(HostProfile profile) {
        mutate(I18n.t("main.error.duplicateProfile"), () -> store.save(profile.duplicate()));
    }

    @Override
    public void delete(HostProfile profile) {
        if (Dialogs.confirm(this, I18n.t("main.profile.delete.title"), I18n.t("main.profile.delete.confirm", profile.name()))) {
            mutate(I18n.t("main.error.deleteProfile"), () -> pruneBookmarks(store.delete(profile.id())));
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
            mutate(I18n.t("main.error.deleteGroup"), () -> pruneBookmarks(store.deleteGroup(group)));
        }
    }

    /** Bookmark folder milik profil yang sudah dihapus ikut dibuang (thread I/O). */
    private void pruneBookmarks(dev.egateza.termul.core.profile.ProfileSnapshot snapshot) {
        ctx.bookmarks().retainHosts(snapshot.profiles().stream().map(HostProfile::id)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Override
    public void setGroupSensitive(String group, Boolean sensitive) {
        var profiles = store.snapshot().profilesUnder(group);
        if (profiles.isEmpty()) {
            return;
        }
        String message = sensitive != null
                ? I18n.t("main.group.sensitive.markConfirm", profiles.size(), group)
                : I18n.t("main.group.sensitive.resetConfirm", profiles.size(), group);
        confirmSensitive(profiles, sensitive, message, () -> store.setGroupSensitive(group, sensitive));
    }

    @Override
    public void setSensitive(List<HostProfile> profiles, Boolean sensitive) {
        if (profiles.isEmpty()) {
            return;
        }
        String message = sensitive != null
                ? I18n.t("main.sensitive.markConfirm", profiles.size())
                : I18n.t("main.sensitive.resetConfirm", profiles.size());
        var ids = profiles.stream().map(HostProfile::id).toList();
        confirmSensitive(profiles, sensitive, message, () -> store.setSensitive(ids, sensitive));
    }

    /** Konfirmasi perubahan flag sensitif massal (plus peringatan auto-sudo), lalu simpan di thread io. */
    private void confirmSensitive(List<HostProfile> profiles, Boolean sensitive, String message, Runnable change) {
        if (Boolean.TRUE.equals(sensitive) && profiles.stream().anyMatch(HostProfile::autoSudo)) {
            message += "\n\n" + I18n.t("main.group.sensitive.autoSudo");
        }
        if (Dialogs.confirm(this, I18n.t("main.group.sensitive.title"), message)) {
            mutate(I18n.t("main.error.groupSensitive"), change);
        }
    }

    @Override
    public void openAll(List<HostProfile> profiles) {
        if (profiles.size() > OPEN_ALL_CONFIRM_ABOVE && !Dialogs.confirm(this, I18n.t("main.openAll.title"),
                I18n.t("main.openAll.confirm", profiles.size()))) {
            return;
        }
        profiles.forEach(this::open);
    }

    @Override
    public void setFavorite(HostProfile profile, boolean favorite) {
        mutate(I18n.t("main.error.favorite"), () -> store.setFavorite(profile.id(), favorite));
    }

    @Override
    public void clearRecent() {
        mutate(I18n.t("main.error.recent"), store::clearRecent);
    }

    @Override
    public void newWslProfile(String distro) {
        wsl.newProfile(distro);
    }

    /** Buka WSL Manager; kalau manajemen WSL masih mati, tawarkan untuk menyalakannya dulu. */
    @Override
    public void openWslManager() {
        if (wsl.enabled()) {
            wsl.openManager();
        } else if (Os.current().isWindows()
                && Dialogs.confirm(this, I18n.t("wsl.manager.title"), I18n.t("wsl.enable.confirm"))) {
            setWslManager(true); // membuka WSL Manager setelah pengaturan tersimpan
        }
    }

    @Override
    public void checkWslSsh(HostProfile profile) {
        wsl.checkProfile(profile);
    }

    /** Status buka/tutup grup panel host disimpan, supaya sama lagi setelah restart. */
    @Override
    public void collapsedGroupsChanged(java.util.Set<String> collapsed) {
        mutate(I18n.t("error.saveSettings"),
                () -> ctx.config().save(ctx.config().current().withCollapsedHostGroups(collapsed)));
    }
}

package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.AppContext;
import dev.egateza.termul.app.edit.EditTrackerDialog;
import dev.egateza.termul.app.edit.EditorSettingsDialog;
import dev.egateza.termul.app.log.LogBuffer;
import dev.egateza.termul.app.log.LogPanel;
import dev.egateza.termul.app.sftp.SftpPanel;
import dev.egateza.termul.app.terminal.TerminalTab;
import dev.egateza.termul.app.ui.tree.HostTreePanel;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.profile.OsInfo;
import dev.egateza.termul.core.profile.ProfileStore;
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
import javax.swing.BorderFactory;
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
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/** Window utama: host tree di kiri, tab terminal di kanan. */
public final class MainFrame extends JFrame implements HostTreePanel.Actions {

    private final AppContext ctx;
    private final ProfileStore store;
    private final ExecutorService io;
    private final HostTreePanel hostTree;
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final JPanel center = new JPanel(new BorderLayout());
    private final JLabel welcome = new JLabel(
            "<html><center><b>TermUL</b><br><br>Double-click host di kiri untuk membuka terminal.<br>"
                    + "Ctrl+N: host baru &nbsp; Ctrl+F: cari host</center></html>", SwingConstants.CENTER);
    private final Runnable onExit;
    private final KeyEventDispatcher hotkeys = this::dispatchHotkey;
    private final LogPanel logPanel;
    private final JSplitPane logSplit;
    private final JCheckBoxMenuItem showLog = new JCheckBoxMenuItem("Tampilkan log");
    private int logHeight = 220; // EDT
    private final JSplitPane hostSplit;
    private final JButton hostToggle = new JButton();
    private int hostWidth = 260; // EDT

    public MainFrame(AppContext ctx, Runnable onExit) {
        super("TermUL");
        this.ctx = ctx;
        this.store = ctx.profiles();
        this.io = ctx.io();
        this.onExit = onExit;
        this.hostTree = new HostTreePanel(this);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });

        tabs.putClientProperty("JTabbedPane.tabClosable", true);
        tabs.putClientProperty("JTabbedPane.tabCloseToolTipText", "Tutup tab");
        tabs.putClientProperty("JTabbedPane.tabCloseCallback",
                (BiConsumer<JTabbedPane, Integer>) (t, index) -> closeTab(index));
        tabs.addChangeListener(e -> {
            updateCenter();
            if (tabs.getSelectedComponent() instanceof TerminalTab tab) {
                SwingUtilities.invokeLater(tab::focusTerminal);
            }
        });
        welcome.setFont(welcome.getFont().deriveFont(Font.PLAIN, welcome.getFont().getSize2D() + 2));
        updateCenter();

        hostSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, hostTree, center);
        hostSplit.setDividerLocation(hostWidth);
        hostSplit.setContinuousLayout(true);
        hostTree.setMinimumSize(new Dimension(160, 100));
        // strip tipis di kiri dengan tombol laci; tetap terlihat saat panel host disembunyikan
        hostToggle.putClientProperty("JButton.buttonType", "toolBarButton");
        hostToggle.setFocusable(false);
        hostToggle.addActionListener(e -> setHostPanelVisible(!hostTree.isVisible()));
        updateHostToggle();
        var rail = new JPanel(new BorderLayout());
        rail.add(hostToggle, BorderLayout.NORTH);
        rail.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIManager.getColor("Component.borderColor")));
        var split = new JPanel(new BorderLayout());
        split.add(rail, BorderLayout.WEST);
        split.add(hostSplit, BorderLayout.CENTER);
        // panel log di bawah; disembunyikan dengan setVisible supaya tab terminal tidak di-reparent
        logPanel = new LogPanel(LogBuffer.global(), ctx.paths().logDir(), io, () -> setLogVisible(false));
        logPanel.setVisible(false);
        logPanel.setMinimumSize(new Dimension(100, 60));
        logSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, split, logPanel);
        logSplit.setResizeWeight(1.0);
        logSplit.setContinuousLayout(true);
        getContentPane().add(logSplit, BorderLayout.CENTER);
        setJMenuBar(buildMenu());

        setSize(1280, 800);
        setLocationRelativeTo(null);

        store.addListener(s -> SwingUtilities.invokeLater(() -> hostTree.setSnapshot(s)));
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(hotkeys);
    }

    /**
     * Shortcut aplikasi (Ctrl+Shift+…, Ctrl+F5) ditangkap sebelum JediTerm, supaya tetap jalan saat
     * fokus di terminal dan tidak ikut terkirim ke remote. Shortcut shell biasa (Ctrl+F, Ctrl+N, ...)
     * tidak disentuh.
     */
    private boolean dispatchHotkey(KeyEvent e) {
        var tab = currentTab();
        if (tab.isPresent() && e.getComponent() != null
                && SwingUtilities.isDescendingFrom(e.getComponent(), tab.get())
                && tab.get().interceptKey(e)) {
            e.consume();
            return true;
        }
        if (e.getID() != KeyEvent.KEY_PRESSED || !e.isControlDown() || e.isAltDown()) {
            return false;
        }
        boolean appCombo = e.isShiftDown() || e.getKeyCode() == KeyEvent.VK_F5;
        if (!appCombo || e.getComponent() == null || SwingUtilities.getWindowAncestor(e.getComponent()) != this) {
            return false;
        }
        JMenuItem item = findAccelerator(getJMenuBar(), KeyStroke.getKeyStrokeForEvent(e));
        if (item == null || !item.isEnabled()) {
            return false;
        }
        item.doClick(0);
        e.consume();
        return true;
    }

    private static JMenuItem findAccelerator(JMenuBar bar, KeyStroke ks) {
        for (int i = 0; i < bar.getMenuCount(); i++) {
            var menu = bar.getMenu(i);
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
        var file = new JMenu("File");
        file.add(menuItem(AppIcon.SERVER, "Host baru...", KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK),
                () -> newHost(hostTree.selectedGroup())));
        file.add(menuItem(AppIcon.FOLDER_PLUS, "Grup baru...", null, () -> newGroup(hostTree.selectedGroup())));
        file.add(menuItem(null, "Cari host", KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
                () -> {
                    setHostPanelVisible(true);
                    hostTree.focusSearch();
                }));
        file.addSeparator();
        file.add(menuItem(null, "Keluar", null, this::exit));
        bar.add(file);

        var terminal = new JMenu("Terminal");
        int ctrlShift = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
        terminal.add(menuItem(null, "Duplikat tab", KeyStroke.getKeyStroke(KeyEvent.VK_T, ctrlShift),
                () -> currentTab().ifPresent(t -> open(t.profile()))));
        terminal.add(menuItem(null, "Reconnect", KeyStroke.getKeyStroke(KeyEvent.VK_F5, InputEvent.CTRL_DOWN_MASK),
                () -> currentTab().ifPresent(TerminalTab::reconnect)));
        terminal.add(menuItem(null, "Tutup tab", KeyStroke.getKeyStroke(KeyEvent.VK_W, ctrlShift),
                () -> closeTab(tabs.getSelectedIndex())));
        terminal.add(menuItem(null, "Panel SFTP", KeyStroke.getKeyStroke(KeyEvent.VK_F, ctrlShift),
                () -> currentTab().ifPresent(TerminalTab::toggleSftp)));
        terminal.addSeparator();
        terminal.add(menuItem(null, "Zoom in", KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK),
                () -> currentTab().ifPresent(t -> t.zoom(1))));
        terminal.add(menuItem(null, "Zoom out", KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK),
                () -> currentTab().ifPresent(t -> t.zoom(-1))));
        terminal.add(menuItem(null, "Ukuran font default", KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK),
                () -> currentTab().ifPresent(t -> t.zoom(0))));
        terminal.addSeparator();
        terminal.add(menuItem(null, "File yang sedang diedit...", KeyStroke.getKeyStroke(KeyEvent.VK_E, ctrlShift),
                this::showEditTracker));
        terminal.addSeparator();
        terminal.add(menuItem(null, "Inject password sudo", KeyStroke.getKeyStroke(KeyEvent.VK_P, ctrlShift),
                () -> currentTab().ifPresent(t -> t.injectSecret(SecretType.SUDO_PASSWORD, ctx.vault()))));
        terminal.add(menuItem(null, "Inject password root", KeyStroke.getKeyStroke(KeyEvent.VK_R, ctrlShift),
                () -> currentTab().ifPresent(t -> t.injectSecret(SecretType.ROOT_PASSWORD, ctx.vault()))));
        bar.add(terminal);
        bar.add(buildVaultMenu());

        var settings = new JMenu("Pengaturan");
        settings.add(menuItem(null, "Editor lokal...", null, () ->
                EditorSettingsDialog.show(this, ctx.config().current().editors()).ifPresent(editors ->
                        mutate("Gagal menyimpan pengaturan", () ->
                                ctx.config().save(ctx.config().current().withEditors(editors))))));
        settings.add(buildIconSetMenu());
        bar.add(settings);

        var help = new JMenu("Bantuan");
        showLog.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, ctrlShift));
        showLog.setToolTipText("Log aplikasi di bagian bawah window (sama dengan isi file log)");
        showLog.addActionListener(e -> setLogVisible(showLog.isSelected()));
        help.add(showLog);
        help.add(menuItem(null, "Buka folder log", null,
                () -> UiAsync.run(io, () -> LogPanel.openFolder(ctx.paths().logDir()),
                        err -> Dialogs.error(this, "Gagal membuka folder log", err))));
        help.addSeparator();
        help.add(menuItem(AppIcon.INFO, "Tentang TermUL", null, () -> Dialogs.info(this, "Tentang TermUL",
                "TermUL (Terminal Utility) — SSH client pribadi\n\nJava " + Runtime.version()
                        + "\nFolder log: " + ctx.paths().logDir()
                        + "\n\nIkon:\n" + Arrays.stream(IconSet.values())
                                .map(s -> "  • " + s.attribution()).collect(Collectors.joining("\n")))));
        bar.add(help);
        return bar;
    }

    /** Buka/tutup panel host di kiri (tombol laci), dengan lebar terakhir yang dipakai. EDT. */
    private void setHostPanelVisible(boolean visible) {
        if (visible == hostTree.isVisible()) {
            return;
        }
        if (!visible) {
            hostWidth = Math.max(160, hostSplit.getDividerLocation());
            var owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (owner != null && SwingUtilities.isDescendingFrom(owner, hostTree)) {
                currentTab().ifPresent(TerminalTab::focusTerminal);
            }
        }
        hostTree.setVisible(visible);
        if (visible) {
            hostSplit.setDividerLocation(hostWidth);
        }
        hostSplit.revalidate();
        updateHostToggle();
    }

    private void updateHostToggle() {
        boolean open = hostTree.isVisible();
        hostToggle.setIcon((open ? AppIcon.ANGLES_LEFT : AppIcon.ANGLES_RIGHT).icon(12));
        hostToggle.setToolTipText(open ? "Sembunyikan panel host" : "Tampilkan panel host");
    }

    /** Submenu pilihan set ikon; ganti langsung terlihat (repaint) dan disimpan di config.json. */
    private JMenu buildIconSetMenu() {
        var menu = new JMenu("Set ikon");
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
                mutate("Gagal menyimpan pengaturan", () ->
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
        var menu = new JMenu("Vault");
        menu.add(menuItem(null, "Buka vault...", null, () -> ctx.sshOps().execute(gate::ensureUnlocked)));
        menu.add(menuItem(null, "Kunci vault", null, () -> ctx.sshOps().execute(gate::lock)));
        menu.add(menuItem(null, "Ganti master password...", null,
                () -> ctx.sshOps().execute(gate::changeMasterPasswordInteractive)));
        menu.addSeparator();
        var remember = new JCheckBoxMenuItem("Ingat di PC ini (Windows DPAPI)");
        remember.setToolTipText("Vault terbuka otomatis dengan login Windows. Lebih nyaman, "
                + "tapi malware yang berjalan sebagai user Anda bisa ikut membukanya.");
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
                Dialogs.error(this, "Vault", err);
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
            mutate("Gagal menyimpan info OS", () -> store.snapshot().find(profileId)
                    .filter(current -> !detected.equals(current.os()))
                    .ifPresent(current -> store.save(current.withOs(detected))));
        }, () -> osDetected.remove(profileId)), err -> osDetected.remove(profileId));
    }

    private void updateTabIcons(java.util.UUID profileId, OsInfo os) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof TerminalTab t && t.profile().id().equals(profileId)) {
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

    private Optional<TerminalTab> currentTab() {
        return tabs.getSelectedComponent() instanceof TerminalTab t ? Optional.of(t) : Optional.empty();
    }

    /** Tutup tab dari tombol ✕ / menu: konfirmasi dulu kalau sesinya masih aktif. */
    private void closeTab(int index) {
        if (index < 0 || index >= tabs.getTabCount()) {
            return;
        }
        if (tabs.getComponentAt(index) instanceof TerminalTab tab && (tab.isSessionActive() || tab.activeTransfers() > 0)) {
            String transfers = tab.activeTransfers() > 0
                    ? "\n\nMasih ada " + tab.activeTransfers() + " transfer SFTP yang akan dibatalkan." : "";
            tabs.setSelectedIndex(index);
            boolean yes = JOptionPane.showConfirmDialog(this,
                    "Akhiri sesi ke " + tab.profile().name() + " (" + tab.profile().address() + ")?" + transfers,
                    "Tutup tab", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
            if (!yes) {
                tab.focusTerminal();
                return;
            }
        }
        removeTab(index);
    }

    private void removeTab(int index) {
        if (tabs.getComponentAt(index) instanceof TerminalTab tab) {
            tab.dispose();
        }
        tabs.removeTabAt(index);
        updateCenter();
    }

    /** Ringkasan hal yang masih aktif (untuk konfirmasi keluar). Dipanggil di EDT, tanpa I/O. */
    private List<String> activeWork() {
        var items = new ArrayList<String>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof TerminalTab tab) {
                if (tab.isSessionActive()) {
                    items.add("Sesi terminal: " + tab.profile().name() + " (" + tab.profile().address() + ")");
                }
                if (tab.activeTransfers() > 0) {
                    items.add("Transfer SFTP berjalan di " + tab.profile().name() + ": " + tab.activeTransfers());
                }
            }
        }
        for (var edit : ctx.edits().entries()) {
            var state = edit.session().state();
            String status = state == RemoteEditSession.State.EDITING ? "tersinkron" : "BELUM TERSINKRON";
            items.add("File diedit (" + status + "): " + edit.profile().name() + ":" + edit.session().remotePath());
        }
        return items;
    }

    private int firstActiveTab() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof TerminalTab tab && (tab.isSessionActive() || tab.activeTransfers() > 0)) {
                return i;
            }
        }
        return -1;
    }

    private static String tabTitle(HostProfile p) {
        Color color = EnvColors.of(p.environment());
        String name = p.name().replace("&", "&amp;").replace("<", "&lt;");
        return color == null ? p.name()
                : "<html><font color='#%06x'>&#9679;</font> %s</html>".formatted(color.getRGB() & 0xFFFFFF, name);
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
            message = "Tutup TermUL?";
        } else {
            var sb = new StringBuilder("Masih ada yang aktif:\n\n");
            active.forEach(a -> sb.append("  • ").append(a).append('\n'));
            sb.append("\nTutup TermUL dan akhiri semuanya?\n(Pilih \"No\" untuk memeriksanya dulu.)");
            message = sb.toString();
        }
        int choice = JOptionPane.showConfirmDialog(this, message, "Keluar TermUL", JOptionPane.YES_NO_OPTION,
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
            removeTab(0);
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
        var tab = new TerminalTab(profile, ctx.terminals(), ctx.sshOps(), ctx.terminalSettings(),
                p -> new SftpPanel(p, ctx.sessions(), ctx.sshOps(), ctx.edits()::open));
        tabs.addTab(tabTitle(profile), tab);
        int index = tabs.indexOfComponent(tab);
        tabs.setToolTipTextAt(index, profile.address());
        tabs.setIconAt(index, OsIcons.of(profile.os()));
        tab.setConnectedListener(tty -> detectOs(profile.id(), tty));
        tabs.setSelectedIndex(index);
        updateCenter();
        tab.connect();
    }

    @Override
    public void newHost(String group) {
        ProfileDialog.create(this, group, store.snapshot()).ifPresent(this::saveProfile);
    }

    @Override
    public void edit(HostProfile profile) {
        // baca metadata vault (disk I/O) di luar EDT, lalu buka dialog
        UiAsync.run(io, () -> storedSecrets(profile), stored ->
                        ProfileDialog.edit(this, profile, store.snapshot(), stored).ifPresent(this::saveProfile),
                err -> Dialogs.error(this, "Gagal membaca vault", err));
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
        mutate("Gagal menyimpan profil", () -> store.save(result.profile()));
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
                Dialogs.info(this, "Vault", "Vault tidak dibuka: password tidak disimpan (profil tetap tersimpan).");
            }
        }, err -> {
            SecretChange.discardAll(result.secrets());
            Dialogs.error(this, "Gagal menyimpan password ke vault", err);
        });
    }

    @Override
    public void duplicate(HostProfile profile) {
        mutate("Gagal menduplikat profil", () -> store.save(profile.duplicate()));
    }

    @Override
    public void delete(HostProfile profile) {
        if (Dialogs.confirm(this, "Hapus profil", "Hapus profil '" + profile.name() + "'?")) {
            mutate("Gagal menghapus profil", () -> store.delete(profile.id()));
            UiAsync.run(ctx.sshOps(), () -> {
                if (!storedSecrets(profile).isEmpty() && ctx.vault().ensureUnlocked()) {
                    ctx.vault().vault().removeProfile(profile.id());
                }
            }, err -> Dialogs.error(this, "Gagal menghapus password dari vault", err));
        }
    }

    @Override
    public void newGroup(String parent) {
        String name = Dialogs.input(this, "Grup baru",
                parent.isEmpty() ? "Nama grup:" : "Nama subgrup di '" + parent + "':", "");
        if (name != null) {
            String path = parent.isEmpty() ? name : parent + "/" + name;
            mutate("Gagal membuat grup", () -> store.addGroup(path));
        }
    }

    @Override
    public void renameGroup(String group) {
        String target = Dialogs.input(this, "Rename grup", "Path grup baru (pisahkan dengan '/'):", group);
        if (target != null && !target.equals(group)) {
            mutate("Gagal rename grup", () -> store.renameGroup(group, target));
        }
    }

    @Override
    public void deleteGroup(String group) {
        if (Dialogs.confirm(this, "Hapus grup", "Hapus grup '" + group + "' (harus kosong)?")) {
            mutate("Gagal menghapus grup", () -> store.deleteGroup(group));
        }
    }
}

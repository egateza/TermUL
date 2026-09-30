package dev.egateza.myterm.app.ui;

import dev.egateza.myterm.app.AppContext;
import dev.egateza.myterm.app.edit.EditTrackerDialog;
import dev.egateza.myterm.app.edit.EditorSettingsDialog;
import dev.egateza.myterm.app.sftp.SftpPanel;
import dev.egateza.myterm.app.terminal.TerminalTab;
import dev.egateza.myterm.app.ui.tree.HostTreePanel;
import java.awt.Color;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.core.profile.ProfileSnapshot;
import dev.egateza.myterm.core.profile.ProfileStore;
import dev.egateza.myterm.vault.SecretType;
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
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
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
    private final HostTreePanel hostTree;
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final JPanel center = new JPanel(new BorderLayout());
    private final JLabel welcome = new JLabel(
            "<html><center><b>MyTerm</b><br><br>Double-click host di kiri untuk membuka terminal.<br>"
                    + "Ctrl+N: host baru &nbsp; Ctrl+F: cari host</center></html>", SwingConstants.CENTER);
    private final Runnable onExit;
    private final KeyEventDispatcher hotkeys = this::dispatchHotkey;

    public MainFrame(AppContext ctx, Runnable onExit) {
        super("MyTerm");
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

        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, hostTree, center);
        split.setDividerLocation(260);
        split.setContinuousLayout(true);
        hostTree.setMinimumSize(new Dimension(160, 100));
        getContentPane().add(split, BorderLayout.CENTER);
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
        file.add(menuItem("Host baru...", KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK),
                () -> newHost(hostTree.selectedGroup())));
        file.add(menuItem("Grup baru...", null, () -> newGroup(hostTree.selectedGroup())));
        file.add(menuItem("Cari host", KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
                hostTree::focusSearch));
        file.addSeparator();
        file.add(menuItem("Keluar", null, this::exit));
        bar.add(file);

        var terminal = new JMenu("Terminal");
        int ctrlShift = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
        terminal.add(menuItem("Duplikat tab", KeyStroke.getKeyStroke(KeyEvent.VK_T, ctrlShift),
                () -> currentTab().ifPresent(t -> open(t.profile()))));
        terminal.add(menuItem("Reconnect", KeyStroke.getKeyStroke(KeyEvent.VK_F5, InputEvent.CTRL_DOWN_MASK),
                () -> currentTab().ifPresent(TerminalTab::reconnect)));
        terminal.add(menuItem("Tutup tab", KeyStroke.getKeyStroke(KeyEvent.VK_W, ctrlShift),
                () -> closeTab(tabs.getSelectedIndex())));
        terminal.add(menuItem("Panel SFTP", KeyStroke.getKeyStroke(KeyEvent.VK_F, ctrlShift),
                () -> currentTab().ifPresent(TerminalTab::toggleSftp)));
        terminal.add(menuItem("File yang sedang diedit...", KeyStroke.getKeyStroke(KeyEvent.VK_E, ctrlShift),
                this::showEditTracker));
        terminal.addSeparator();
        terminal.add(menuItem("Inject password sudo", KeyStroke.getKeyStroke(KeyEvent.VK_P, ctrlShift),
                () -> currentTab().ifPresent(t -> t.injectSecret(SecretType.SUDO_PASSWORD, ctx.vault()))));
        terminal.add(menuItem("Inject password root", KeyStroke.getKeyStroke(KeyEvent.VK_R, ctrlShift),
                () -> currentTab().ifPresent(t -> t.injectSecret(SecretType.ROOT_PASSWORD, ctx.vault()))));
        bar.add(terminal);
        bar.add(buildVaultMenu());

        var settings = new JMenu("Pengaturan");
        settings.add(menuItem("Editor lokal...", null, () ->
                EditorSettingsDialog.show(this, ctx.config().current().editors()).ifPresent(editors ->
                        mutate("Gagal menyimpan pengaturan", () ->
                                ctx.config().save(ctx.config().current().withEditors(editors))))));
        bar.add(settings);
        return bar;
    }

    private JMenu buildVaultMenu() {
        var gate = ctx.vault();
        var menu = new JMenu("Vault");
        menu.add(menuItem("Buka vault...", null, () -> ctx.sshOps().execute(gate::ensureUnlocked)));
        menu.add(menuItem("Kunci vault", null, () -> ctx.sshOps().execute(gate::lock)));
        menu.add(menuItem("Ganti master password...", null,
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

    private void closeTab(int index) {
        if (index < 0 || index >= tabs.getTabCount()) {
            return;
        }
        if (tabs.getComponentAt(index) instanceof TerminalTab tab) {
            tab.dispose();
        }
        tabs.removeTabAt(index);
        updateCenter();
    }

    private static String tabTitle(HostProfile p) {
        Color color = EnvColors.of(p.environment());
        String name = p.name().replace("&", "&amp;").replace("<", "&lt;");
        return color == null ? p.name()
                : "<html><font color='#%06x'>&#9679;</font> %s</html>".formatted(color.getRGB() & 0xFFFFFF, name);
    }

    private static JMenuItem menuItem(String label, KeyStroke key, Runnable action) {
        var item = new JMenuItem(label);
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

    private void exit() {
        while (tabs.getTabCount() > 0) {
            closeTab(0);
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

package dev.egateza.myterm.app.ui;

import dev.egateza.myterm.app.ui.tree.HostTreePanel;
import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.core.profile.ProfileSnapshot;
import dev.egateza.myterm.core.profile.ProfileStore;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.ExecutorService;
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

    private final ProfileStore store;
    private final ExecutorService io;
    private final HostTreePanel hostTree;
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final JPanel center = new JPanel(new BorderLayout());
    private final JLabel welcome = new JLabel(
            "<html><center><b>MyTerm</b><br><br>Double-click host di kiri untuk membuka terminal.<br>"
                    + "Ctrl+N: host baru &nbsp; Ctrl+F: cari host</center></html>", SwingConstants.CENTER);
    private final Runnable onExit;

    public MainFrame(ProfileStore store, ExecutorService io, Runnable onExit) {
        super("MyTerm");
        this.store = store;
        this.io = io;
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
        tabs.addChangeListener(e -> updateCenter());
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
        return bar;
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
        Dialogs.info(this, "Belum tersedia", "Koneksi SSH belum diimplementasikan.");
    }

    @Override
    public void newHost(String group) {
        ProfileDialog.create(this, group, store.snapshot())
                .ifPresent(p -> mutate("Gagal menyimpan profil", () -> store.save(p)));
    }

    @Override
    public void edit(HostProfile profile) {
        ProfileDialog.edit(this, profile, store.snapshot())
                .ifPresent(p -> mutate("Gagal menyimpan profil", () -> store.save(p)));
    }

    @Override
    public void duplicate(HostProfile profile) {
        mutate("Gagal menduplikat profil", () -> store.save(profile.duplicate()));
    }

    @Override
    public void delete(HostProfile profile) {
        if (Dialogs.confirm(this, "Hapus profil", "Hapus profil '" + profile.name() + "'?")) {
            mutate("Gagal menghapus profil", () -> store.delete(profile.id()));
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

package dev.egateza.termul.app.ui.tree;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.EnvColors;
import dev.egateza.termul.app.ui.OsIcons;
import dev.egateza.termul.app.ui.tree.HostTreeModelBuilder.BuiltinGroup;
import dev.egateza.termul.app.ui.tree.HostTreeModelBuilder.GroupNode;
import dev.egateza.termul.app.ui.tree.HostTreeModelBuilder.LocalProfileNode;
import dev.egateza.termul.app.ui.tree.HostTreeModelBuilder.LocalTerminalNode;
import dev.egateza.termul.app.ui.tree.HostTreeModelBuilder.WslDistroNode;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.OsInfo;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/** Panel kiri: search field + tree grup/host. Semua method dipanggil di EDT. */
public final class HostTreePanel extends JPanel {

    /** Aksi yang di-trigger dari tree; diimplementasikan oleh MainFrame. */
    public interface Actions {
        void open(HostProfile profile);

        /** Buka tab terminal lokal baru dengan profil default. */
        void openLocalTerminal();

        /** Buka tab terminal lokal dengan profil/shell tertentu. */
        void openLocalProfile(java.util.UUID id);

        /** Jadikan profil/shell ini default untuk Terminal lokal baru. */
        void setDefaultLocalProfile(java.util.UUID id);

        /** Sembunyikan profil/shell dari daftar terminal lokal; tidak menghapus apa pun. */
        void disableLocalProfile(java.util.UUID id);

        /** Buka dialog kelola profil terminal lokal. */
        void manageLocalProfiles();

        /** Buka tab yang hanya berisi panel SFTP (tanpa terminal). */
        void openSftp(HostProfile profile);

        void newHost(String group);

        void edit(HostProfile profile);

        void duplicate(HostProfile profile);

        void delete(HostProfile profile);

        /** Hapus host key profil ini dari known_hosts (mis. server di-reinstall); konfirmasi dulu. */
        void forgetHostKey(HostProfile profile);

        /** Hapus sertifikat FTPS yang dipercaya untuk profil ini (mis. sertifikat server diganti); konfirmasi dulu. */
        void forgetFtpsCert(HostProfile profile);

        void newGroup(String parent);

        void renameGroup(String group);

        void deleteGroup(String group);

        /**
         * Ubah flag sensitif semua profil di grup beserta subgrupnya; konfirmasi dulu.
         *
         * @param sensitive true = tandai sensitif, null = ikut environment (PROD tetap sensitif)
         */
        void setGroupSensitive(String group, Boolean sensitive);

        /** Buka tab untuk semua profil terpilih (multi-seleksi); konfirmasi kalau banyak. */
        void openAll(List<HostProfile> profiles);

        /**
         * Ubah flag sensitif profil terpilih (multi-seleksi); konfirmasi dulu.
         *
         * @param sensitive true = tandai sensitif, null = ikut environment (PROD tetap sensitif)
         */
        void setSensitive(List<HostProfile> profiles, Boolean sensitive);

        /** Tambah ({@code true}) atau keluarkan profil dari grup bawaan Favorites. */
        void setFavorite(HostProfile profile, boolean favorite);

        /** Kosongkan grup bawaan Last used. */
        void clearRecent();

        /** Buat profil SSH untuk distro WSL yang belum punya profil. */
        void newWslProfile(String distro);

        void openWslManager();

        /** Periksa sshd di distro WSL profil ini dan tampilkan perbaikannya. */
        void checkWslSsh(HostProfile profile);
    }

    private final Actions actions;
    private final JTextField search = new JTextField();
    private final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree tree = new JTree(model);
    private ProfileSnapshot snapshot = ProfileSnapshot.empty();
    private List<String> wslRunning; // null = manajemen WSL mati
    private List<dev.egateza.termul.app.terminal.LocalProfiles.Choice> localChoices = List.of(); // default dulu

    public HostTreePanel(Actions actions) {
        super(new BorderLayout());
        this.actions = actions;

        search.putClientProperty("JTextField.placeholderText", I18n.t("tree.search.placeholder"));
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuild();
            }
        });

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        // Ctrl+klik (Cmd di Mac) / Shift+klik untuk memilih beberapa profil sekaligus
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        tree.setCellRenderer(new Renderer(this::isWslStopped));
        tree.setToggleClickCount(0);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null && userObject(path) instanceof HostProfile p) {
                        actions.open(p);
                    } else if (path != null && userObject(path) instanceof LocalTerminalNode) {
                        actions.openLocalTerminal(); // grup khusus: double-click = buka default
                    } else if (path != null && userObject(path) instanceof LocalProfileNode n) {
                        actions.openLocalProfile(n.choice().id());
                    } else if (path != null && userObject(path) instanceof WslDistroNode d) {
                        actions.newWslProfile(d.name());
                    } else if (path != null) {
                        toggle(path);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybePopup(e);
            }
        });
        bindKey(KeyEvent.VK_ENTER, "open", () -> {
            var hosts = selectedProfiles();
            if (hosts.size() > 1) {
                actions.openAll(hosts);
                return;
            }
            switch (selectedObject()) {
                case HostProfile p -> actions.open(p);
                case LocalTerminalNode n -> actions.openLocalTerminal();
                case LocalProfileNode n -> actions.openLocalProfile(n.choice().id());
                case WslDistroNode d -> actions.newWslProfile(d.name());
                case null, default -> { }
            }
        });
        bindKey(KeyEvent.VK_DELETE, "delete", () -> {
            if (tree.getSelectionCount() > 1) {
                return; // multi-seleksi: hapus tetap satu per satu
            }
            if (selectedObject() instanceof HostProfile p) {
                actions.delete(p);
            } else if (selectedObject() instanceof GroupNode g && !g.path().isEmpty()) {
                actions.deleteGroup(g.path());
            } // grup bawaan (BuiltinGroup) sengaja tidak bisa dihapus
        });
        bindKey(KeyEvent.VK_F2, "edit", () -> {
            if (tree.getSelectionCount() > 1) {
                return;
            }
            if (selectedObject() instanceof HostProfile p) {
                actions.edit(p);
            } else if (selectedObject() instanceof GroupNode g && !g.path().isEmpty()) {
                actions.renameGroup(g.path());
            }
        });

        // Jarak di pembungkus, bukan CompoundBorder di field: border FlatLaf harus tetap border langsung field
        // supaya style "arc" (sudut panel) bisa diterapkan.
        var searchBox = new JPanel(new BorderLayout());
        searchBox.setOpaque(false);
        searchBox.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        searchBox.add(search, BorderLayout.CENTER);
        add(searchBox, BorderLayout.NORTH);
        var scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
    }

    public void setSnapshot(ProfileSnapshot snapshot) {
        this.snapshot = snapshot;
        rebuild();
    }

    /** @param running nama distro WSL yang berjalan; null = manajemen WSL mati (grup WSL tidak tampil) */
    /** Isi grup Terminal lokal (default dulu); dipanggil saat profil/deteksi/WSL berubah. */
    public void setLocalChoices(List<dev.egateza.termul.app.terminal.LocalProfiles.Choice> choices) {
        var next = List.copyOf(choices);
        if (!next.equals(localChoices)) {
            localChoices = next;
            rebuild();
        }
    }

    public void setWslRunning(List<String> running) {
        var next = running == null ? null : List.copyOf(running);
        if (!java.util.Objects.equals(next, wslRunning)) {
            wslRunning = next;
            rebuild();
        }
    }

    /** Grup dari seleksi saat ini (grup itu sendiri, atau grup milik host terpilih). */
    public String selectedGroup() {
        return switch (selectedObject()) {
            case GroupNode g -> g.path();
            case HostProfile p -> p.group();
            case null, default -> "";
        };
    }

    public Optional<HostProfile> selectedProfile() {
        return selectedObject() instanceof HostProfile p ? Optional.of(p) : Optional.empty();
    }

    /**
     * Pilih host di grup aslinya (bukan Favorites/Last used), buka grupnya, gulir ke sana, dan fokus ke tree. Filter
     * pencarian dikosongkan kalau menyembunyikan host itu.
     *
     * @return false kalau profil tidak ada lagi di tree (mis. sudah dihapus)
     */
    public boolean reveal(java.util.UUID profileId) {
        TreePath path = hostPath(profileId);
        if (path == null && !search.getText().isEmpty()) {
            search.setText(""); // rebuild sinkron lewat DocumentListener
            path = hostPath(profileId);
        }
        if (path == null) {
            return false;
        }
        tree.expandPath(path.getParentPath());
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
        tree.requestFocusInWindow();
        return true;
    }

    /** Node host di grup aslinya; node di grup bawaan hanya dipakai kalau tidak ada yang lain. */
    private TreePath hostPath(java.util.UUID profileId) {
        TreePath fallback = null;
        var root = (DefaultMutableTreeNode) model.getRoot();
        for (var node : Collections.list(root.depthFirstEnumeration())) {
            var n = (DefaultMutableTreeNode) node;
            if (n.getUserObject() instanceof HostProfile p && p.id().equals(profileId)) {
                var path = new TreePath(n.getPath());
                if (!(((DefaultMutableTreeNode) n.getParent()).getUserObject() instanceof BuiltinGroup)) {
                    return path;
                }
                fallback = fallback == null ? path : fallback;
            }
        }
        return fallback;
    }

    public void focusSearch() {
        search.requestFocusInWindow();
        search.selectAll();
    }

    /** Diameter lengkungan sudut search field, px (0 = kotak); mengikuti pengaturan sudut panel. */
    public void setSearchArc(int arc) {
        search.putClientProperty("FlatLaf.style", "arc: " + arc);
    }

    private void rebuild() {
        Set<Object> expanded = expandedGroups();
        TreePath[] selectedPaths = tree.getSelectionPaths();
        var selected = new java.util.ArrayList<Object[]>(); // {user object node, user object parent-nya}
        for (TreePath p : selectedPaths == null ? new TreePath[0] : selectedPaths) {
            selected.add(new Object[] {userObject(p), p.getParentPath() == null ? null : userObject(p.getParentPath())});
        }
        var reselect = new java.util.ArrayList<TreePath>();
        boolean filtering = !search.getText().isBlank();
        boolean hadWsl = hasChild((DefaultMutableTreeNode) model.getRoot(), BuiltinGroup.WSL);

        var root = HostTreeModelBuilder.build(snapshot, search.getText(), wslRunning, localChoices);
        model.setRoot(root);

        for (var node : Collections.list(root.depthFirstEnumeration())) {
            var n = (DefaultMutableTreeNode) node;
            var path = new TreePath(n.getPath());
            Object uo = n.getUserObject();
            if ((uo instanceof GroupNode || uo instanceof BuiltinGroup || uo instanceof LocalTerminalNode)
                    && (filtering || expanded.contains(uo) || expanded.isEmpty() || (uo == BuiltinGroup.WSL && !hadWsl))) {
                tree.expandPath(path);
            }
            // profil bisa muncul dua kali (grup asli + Favorites/Last used): cocokkan juga parent-nya
            var parent = (DefaultMutableTreeNode) n.getParent();
            if (parent != null && selected.stream()
                    .anyMatch(s -> isSame(uo, s[0]) && isSame(parent.getUserObject(), s[1]))) {
                reselect.add(path);
            }
        }
        if (!reselect.isEmpty()) {
            tree.setSelectionPaths(reselect.toArray(TreePath[]::new));
        }
    }

    /** Profil WSL yang distronya tidak sedang berjalan (manajemen WSL aktif). */
    private boolean isWslStopped(HostProfile p) {
        return p.wslDistro() != null && wslRunning != null
                && wslRunning.stream().noneMatch(d -> d.equalsIgnoreCase(p.wslDistro()));
    }

    private static boolean hasChild(DefaultMutableTreeNode node, Object userObject) {
        for (int i = 0; i < node.getChildCount(); i++) {
            if (((DefaultMutableTreeNode) node.getChildAt(i)).getUserObject() == userObject) {
                return true;
            }
        }
        return false;
    }

    /** User object grup ({@link GroupNode}/{@link BuiltinGroup}) yang sedang terbuka. */
    private Set<Object> expandedGroups() {
        var result = new HashSet<Object>();
        var root = (DefaultMutableTreeNode) model.getRoot();
        var e = tree.getExpandedDescendants(new TreePath(root.getPath()));
        if (e != null) {
            for (var p : Collections.list(e)) {
                Object uo = userObject(p);
                if (uo instanceof GroupNode || uo instanceof BuiltinGroup || uo instanceof LocalTerminalNode) {
                    result.add(uo);
                }
            }
        }
        return result;
    }

    private static boolean isSame(Object a, Object b) {
        return switch (a) {
            case HostProfile p when b instanceof HostProfile q -> p.id().equals(q.id());
            case GroupNode g when b instanceof GroupNode h -> g.path().equals(h.path());
            case BuiltinGroup g -> g == b;
            case WslDistroNode d -> d.equals(b);
            case LocalTerminalNode n -> n == b;
            case LocalProfileNode n -> b instanceof LocalProfileNode m && n.choice().id().equals(m.choice().id());
            case null, default -> false;
        };
    }

    private void toggle(TreePath path) {
        if (tree.isExpanded(path)) {
            tree.collapsePath(path);
        } else {
            tree.expandPath(path);
        }
    }

    private void maybePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            tree.clearSelection();
        } else if (!tree.isPathSelected(path)) {
            tree.setSelectionPath(path); // klik kanan di luar seleksi: seleksi pindah ke node itu saja
        }
        Object target = path == null ? null : userObject(path);
        var hosts = selectedProfiles();
        var popup = target instanceof HostProfile && hosts.size() > 1 ? buildMultiPopup(hosts) : buildPopup(target);
        popup.show(tree, e.getX(), e.getY());
    }

    /** Menu klik kanan saat beberapa profil terpilih. */
    private JPopupMenu buildMultiPopup(List<HostProfile> hosts) {
        var menu = new JPopupMenu();
        menu.add(item(AppIcon.TERMINAL, I18n.t("tree.menu.multi.open", hosts.size()), () -> actions.openAll(hosts)));
        menu.addSeparator();
        menu.add(sensitiveMenu(hosts, v -> actions.setSensitive(hosts, v)));
        return menu;
    }

    private JPopupMenu buildPopup(Object target) {
        var menu = new JPopupMenu();
        switch (target) {
            case HostProfile p -> {
                if (p.isFtp()) {
                    menu.add(item(AppIcon.SFTP, I18n.t("tree.menu.openFtp"), () -> actions.openSftp(p)));
                } else {
                    menu.add(item(AppIcon.TERMINAL, I18n.t("tree.menu.openTerminal"), () -> actions.open(p)));
                    menu.add(item(AppIcon.SFTP, I18n.t("tree.menu.openSftp"), () -> actions.openSftp(p)));
                }
                menu.addSeparator();
                menu.add(item(null, I18n.t("tree.menu.edit"), () -> actions.edit(p)));
                menu.add(item(null, I18n.t("tree.menu.duplicate"), () -> actions.duplicate(p)));
                boolean favorite = snapshot.isFavorite(p.id());
                menu.add(item(AppIcon.STAR, I18n.t(favorite ? "tree.menu.unfavorite" : "tree.menu.favorite"),
                        () -> actions.setFavorite(p, !favorite)));
                menu.add(item(null, I18n.t("tree.menu.delete"), () -> actions.delete(p)));
                menu.addSeparator();
                switch (p.protocol()) {
                    case SSH -> menu.add(item(null, I18n.t("tree.menu.forgetHostKey"), () -> actions.forgetHostKey(p)));
                    case FTPS, FTPS_IMPLICIT ->
                            menu.add(item(null, I18n.t("tree.menu.forgetFtpsCert"), () -> actions.forgetFtpsCert(p)));
                    case FTP -> { } // tanpa enkripsi: tidak ada kunci/sertifikat yang dipercaya
                }
                if (p.wslDistro() != null && wslRunning != null) {
                    menu.add(item(AppIcon.TERMINAL, I18n.t("tree.menu.checkWslSsh"), () -> actions.checkWslSsh(p)));
                }
            }
            case GroupNode g -> {
                menu.add(item(AppIcon.SERVER, I18n.t("tree.menu.newHostInGroup"), () -> actions.newHost(g.path())));
                menu.add(item(AppIcon.FOLDER_PLUS, I18n.t("tree.menu.newSubgroup"), () -> actions.newGroup(g.path())));
                menu.addSeparator();
                menu.add(item(null, I18n.t("tree.menu.renameGroup"), () -> actions.renameGroup(g.path())));
                menu.add(item(null, I18n.t("tree.menu.deleteGroup"), () -> actions.deleteGroup(g.path())));
                menu.addSeparator();
                menu.add(sensitiveMenu(snapshot.profilesUnder(g.path()), v -> actions.setGroupSensitive(g.path(), v)));
            }
            case BuiltinGroup.RECENT -> {
                var clear = item(AppIcon.BROOM, I18n.t("tree.menu.clearRecent"), actions::clearRecent);
                clear.setEnabled(!snapshot.recent().isEmpty());
                menu.add(clear);
            }
            case BuiltinGroup.WSL -> menu.add(item(AppIcon.TERMINAL, I18n.t("tree.menu.wslManager"), actions::openWslManager));
            case LocalTerminalNode n -> {
                menu.add(item(AppIcon.TERMINAL, I18n.t("tree.menu.openLocal"), actions::openLocalTerminal));
                menu.addSeparator();
                menu.add(item(null, I18n.t("tree.menu.manageLocal"), actions::manageLocalProfiles));
            }
            case LocalProfileNode n -> {
                menu.add(item(AppIcon.TERMINAL, I18n.t("tree.menu.openTerminal"),
                        () -> actions.openLocalProfile(n.choice().id())));
                var makeDefault = item(null, I18n.t("tree.menu.localDefault"),
                        () -> actions.setDefaultLocalProfile(n.choice().id()));
                makeDefault.setEnabled(!n.choice().isDefault());
                menu.add(makeDefault);
                var disable = item(null, I18n.t("tree.menu.localDisable"),
                        () -> actions.disableLocalProfile(n.choice().id()));
                disable.setToolTipText(I18n.t("localProfiles.disable.tooltip"));
                disable.setEnabled(localChoices.size() > 1);
                menu.add(disable);
                menu.addSeparator();
                menu.add(item(null, I18n.t("tree.menu.manageLocal"), actions::manageLocalProfiles));
            }
            case WslDistroNode d -> {
                menu.add(item(AppIcon.SERVER, I18n.t("tree.menu.newWslProfile"), () -> actions.newWslProfile(d.name())));
                menu.addSeparator();
                menu.add(item(AppIcon.TERMINAL, I18n.t("tree.menu.wslManager"), actions::openWslManager));
            }
            case null, default -> {
                menu.add(item(AppIcon.SERVER, I18n.t("tree.menu.newHost"), () -> actions.newHost("")));
                menu.add(item(AppIcon.FOLDER_PLUS, I18n.t("tree.menu.newGroup"), () -> actions.newGroup("")));
                if (dev.egateza.termul.core.Os.current().isWindows()) {
                    menu.addSeparator();
                    menu.add(item(AppIcon.TERMINAL, I18n.t("tree.menu.wslManager"), actions::openWslManager));
                }
            }
        }
        return menu;
    }

    /**
     * Submenu "Profil sensitif" untuk {@code profiles}: item yang tidak mengubah apa pun dinonaktifkan.
     *
     * @param apply dipanggil dengan true (tandai sensitif) atau null (ikut environment)
     */
    private JMenu sensitiveMenu(List<HostProfile> profiles, java.util.function.Consumer<Boolean> apply) {
        var sensitive = new JMenu(I18n.t("tree.menu.groupSensitive"));
        sensitive.setEnabled(!profiles.isEmpty());
        var mark = item(null, I18n.t("tree.menu.groupSensitive.mark"), () -> apply.accept(true));
        mark.setEnabled(profiles.stream().anyMatch(p -> !Boolean.TRUE.equals(p.sensitive())));
        var reset = item(null, I18n.t("tree.menu.groupSensitive.reset"), () -> apply.accept(null));
        reset.setEnabled(profiles.stream().anyMatch(p -> p.sensitive() != null));
        reset.setToolTipText(I18n.t("tree.menu.groupSensitive.reset.tooltip"));
        sensitive.add(mark);
        sensitive.add(reset);
        return sensitive;
    }

    private static JMenuItem item(AppIcon icon, String label, Runnable r) {
        var item = icon == null ? new JMenuItem(label) : new JMenuItem(label, icon.icon());
        item.addActionListener(e -> r.run());
        return item;
    }

    private void bindKey(int key, String name, Runnable r) {
        tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name);
        tree.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                r.run();
            }
        });
    }

    /**
     * Profil terpilih sesuai urutan di tree, tanpa duplikat (profil yang sama bisa terpilih di grup asli dan di
     * Favorites/Last used). Node selain profil diabaikan.
     */
    List<HostProfile> selectedProfiles() {
        int[] rows = tree.getSelectionRows();
        if (rows == null) {
            return List.of();
        }
        java.util.Arrays.sort(rows);
        var result = new java.util.LinkedHashMap<java.util.UUID, HostProfile>();
        for (int row : rows) {
            TreePath path = tree.getPathForRow(row);
            if (path != null && userObject(path) instanceof HostProfile p) {
                result.putIfAbsent(p.id(), p);
            }
        }
        return List.copyOf(result.values());
    }

    private Object selectedObject() {
        TreePath path = tree.getSelectionPath();
        return path == null ? null : userObject(path);
    }

    private static Object userObject(TreePath path) {
        return ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
    }

    /** Menampilkan nama host + alamat abu-abu + warna environment. */
    private static final class Renderer extends DefaultTreeCellRenderer {
        private static final javax.swing.Icon FOLDER = AppIcon.FOLDER.icon(AppIcon.SIZE, () -> AppIcon.FOLDER_COLOR);
        private static final javax.swing.Icon FOLDER_OPEN =
                AppIcon.FOLDER_OPEN.icon(AppIcon.SIZE, () -> AppIcon.FOLDER_COLOR);
        private static final javax.swing.Icon STAR = AppIcon.STAR.icon(AppIcon.SIZE, () -> AppIcon.FOLDER_COLOR);
        private static final javax.swing.Icon HISTORY = AppIcon.HISTORY.icon();
        private static final javax.swing.Icon WSL = AppIcon.TERMINAL.icon();
        private final java.util.function.Predicate<HostProfile> wslStopped;

        Renderer(java.util.function.Predicate<HostProfile> wslStopped) {
            this.wslStopped = wslStopped;
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            Object uo = ((DefaultMutableTreeNode) value).getUserObject();
            boolean isGroup = uo instanceof GroupNode || uo instanceof BuiltinGroup
                    || uo instanceof LocalTerminalNode && ((DefaultMutableTreeNode) value).getAllowsChildren();
            super.getTreeCellRendererComponent(tree, value, sel, expanded, !isGroup, row, hasFocus);
            if (uo instanceof HostProfile p) {
                var color = EnvColors.of(p.environment());
                String dot = color == null ? "" : "<font color='#%06x'>&#9679;</font> ".formatted(color.getRGB() & 0xFFFFFF);
                String stopped = wslStopped.test(p) ? " &middot; " + escape(I18n.t("tree.wsl.stopped")) : "";
                setText("<html>" + dot + escape(p.name()) + " <font color='gray'>" + escape(p.address()) + stopped
                        + "</font></html>");
                setIcon(OsIcons.forHost(p));
                String os = escape(OsIcons.hostLabel(p));
                setToolTipText(p.notes() == null ? os : "<html>" + os + "<br>" + escape(p.notes()) + "</html>");
            } else if (uo instanceof BuiltinGroup b) {
                setText(switch (b) {
                    case FAVORITES -> I18n.t("tree.group.favorites");
                    case RECENT -> I18n.t("tree.group.recent");
                    case WSL -> I18n.t("tree.group.wsl");
                });
                setIcon(switch (b) {
                    case FAVORITES -> STAR;
                    case RECENT -> HISTORY;
                    case WSL -> WSL;
                });
                setToolTipText(switch (b) {
                    case FAVORITES -> I18n.t("tree.group.favorites.tooltip");
                    case RECENT -> I18n.t("tree.group.recent.tooltip", ProfileSnapshot.RECENT_LIMIT);
                    case WSL -> I18n.t("tree.group.wsl.tooltip");
                });
            } else if (uo instanceof LocalProfileNode(var c)) {
                String def = c.isDefault() ? " &middot; " + escape(I18n.t("tree.local.default")) : "";
                setText("<html>" + escape(c.name()) + " <font color='gray'>" + escape(c.commandLine()) + def
                        + "</font></html>");
                setIcon(WSL);
                setToolTipText(escape(c.commandLine()));
            } else if (uo instanceof LocalTerminalNode) {
                setText(I18n.t("tree.local"));
                setIcon(WSL);
                setToolTipText(I18n.t("tree.local.tooltip"));
            } else if (uo instanceof WslDistroNode d) {
                setText("<html>" + escape(d.name()) + " <font color='gray'>" + escape(I18n.t("tree.wsl.noProfile"))
                        + "</font></html>");
                setIcon(OsIcons.of(guessOs(d.name())));
                setToolTipText(I18n.t("tree.wsl.noProfile.tooltip"));
            } else {
                if (isGroup) {
                    setIcon(expanded ? FOLDER_OPEN : FOLDER);
                }
                setToolTipText(null);
            }
            return this;
        }

        /** Ikon distro dari namanya, mis. {@code Ubuntu-22.04} → ubuntu; null kalau tidak bisa ditebak. */
        static OsInfo guessOs(String distro) {
            var m = java.util.regex.Pattern.compile("[a-z]+").matcher(distro.toLowerCase(Locale.ROOT));
            return m.lookingAt() ? new OsInfo(m.group(), null, distro) : null;
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}

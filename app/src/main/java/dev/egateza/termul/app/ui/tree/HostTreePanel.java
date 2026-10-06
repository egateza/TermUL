package dev.egateza.termul.app.ui.tree;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.EnvColors;
import dev.egateza.termul.app.ui.OsIcons;
import dev.egateza.termul.app.ui.tree.HostTreeModelBuilder.BuiltinGroup;
import dev.egateza.termul.app.ui.tree.HostTreeModelBuilder.GroupNode;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
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

        /** Buka tab yang hanya berisi panel SFTP (tanpa terminal). */
        void openSftp(HostProfile profile);

        void newHost(String group);

        void edit(HostProfile profile);

        void duplicate(HostProfile profile);

        void delete(HostProfile profile);

        /** Hapus host key profil ini dari known_hosts (mis. server di-reinstall); konfirmasi dulu. */
        void forgetHostKey(HostProfile profile);

        void newGroup(String parent);

        void renameGroup(String group);

        void deleteGroup(String group);

        /** Tambah ({@code true}) atau keluarkan profil dari grup bawaan Favorites. */
        void setFavorite(HostProfile profile, boolean favorite);

        /** Kosongkan grup bawaan Last used. */
        void clearRecent();
    }

    private final Actions actions;
    private final JTextField search = new JTextField();
    private final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree tree = new JTree(model);
    private ProfileSnapshot snapshot = ProfileSnapshot.empty();

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
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new Renderer());
        tree.setToggleClickCount(0);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null && userObject(path) instanceof HostProfile p) {
                        actions.open(p);
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
        bindKey(KeyEvent.VK_ENTER, "open", () -> selectedProfile().ifPresent(actions::open));
        bindKey(KeyEvent.VK_DELETE, "delete", () -> {
            if (selectedObject() instanceof HostProfile p) {
                actions.delete(p);
            } else if (selectedObject() instanceof GroupNode g && !g.path().isEmpty()) {
                actions.deleteGroup(g.path());
            } // grup bawaan (BuiltinGroup) sengaja tidak bisa dihapus
        });
        bindKey(KeyEvent.VK_F2, "edit", () -> {
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
        TreePath selectedPath = tree.getSelectionPath();
        Object selected = selectedPath == null ? null : userObject(selectedPath);
        Object selectedParent = selectedPath == null || selectedPath.getParentPath() == null
                ? null : userObject(selectedPath.getParentPath());
        boolean filtering = !search.getText().isBlank();

        var root = HostTreeModelBuilder.build(snapshot, search.getText());
        model.setRoot(root);

        for (var node : Collections.list(root.depthFirstEnumeration())) {
            var n = (DefaultMutableTreeNode) node;
            var path = new TreePath(n.getPath());
            Object uo = n.getUserObject();
            if ((uo instanceof GroupNode || uo instanceof BuiltinGroup)
                    && (filtering || expanded.contains(uo) || expanded.isEmpty())) {
                tree.expandPath(path);
            }
            // profil bisa muncul dua kali (grup asli + Favorites/Last used): cocokkan juga parent-nya
            var parent = (DefaultMutableTreeNode) n.getParent();
            if (isSame(uo, selected) && parent != null && isSame(parent.getUserObject(), selectedParent)) {
                tree.setSelectionPath(path);
            }
        }
    }

    /** User object grup ({@link GroupNode}/{@link BuiltinGroup}) yang sedang terbuka. */
    private Set<Object> expandedGroups() {
        var result = new HashSet<Object>();
        var root = (DefaultMutableTreeNode) model.getRoot();
        var e = tree.getExpandedDescendants(new TreePath(root.getPath()));
        if (e != null) {
            for (var p : Collections.list(e)) {
                Object uo = userObject(p);
                if (uo instanceof GroupNode || uo instanceof BuiltinGroup) {
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
        if (path != null) {
            tree.setSelectionPath(path);
        } else {
            tree.clearSelection();
        }
        buildPopup(path == null ? null : userObject(path)).show(tree, e.getX(), e.getY());
    }

    private JPopupMenu buildPopup(Object target) {
        var menu = new JPopupMenu();
        switch (target) {
            case HostProfile p -> {
                menu.add(item(AppIcon.TERMINAL, I18n.t("tree.menu.openTerminal"), () -> actions.open(p)));
                menu.add(item(AppIcon.SFTP, I18n.t("tree.menu.openSftp"), () -> actions.openSftp(p)));
                menu.addSeparator();
                menu.add(item(null, I18n.t("tree.menu.edit"), () -> actions.edit(p)));
                menu.add(item(null, I18n.t("tree.menu.duplicate"), () -> actions.duplicate(p)));
                boolean favorite = snapshot.isFavorite(p.id());
                menu.add(item(AppIcon.STAR, I18n.t(favorite ? "tree.menu.unfavorite" : "tree.menu.favorite"),
                        () -> actions.setFavorite(p, !favorite)));
                menu.add(item(null, I18n.t("tree.menu.delete"), () -> actions.delete(p)));
                menu.addSeparator();
                menu.add(item(null, I18n.t("tree.menu.forgetHostKey"), () -> actions.forgetHostKey(p)));
            }
            case GroupNode g -> {
                menu.add(item(AppIcon.SERVER, I18n.t("tree.menu.newHostInGroup"), () -> actions.newHost(g.path())));
                menu.add(item(AppIcon.FOLDER_PLUS, I18n.t("tree.menu.newSubgroup"), () -> actions.newGroup(g.path())));
                menu.addSeparator();
                menu.add(item(null, I18n.t("tree.menu.renameGroup"), () -> actions.renameGroup(g.path())));
                menu.add(item(null, I18n.t("tree.menu.deleteGroup"), () -> actions.deleteGroup(g.path())));
            }
            case BuiltinGroup.RECENT -> {
                var clear = item(AppIcon.BROOM, I18n.t("tree.menu.clearRecent"), actions::clearRecent);
                clear.setEnabled(!snapshot.recent().isEmpty());
                menu.add(clear);
            }
            case null, default -> {
                menu.add(item(AppIcon.SERVER, I18n.t("tree.menu.newHost"), () -> actions.newHost("")));
                menu.add(item(AppIcon.FOLDER_PLUS, I18n.t("tree.menu.newGroup"), () -> actions.newGroup("")));
            }
        }
        return menu;
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

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            Object uo = ((DefaultMutableTreeNode) value).getUserObject();
            boolean isGroup = uo instanceof GroupNode || uo instanceof BuiltinGroup;
            super.getTreeCellRendererComponent(tree, value, sel, expanded, !isGroup, row, hasFocus);
            if (uo instanceof HostProfile p) {
                var color = EnvColors.of(p.environment());
                String dot = color == null ? "" : "<font color='#%06x'>&#9679;</font> ".formatted(color.getRGB() & 0xFFFFFF);
                setText("<html>" + dot + escape(p.name()) + " <font color='gray'>" + escape(p.address()) + "</font></html>");
                setIcon(OsIcons.of(p.os()));
                String os = escape(OsIcons.label(p.os()));
                setToolTipText(p.notes() == null ? os : "<html>" + os + "<br>" + escape(p.notes()) + "</html>");
            } else if (uo instanceof BuiltinGroup b) {
                setText(switch (b) {
                    case FAVORITES -> I18n.t("tree.group.favorites");
                    case RECENT -> I18n.t("tree.group.recent");
                });
                setIcon(b == BuiltinGroup.FAVORITES ? STAR : HISTORY);
                setToolTipText(b == BuiltinGroup.FAVORITES ? I18n.t("tree.group.favorites.tooltip")
                        : I18n.t("tree.group.recent.tooltip", ProfileSnapshot.RECENT_LIMIT));
            } else {
                if (isGroup) {
                    setIcon(expanded ? FOLDER_OPEN : FOLDER);
                }
                setToolTipText(null);
            }
            return this;
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}

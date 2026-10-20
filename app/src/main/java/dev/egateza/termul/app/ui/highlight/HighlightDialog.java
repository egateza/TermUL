package dev.egateza.termul.app.ui.highlight;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.terminal.LocalProfiles;
import dev.egateza.termul.app.terminal.TerminalSettings;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.core.highlight.HighlightKeyword;
import dev.egateza.termul.core.highlight.HighlightMatcher;
import dev.egateza.termul.core.highlight.HighlightProfile;
import dev.egateza.termul.core.highlight.HighlightSettings;
import dev.egateza.termul.core.highlight.HighlightStore;
import dev.egateza.termul.core.highlight.TabbyHighlightJson;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * Pengaturan → Highlight teks: saklar utama, profil keyword (import/export format JSON Tabby), dan penugasan profil ke
 * host, grup, dan terminal lokal ("Berlaku untuk"). Semua perubahan dibuat di salinan; Simpan menulisnya sekaligus di
 * executor I/O. Prioritas per tab: host/profil lokal → grup/semua lokal → profil global.
 */
public final class HighlightDialog extends JDialog {

    private static final int COL_ENABLED = 0;
    private static final int COL_REGEX = 1;
    private static final int COL_CASE = 2;
    private static final int COL_KEYWORD = 3;
    private static final int COL_EDIT = 4;
    private static final int COL_DELETE = 5;

    private final HighlightStore store;
    private final Supplier<ProfileSnapshot> hosts;
    private final Supplier<List<LocalProfiles.Choice>> locals;
    private final Executor io;
    private final HighlightPreview.Look look;
    private final HighlightSettings saved;
    private HighlightSettings draft; // EDT
    private String currentId;        // EDT; profil yang sedang diedit

    private final JCheckBox enabled = new JCheckBox(I18n.t("highlight.enabled"));
    private final JCheckBox alternateDisable = new JCheckBox(I18n.t("highlight.alternateDisable"));
    private final JComboBox<HighlightProfile> profiles = new JComboBox<>();
    private final KeywordModel keywords = new KeywordModel();
    private final JTable table = new JTable(keywords);
    private final JCheckBox global = new JCheckBox(I18n.t("highlight.global"));
    private final JTree tree = new JTree(new DefaultMutableTreeNode());
    private final JLabel error = new JLabel(" ");
    private boolean updating; // EDT; combo sedang diisi ulang

    public HighlightDialog(Window owner, HighlightStore store, Supplier<ProfileSnapshot> hosts,
                           Supplier<List<LocalProfiles.Choice>> locals, TerminalSettings terminal, Executor io) {
        super(owner, I18n.t("highlight.title"), ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.hosts = hosts;
        this.locals = locals;
        this.io = io;
        this.look = new HighlightPreview.Look(terminal.getTerminalColorPalette(), terminal.getDefaultForeground(),
                terminal.getDefaultBackground(), terminal.getTerminalFont().deriveFont(13f));
        this.saved = store.snapshot();
        this.draft = saved;
        this.currentId = draft.profile(draft.globalProfileId()).or(() -> draft.profiles().stream().findFirst())
                .map(HighlightProfile::id).orElse(null);

        enabled.setSelected(draft.enabled());
        enabled.addActionListener(e -> draft = draft.withEnabled(enabled.isSelected()));
        alternateDisable.setSelected(draft.alternateDisable());
        alternateDisable.addActionListener(e -> draft = draft.withAlternateDisable(alternateDisable.isSelected()));
        var hint = new JLabel(I18n.t("highlight.enabledHint"));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        var switches = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        switches.add(enabled);
        switches.add(alternateDisable);

        profiles.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                String text = value instanceof HighlightProfile p ? label(p) : "";
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        profiles.addActionListener(e -> {
            if (!updating && profiles.getSelectedItem() instanceof HighlightProfile p) {
                currentId = p.id();
                refreshProfile();
            }
        });
        var profileBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        profileBar.add(new JLabel(I18n.t("highlight.profile")));
        profileBar.add(profiles);
        profileBar.add(button(AppIcon.PLUS, "highlight.profile.new", this::newProfile));
        profileBar.add(button(AppIcon.COPY, "highlight.profile.duplicate", this::duplicateProfile));
        profileBar.add(button(AppIcon.PEN, "highlight.profile.rename", this::renameProfile));
        profileBar.add(button(AppIcon.TRASH, "highlight.profile.delete", this::deleteProfile));
        profileBar.add(button(AppIcon.DOWNLOAD, "highlight.import", this::importProfiles));
        profileBar.add(button(AppIcon.UPLOAD, "highlight.export", this::exportProfile));

        var top = new JPanel(new BorderLayout(0, 6));
        top.add(switches, BorderLayout.NORTH);
        top.add(hint, BorderLayout.CENTER);
        top.add(profileBar, BorderLayout.SOUTH);

        setUpTable();
        var tableBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tableBar.add(button(AppIcon.PLUS, "highlight.keyword.add", this::addKeyword));
        tableBar.add(button(AppIcon.ARROW_UP, "highlight.keyword.up", () -> moveSelected(-1)));
        tableBar.add(button(AppIcon.ARROW_DOWN, "highlight.keyword.down", () -> moveSelected(1)));
        var orderHint = new JLabel(I18n.t("highlight.keyword.orderHint"));
        orderHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        tableBar.add(orderHint);
        var left = new JPanel(new BorderLayout(0, 6));
        left.add(tableBar, BorderLayout.NORTH);
        left.add(new JScrollPane(table), BorderLayout.CENTER);

        setUpTree();
        global.addActionListener(e -> {
            String previous = draft.globalProfileId();
            if (global.isSelected() && previous != null && !previous.equals(currentId)
                    && draft.profile(previous).isPresent()
                    && !confirmMove(I18n.t("highlight.move.global", profileName(previous), profileName(currentId)))) {
                global.setSelected(false);
                return;
            }
            draft = draft.withGlobalProfile(global.isSelected() ? currentId
                    : Objects.equals(draft.globalProfileId(), currentId) ? null : draft.globalProfileId());
            refreshProfiles();
        });
        var targetsHint = new JLabel("<html><div style='width:260px'>" + I18n.t("highlight.targets.hint")
                + "</div></html>");
        targetsHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        var rightTop = new JPanel(new BorderLayout(0, 4));
        rightTop.add(new JLabel(I18n.t("highlight.targets")), BorderLayout.NORTH);
        rightTop.add(global, BorderLayout.CENTER);
        rightTop.add(targetsHint, BorderLayout.SOUTH);
        var right = new JPanel(new BorderLayout(0, 6));
        right.add(rightTop, BorderLayout.NORTH);
        right.add(new JScrollPane(tree), BorderLayout.CENTER);

        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.62);
        split.setBorder(null);

        error.setForeground(UIManager.getColor("Component.error.focusedBorderColor"));
        var saveButton = new JButton(I18n.t("highlight.save"));
        saveButton.addActionListener(e -> save());
        var cancel = new JButton(I18n.t("highlight.cancel"));
        cancel.addActionListener(e -> cancel());
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(saveButton);
        buttons.add(cancel);
        var bottom = new JPanel(new BorderLayout());
        bottom.add(error, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);

        var content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(top, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(saveButton);
        getRootPane().registerKeyboardAction(e -> cancel(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cancel();
            }
        });

        refreshProfiles();
        setSize(new Dimension(1040, 640));
        setLocationRelativeTo(owner);
    }

    private String label(HighlightProfile p) {
        var tags = new ArrayList<String>();
        if (p.id().equals(draft.globalProfileId())) {
            tags.add(I18n.t("highlight.tag.global"));
        }
        int count = HighlightTargets.count(draft, p.id());
        if (count > 0) {
            tags.add(I18n.t("highlight.tag.targets", count));
        }
        return p.name() + (tags.isEmpty() ? "" : "  · " + String.join(" · ", tags));
    }

    private static JButton button(AppIcon icon, String key, Runnable action) {
        var b = new JButton(I18n.t(key), icon.icon());
        b.addActionListener(e -> action.run());
        return b;
    }

    private HighlightProfile current() {
        return draft.profile(currentId).orElse(null);
    }

    /** Isi ulang combo profil (nama/tag bisa berubah), lalu tampilkan profil yang sedang diedit. */
    private void refreshProfiles() {
        updating = true;
        try {
            profiles.removeAllItems();
            draft.profiles().forEach(profiles::addItem);
            if (current() == null) {
                currentId = draft.profiles().isEmpty() ? null : draft.profiles().getFirst().id();
            }
            profiles.setSelectedItem(current());
        } finally {
            updating = false;
        }
        refreshProfile();
    }

    private void refreshProfile() {
        keywords.fireTableDataChanged();
        global.setEnabled(currentId != null);
        global.setSelected(currentId != null && currentId.equals(draft.globalProfileId()));
        tree.setEnabled(currentId != null);
        tree.repaint();
        profiles.repaint();
    }

    // --- profil ---

    private void newProfile() {
        String name = Dialogs.input(this, I18n.t("highlight.title"), I18n.t("highlight.profile.namePrompt"), "");
        if (name == null || name.isBlank()) {
            return;
        }
        var p = HighlightProfile.create(name, List.of());
        draft = draft.withProfile(p);
        currentId = p.id();
        refreshProfiles();
    }

    private void duplicateProfile() {
        var p = current();
        if (p == null) {
            return;
        }
        var copy = HighlightProfile.create(I18n.t("highlight.profile.copyName", p.name()), p.keywords());
        draft = draft.withProfile(copy);
        currentId = copy.id();
        refreshProfiles();
    }

    private void renameProfile() {
        var p = current();
        if (p == null) {
            return;
        }
        String name = Dialogs.input(this, I18n.t("highlight.title"), I18n.t("highlight.profile.namePrompt"), p.name());
        if (name != null && !name.isBlank()) {
            draft = draft.withProfile(p.withName(name));
            refreshProfiles();
        }
    }

    private void deleteProfile() {
        var p = current();
        if (p == null || !Dialogs.confirm(this, I18n.t("highlight.title"),
                I18n.t("highlight.profile.deleteConfirm", p.name()))) {
            return;
        }
        draft = draft.withoutProfile(p.id());
        currentId = null;
        refreshProfiles();
    }

    private void importProfiles() {
        var chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        UiAsync.run(io, () -> {
            try {
                return TabbyHighlightJson.parse(Files.readAllBytes(file.toPath()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, this::imported, err -> Dialogs.error(this, I18n.t("highlight.import"),
                I18n.t("highlight.importFailed", file.getName(), messageOf(err))));
    }

    private void imported(List<HighlightProfile> list) {
        String first = null;
        int js = 0;
        for (HighlightProfile p : list) {
            HighlightProfile incoming = p;
            if (draft.profile(p.id()).isPresent() && !Dialogs.confirm(this, I18n.t("highlight.import"),
                    I18n.t("highlight.importReplace", draft.profile(p.id()).get().name(), p.name()))) {
                incoming = HighlightProfile.create(p.name(), p.keywords()); // simpan sebagai profil baru
            }
            js += (int) incoming.keywords().stream().filter(HighlightKeyword::js).count();
            draft = draft.withProfile(incoming);
            if (first == null) {
                first = incoming.id();
            }
        }
        currentId = first;
        refreshProfiles();
        error.setText(js == 0 ? I18n.t("highlight.imported", list.size())
                : I18n.t("highlight.importedJs", list.size(), js));
    }

    private void exportProfile() {
        var p = current();
        if (p == null) {
            return;
        }
        var chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
        chooser.setSelectedFile(new File(p.name().replaceAll("[\\\\/:*?\"<>|]", "_") + ".json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        File file = chosen.getName().contains(".") ? chosen : new File(chosen.getPath() + ".json");
        if (file.exists() && !Dialogs.confirm(this, I18n.t("highlight.export"),
                I18n.t("highlight.exportOverwrite", file.getName()))) {
            return;
        }
        byte[] json = TabbyHighlightJson.export(p);
        UiAsync.run(io, () -> {
            try {
                Files.write(file.toPath(), json);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, err -> Dialogs.error(this, I18n.t("highlight.export"),
                I18n.t("highlight.exportFailed", file.getName(), messageOf(err))))
                .thenRun(() -> SwingUtilities.invokeLater(() ->
                        error.setText(I18n.t("highlight.exported", file.getName()))));
    }

    private static String messageOf(Throwable err) {
        Throwable t = err instanceof UncheckedIOException u ? u.getCause() : err;
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }

    // --- keyword ---

    private List<HighlightKeyword> currentKeywords() {
        var p = current();
        return p == null ? List.of() : p.keywords();
    }

    private void setKeywords(List<HighlightKeyword> list) {
        var p = current();
        if (p != null) {
            draft = draft.withProfile(p.withKeywords(list));
        }
    }

    private void addKeyword() {
        if (current() == null) {
            return;
        }
        HighlightKeywordEditor.edit(this, HighlightKeyword.of("", "1"), look, null).ifPresent(k -> {
            var list = new ArrayList<>(currentKeywords());
            list.add(k);
            setKeywords(list);
            keywords.fireTableDataChanged();
            table.setRowSelectionInterval(list.size() - 1, list.size() - 1);
            profiles.repaint();
        });
    }

    private void editKeyword(int row) {
        var list = new ArrayList<>(currentKeywords());
        if (row < 0 || row >= list.size()) {
            return;
        }
        var cell = table.getCellRect(row, 0, true);
        Point anchor = new Point(cell.x, cell.y + cell.height);
        SwingUtilities.convertPointToScreen(anchor, table);
        HighlightKeywordEditor.edit(this, list.get(row), look, anchor).ifPresent(k -> {
            list.set(row, k);
            setKeywords(list);
            keywords.fireTableRowsUpdated(row, row);
        });
    }

    private void deleteKeyword(int row) {
        var list = new ArrayList<>(currentKeywords());
        if (row < 0 || row >= list.size()) {
            return;
        }
        list.remove(row);
        setKeywords(list);
        keywords.fireTableDataChanged();
    }

    private void moveSelected(int delta) {
        int row = table.getSelectedRow();
        int to = row + delta;
        if (row < 0 || to < 0 || to >= keywords.getRowCount()) {
            return;
        }
        moveKeyword(row, to);
    }

    /** Pindahkan keyword {@code from} ke posisi {@code to} (indeks setelah dipindah). */
    private void moveKeyword(int from, int to) {
        setKeywords(reorder(currentKeywords(), from, to));
        keywords.fireTableDataChanged();
        table.setRowSelectionInterval(to, to);
    }

    /** Daftar baru dengan elemen {@code from} dipindah ke indeks {@code to}. */
    static <T> List<T> reorder(List<T> list, int from, int to) {
        var out = new ArrayList<>(list);
        T item = out.remove(from);
        out.add(Math.max(0, Math.min(to, out.size())), item);
        return out;
    }

    private void setUpTable() {
        table.setRowHeight(Math.max(26, getFontMetrics(look.font()).getHeight() + 10));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        int[] widths = {44, 40, 40, 0, 36, 36};
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] > 0) {
                TableColumn c = table.getColumnModel().getColumn(i);
                c.setMaxWidth(widths[i] + 10);
                c.setPreferredWidth(widths[i]);
            }
        }
        table.getColumnModel().getColumn(COL_KEYWORD).setCellRenderer((t, value, selected, focus, row, col) -> {
            var k = currentKeywords().get(row);
            var preview = new HighlightPreview(look);
            String text = k.text() + (k.js() ? "  [JS]" : HighlightMatcher.validate(k).isPresent() ? "  ⚠" : "");
            preview.showWhole(text, k.enabled() ? k : k.withEnabled(true));
            preview.setToolTipText(k.remark());
            preview.setBorder(selected ? BorderFactory.createLineBorder(t.getSelectionBackground(), 2) : null);
            return preview;
        });
        var iconRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                           int row, int col) {
                super.getTableCellRendererComponent(t, "", selected, false, row, col);
                setIcon(col == COL_EDIT ? AppIcon.PEN.icon() : AppIcon.TRASH.icon());
                setHorizontalAlignment(CENTER);
                setToolTipText(I18n.t(col == COL_EDIT ? "highlight.keyword.edit" : "highlight.keyword.delete"));
                return this;
            }
        };
        table.getColumnModel().getColumn(COL_EDIT).setCellRenderer(iconRenderer);
        table.getColumnModel().getColumn(COL_DELETE).setCellRenderer(iconRenderer);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row < 0 || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (col == COL_EDIT || (col == COL_KEYWORD && e.getClickCount() == 2)) {
                    editKeyword(row);
                } else if (col == COL_DELETE) {
                    deleteKeyword(row);
                }
            }
        });
        // drag baris untuk mengubah urutan (keyword di atas menang)
        table.setDragEnabled(true);
        table.setDropMode(DropMode.INSERT_ROWS);
        table.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                return new StringSelection(Integer.toString(table.getSelectedRow()));
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDrop() && support.isDataFlavorSupported(DataFlavor.stringFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    int from = Integer.parseInt((String) support.getTransferable()
                            .getTransferData(DataFlavor.stringFlavor));
                    int drop = ((JTable.DropLocation) support.getDropLocation()).getRow();
                    if (from < 0 || from >= keywords.getRowCount()) {
                        return false;
                    }
                    int to = drop > from ? drop - 1 : drop;
                    if (to != from) {
                        moveKeyword(from, to);
                    }
                    return true;
                } catch (java.awt.datatransfer.UnsupportedFlavorException | IOException | NumberFormatException e) {
                    return false;
                }
            }
        });
    }

    private final class KeywordModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return currentKeywords().size();
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public String getColumnName(int col) {
            return switch (col) {
                case COL_ENABLED -> I18n.t("highlight.col.enabled");
                case COL_REGEX -> "(.*)";
                case COL_CASE -> "Aa";
                case COL_KEYWORD -> I18n.t("highlight.col.keyword");
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return col <= COL_CASE ? Boolean.class : Object.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col <= COL_CASE && !currentKeywords().get(row).js();
        }

        @Override
        public Object getValueAt(int row, int col) {
            var k = currentKeywords().get(row);
            return switch (col) {
                case COL_ENABLED -> k.enabled();
                case COL_REGEX -> k.regExp();
                case COL_CASE -> k.caseSensitive();
                default -> k.text();
            };
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            var list = new ArrayList<>(currentKeywords());
            var k = list.get(row);
            boolean on = Boolean.TRUE.equals(value);
            list.set(row, switch (col) {
                case COL_ENABLED -> k.withEnabled(on);
                case COL_REGEX -> k.withRegExp(on);
                case COL_CASE -> k.withCaseSensitive(on);
                default -> k;
            });
            setKeywords(list);
            fireTableRowsUpdated(row, row);
        }
    }

    // --- berlaku untuk ---

    private void setUpTree() {
        var root = HighlightTargets.tree(hosts.get(), locals.get());
        tree.setModel(new DefaultTreeModel(root));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(0);
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        tree.setCellRenderer((t, value, selected, expanded, leaf, row, focus) -> targetRow(value, selected));
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null || currentId == null || !tree.isEnabled() || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                var bounds = tree.getPathBounds(path);
                if (bounds != null && e.getX() < bounds.x + 24
                        && path.getLastPathComponent() instanceof DefaultMutableTreeNode n
                        && n.getUserObject() instanceof HighlightTargets.Target target
                        && !HighlightTargets.inheritedChecked(target, draft, currentId)) {
                    var from = HighlightTargets.takenFrom(target, draft, currentId);
                    if (from.isPresent() && !confirmMove(I18n.t("highlight.move.message", describe(target),
                            profileName(from.get()), profileName(currentId)))) {
                        return;
                    }
                    draft = HighlightTargets.toggle(target, draft, currentId);
                    tree.repaint();
                    profiles.repaint();
                }
            }
        });
    }

    private Component targetRow(Object value, boolean selected) {
        var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        panel.setOpaque(false);
        if (!(value instanceof DefaultMutableTreeNode n) || !(n.getUserObject() instanceof HighlightTargets.Target t)) {
            return panel;
        }
        var box = new JCheckBox();
        box.setOpaque(false);
        boolean inherited = currentId != null && HighlightTargets.inheritedChecked(t, draft, currentId);
        box.setEnabled(tree.isEnabled() && !inherited);
        box.setSelected(currentId != null && (inherited || HighlightTargets.checked(t, draft, currentId)));
        var label = new JLabel(switch (t) {
            case HighlightTargets.Group g -> g.name();
            case HighlightTargets.Host h -> h.profile().name();
            case HighlightTargets.AllLocals _ -> I18n.t("highlight.target.locals");
            case HighlightTargets.Local l -> l.choice().name();
        });
        label.setIcon(switch (t) {
            case HighlightTargets.Group _ -> AppIcon.FOLDER.icon();
            case HighlightTargets.Host _ -> AppIcon.SERVER.icon();
            case HighlightTargets.AllLocals _, HighlightTargets.Local _ -> AppIcon.TERMINAL.icon();
        });
        panel.add(box);
        panel.add(label);
        String note = currentId == null ? ""
                : HighlightTargets.note(t, draft, currentId, HighlightTargets.names(draft.profiles()));
        if (!note.isEmpty()) {
            var noteLabel = new JLabel(note);
            noteLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            panel.add(noteLabel);
        }
        return panel;
    }

    /** Target dengan jenisnya untuk kalimat konfirmasi, mis. grup “Prod”. */
    private static String describe(HighlightTargets.Target target) {
        return switch (target) {
            case HighlightTargets.Group g -> I18n.t("highlight.target.kind.group", g.path());
            case HighlightTargets.Host h -> I18n.t("highlight.target.kind.host", h.profile().name());
            case HighlightTargets.AllLocals _ -> I18n.t("highlight.target.kind.allLocals");
            case HighlightTargets.Local l -> I18n.t("highlight.target.kind.local", l.choice().name());
        };
    }

    private String profileName(String id) {
        return draft.profile(id).map(HighlightProfile::name).orElse("?");
    }

    /** Konfirmasi memindahkan target (atau status global) dari profil lain ke profil yang sedang diedit. */
    private boolean confirmMove(String message) {
        Object[] options = {I18n.t("highlight.move.ok"), I18n.t("highlight.cancel")};
        return JOptionPane.showOptionDialog(this, message, I18n.t("highlight.move.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]) == 0;
    }

    // --- simpan ---

    private void save() {
        for (HighlightProfile p : draft.profiles()) {
            for (int i = 0; i < p.keywords().size(); i++) {
                var k = p.keywords().get(i);
                var problem = k.usable() ? HighlightMatcher.validate(k) : java.util.Optional.<String>empty();
                if (problem.isPresent()) {
                    currentId = p.id();
                    refreshProfiles();
                    table.setRowSelectionInterval(i, i);
                    error.setText(I18n.t("highlight.invalid", p.name(), k.text(), problem.get()));
                    return;
                }
            }
        }
        HighlightSettings toSave = draft;
        UiAsync.run(io, () -> store.save(toSave),
                        err -> error.setText(I18n.t("highlight.saveFailed", messageOf(err))))
                .thenRun(() -> SwingUtilities.invokeLater(this::dispose));
    }

    private void cancel() {
        if (!draft.equals(saved) && !Dialogs.confirm(this, I18n.t("highlight.title"), I18n.t("highlight.discard"))) {
            return;
        }
        dispose();
    }
}

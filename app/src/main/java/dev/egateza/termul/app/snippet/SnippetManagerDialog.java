package dev.egateza.termul.app.snippet;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.ShortcutAction;
import dev.egateza.termul.app.ui.Shortcuts;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.core.snippet.DangerousCommands;
import dev.egateza.termul.core.snippet.Snippet;
import dev.egateza.termul.core.snippet.SnippetCategory;
import dev.egateza.termul.core.snippet.SnippetScope;
import dev.egateza.termul.core.snippet.SnippetStore;
import dev.egateza.termul.core.snippet.SnippetVariables;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

/**
 * Snippet manager (Terminal → Snippet manager…, {@code Ctrl/Cmd+Shift+S}), mengikuti prototipe "TermUL Desktop":
 * window tanpa dekorasi di atas latar gelap, header dengan target, toolbar cari + Snippet baru, kategori di kiri, daftar
 * di tengah, detail (isi variabel, tempel, salin) atau form edit di kanan, dan status bar. Snippet ditempel ke terminal
 * yang aktif saat dialog dibuka. Simpan/hapus berjalan di executor I/O; UI hanya di EDT.
 */
public final class SnippetManagerDialog extends JDialog {

    private static final String VIEW = "view";
    private static final String NONE = "none";
    private static final String EDIT = "edit";
    private static final String TABLE = "table";
    private static final String EMPTY = "empty";
    private static final String EXPORT = "M12 15V4M7 9l5-5 5 5M5 20h14";
    private static final String SORT = "M8 4v16M4 8l4-4 4 4M16 20V4M12 16l4 4 4-4";

    /** Item navigasi kiri: semua, favorit, satu kategori, atau tanpa kategori. */
    record Nav(Kind kind, String categoryId, String label, int count) {
        enum Kind { ALL, FAVORITES, CATEGORY, NONE }

        boolean includes(Snippet s, List<String> categoryIds) {
            return switch (kind) {
                case ALL -> true;
                case FAVORITES -> s.favorite();
                case CATEGORY -> s.category().equals(categoryId);
                case NONE -> !categoryIds.contains(s.category());
            };
        }
    }

    /** Pilihan "Berlaku untuk". */
    record ScopeChoice(SnippetScope scope, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** Pilihan "Kategori" di form edit; {@code id} kosong = Tanpa kategori. */
    record CategoryChoice(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    record EditorState(String title, String command, boolean enter, String categoryId, SnippetScope scope,
                       boolean favorite, boolean dangerous, Map<String, String> variables) {
    }

    private final SnippetSender sender;
    private final SnippetStore store;
    private final Executor io;
    private final SnippetTarget target; // null = tidak ada terminal aktif
    private final List<String> groups;

    private final JPanel nav = new JPanel();
    private final JTextField search = new JTextField();
    private final JLabel count = SnippetUi.mutedLabel("");
    private final SnippetTableModel tableModel = new SnippetTableModel();
    private final JTable table = new JTable(tableModel);
    private final CardLayout listCards = new CardLayout();
    private final JPanel listArea = new JPanel(listCards);
    private final JLabel emptyTitle = SnippetUi.label("", SnippetUi.font(0, Font.PLAIN), SnippetUi.fg());
    private final JLabel emptyHint = SnippetUi.label("", SnippetUi.font(0, Font.PLAIN), SnippetUi.muted());
    private final CardLayout cards = new CardLayout();
    private final JPanel detail = new JPanel(cards);
    private final JPanel viewCard = new JPanel(new BorderLayout());
    private final JPanel editCard = new JPanel(new BorderLayout());
    private final JLabel footer = new JLabel();
    private final Map<UUID, Map<String, String>> values = new HashMap<>(); // EDT
    private Nav.Kind navKind = Nav.Kind.ALL; // EDT
    private String navCategory = "";        // EDT
    private UUID selected;                  // EDT
    private int hoverRow = -1;              // EDT
    private boolean editing;                // EDT
    private Timer toastTimer;               // EDT
    private boolean reloading;              // EDT; abaikan event seleksi saat daftar diisi ulang
    private boolean sorting;                // EDT; mode Atur urutan: semua snippet, urutan manual, seret baris
    private final SnippetUi.Button sortButton = new SnippetUi.Button(I18n.t("snippet.manager.reorder"),
            SnippetUi.stroke(SORT, 14, SnippetUi::fg, 2), SnippetUi.Variant.DEFAULT);
    private final JLabel sortBanner = new JLabel();
    private final Runnable undim;

    /**
     * @param target terminal tempat snippet ditempel (tab aktif), null kalau tidak ada
     * @param groups grup host yang ada (pilihan "Berlaku untuk")
     */
    public SnippetManagerDialog(Window owner, SnippetSender sender, Executor io, SnippetTarget target,
                                List<String> groups) {
        super(owner, I18n.t("snippet.manager.title"), ModalityType.APPLICATION_MODAL);
        this.sender = sender;
        this.store = sender.store();
        this.io = io;
        this.target = target;
        this.groups = groups;
        setUndecorated(true);
        boolean round = SnippetUi.roundWindow(this);

        var root = new SnippetUi.RoundPanel(new BorderLayout(), SnippetUi::panel, SnippetUi::frame, round ? 16 : 0);
        root.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        var top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header(round), BorderLayout.NORTH);
        top.add(toolbar(), BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);

        var body = new JPanel(new BorderLayout());
        body.setBackground(SnippetUi.panel());
        body.add(navPane(), BorderLayout.WEST);
        body.add(listPane(), BorderLayout.CENTER);
        detail.setPreferredSize(new Dimension(392, 0));
        detail.setBackground(SnippetUi.panel());
        viewCard.setBackground(SnippetUi.panel());
        editCard.setBackground(SnippetUi.panel());
        detail.add(viewCard, VIEW);
        detail.add(noneCard(), NONE);
        detail.add(editCard, EDIT);
        body.add(detail, BorderLayout.EAST);
        root.add(body, BorderLayout.CENTER);
        root.add(statusBar(round), BorderLayout.SOUTH);
        setContentPane(root);
        bindKeys();
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        showHint();

        reload(null);
        if (tableModel.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        } else if (store.snapshot().isEmpty()) {
            startNew();
        }
        int w = owner == null ? 1120 : Math.min(1120, owner.getWidth() - 32);
        int h = owner == null ? 680 : Math.min(680, owner.getHeight() - 32);
        setSize(Math.max(760, w), Math.max(460, h));
        SnippetUi.keepSizeAcrossScreens(this);
        setLocationRelativeTo(owner);
        undim = SnippetUi.dim(owner);
    }

    @Override
    public void dispose() {
        undim.run();
        super.dispose();
    }

    // --- header, toolbar, status bar ---

    /** Header 40 px: petir, judul, chip target, shortcut, tutup. Seret untuk memindah dialog. */
    private JComponent header(boolean round) {
        var bar = new EdgePanel(true, round);
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(0, 14, 0, 6)));
        bar.setPreferredSize(new Dimension(0, 40));
        var title = SnippetUi.label(I18n.t("snippet.manager.title"), SnippetUi.font(0, Font.PLAIN), SnippetUi.fg());
        title.setIcon(SnippetUi.stroke(SnippetUi.BOLT, 15, SnippetUi::favorite, 2));
        title.setIconTextGap(10);
        bar.add(title);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(targetChip());
        bar.add(Box.createHorizontalGlue());
        bar.add(SnippetUi.mutedLabel(Shortcuts.text(ShortcutAction.SNIPPETS)));
        bar.add(Box.createHorizontalStrut(6));
        var close = new SnippetUi.Button(null, SnippetUi.stroke(SnippetUi.CLOSE, 14, SnippetUi::muted, 2),
                SnippetUi.Variant.ICON);
        close.setToolTipText(I18n.t("snippet.manager.close"));
        close.getAccessibleContext().setAccessibleName(I18n.t("snippet.manager.close"));
        close.addActionListener(e -> dispose());
        bar.add(close);
        var drag = new MouseAdapter() {
            private Point start;

            @Override
            public void mousePressed(MouseEvent e) {
                start = e.getLocationOnScreen();
                start.translate(-getX(), -getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (start != null) {
                    var p = e.getLocationOnScreen();
                    setLocation(p.x - start.x, p.y - start.y);
                }
            }
        };
        bar.addMouseListener(drag);
        bar.addMouseMotionListener(drag);
        return bar;
    }

    private JComponent targetChip() {
        if (target == null) {
            var chip = new SnippetUi.Chip(22, 10, SnippetUi.mutedLabel(I18n.t("snippet.manager.noTarget")));
            chip.setMaximumSize(chip.getPreferredSize()); // jangan melar memenuhi header (BoxLayout)
            return chip;
        }
        var dot = new JLabel(SnippetUi.dot(7, target.canPaste() ? SnippetUi::success : SnippetUi::muted));
        var chip = new SnippetUi.Chip(22, 10, dot, SnippetUi.mutedLabel(I18n.t("snippet.manager.target")),
                SnippetUi.label(target.name(), SnippetUi.font(0, Font.PLAIN), SnippetUi.fg()),
                SnippetUi.label(target.address(), SnippetUi.font(0, Font.PLAIN), SnippetUi.muted()));
        chip.setMaximumSize(chip.getPreferredSize());
        return chip;
    }

    /** Toolbar 48 px: cari (320 px), jumlah, Ekspor, Atur urutan, Snippet baru. */
    private JComponent toolbar() {
        SnippetUi.styleField(search, 30);
        search.setFont(SnippetUi.font(0, Font.PLAIN));
        search.putClientProperty("JTextField.placeholderText",
                I18n.t("snippet.manager.search", Shortcuts.text(Shortcuts.menu(KeyEvent.VK_F))));
        search.putClientProperty("JTextField.leadingIcon", SnippetUi.search(14));
        search.putClientProperty("JTextField.showClearButton", true);
        search.getAccessibleContext().setAccessibleName(I18n.t("snippet.manager.searchName"));
        search.getDocument().addDocumentListener(SnippetQuickPick.onChange(() -> reload(selected)));
        search.setPreferredSize(new Dimension(320, 30));
        search.setMaximumSize(new Dimension(320, 30));
        var newButton = new SnippetUi.Button(I18n.t("snippet.manager.new"),
                SnippetUi.stroke(SnippetUi.PLUS, 14, () -> SnippetUi.onAccent(SnippetUi.accent()), 2.4f),
                SnippetUi.Variant.PRIMARY);
        newButton.setToolTipText(I18n.t("snippet.manager.new.tip", Shortcuts.text(Shortcuts.menu(KeyEvent.VK_N))));
        newButton.addActionListener(e -> startNew());
        var exportButton = new SnippetUi.Button(I18n.t("snippet.manager.export"),
                SnippetUi.stroke(EXPORT, 14, SnippetUi::fg, 2), SnippetUi.Variant.DEFAULT);
        exportButton.setToolTipText(I18n.t("snippet.manager.export.tip",
                Shortcuts.text(Shortcuts.menu(KeyEvent.VK_E))));
        exportButton.addActionListener(e -> openExport());
        sortButton.setToolTipText(I18n.t("snippet.manager.reorder.tip"));
        sortButton.addActionListener(e -> setSorting(!sorting));

        var bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBackground(SnippetUi.panel());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(9, 14, 9, 14)));
        bar.add(search);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(count);
        bar.add(Box.createHorizontalGlue());
        bar.add(exportButton);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(sortButton);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(newButton);
        return bar;
    }

    private JComponent statusBar(boolean round) {
        var bar = new EdgePanel(false, round);
        bar.setLayout(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(0, 14, 0, 14)));
        bar.setPreferredSize(new Dimension(0, 28));
        footer.setFont(SnippetUi.font(0, Font.PLAIN));
        footer.setIconTextGap(6);
        bar.add(footer, BorderLayout.CENTER);
        return bar;
    }

    /** Panel bar (header/status bar) yang sudut atas / bawahnya ikut membulat dengan bingkai dialog. */
    private static final class EdgePanel extends JPanel {
        private final boolean top;
        private final boolean round;

        EdgePanel(boolean top, boolean round) {
            this.top = top;
            this.round = round;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(SnippetUi.bar());
            int arc = round ? 14 : 0;
            g2.fill(new RoundRectangle2D.Float(0, top ? 0 : -arc, getWidth(), getHeight() + arc, arc, arc));
            g2.dispose();
        }
    }

    // --- navigasi kiri ---

    private JComponent navPane() {
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBackground(SnippetUi.bar());
        nav.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
        nav.getAccessibleContext().setAccessibleName(I18n.t("snippet.manager.categories"));
        var scroll = new JScrollPane(nav);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SnippetUi.bar());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // "Kelola kategori…" selalu di bawah navigasi (prototipe), tidak ikut tergulung
        var manage = new NavItem(new Nav(Nav.Kind.ALL, "\u0000manage", I18n.t("snippet.nav.manageCategories"), -1),
                SnippetUi.sliders(14), false, this::openCategories);
        manage.setToolTipText(I18n.t("snippet.nav.manageCategories.tip"));
        var bottom = new JPanel(new BorderLayout());
        bottom.setBackground(SnippetUi.bar());
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 10, 8));
        bottom.add(manage);

        var pane = new JPanel(new BorderLayout());
        pane.setBackground(SnippetUi.bar());
        pane.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, SnippetUi.border()));
        pane.setPreferredSize(new Dimension(196, 0));
        pane.add(scroll, BorderLayout.CENTER);
        pane.add(bottom, BorderLayout.SOUTH);
        return pane;
    }

    private void rebuildNav(List<Snippet> all, List<String> ids) {
        nav.removeAll();
        addNav(new Nav(Nav.Kind.ALL, "", I18n.t("snippet.nav.all"), all.size()),
                SnippetUi.stroke(SnippetUi.BOLT, 14, SnippetUi::favorite, 2));
        addNav(new Nav(Nav.Kind.FAVORITES, "", I18n.t("snippet.nav.favorites"),
                (int) all.stream().filter(Snippet::favorite).count()), SnippetUi.star(true, 14));
        var section = SnippetUi.section(I18n.t("snippet.nav.categories"));
        section.setBorder(BorderFactory.createEmptyBorder(14, 10, 6, 10));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(section);
        for (var c : store.categories()) {
            addNav(new Nav(Nav.Kind.CATEGORY, c.id(), c.name(),
                    (int) all.stream().filter(s -> s.category().equals(c.id())).count()),
                    SnippetUi.folder(14, SnippetUi::accent));
        }
        addNav(new Nav(Nav.Kind.NONE, "", I18n.t("snippet.nav.uncategorized"),
                (int) all.stream().filter(s -> !ids.contains(s.category())).count()),
                SnippetUi.folder(14, SnippetUi::muted));
        nav.revalidate();
        nav.repaint();
    }

    private void addNav(Nav n, Icon icon) {
        boolean on = !sorting && n.kind() == navKind && n.categoryId().equals(navCategory);
        var item = new NavItem(n, icon, on, () -> {
            navKind = n.kind();
            navCategory = n.categoryId();
            if (sorting) {
                setSorting(false); // memilih kategori mengakhiri mode urutan (prototipe)
            } else {
                reload(selected);
            }
        });
        item.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(item);
        nav.add(Box.createVerticalStrut(2));
    }

    /** Item navigasi 28 px, sudut 4 px, latar seleksi / hover (prototipe {@code .nav-item}). */
    private final class NavItem extends JPanel {
        private final boolean on;
        private boolean hover;

        /** @param n {@code count} −1 = tanpa angka (aksi, bukan filter) */
        NavItem(Nav n, Icon icon, boolean on, Runnable pick) {
            super(new BorderLayout(8, 0));
            this.on = on;
            setOpaque(false);
            setFocusable(true);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setPreferredSize(new Dimension(180, 28));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            boolean action = n.count() < 0;
            var label = SnippetUi.label(n.label(), SnippetUi.font(0, Font.PLAIN), on ? SnippetUi.onSelection()
                    : action ? SnippetUi.muted() : SnippetUi.fg());
            label.setIcon(icon);
            label.setIconTextGap(8);
            add(label, BorderLayout.CENTER);
            if (!action) {
                add(SnippetUi.label(String.valueOf(n.count()), SnippetUi.font(0, Font.PLAIN), SnippetUi.muted()),
                        BorderLayout.EAST);
            }
            getAccessibleContext().setAccessibleName(n.label());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    pick.run();
                }
            });
            getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pick");
            getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "pick");
            getActionMap().put("pick", SnippetQuickPick.action(pick));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (on || hover || isFocusOwner()) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(on ? SnippetUi.selection() : SnippetUi.hover());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                if (isFocusOwner()) {
                    g2.setColor(SnippetUi.accent());
                    g2.setStroke(new java.awt.BasicStroke(2));
                    g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 7, 7);
                }
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    // --- daftar ---

    private JComponent listPane() {
        table.setRowHeight(40);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setBackground(SnippetUi.panel());
        table.setFocusable(true);
        var header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setResizingAllowed(false);
        header.setPreferredSize(new Dimension(0, 28));
        header.setBackground(SnippetUi.panel());
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.border()));
        header.setDefaultRenderer((t, value, sel, focus, row, column) -> {
            var l = SnippetUi.label(Objects.toString(value, ""), SnippetUi.font(0, Font.PLAIN), SnippetUi.muted());
            l.setOpaque(true);
            l.setBackground(SnippetUi.panel());
            l.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
            return l;
        });
        var columns = table.getColumnModel();
        fix(columns.getColumn(0), 36);
        columns.getColumn(1).setPreferredWidth(220);
        columns.getColumn(2).setPreferredWidth(280);
        fix(columns.getColumn(3), 104);
        columns.getColumn(0).setCellRenderer(new StarRenderer());
        columns.getColumn(1).setCellRenderer(new TitleRenderer());
        columns.getColumn(2).setCellRenderer(new TextRenderer(true));
        columns.getColumn(3).setCellRenderer(new TextRenderer(false));
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !reloading) {
                int row = table.getSelectedRow();
                select(row < 0 ? null : tableModel.at(row).id());
            }
        });
        var mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0 || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                var s = tableModel.at(row);
                if (sorting) {
                    return; // klik di mode urutan hanya memilih / menyeret
                }
                if (table.columnAtPoint(e.getPoint()) == 0 && e.getClickCount() == 1) {
                    toggleFavorite(s);
                } else if (e.getClickCount() == 2) {
                    paste(s);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row != hoverRow) {
                    hoverRow = row;
                    table.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                table.repaint();
            }
        };
        table.addMouseListener(mouse);
        table.addMouseMotionListener(mouse);
        table.setToolTipText(target == null ? null : I18n.t("snippet.manager.doubleClick", target.name()));
        var scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, SnippetUi.border()));
        scroll.getViewport().setBackground(SnippetUi.panel());

        var empty = new JPanel(new GridBagLayout());
        empty.setBackground(SnippetUi.panel());
        var texts = new JPanel(new GridLayout(2, 1, 0, 6));
        texts.setOpaque(false);
        emptyTitle.setHorizontalAlignment(JLabel.CENTER);
        emptyHint.setHorizontalAlignment(JLabel.CENTER);
        texts.add(emptyTitle);
        texts.add(emptyHint);
        empty.add(texts);
        var emptyWrap = new JPanel(new BorderLayout());
        emptyWrap.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, SnippetUi.border()));
        emptyWrap.add(empty);
        table.setDragEnabled(true);
        table.setDropMode(javax.swing.DropMode.INSERT_ROWS);
        table.setTransferHandler(new ReorderHandler());

        sortBanner.setText("<html>" + SnippetUi.esc(I18n.t("snippet.manager.reorder.banner")) + "</html>");
        sortBanner.setFont(SnippetUi.font(-1, Font.PLAIN));
        sortBanner.setForeground(SnippetUi.muted());
        sortBanner.setOpaque(true);
        sortBanner.setBackground(SnippetUi.chip());
        sortBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, SnippetUi.border()),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        sortBanner.setVisible(false);
        var tableCard = new JPanel(new BorderLayout());
        tableCard.add(sortBanner, BorderLayout.NORTH);
        tableCard.add(scroll, BorderLayout.CENTER);
        listArea.add(tableCard, TABLE);
        listArea.add(emptyWrap, EMPTY);
        return listArea;
    }

    /**
     * Seret baris di mode Atur urutan. Data yang dipindah hanya indeks baris (di dalam tabel ini), lalu disimpan lewat
     * {@link SnippetStore#moveSnippet}; di luar mode itu seret tidak melakukan apa-apa.
     */
    private final class ReorderHandler extends javax.swing.TransferHandler {
        private static final java.awt.datatransfer.DataFlavor ROW = new java.awt.datatransfer.DataFlavor(
                Integer.class, "snippet row");

        @Override
        public int getSourceActions(JComponent c) {
            return sorting ? MOVE : NONE;
        }

        @Override
        protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
            int row = table.getSelectedRow();
            return new java.awt.datatransfer.Transferable() {
                @Override
                public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                    return new java.awt.datatransfer.DataFlavor[] {ROW};
                }

                @Override
                public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                    return ROW.equals(flavor);
                }

                @Override
                public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) {
                    return row;
                }
            };
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return sorting && support.isDrop() && support.isDataFlavorSupported(ROW);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                int from = (Integer) support.getTransferable().getTransferData(ROW);
                int to = ((javax.swing.JTable.DropLocation) support.getDropLocation()).getRow();
                moveRow(from, dropIndex(from, to));
                return true;
            } catch (java.awt.datatransfer.UnsupportedFlavorException | java.io.IOException e) {
                return false;
            }
        }
    }

    /** Indeks tujuan setelah baris {@code from} diambil, untuk sisipan sebelum baris {@code insertBefore}. */
    static int dropIndex(int from, int insertBefore) {
        return insertBefore > from ? insertBefore - 1 : insertBefore;
    }

    private void moveRow(int from, int to) {
        if (from < 0 || from >= tableModel.getRowCount() || from == to) {
            return;
        }
        var s = tableModel.at(from);
        UiAsync.run(io, () -> store.moveSnippet(s.id(), to), this::saveFailed)
                .thenRun(() -> SwingUtilities.invokeLater(() -> reload(s.id())));
    }

    private void setSorting(boolean on) {
        if (editing && on) {
            cancelEdit();
        }
        sorting = on;
        sortButton.setText(I18n.t(on ? "snippet.manager.reorder.done" : "snippet.manager.reorder"));
        sortButton.getModel().setPressed(false);
        sortBanner.setVisible(on);
        search.setEnabled(!on);
        reload(selected);
    }

    private static void fix(javax.swing.table.TableColumn column, int width) {
        column.setMinWidth(width);
        column.setMaxWidth(width);
        column.setPreferredWidth(width);
    }

    private JComponent noneCard() {
        var box = new JPanel(new GridBagLayout());
        box.setBackground(SnippetUi.panel());
        var inner = new JPanel(new GridLayout(2, 1, 0, 6));
        inner.setOpaque(false);
        var title = SnippetUi.label(I18n.t("snippet.manager.none.title"), SnippetUi.font(0, Font.PLAIN),
                SnippetUi.fg());
        title.setHorizontalAlignment(JLabel.CENTER);
        var hint = SnippetUi.label(I18n.t("snippet.manager.none.hint"), SnippetUi.font(0, Font.PLAIN),
                SnippetUi.muted());
        hint.setHorizontalAlignment(JLabel.CENTER);
        inner.add(title);
        inner.add(hint);
        box.add(inner);
        return box;
    }

    private void bindKeys() {
        var root = getRootPane();
        key(root, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape", () -> {
            if (editing) {
                cancelEdit();
            } else {
                dispose();
            }
        });
        key(root, Shortcuts.menu(KeyEvent.VK_N), "new", this::startNew);
        key(root, Shortcuts.menu(KeyEvent.VK_F), "search", search::requestFocusInWindow);
        key(root, Shortcuts.menu(KeyEvent.VK_E), "export", this::openExport);
        // pengganti seret untuk keyboard: Alt+↑ / Alt+↓ menggeser snippet terpilih di mode urutan
        map(KeyStroke.getKeyStroke(KeyEvent.VK_UP, java.awt.event.InputEvent.ALT_DOWN_MASK), "moveUp", () -> moveBy(-1));
        map(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, java.awt.event.InputEvent.ALT_DOWN_MASK), "moveDown",
                () -> moveBy(1));
        var map = table.getInputMap(JComponent.WHEN_FOCUSED);
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pasteSnippet");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "editSnippet");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSnippet");
        table.getActionMap().put("pasteSnippet", SnippetQuickPick.action(() -> current().ifPresent(this::paste)));
        table.getActionMap().put("editSnippet", SnippetQuickPick.action(() -> current().ifPresent(this::startEdit)));
        table.getActionMap().put("deleteSnippet", SnippetQuickPick.action(() -> current().ifPresent(this::delete)));
    }

    private void map(KeyStroke ks, String name, Runnable run) {
        table.getInputMap(JComponent.WHEN_FOCUSED).put(ks, name);
        table.getActionMap().put(name, SnippetQuickPick.action(run));
    }

    private void moveBy(int delta) {
        int row = table.getSelectedRow();
        if (sorting && row >= 0) {
            moveRow(row, Math.clamp(row + delta, 0, tableModel.getRowCount() - 1));
        }
    }

    private static void key(JComponent c, KeyStroke ks, String name, Runnable run) {
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        c.getActionMap().put(name, SnippetQuickPick.action(run));
    }

    // --- data ---

    private java.util.Optional<Snippet> current() {
        return store.snapshot().stream().filter(s -> s.id().equals(selected)).findFirst();
    }

    private List<String> categoryIds() {
        return store.categories().stream().map(SnippetCategory::id).toList();
    }

    /** Snippet di tampilan sekarang: kategori + pencarian, favorit di atas, sisanya urutan manual. */
    static List<Snippet> shown(List<Snippet> all, Nav nav, List<String> categoryIds, String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        var out = new ArrayList<>(all.stream().filter(s -> nav.includes(s, categoryIds))
                .filter(s -> q.isEmpty() || (s.title() + " " + s.command()).toLowerCase(Locale.ROOT).contains(q))
                .toList());
        out.sort((a, b) -> Boolean.compare(b.favorite(), a.favorite()));
        return out;
    }

    /** Isi ulang navigasi dan daftar; {@code keep} tetap terpilih kalau masih tampil. */
    private void reload(UUID keep) {
        reloading = true;
        try {
            var all = store.snapshot();
            var ids = categoryIds();
            boolean exists = switch (navKind) {
                case CATEGORY -> ids.contains(navCategory);
                default -> true;
            };
            if (!exists) { // kategori sudah tidak ada
                navKind = Nav.Kind.ALL;
                navCategory = "";
            }
            rebuildNav(all, ids);
            var active = new Nav(navKind, navCategory, "", 0);
            // mode urutan: semua snippet apa adanya (urutan manual), tanpa filter dan tanpa favorit di atas
            var rows = sorting ? all : shown(all, active, ids, search.getText());
            tableModel.set(rows);
            count.setText(rows.size() == all.size() ? I18n.t("snippet.manager.count", all.size())
                    : I18n.t("snippet.manager.countFiltered", rows.size(), all.size()));
            int at = -1;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).id().equals(keep)) {
                    at = i;
                }
            }
            if (at >= 0) {
                table.setRowSelectionInterval(at, at);
            }
            boolean none = all.isEmpty();
            emptyTitle.setText(I18n.t(none ? "snippet.manager.empty.noneTitle" : "snippet.manager.empty.noMatchTitle"));
            emptyHint.setText("<html><div style='text-align:center;width:300px'>" + SnippetUi.esc(I18n.t(none
                    ? "snippet.manager.empty.none" : "snippet.manager.empty.noMatch")) + "</div></html>");
            listCards.show(listArea, rows.isEmpty() ? EMPTY : TABLE);
        } finally {
            reloading = false;
        }
        if (!editing) {
            select(table.getSelectedRow() < 0 ? null : tableModel.at(table.getSelectedRow()).id());
        }
    }

    private void select(UUID id) {
        if (editing) {
            return;
        }
        selected = id;
        var s = current();
        if (s.isEmpty()) {
            cards.show(detail, NONE);
            return;
        }
        buildView(s.get());
        cards.show(detail, VIEW);
    }

    // --- detail ---

    private static GridBagConstraints column() {
        var gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.NORTHWEST;
        return gc;
    }

    private static void add(JPanel box, GridBagConstraints gc, Component c, int gapBelow) {
        gc.insets = new Insets(0, 0, gapBelow, 0);
        box.add(c, gc);
        gc.gridy++;
    }

    private static JScrollPane scroll(JComponent content) {
        var scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SnippetUi.panel());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private void buildView(Snippet s) {
        viewCard.removeAll();
        var vals = values.computeIfAbsent(s.id(), id -> sender.initialValues(s, target));
        var box = new SnippetUi.TrackingPanel(new GridBagLayout());
        box.setBackground(SnippetUi.panel());
        box.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        var gc = column();

        var head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        var titles = new JPanel(new BorderLayout(0, 4));
        titles.setOpaque(false);
        titles.add(SnippetUi.text(s.title(), SnippetUi.font(2, Font.BOLD), SnippetUi.fg()), BorderLayout.NORTH);
        titles.add(SnippetUi.label(categoryLabel(s) + " · " + scopeLabel(s.scope()), SnippetUi.font(0, Font.PLAIN),
                SnippetUi.muted()), BorderLayout.CENTER);
        head.add(titles, BorderLayout.CENTER);
        var star = new SnippetUi.Button(null, s.favorite() ? SnippetUi.star(true, 16)
                : SnippetUi.stroke(SnippetUi.STAR, 16, SnippetUi::muted, 1.8f), SnippetUi.Variant.ICON);
        star.setToolTipText(I18n.t(s.favorite() ? "snippet.favorite.remove" : "snippet.favorite.add"));
        star.getAccessibleContext().setAccessibleName(star.getToolTipText());
        star.addActionListener(e -> toggleFavorite(s));
        var starWrap = new JPanel(new BorderLayout());
        starWrap.setOpaque(false);
        starWrap.add(star, BorderLayout.NORTH);
        head.add(starWrap, BorderLayout.EAST);
        add(box, gc, head, 14);

        if (s.isDangerous()) {
            String why = DangerousCommands.detect(s.command()).map(w -> I18n.t("snippet.danger.why", w))
                    .orElse(I18n.t("snippet.danger.marked"));
            var text = new SnippetUi.Paragraph(SnippetUi.font(0, Font.PLAIN), SnippetUi.fg())
                    .add(I18n.t("snippet.danger.title") + " ", SnippetUi.danger(), false, false)
                    .add(why + " " + I18n.t("snippet.danger.confirmNote"));
            var warn = SnippetUi.dangerBox(text, 0.45f);
            warn.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            var icon = new JLabel(SnippetUi.stroke(SnippetUi.WARN, 16, SnippetUi::danger, 2));
            icon.setVerticalAlignment(JLabel.TOP);
            warn.add(icon, BorderLayout.WEST);
            add(box, gc, warn, 14);
        }

        add(box, gc, SnippetUi.section(I18n.t("snippet.section.command")), 6);
        add(box, gc, SnippetUi.codeBox(SnippetUi.commandPane(s.command(), 0)), 14);

        var resolved = SnippetUi.promptPane(0);
        String sendTo = target == null ? "-" : target.name();
        var paste = new SnippetUi.Button(I18n.t(s.enter() ? "snippet.action.run" : "snippet.action.paste", sendTo),
                SnippetUi.stroke(SnippetUi.CLIPBOARD, 14, () -> SnippetUi.onAccent(SnippetUi.accent()), 2),
                SnippetUi.Variant.PRIMARY);
        var copy = new SnippetUi.Button(I18n.t("snippet.action.copy"), null, SnippetUi.Variant.DEFAULT);
        Runnable refresh = () -> {
            SnippetUi.setPrompt(resolved, SnippetVariables.resolve(s.command(), vals), s.enter());
            boolean ready = SnippetVariables.missing(s.command(), vals).isEmpty();
            paste.setEnabled(ready && target != null && target.canPaste());
            copy.setEnabled(ready);
        };

        if (!s.variableNames().isEmpty()) {
            add(box, gc, SnippetUi.section(I18n.t("snippet.section.variables")), 8);
            var grid = new JPanel(new GridBagLayout());
            grid.setOpaque(false);
            var g2 = new GridBagConstraints();
            g2.gridy = 0;
            g2.fill = GridBagConstraints.HORIZONTAL;
            for (String name : s.variableNames()) {
                g2.gridx = 0;
                g2.weightx = 0;
                g2.insets = new Insets(0, 0, 8, 10);
                var label = SnippetUi.label(name, SnippetUi.mono(0), SnippetUi.variable());
                label.setPreferredSize(new Dimension(96, label.getPreferredSize().height));
                grid.add(label, g2);
                g2.gridx = 1;
                g2.weightx = 1;
                g2.insets = new Insets(0, 0, 8, 0);
                var field = SnippetQuickPick.variableField(vals.getOrDefault(name, ""), 28, 0);
                field.putClientProperty("JTextField.placeholderText", I18n.t("snippet.variable.placeholder", name));
                label.setLabelFor(field);
                field.getDocument().addDocumentListener(SnippetQuickPick.onChange(() -> {
                    vals.put(name, field.getText());
                    SnippetQuickPick.markEmpty(field);
                    refresh.run();
                }));
                field.addActionListener(e -> paste.doClick());
                grid.add(field, g2);
                g2.gridy++;
            }
            add(box, gc, grid, 0);
            add(box, gc, SnippetUi.text(I18n.t("snippet.variable.remembered"), SnippetUi.font(0, Font.PLAIN),
                    SnippetUi.muted()), 14);
        }

        add(box, gc, SnippetUi.section(I18n.t(s.enter() ? "snippet.section.willRun" : "snippet.section.willPaste",
                sendTo)), 6);
        add(box, gc, SnippetUi.codeBox(resolved), 6);
        add(box, gc, SnippetUi.text(I18n.t(s.enter() ? "snippet.note.runs" : "snippet.note.waits"),
                SnippetUi.font(0, Font.PLAIN), SnippetUi.muted()), 0);
        gc.weighty = 1;
        add(box, gc, Box.createGlue(), 0);
        viewCard.add(scroll(box), BorderLayout.CENTER);

        paste.setToolTipText(target == null ? I18n.t("snippet.manager.noTarget")
                : I18n.t(s.enter() ? "snippet.action.run" : "snippet.action.paste", sendTo) + " (Enter)");
        paste.addActionListener(e -> paste(s));
        // nama host panjang: tombol ini yang menyusut (teks dengan elipsis, lengkap di tooltip), bukan Edit / Hapus
        // yang terdorong keluar panel
        paste.setMinimumSize(new Dimension(80, paste.getPreferredSize().height));
        copy.setToolTipText(I18n.t("snippet.action.copy") + " (" + Shortcuts.text(Shortcuts.menu(KeyEvent.VK_C)) + ")");
        copy.addActionListener(e -> toast(sender.copy(this, s, Map.copyOf(vals), target)));
        var edit = new SnippetUi.Button(I18n.t("snippet.action.edit"), null, SnippetUi.Variant.DEFAULT);
        edit.setToolTipText(I18n.t("snippet.action.edit") + " (F2)");
        edit.addActionListener(e -> startEdit(s));
        var del = new SnippetUi.Button(null, SnippetUi.stroke(SnippetUi.TRASH, 15, SnippetUi::danger, 1.8f),
                SnippetUi.Variant.ICON);
        del.setToolTipText(I18n.t("snippet.action.delete") + " (Del)");
        del.getAccessibleContext().setAccessibleName(I18n.t("snippet.action.delete"));
        del.addActionListener(e -> delete(s));
        viewCard.add(actionBar(List.of(paste, copy), List.of(edit, del)), BorderLayout.SOUTH);
        refresh.run();
        viewCard.revalidate();
        viewCard.repaint();
    }

    /** Baris tombol bawah panel kanan: garis atas, jarak 12/16 px, tombol berjarak 8 px. */
    private static JComponent actionBar(List<? extends JComponent> left, List<? extends JComponent> right) {
        var bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBackground(SnippetUi.panel());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        for (var c : left) {
            bar.add(c);
            bar.add(Box.createHorizontalStrut(8));
        }
        bar.add(Box.createHorizontalGlue());
        for (int i = 0; i < right.size(); i++) {
            if (i > 0) {
                bar.add(Box.createHorizontalStrut(8));
            }
            bar.add(right.get(i));
        }
        return bar;
    }

    private String categoryLabel(Snippet s) {
        String name = store.categoryName(s.category());
        return name.isEmpty() ? I18n.t("snippet.nav.uncategorized") : name;
    }

    private String scopeLabel(SnippetScope scope) {
        return switch (scope) {
            case SnippetScope.All all -> I18n.t("snippet.scope.all");
            case SnippetScope.Group(String path) -> path;
            case SnippetScope.Host(String name) -> I18n.t("snippet.scope.host", name);
        };
    }

    // --- aksi ---

    private void paste(Snippet s) {
        if (target == null) {
            toast(I18n.t("snippet.manager.noTarget"), true);
            return;
        }
        var vals = values.computeIfAbsent(s.id(), id -> sender.initialValues(s, target));
        if (!SnippetVariables.missing(s.command(), vals).isEmpty()) {
            toast(I18n.t("snippet.variable.fillFirst"), true);
            return;
        }
        toast(sender.paste(this, s, Map.copyOf(vals), target));
    }

    private void toggleFavorite(Snippet s) {
        UiAsync.run(io, () -> store.setFavorite(s.id(), !s.favorite()), this::saveFailed)
                .thenRun(() -> SwingUtilities.invokeLater(() -> reload(selected)));
    }

    private void delete(Snippet s) {
        if (!SnippetConfirm.show(this, false, I18n.t("snippet.delete.title", s.title()), I18n.t("snippet.delete.body"),
                null, I18n.t("snippet.action.delete"))) {
            return;
        }
        UiAsync.run(io, () -> store.delete(s.id()), this::saveFailed)
                .thenRun(() -> SwingUtilities.invokeLater(() -> {
                    editing = false;
                    values.remove(s.id());
                    selected = null;
                    reload(null);
                    if (tableModel.getRowCount() > 0) {
                        table.setRowSelectionInterval(0, 0);
                    }
                    toast(I18n.t("snippet.result.deleted"), false);
                }));
    }

    private void openCategories() {
        if (editing) {
            cancelEdit(); // pilihan kategori di form edit dibangun ulang setelah kategori berubah
        }
        SnippetCategoriesDialog.show(this, store, io, message -> toast(message, false));
        reload(selected);
    }

    private void openExport() {
        var all = store.snapshot();
        var ids = categoryIds();
        var nav = new Nav(navKind, navCategory, "", 0);
        var view = all.stream().filter(s -> nav.includes(s, ids)).toList();
        String label = switch (navKind) {
            case ALL -> I18n.t("snippet.nav.all");
            case FAVORITES -> I18n.t("snippet.nav.favorites");
            case NONE -> I18n.t("snippet.nav.uncategorized");
            case CATEGORY -> store.categoryName(navCategory);
        };
        var start = navKind == Nav.Kind.ALL ? SnippetExportDialog.Which.ALL : SnippetExportDialog.Which.VIEW;
        SnippetExportDialog.show(this, store, io, message -> toast(message, false),
                new SnippetExportDialog.Sets(all, view, label, start));
    }

    private void saveFailed(Throwable err) {
        Dialogs.error(this, I18n.t("snippet.saveFailed", err.getMessage()), err);
    }

    // --- edit ---

    private void startNew() {
        String category = navKind == Nav.Kind.CATEGORY ? navCategory : "";
        startEditor(null, new EditorState("", "", false, category, SnippetScope.ALL, navKind == Nav.Kind.FAVORITES,
                false, Map.of()));
    }

    private void startEdit(Snippet s) {
        startEditor(s, new EditorState(s.title(), s.command(), s.enter(), s.category(), s.scope(),
                s.favorite(), s.dangerous(), s.variables()));
    }

    /** @param original null = snippet baru */
    private void startEditor(Snippet original, EditorState init) {
        editing = true;
        editCard.removeAll();
        var box = new SnippetUi.TrackingPanel(new GridBagLayout());
        box.setBackground(SnippetUi.panel());
        box.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        var gc = column();
        add(box, gc, SnippetUi.label(I18n.t(original == null ? "snippet.edit.newTitle" : "snippet.edit.editTitle"),
                SnippetUi.font(2, Font.BOLD), SnippetUi.fg()), 12);

        var title = SnippetUi.styleField(new JTextField(init.title()), 30);
        title.setFont(SnippetUi.font(0, Font.PLAIN));
        title.putClientProperty("JTextField.placeholderText", I18n.t("snippet.edit.titlePlaceholder"));
        add(box, gc, labeled(I18n.t("snippet.edit.title"), title), 12);

        var command = new JTextArea(init.command(), 5, 20);
        command.setFont(SnippetUi.mono(0));
        command.setLineWrap(true);
        command.setForeground(SnippetUi.fg());
        command.setBackground(SnippetUi.field());
        command.setCaretColor(SnippetUi.fg());
        command.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        command.putClientProperty("JTextArea.placeholderText", I18n.t("snippet.edit.commandPlaceholder"));
        var commandScroll = new JScrollPane(command);
        commandScroll.setBorder(new javax.swing.border.AbstractBorder() {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(command.isFocusOwner() ? SnippetUi.accent() : SnippetUi.border());
                g2.drawRoundRect(x, y, w - 1, h - 1, 8, 8);
                g2.dispose();
            }

            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(1, 1, 1, 1);
            }
        });
        command.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                commandScroll.repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                commandScroll.repaint();
            }
        });
        commandScroll.setPreferredSize(new Dimension(300, 110));
        var commandBlock = new JPanel(new BorderLayout(0, 5));
        commandBlock.setOpaque(false);
        commandBlock.add(labeled(I18n.t("snippet.edit.command"), commandScroll), BorderLayout.NORTH);
        String[] hintParts = I18n.t("snippet.edit.commandHint", "\u0000").split("\u0000", 2);
        var hint = new SnippetUi.Paragraph(SnippetUi.font(0, Font.PLAIN), SnippetUi.muted()).add(hintParts[0])
                .add("${name}", SnippetUi.variable(), false, true);
        if (hintParts.length > 1) {
            hint.add(hintParts[1]);
        }
        commandBlock.add(hint, BorderLayout.CENTER);
        var found = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        found.setOpaque(false);
        commandBlock.add(found, BorderLayout.SOUTH);
        add(box, gc, commandBlock, 12);

        // pilihan tetap seperti prototipe: kategori (urutan Kelola kategori) lalu Tanpa kategori di akhir
        var category = SnippetUi.styleField(new JComboBox<CategoryChoice>(), 30);
        store.categories().forEach(c -> category.addItem(new CategoryChoice(c.id(), c.name())));
        category.addItem(new CategoryChoice("", I18n.t("snippet.nav.uncategorized")));
        for (int i = 0; i < category.getItemCount(); i++) {
            if (category.getItemAt(i).id().equals(init.categoryId())) {
                category.setSelectedIndex(i);
            }
        }
        if (category.getSelectedIndex() < 0) {
            category.setSelectedIndex(category.getItemCount() - 1);
        }
        category.setToolTipText(I18n.t("snippet.edit.categoryTip"));
        var scope = SnippetUi.styleField(new JComboBox<ScopeChoice>(), 30);
        scopeChoices(init.scope()).forEach(scope::addItem);
        for (int i = 0; i < scope.getItemCount(); i++) {
            if (scope.getItemAt(i).scope().equals(init.scope())) {
                scope.setSelectedIndex(i);
            }
        }
        var pair = new JPanel(new GridLayout(1, 2, 10, 0));
        pair.setOpaque(false);
        pair.add(labeled(I18n.t("snippet.edit.category"), category));
        pair.add(labeled(I18n.t("snippet.edit.scope"), scope));
        add(box, gc, pair, 12);

        var favoriteRow = SnippetUi.toggle(I18n.t("snippet.edit.favorite"), I18n.t("snippet.edit.favoriteHint"),
                init.favorite(), SnippetUi::favorite);
        var enterRow = SnippetUi.toggle(I18n.t("snippet.edit.enter"), I18n.t("snippet.edit.enterHint"), init.enter(),
                SnippetUi::accent);
        var dangerousRow = SnippetUi.toggle(I18n.t("snippet.edit.dangerous"), I18n.t("snippet.edit.dangerousHint"),
                init.dangerous(), SnippetUi::danger);
        for (var t : List.of(favoriteRow, enterRow, dangerousRow)) {
            add(box, gc, t.row(), 4);
        }
        JCheckBox favorite = favoriteRow.box();
        JCheckBox enter = enterRow.box();
        JCheckBox dangerous = dangerousRow.box();
        var detected = SnippetUi.dangerBox(new JPanel(), 0.4f);
        add(box, gc, detected, 6);
        var error = SnippetUi.label(" ", SnippetUi.font(0, Font.PLAIN), SnippetUi.danger());
        add(box, gc, error, 0);
        gc.weighty = 1;
        add(box, gc, Box.createGlue(), 0);

        Runnable analyze = () -> {
            var names = SnippetVariables.names(command.getText());
            found.removeAll();
            if (!names.isEmpty()) {
                found.add(SnippetUi.mutedLabel(I18n.t("snippet.edit.varsFound")));
                for (String n : names) {
                    found.add(new SnippetUi.Badge(n, SnippetUi::variable, 0.14f, true));
                }
            }
            found.setVisible(!names.isEmpty());
            found.revalidate();
            var what = DangerousCommands.detect(command.getText());
            detected.setVisible(what.isPresent() && !dangerous.isSelected());
            what.ifPresent(w -> {
                String[] parts = I18n.t("snippet.edit.detected", "\u0000").split("\u0000", 2);
                var p = new SnippetUi.Paragraph(SnippetUi.font(0, Font.PLAIN), SnippetUi.fg()).add(parts[0])
                        .add(w, SnippetUi.danger(), false, true);
                if (parts.length > 1) {
                    p.add(parts[1]);
                }
                detected.removeAll();
                detected.add(p, BorderLayout.CENTER);
                detected.revalidate();
            });
        };
        command.getDocument().addDocumentListener(SnippetQuickPick.onChange(analyze));
        dangerous.addActionListener(e -> analyze.run());
        analyze.run();

        editCard.add(scroll(box), BorderLayout.CENTER);

        var save = new SnippetUi.Button(I18n.t("snippet.save"), null, SnippetUi.Variant.PRIMARY);
        save.setToolTipText(I18n.t("snippet.save") + " (" + Shortcuts.text(Shortcuts.menu(KeyEvent.VK_S)) + ")");
        var cancel = new SnippetUi.Button(I18n.t("snippet.cancel"), null, SnippetUi.Variant.DEFAULT);
        cancel.setToolTipText(I18n.t("snippet.cancel") + " (Esc)");
        cancel.addActionListener(e -> cancelEdit());
        save.addActionListener(e -> {
            var picked = (CategoryChoice) category.getSelectedItem();
            String categoryId = picked == null ? "" : picked.id();
            var choice = (ScopeChoice) scope.getSelectedItem();
            UUID id = original == null ? UUID.randomUUID() : original.id();
            var defaults = new LinkedHashMap<>(init.variables());
            defaults.keySet().retainAll(SnippetVariables.names(command.getText()));
            Snippet draft;
            try {
                draft = new Snippet(id, title.getText(), command.getText(), enter.isSelected(), categoryId,
                        favorite.isSelected(), dangerous.isSelected(),
                        choice == null ? SnippetScope.ALL : choice.scope(), defaults);
            } catch (IllegalArgumentException ex) {
                error.setText("<html><div style='width:330px'>" + SnippetUi.esc(ex.getMessage()) + "</div></html>");
                return;
            }
            UiAsync.run(io, () -> {
                store.save(draft);
                return draft;
            }, saved -> {
                editing = false;
                selected = saved.id();
                if (navKind == Nav.Kind.CATEGORY && !saved.category().equals(navCategory)
                        || navKind == Nav.Kind.FAVORITES && !saved.favorite()) {
                    navKind = Nav.Kind.ALL;
                    navCategory = "";
                }
                search.setText("");
                reload(saved.id());
                toast(I18n.t("snippet.result.saved"), false);
            }, err -> error.setText(I18n.t("snippet.saveFailed", err.getMessage())));
        });
        key(editCard, Shortcuts.menu(KeyEvent.VK_S), "save", () -> {
            if (editing) {
                save.doClick();
            }
        });
        var left = new ArrayList<JComponent>();
        if (original != null) {
            var del = new SnippetUi.Button(I18n.t("snippet.action.delete"), null, SnippetUi.Variant.DANGER);
            del.addActionListener(e -> delete(original));
            left.add(del);
        }
        editCard.add(actionBar(left, List.of(cancel, save)), BorderLayout.SOUTH);
        cards.show(detail, EDIT);
        editCard.revalidate();
        editCard.repaint();
        SwingUtilities.invokeLater(title::requestFocusInWindow);
    }

    private void cancelEdit() {
        editing = false;
        select(selected);
    }

    private List<ScopeChoice> scopeChoices(SnippetScope current) {
        var out = new ArrayList<ScopeChoice>();
        out.add(new ScopeChoice(SnippetScope.ALL, I18n.t("snippet.scope.all")));
        for (String g : groups) {
            if (!g.isEmpty()) {
                out.add(new ScopeChoice(new SnippetScope.Group(g), g));
            }
        }
        if (target != null && !target.hostName().isEmpty()) {
            out.add(new ScopeChoice(new SnippetScope.Host(target.hostName()),
                    I18n.t("snippet.scope.host", target.hostName())));
        }
        if (out.stream().noneMatch(c -> c.scope().equals(current))) {
            out.add(new ScopeChoice(current, scopeLabel(current)));
        }
        return out;
    }

    private static JComponent labeled(String label, JComponent field) {
        var panel = new JPanel(new BorderLayout(0, 5));
        panel.setOpaque(false);
        var l = SnippetUi.section(label);
        l.setLabelFor(field instanceof JScrollPane sp ? sp.getViewport().getView() : field);
        panel.add(l, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    // --- status bar ---

    private void toast(SnippetSender.Result result) {
        if (result != null) {
            toast(result.message(), false);
        }
    }

    private void toast(String message, boolean warning) {
        Supplier<Color> color = warning ? SnippetUi::variable : SnippetUi::success;
        footer.setForeground(color.get());
        footer.setIcon(warning ? null : SnippetUi.stroke(SnippetUi.CHECK, 12, color, 2.6f));
        footer.setText(message);
        if (toastTimer != null) {
            toastTimer.stop();
        }
        toastTimer = new Timer(2600, e -> showHint());
        toastTimer.setRepeats(false);
        toastTimer.start();
    }

    private void showHint() {
        footer.setIcon(null);
        footer.setForeground(SnippetUi.muted());
        footer.setText(I18n.t("snippet.manager.hint", Shortcuts.text(ShortcutAction.SNIPPET_QUICK)));
    }

    // --- tabel ---

    private final class SnippetTableModel extends AbstractTableModel {
        private List<Snippet> rows = List.of();

        void set(List<Snippet> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        Snippet at(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 1 -> I18n.t("snippet.column.title");
                case 2 -> I18n.t("snippet.column.command");
                case 3 -> I18n.t("snippet.column.category");
                default -> "";
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            var s = rows.get(row);
            return switch (column) {
                case 2 -> SnippetUi.preview(s);
                case 3 -> categoryLabel(s);
                default -> s;
            };
        }
    }

    /** Latar baris: seleksi, hover, atau panel; garis bawah tipis (prototipe {@code .srow}). */
    private Color rowBackground(JTable t, int row, boolean sel) {
        return sel ? SnippetUi.selection() : row == hoverRow ? SnippetUi.hover() : SnippetUi.panel();
    }

    private static javax.swing.border.Border rowLine(int left, int right) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.alpha(SnippetUi.border(), 0.6f)),
                BorderFactory.createEmptyBorder(0, left, 0, right));
    }

    /** Pegangan seret 2×3 titik (prototipe), di kolom bintang selama mode urutan. */
    private static final Icon GRIP = new Icon() {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(SnippetUi.muted());
            for (int col = 0; col < 2; col++) {
                for (int r = 0; r < 3; r++) {
                    g2.fillOval(x + 1 + col * 6, y + r * 5, 3, 3);
                }
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    };

    private final class StarRenderer implements TableCellRenderer {
        private final JLabel label = new JLabel();

        StarRenderer() {
            label.setHorizontalAlignment(JLabel.CENTER);
            label.setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row,
                                                       int column) {
            var s = (Snippet) value;
            label.setIcon(sorting ? GRIP : SnippetUi.star(s.favorite(), 14));
            label.setToolTipText(sorting ? I18n.t("snippet.manager.reorder.handle")
                    : I18n.t(s.favorite() ? "snippet.favorite.remove" : "snippet.favorite.add"));
            label.setBackground(rowBackground(t, row, sel));
            label.setBorder(rowLine(0, 0));
            return label;
        }
    }

    private final class TitleRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row,
                                                       int column) {
            var s = (Snippet) value;
            // satu baris: judul (menyusut dengan elipsis) lalu badge, rata tengah vertikal
            var panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            panel.setBackground(rowBackground(t, row, sel));
            panel.setBorder(rowLine(0, 12));
            var title = SnippetUi.label(s.title(), SnippetUi.font(0, Font.PLAIN), SnippetUi.fg());
            title.setMinimumSize(new Dimension(0, title.getPreferredSize().height));
            panel.add(title);
            var badges = new JPanel();
            badges.setLayout(new BoxLayout(badges, BoxLayout.X_AXIS));
            badges.setOpaque(false);
            SnippetUi.addBadges(badges, s);
            for (var b : badges.getComponents()) {
                panel.add(Box.createHorizontalStrut(6));
                panel.add(b);
            }
            panel.add(Box.createHorizontalGlue());
            panel.setToolTipText(target == null ? null : I18n.t("snippet.manager.doubleClick", target.name()));
            return panel;
        }
    }

    private final class TextRenderer extends javax.swing.table.DefaultTableCellRenderer {
        private final boolean mono;

        TextRenderer(boolean mono) {
            this.mono = mono;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row,
                                                       int column) {
            var c = (JLabel) super.getTableCellRendererComponent(t, value, sel, false, row, column);
            c.setFont(mono ? SnippetUi.mono(0) : SnippetUi.font(0, Font.PLAIN));
            c.setForeground(SnippetUi.muted());
            c.setBackground(rowBackground(t, row, sel));
            c.setBorder(rowLine(0, 12));
            return c;
        }
    }
}

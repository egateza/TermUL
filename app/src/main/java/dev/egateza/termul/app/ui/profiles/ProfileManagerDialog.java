package dev.egateza.termul.app.ui.profiles;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.snippet.SnippetUi;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.HostTerminalColors;
import dev.egateza.termul.app.ui.OsIcons;
import dev.egateza.termul.app.ui.SecretChange;
import dev.egateza.termul.app.ui.ShortcutAction;
import dev.egateza.termul.app.ui.Shortcuts;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.profile.Protocol;
import dev.egateza.termul.core.snippet.Snippet;
import dev.egateza.termul.core.snippet.SnippetScope;
import dev.egateza.termul.ssh.ConnectionTester;
import dev.egateza.termul.vault.SecretType;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Profile manager (prototipe "Profiles"): navigasi folder, daftar profil dengan pilihan ganda dan mode urutan, panel
 * detail / form edit / aksi massal, Test koneksi, serta Manage folders, Export dan Import. Profil disimpan lewat
 * {@link dev.egateza.termul.core.profile.ProfileStore} (I/O di executor), secret lewat vault (tidak pernah di
 * profil, di-zero setelah dipakai). EDT.
 */
public final class ProfileManagerDialog extends JDialog {

    static final String SERVERS = "M3 4h18v7H3z M3 13h18v7H3z M7 7.5h.01 M7 16.5h.01";
    static final String IMPORT = "M12 4v11M7 10l5 5 5-5M5 20h14";
    static final String EXPORT = "M12 15V4M7 9l5-5 5 5M5 20h14";
    static final String SORT = "M8 4v16M4 8l4-4 4 4M16 20V4M12 16l4 4 4-4";
    static final String PROMPT = "M5 7l5 5-5 5M12 18h7";
    static final String EDIT = "M4.5 19.5h4l11-11-4-4-11 11z M14 6l4 4"; // pensil di tengah kotak 24
    static final String COPY = "M9 9h11v11H9z M5 15H4V4h11v1";
    static final String RIGHT = "M9 6l6 6-6 6";
    static final String UP = "M6 15l6-6 6 6";
    static final String DOWN = "M6 9l6 6 6-6";
    static final String CLOCK = "M12 7v5l3 2 M12 3L15.5 3.7L18.4 5.6L20.3 8.5L21 12L20.3 15.5L18.4 18.4L15.5 20.3L12 21"
            + "L8.5 20.3L5.6 18.4L3.7 15.5L3 12L3.7 8.5L5.6 5.6L8.5 3.7Z";
    private static final int NAV_W = 188;
    private static final int DETAIL_W = 420;
    private static final int ROW_H = 40;

    /** Item navigasi kiri. */
    record Nav(Kind kind, String group) {
        enum Kind { ALL, FAVORITES, RECENT, FOLDER, NONE }

        static final Nav ALL = new Nav(Kind.ALL, "");
    }

    private enum Mode { NONE, VIEW, EDIT, BULK }

    private final ProfileManagerHost host;
    private final Runnable undim;
    private final Consumer<ProfileSnapshot> storeListener = s -> SwingUtilities.invokeLater(() -> onStore(s));

    private ProfileSnapshot snapshot;                                 // EDT
    private final Map<UUID, Set<SecretType>> stored = new HashMap<>(); // EDT
    private Nav nav = Nav.ALL;                                       // EDT
    private UUID selected;                                           // EDT
    private Mode mode = Mode.NONE;                                   // EDT
    private boolean sorting;                                         // EDT
    private ProfileDraft draft;                                      // EDT; mode EDIT
    private String draftOrigin = "";                                 // EDT; isi draft saat dibuka
    private boolean tried;                                           // EDT; Save/Test sudah ditekan sekali
    private final Map<UUID, ConnectionTester.Result> bulkTests = new HashMap<>(); // EDT; null value = sedang dites
    private final Set<UUID> bulkRunning = new LinkedHashSet<>();     // EDT
    private int testRun;                                             // EDT; abaikan hasil test yang sudah usang
    private boolean reloading;                                       // EDT
    private Timer toastTimer;                                        // EDT

    private final JPanel navPanel = new JPanel();
    private final JTextField search = new JTextField();
    private final JLabel count = SnippetUi.mutedLabel("");
    private final SnippetUi.Button sortButton = new SnippetUi.Button(I18n.t("pm.reorder"),
            SnippetUi.stroke(SORT, 14, SnippetUi::fg, 2), SnippetUi.Variant.DEFAULT);
    private final DefaultListModel<HostProfile> listModel = new DefaultListModel<>();
    private final ProfileList list = new ProfileList();
    private final ListHeader listHeader = new ListHeader();
    private final JLabel sortBanner = new JLabel();
    private final CardLayout listCards = new CardLayout();
    private final JPanel listArea = new JPanel(listCards);
    private final JLabel emptyTitle = SnippetUi.label("", SnippetUi.font(1, Font.PLAIN), SnippetUi.fg());
    private final JLabel emptyHint = SnippetUi.label("", SnippetUi.font(0, Font.PLAIN), SnippetUi.muted());
    private final CardLayout detailCards = new CardLayout();
    private final JPanel detailArea = new JPanel(detailCards);
    private final JPanel viewContent = column();
    private final JPanel bulkContent = column();
    private final JPanel editContent = column();
    private final CardLayout footerCards = new CardLayout();
    private final JPanel footerArea = new JPanel(footerCards);
    private final JPanel testPanel = new JPanel(new BorderLayout());
    private final JPanel bulkFooter = footerRow();
    private final JLabel status = new JLabel();
    private final ProfileForm form = new ProfileForm();

    /**
     * @param editId   profil yang langsung dibuka di form edit, null = tidak ada
     * @param newGroup non-null = langsung membuat profil baru di grup ini (Ctrl+N)
     */
    public ProfileManagerDialog(Window owner, ProfileManagerHost host, UUID editId, String newGroup) {
        super(owner, I18n.t("pm.title"), ModalityType.APPLICATION_MODAL);
        this.host = host;
        // latar gelap dulu, sebelum isi dialog dibangun (pertama kali ±0,5 dtk): langsung terasa begitu diklik
        undim = SnippetUi.dim(owner);
        try {
            build(owner, editId, newGroup);
        } catch (RuntimeException | Error e) {
            undim.run();
            throw e;
        }
    }

    private void build(Window owner, UUID editId, String newGroup) {
        this.snapshot = host.store().snapshot();
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
        body.add(detailPane(), BorderLayout.EAST);
        root.add(body, BorderLayout.CENTER);
        root.add(statusBar(round), BorderLayout.SOUTH);
        setContentPane(root);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        bindKeys();
        showHint();
        host.store().addListener(storeListener);

        reload(null);
        if (newGroup != null) {
            startNew(newGroup);
        } else if (editId != null && snapshot.find(editId).isPresent()) {
            select(editId);
        } else if (!listModel.isEmpty()) {
            select(listModel.get(0).id());
        }
        loadStoredSecrets(editId);

        int w = owner == null ? 1200 : Math.min(1200, owner.getWidth() - 32);
        int h = owner == null ? 760 : Math.min(760, owner.getHeight() - 32);
        setSize(Math.max(820, w), Math.max(500, h));
        SnippetUi.keepSizeAcrossScreens(this);
        setLocationRelativeTo(owner);
    }

    @Override
    public void dispose() {
        host.store().removeListener(storeListener);
        form.clearSecrets();
        if (toastTimer != null) {
            toastTimer.stop();
        }
        undim.run();
        super.dispose();
    }

    private void loadStoredSecrets(UUID openEdit) {
        var ids = snapshot.profiles().stream().map(HostProfile::id).toList();
        UiAsync.run(host.io(), () -> {
            var result = new HashMap<UUID, Set<SecretType>>();
            ids.forEach(id -> result.put(id, host.storedSecrets(id)));
            return result;
        }, result -> {
            stored.putAll(result);
            if (openEdit != null && Objects.equals(selected, openEdit) && mode == Mode.VIEW) {
                snapshot.find(openEdit).ifPresent(this::edit);
            } else if (mode == Mode.VIEW) {
                showDetail();
            }
        }, err -> Dialogs.error(this, I18n.t("main.error.readVault"), err));
    }

    private Set<SecretType> storedFor(UUID id) {
        return stored.getOrDefault(id, Set.of());
    }

    // --- header, toolbar, status bar ---

    private JComponent header(boolean round) {
        var bar = new EdgePanel(true, round);
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(0, 14, 0, 6)));
        bar.setPreferredSize(new Dimension(0, 40));
        var title = SnippetUi.label(I18n.t("pm.title"), SnippetUi.font(1, Font.BOLD), SnippetUi.fg());
        title.setIcon(SnippetUi.stroke(SERVERS, 15, SnippetUi::accent, 2));
        title.setIconTextGap(10);
        bar.add(title);
        bar.add(Box.createHorizontalGlue());
        bar.add(SnippetUi.mutedLabel(Shortcuts.text(ShortcutAction.MANAGE_PROFILES)));
        bar.add(Box.createHorizontalStrut(6));
        var close = new SnippetUi.Button(null, SnippetUi.stroke(SnippetUi.CLOSE, 14, SnippetUi::muted, 2),
                SnippetUi.Variant.ICON);
        close.setToolTipText(I18n.t("pm.close"));
        close.getAccessibleContext().setAccessibleName(I18n.t("pm.close"));
        close.addActionListener(e -> closeManager());
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

    private JComponent toolbar() {
        SnippetUi.styleField(search, 30);
        search.setFont(SnippetUi.font(0, Font.PLAIN));
        search.putClientProperty("JTextField.placeholderText",
                I18n.t("pm.search", Shortcuts.text(Shortcuts.menu(KeyEvent.VK_F))));
        search.putClientProperty("JTextField.leadingIcon", SnippetUi.search(14));
        search.putClientProperty("JTextField.showClearButton", true);
        search.getAccessibleContext().setAccessibleName(I18n.t("pm.searchName"));
        search.getDocument().addDocumentListener(onChange(() -> reload(selected)));
        search.setPreferredSize(new Dimension(320, 30));
        search.setMaximumSize(new Dimension(320, 30));
        var importButton = new SnippetUi.Button(I18n.t("pm.import"), SnippetUi.stroke(IMPORT, 14, SnippetUi::fg, 2),
                SnippetUi.Variant.DEFAULT);
        importButton.setToolTipText(I18n.t("pm.import.tip"));
        importButton.addActionListener(e -> guard(this::openImport));
        var exportButton = new SnippetUi.Button(I18n.t("pm.export"), SnippetUi.stroke(EXPORT, 14, SnippetUi::fg, 2),
                SnippetUi.Variant.DEFAULT);
        exportButton.setToolTipText(I18n.t("pm.export.tip", Shortcuts.text(Shortcuts.menu(KeyEvent.VK_E))));
        exportButton.addActionListener(e -> openExport(null));
        sortButton.setToolTipText(I18n.t("pm.reorder.tip"));
        sortButton.addActionListener(e -> toggleSort());
        var newButton = new SnippetUi.Button(I18n.t("pm.new"),
                SnippetUi.stroke(SnippetUi.PLUS, 14, () -> SnippetUi.onAccent(SnippetUi.accent()), 2.4f),
                SnippetUi.Variant.PRIMARY);
        newButton.setToolTipText(I18n.t("pm.new.tip", Shortcuts.text(ShortcutAction.NEW_HOST)));
        newButton.addActionListener(e -> guard(() -> startNew(currentFolder())));

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
        for (var b : new JComponent[] {importButton, exportButton, sortButton, newButton}) {
            bar.add(b);
            if (b != newButton) {
                bar.add(Box.createHorizontalStrut(10));
            }
        }
        return bar;
    }

    private JComponent statusBar(boolean round) {
        var bar = new EdgePanel(false, round);
        bar.setLayout(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(0, 14, 0, 14)));
        bar.setPreferredSize(new Dimension(0, 28));
        status.setFont(SnippetUi.font(-1, Font.PLAIN));
        status.setIconTextGap(6);
        bar.add(status, BorderLayout.CENTER);
        return bar;
    }

    private void showHint() {
        status.setIcon(null);
        status.setForeground(SnippetUi.muted());
        status.setText(I18n.t("pm.footer.hint"));
    }

    /** Pesan singkat di status bar selama 2,6 detik (prototipe). */
    void toast(String message, boolean error) {
        status.setIcon(SnippetUi.stroke(error ? SnippetUi.WARN : SnippetUi.CHECK, 12,
                error ? SnippetUi::danger : SnippetUi::success, 2.6f));
        status.setForeground(error ? SnippetUi.danger() : SnippetUi.success());
        status.setText(message);
        if (toastTimer != null) {
            toastTimer.stop();
        }
        toastTimer = new Timer(2600, e -> showHint());
        toastTimer.setRepeats(false);
        toastTimer.start();
    }

    // --- navigasi ---

    private JComponent navPane() {
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.Y_AXIS));
        navPanel.setBackground(SnippetUi.bar());
        navPanel.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
        navPanel.getAccessibleContext().setAccessibleName(I18n.t("pm.nav.name"));
        var scroll = new JScrollPane(navPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SnippetUi.bar());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        var manage = new NavItem(I18n.t("pm.nav.manageFolders"), SnippetUi.sliders(14), -1, false, true,
                this::openFolders);
        manage.setToolTipText(I18n.t("pm.nav.manageFolders.tip"));
        var bottom = new JPanel(new BorderLayout());
        bottom.setBackground(SnippetUi.bar());
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 10, 8));
        bottom.add(manage);
        var pane = new JPanel(new BorderLayout());
        pane.setBackground(SnippetUi.bar());
        pane.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, SnippetUi.border()));
        pane.setPreferredSize(new Dimension(NAV_W, 0));
        pane.add(scroll, BorderLayout.CENTER);
        pane.add(bottom, BorderLayout.SOUTH);
        return pane;
    }

    private void rebuildNav() {
        navPanel.removeAll();
        var all = snapshot.profiles();
        addNav(Nav.ALL, I18n.t("pm.nav.all"), SnippetUi.stroke(SERVERS, 14, SnippetUi::accent, 2), all.size(), 0);
        addNav(new Nav(Nav.Kind.FAVORITES, ""), I18n.t("pm.nav.favorites"), SnippetUi.star(true, 14),
                snapshot.favoriteProfiles().size(), 0);
        addNav(new Nav(Nav.Kind.RECENT, ""), I18n.t("pm.nav.recent"),
                SnippetUi.stroke(CLOCK, 14, SnippetUi::muted, 2), snapshot.recentProfiles().size(), 0);
        var section = SnippetUi.label(I18n.t("pm.nav.folders").toUpperCase(Locale.ROOT), SnippetUi.font(-1, Font.BOLD),
                SnippetUi.muted());
        section.setBorder(BorderFactory.createEmptyBorder(14, 10, 6, 10));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        navPanel.add(section);
        for (String g : snapshot.allGroups()) {
            int depth = (int) g.chars().filter(c -> c == '/').count();
            addNav(new Nav(Nav.Kind.FOLDER, g), g.substring(g.lastIndexOf('/') + 1),
                    SnippetUi.folder(14, SnippetUi::favorite), snapshot.profilesIn(g).size(), depth);
        }
        addNav(new Nav(Nav.Kind.NONE, ""), I18n.t("pm.folder.ungrouped"), SnippetUi.folder(14, SnippetUi::muted),
                snapshot.profilesIn("").size(), 0);
        navPanel.revalidate();
        navPanel.repaint();
    }

    private void addNav(Nav n, String label, Icon icon, int amount, int depth) {
        var item = new NavItem(label, icon, amount, n.equals(nav), false, () -> guard(() -> {
            if (sorting && n.kind() != Nav.Kind.FOLDER && n.kind() != Nav.Kind.NONE) {
                setSorting(false);
            }
            nav = n;
            reload(selected);
        }));
        item.setBorder(BorderFactory.createEmptyBorder(0, 10 + depth * 14, 0, 10));
        item.setAlignmentX(Component.LEFT_ALIGNMENT);
        navPanel.add(item);
        navPanel.add(Box.createVerticalStrut(2));
    }

    /** Folder yang sedang dibuka di navigasi ({@code ""} di luar folder). */
    private String currentFolder() {
        return nav.kind() == Nav.Kind.FOLDER ? nav.group() : "";
    }

    /** Item navigasi 28 px, sudut 4 px (prototipe {@code .nav-item}). */
    private static final class NavItem extends JPanel {
        private final boolean on;
        private boolean hover;

        /** @param amount −1 = tanpa angka (aksi) */
        NavItem(String text, Icon icon, int amount, boolean on, boolean action, Runnable pick) {
            super(new BorderLayout(8, 0));
            this.on = on;
            setOpaque(false);
            setFocusable(true);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setPreferredSize(new Dimension(NAV_W - 16, 28));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            var label = SnippetUi.label(text, SnippetUi.font(0, Font.PLAIN),
                    on ? SnippetUi.onSelection() : action ? SnippetUi.muted() : SnippetUi.fg());
            label.setIcon(icon);
            label.setIconTextGap(8);
            add(label, BorderLayout.CENTER);
            if (amount >= 0) {
                add(SnippetUi.label(String.valueOf(amount), SnippetUi.font(0, Font.PLAIN), SnippetUi.muted()),
                        BorderLayout.EAST);
            }
            getAccessibleContext().setAccessibleName(text);
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
            getActionMap().put("pick", ProfileManagerDialog.action(pick));
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
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 7, 7);
                }
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    // --- daftar profil ---

    private JComponent listPane() {
        list.setModel(listModel);
        list.setCellRenderer(new RowRenderer());
        list.setFixedCellHeight(ROW_H);
        list.setBackground(SnippetUi.panel());
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.getAccessibleContext().setAccessibleName(I18n.t("pm.list.name"));
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !reloading) {
                onListSelection();
            }
        });
        list.setDragEnabled(true);
        list.setDropMode(javax.swing.DropMode.INSERT);
        list.setTransferHandler(new ReorderHandler());
        var scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SnippetUi.panel());

        sortBanner.setFont(SnippetUi.font(-1, Font.PLAIN));
        sortBanner.setForeground(SnippetUi.muted());
        sortBanner.setOpaque(true);
        sortBanner.setBackground(SnippetUi.chip());
        sortBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        sortBanner.setVisible(false);
        var top = new JPanel(new BorderLayout());
        top.add(listHeader, BorderLayout.NORTH);
        top.add(sortBanner, BorderLayout.SOUTH);
        var withList = new JPanel(new BorderLayout());
        withList.add(top, BorderLayout.NORTH);
        withList.add(scroll, BorderLayout.CENTER);

        var empty = new JPanel(new GridBagLayout());
        empty.setBackground(SnippetUi.panel());
        var texts = new JPanel(new GridLayout(2, 1, 0, 4));
        texts.setOpaque(false);
        emptyTitle.setHorizontalAlignment(JLabel.CENTER);
        emptyHint.setHorizontalAlignment(JLabel.CENTER);
        texts.add(emptyTitle);
        texts.add(emptyHint);
        empty.add(texts);
        var emptyWrap = new JPanel(new BorderLayout());
        emptyWrap.add(new ListHeader(), BorderLayout.NORTH);
        emptyWrap.add(empty, BorderLayout.CENTER);

        listArea.add(withList, "list");
        listArea.add(emptyWrap, "empty");
        listArea.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, SnippetUi.border()));
        return listArea;
    }

    /** Profil yang tampil di daftar sesuai navigasi dan pencarian (prototipe {@code shown()}). */
    List<HostProfile> shown() {
        String q = search.getText().strip().toLowerCase(Locale.ROOT);
        List<HostProfile> base = switch (nav.kind()) {
            case ALL -> {
                var ordered = new ArrayList<HostProfile>();
                snapshot.allGroups().forEach(g -> ordered.addAll(snapshot.profilesIn(g)));
                ordered.addAll(snapshot.profilesIn(""));
                var favorites = ordered.stream().filter(p -> snapshot.isFavorite(p.id())).toList();
                var rest = ordered.stream().filter(p -> !snapshot.isFavorite(p.id())).toList();
                var out = new ArrayList<>(favorites);
                out.addAll(rest);
                yield out;
            }
            case FAVORITES -> snapshot.favoriteProfiles();
            case RECENT -> snapshot.recentProfiles();
            case FOLDER -> snapshot.profilesIn(nav.group());
            case NONE -> snapshot.profilesIn("");
        };
        if (sorting || q.isEmpty()) {
            return base;
        }
        return base.stream().filter(p -> (p.name() + " " + p.host() + " " + p.username())
                .toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    private void onStore(ProfileSnapshot s) {
        snapshot = s;
        s.profiles().stream().map(HostProfile::id).filter(id -> !stored.containsKey(id)).forEach(id ->
                UiAsync.run(host.io(), () -> host.storedSecrets(id), set -> {
                    stored.put(id, set);
                    if (Objects.equals(id, selected) && mode == Mode.VIEW) {
                        showDetail();
                    }
                }, err -> { }));
        if (nav.kind() == Nav.Kind.FOLDER && !s.allGroups().contains(nav.group())) {
            nav = Nav.ALL;
        }
        reload(selected);
    }

    /** Isi ulang navigasi, daftar dan detail; pertahankan pilihan kalau masih tampil. */
    private void reload(UUID keep) {
        rebuildNav();
        var picked = new LinkedHashSet<UUID>(selectedIds());
        reloading = true;
        try {
            listModel.clear();
            shown().forEach(listModel::addElement);
            list.clearSelection();
            for (int i = 0; i < listModel.size(); i++) {
                UUID id = listModel.get(i).id();
                if (picked.size() > 1 ? picked.contains(id) : id.equals(keep)) {
                    list.addSelectionInterval(i, i);
                }
            }
        } finally {
            reloading = false;
        }
        int total = snapshot.profiles().size();
        count.setText(listModel.size() == total ? ProfileFacts.profiles(total)
                : I18n.t("pm.count.of", listModel.size(), total));
        if (listModel.isEmpty()) {
            String q = search.getText().strip();
            emptyTitle.setText(total == 0 ? I18n.t("pm.empty.none.title")
                    : q.isEmpty() ? I18n.t("pm.empty.view.title") : I18n.t("pm.empty.search.title", q));
            emptyHint.setText(total == 0 ? I18n.t("pm.empty.none.hint")
                    : q.isEmpty() ? I18n.t("pm.empty.view.hint") : I18n.t("pm.empty.search.hint"));
            listCards.show(listArea, "empty");
        } else {
            listCards.show(listArea, "list");
        }
        sortBanner.setText("<html>" + SnippetUi.esc(I18n.t("pm.reorder.banner",
                ProfileFacts.folderName(currentFolder()))) + "</html>");
        listHeader.repaint();
        if (mode != Mode.EDIT) {
            if (selectedIds().size() > 1) {
                mode = Mode.BULK;
            } else if (keep != null && snapshot.find(keep).isPresent()) {
                selected = keep;
                mode = Mode.VIEW;
            } else if (mode != Mode.NONE || selected != null) {
                selected = null;
                mode = Mode.NONE;
            }
            showDetail();
        }
        list.repaint();
    }

    private List<UUID> selectedIds() {
        return list.getSelectedValuesList().stream().map(HostProfile::id).toList();
    }

    private void onListSelection() {
        var ids = selectedIds();
        if (mode == Mode.EDIT && dirty()) {
            // pilihan baru menunggu konfirmasi buang perubahan; kembalikan seleksi lama dulu
            UUID target = ids.size() == 1 ? ids.get(0) : null;
            restoreListSelection();
            if (confirmDiscard()) {
                leaveEdit();
                if (target != null) {
                    select(target);
                } else {
                    reload(selected);
                }
            }
            return;
        }
        if (mode == Mode.EDIT) {
            leaveEdit();
        }
        bulkTests.clear();
        bulkRunning.clear();
        clearTest();
        if (ids.size() > 1) {
            mode = Mode.BULK;
        } else if (ids.size() == 1) {
            selected = ids.get(0);
            mode = Mode.VIEW;
        } else {
            mode = Mode.NONE;
            selected = null;
        }
        showDetail();
        listHeader.repaint();
        list.repaint();
    }

    private void restoreListSelection() {
        reloading = true;
        try {
            list.clearSelection();
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).id().equals(selected)) {
                    list.setSelectedIndex(i);
                }
            }
        } finally {
            reloading = false;
        }
    }

    /** Pilih satu profil (pindah ke navigasi yang memuatnya kalau tidak tampil). */
    private void select(UUID id) {
        if (shown().stream().noneMatch(p -> p.id().equals(id))) {
            nav = Nav.ALL;
            search.setText("");
        }
        mode = Mode.VIEW;
        selected = id;
        clearTest();
        reload(id);
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).id().equals(id)) {
                list.ensureIndexIsVisible(i);
            }
        }
    }

    /** Daftar profil: klik di kolom centang / bintang / panah urutan ditangani sendiri, sisanya seleksi JList. */
    private final class ProfileList extends JList<HostProfile> {
        int hoverRow = -1;

        ProfileList() {
            var mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int row = rowAt(e.getPoint());
                    if (row != hoverRow) {
                        hoverRow = row;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverRow = -1;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int row = rowAt(e.getPoint());
                    if (row >= 0 && e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && !sorting
                            && regionAt(e.getX()) == Region.MAIN) {
                        connect(List.of(listModel.get(row)));
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        int rowAt(Point p) {
            int row = locationToIndex(p);
            return row >= 0 && getCellBounds(row, row).contains(p) ? row : -1;
        }

        @Override
        protected void processMouseEvent(MouseEvent e) {
            int row = rowAt(e.getPoint());
            Region region = row < 0 ? Region.MAIN : regionAt(e.getX());
            if (row >= 0 && region != Region.MAIN && SwingUtilities.isLeftMouseButton(e)) {
                if (e.getID() == MouseEvent.MOUSE_PRESSED) {
                    var p = listModel.get(row);
                    switch (region) {
                        case CHECK -> togglePick(row);
                        case STAR -> toggleFavorite(p);
                        case UP -> moveBy(p, -1);
                        case DOWN -> moveBy(p, 1);
                        default -> { }
                    }
                }
                if (region != Region.HANDLE) {
                    e.consume();
                    return;
                }
            }
            super.processMouseEvent(e);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int row = rowAt(e.getPoint());
            if (row < 0) {
                return null;
            }
            var p = listModel.get(row);
            return switch (regionAt(e.getX())) {
                case STAR -> I18n.t(snapshot.isFavorite(p.id()) ? "pm.fav.remove" : "pm.fav.add");
                case HANDLE -> I18n.t("pm.reorder.drag");
                case UP -> I18n.t("pm.reorder.up");
                case DOWN -> I18n.t("pm.reorder.down");
                case CHECK -> I18n.t("pm.pick", p.name());
                case MAIN -> p.jumpHostId() == null ? null : snapshot.find(p.jumpHostId())
                        .map(j -> I18n.t("pm.jump.through", j.name())).orElse(null);
            };
        }
    }

    private enum Region { CHECK, STAR, HANDLE, MAIN, UP, DOWN }

    private boolean picksVisible() {
        return !sorting && mode == Mode.BULK;
    }

    /** Kolom di posisi x baris daftar (sama dengan {@link RowRenderer}). */
    private Region regionAt(int x) {
        int left = 0;
        if (picksVisible()) {
            if (x < 32) {
                return Region.CHECK;
            }
            left = 32;
        }
        if (sorting) {
            if (x < left + 36) {
                return Region.HANDLE;
            }
            int right = list.getWidth() - 64;
            if (x >= right && x < right + 30) {
                return Region.UP;
            }
            if (x >= right + 30 && x < right + 60) {
                return Region.DOWN;
            }
            return Region.MAIN;
        }
        return x < left + 36 ? Region.STAR : Region.MAIN;
    }

    private void togglePick(int row) {
        if (list.isSelectedIndex(row)) {
            list.removeSelectionInterval(row, row);
        } else {
            list.addSelectionInterval(row, row);
        }
    }

    /** Header kolom 28 px: centang semua (saat pilihan ganda), Name, Address, Sign-in. */
    private final class ListHeader extends JComponent {
        ListHeader() {
            setPreferredSize(new Dimension(0, 28));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (picksVisible() && e.getX() < 32) {
                        if (list.getSelectedIndices().length == listModel.size()) {
                            list.clearSelection();
                        } else {
                            list.setSelectionInterval(0, listModel.size() - 1);
                        }
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(SnippetUi.panel());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(SnippetUi.border());
            g2.fillRect(0, getHeight() - 1, getWidth(), 1);
            int left = picksVisible() ? 68 : 36;
            if (picksVisible()) {
                paintCheck(g2, 9, (getHeight() - 14) / 2,
                        !listModel.isEmpty() && list.getSelectedIndices().length == listModel.size());
            }
            var cols = columns(left, getWidth() - (sorting ? 76 : 12));
            g2.setFont(SnippetUi.font(-1, Font.BOLD));
            g2.setColor(SnippetUi.muted());
            var fm = g2.getFontMetrics();
            int base = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(I18n.t("pm.col.name"), cols[0], base);
            g2.drawString(I18n.t("pm.col.address"), cols[1], base);
            g2.drawString(I18n.t("pm.col.signIn"), cols[2], base);
            g2.dispose();
        }
    }

    /** Posisi x kolom Name, Address, Sign-in (prototipe: 1.1fr 1fr 112px, jarak 12). */
    static int[] columns(int left, int right) {
        int width = Math.max(0, right - left - 112 - 24);
        int name = Math.round(width * 1.1f / 2.1f);
        return new int[] {left, left + name + 12, right - 112};
    }

    private static void paintCheck(Graphics2D g2, int x, int y, boolean on) {
        g2.setColor(on ? SnippetUi.accent() : SnippetUi.field());
        g2.fillRoundRect(x, y, 14, 14, 4, 4);
        g2.setColor(on ? SnippetUi.accent() : SnippetUi.muted());
        g2.drawRoundRect(x, y, 13, 13, 4, 4);
        if (on) {
            g2.setColor(SnippetUi.onAccent(SnippetUi.accent()));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawPolyline(new int[] {x + 3, x + 6, x + 11}, new int[] {y + 7, y + 10, y + 4}, 3);
        }
    }

    /** Satu baris daftar (40 px): centang, bintang / pegangan, ikon OS + nama, alamat, badge, panah urutan. */
    private final class RowRenderer extends JPanel implements ListCellRenderer<HostProfile> {
        private HostProfile profile;
        private int row;
        private boolean selectedRow;

        @Override
        public Component getListCellRendererComponent(JList<? extends HostProfile> l, HostProfile value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            profile = value;
            row = index;
            selectedRow = isSelected;
            getAccessibleContext().setAccessibleName(value.name() + ", " + value.address());
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(selectedRow ? SnippetUi.selection() : row == list.hoverRow ? SnippetUi.hover()
                    : SnippetUi.panel());
            g2.fillRect(0, 0, w, h);
            g2.setColor(SnippetUi.alpha(SnippetUi.border(), 0.6f));
            g2.fillRect(0, h - 1, w, 1);
            int x = 0;
            if (picksVisible()) {
                paintCheck(g2, 9, (h - 14) / 2, selectedRow);
                x = 32;
            }
            if (sorting) {
                g2.setColor(SnippetUi.muted());
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 2; c++) {
                        g2.fillOval(x + 13 + c * 6, h / 2 - 7 + r * 5, 3, 3);
                    }
                }
            } else {
                boolean fav = snapshot.isFavorite(profile.id());
                SnippetUi.star(fav, 14).paintIcon(this, g2, x + 11, (h - 14) / 2);
            }
            x += 36;
            int right = w - (sorting ? 76 : 12);
            var cols = columns(x, right);
            Icon os = OsIcons.forHost(profile);
            os.paintIcon(this, g2, cols[0], (h - os.getIconHeight()) / 2);
            int nameX = cols[0] + os.getIconWidth() + 6;
            g2.setFont(SnippetUi.font(0, Font.BOLD));
            g2.setColor(selectedRow ? SnippetUi.onSelection() : SnippetUi.fg());
            var fm = g2.getFontMetrics();
            int base = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(SnippetUi.elide(profile.name(), fm, cols[1] - 12 - nameX), nameX, base);
            g2.setFont(SnippetUi.mono(-1.5f));
            g2.setColor(SnippetUi.muted());
            var mfm = g2.getFontMetrics();
            g2.drawString(SnippetUi.elide(profile.address(), mfm, cols[2] - 12 - cols[1]), cols[1],
                    (h - mfm.getHeight()) / 2 + mfm.getAscent());
            int bx = badge(g2, ProfileFacts.authBadge(profile), SnippetUi.accent(), cols[2], h);
            if (profile.jumpHostId() != null) {
                badge(g2, "jump", SnippetUi.favorite(), bx + 4, h);
            }
            if (sorting) {
                int ux = w - 64;
                boolean first = row == 0;
                boolean last = row == listModel.size() - 1;
                SnippetUi.stroke(UP, 13, () -> SnippetUi.alpha(SnippetUi.muted(), first ? 0.35f : 1f), 2.2f)
                        .paintIcon(this, g2, ux + 8, (h - 13) / 2);
                SnippetUi.stroke(DOWN, 13, () -> SnippetUi.alpha(SnippetUi.muted(), last ? 0.35f : 1f), 2.2f)
                        .paintIcon(this, g2, ux + 38, (h - 13) / 2);
            }
            g2.dispose();
        }
    }

    /** Badge membulat 16 px (prototipe {@code .badge}). @return x setelah badge */
    static int badge(Graphics2D g2, String text, Color color, int x, int h) {
        g2.setFont(SnippetUi.font(-2, Font.BOLD));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(text) + 12;
        int y = (h - 16) / 2;
        g2.setColor(SnippetUi.alpha(color, 0.14f));
        g2.fill(new RoundRectangle2D.Float(x, y, w, 16, 16, 16));
        g2.setColor(color);
        g2.drawString(text, x + 6, y + (16 - fm.getHeight()) / 2 + fm.getAscent());
        return x + w;
    }

    // --- mode urutan ---

    private void toggleSort() {
        if (sorting) {
            setSorting(false);
            return;
        }
        guard(() -> {
            if (nav.kind() != Nav.Kind.FOLDER && nav.kind() != Nav.Kind.NONE) {
                var cur = selected == null ? null : snapshot.find(selected).orElse(null);
                String g = cur != null ? cur.group() : snapshot.childGroups("").stream().findFirst().orElse("");
                nav = g.isEmpty() ? new Nav(Nav.Kind.NONE, "") : new Nav(Nav.Kind.FOLDER, g);
            }
            search.setText("");
            setSorting(true);
        });
    }

    private void setSorting(boolean on) {
        sorting = on;
        sortButton.setText(I18n.t(on ? "pm.reorder.done" : "pm.reorder"));
        sortButton.setForeground(on ? SnippetUi.accent() : SnippetUi.fg());
        sortBanner.setVisible(on);
        search.setEnabled(!on);
        list.setSelectionMode(on ? ListSelectionModel.SINGLE_SELECTION
                : ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        reload(selected);
    }

    private void moveBy(HostProfile p, int delta) {
        var ids = new ArrayList<>(shown().stream().map(HostProfile::id).toList());
        int i = ids.indexOf(p.id());
        int j = i + delta;
        if (i < 0 || j < 0 || j >= ids.size()) {
            return;
        }
        java.util.Collections.swap(ids, i, j);
        saveOrder(ids);
    }

    private void saveOrder(List<UUID> ids) {
        UiAsync.run(host.io(), () -> host.store().reorderProfiles(ids),
                err -> Dialogs.error(this, I18n.t("main.error.saveProfile"), err));
    }

    /** Seret baris di mode urutan; di luar mode itu seret tidak melakukan apa-apa. */
    private final class ReorderHandler extends javax.swing.TransferHandler {
        private static final java.awt.datatransfer.DataFlavor ROW = new java.awt.datatransfer.DataFlavor(
                Integer.class, "profile row");

        @Override
        public int getSourceActions(JComponent c) {
            return sorting ? MOVE : NONE;
        }

        @Override
        protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
            int row = list.getSelectedIndex();
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
                int to = ((JList.DropLocation) support.getDropLocation()).getIndex();
                var ids = new ArrayList<>(shown().stream().map(HostProfile::id).toList());
                if (from < 0 || from >= ids.size()) {
                    return false;
                }
                UUID moving = ids.remove(from);
                ids.add(Math.min(ids.size(), to > from ? to - 1 : to), moving);
                saveOrder(ids);
                return true;
            } catch (java.awt.datatransfer.UnsupportedFlavorException | java.io.IOException e) {
                return false;
            }
        }
    }

    // --- panel detail ---

    private JComponent detailPane() {
        for (var c : new JPanel[] {viewContent, bulkContent, editContent}) {
            c.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        }
        detailArea.add(scroll(viewContent), Mode.VIEW.name());
        detailArea.add(scroll(bulkContent), Mode.BULK.name());
        detailArea.add(scroll(editContent), Mode.EDIT.name());
        detailArea.add(noneCard(), Mode.NONE.name());
        detailArea.setBackground(SnippetUi.panel());

        footerArea.setOpaque(false);
        footerArea.add(viewFooter(), Mode.VIEW.name());
        footerArea.add(bulkFooter, Mode.BULK.name());
        footerArea.add(editFooter(), Mode.EDIT.name());
        var none = new JPanel();
        none.setOpaque(false);
        none.setPreferredSize(new Dimension(0, 0));
        footerArea.add(none, Mode.NONE.name());
        testPanel.setVisible(false);
        var south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(testPanel, BorderLayout.NORTH);
        south.add(footerArea, BorderLayout.SOUTH);

        var pane = new JPanel(new BorderLayout());
        pane.setBackground(SnippetUi.panel());
        pane.setPreferredSize(new Dimension(DETAIL_W, 0));
        pane.add(detailArea, BorderLayout.CENTER);
        pane.add(south, BorderLayout.SOUTH);
        return pane;
    }

    private static JPanel column() {
        var p = new SnippetUi.TrackingPanel(null);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(SnippetUi.panel());
        return p;
    }

    private static JScrollPane scroll(JComponent content) {
        var s = new JScrollPane(content);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.getViewport().setBackground(SnippetUi.panel());
        s.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        s.getVerticalScrollBar().setUnitIncrement(16);
        return s;
    }

    private static JPanel footerRow() {
        var row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBackground(SnippetUi.panel());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        return row;
    }

    private JComponent noneCard() {
        var box = new JPanel(new GridBagLayout());
        box.setBackground(SnippetUi.panel());
        var inner = new JPanel(new GridLayout(2, 1, 0, 6));
        inner.setOpaque(false);
        var title = SnippetUi.label(I18n.t("pm.none.title"), SnippetUi.font(1, Font.PLAIN), SnippetUi.fg());
        title.setHorizontalAlignment(JLabel.CENTER);
        var hint = SnippetUi.label(I18n.t("pm.none.hint"), SnippetUi.font(0, Font.PLAIN), SnippetUi.muted());
        hint.setHorizontalAlignment(JLabel.CENTER);
        inner.add(title);
        inner.add(hint);
        box.add(inner);
        return box;
    }

    private void showDetail() {
        switch (mode) {
            case VIEW -> snapshot.find(selected).ifPresentOrElse(this::buildView, () -> mode = Mode.NONE);
            case BULK -> buildBulk();
            default -> { }
        }
        detailCards.show(detailArea, mode.name());
        footerCards.show(footerArea, mode.name());
        footerArea.setVisible(mode != Mode.NONE);
    }

    private SnippetUi.Button viewConnect;
    private SnippetUi.Button viewTest;

    private JComponent viewFooter() {
        var row = footerRow();
        viewConnect = primary(I18n.t("pm.connect"), PROMPT);
        viewConnect.setToolTipText(I18n.t("pm.connect.tip"));
        viewConnect.addActionListener(e -> currentProfile().ifPresent(p -> connect(List.of(p))));
        viewTest = new SnippetUi.Button(I18n.t("pm.test"), null, SnippetUi.Variant.DEFAULT);
        viewTest.addActionListener(e -> currentProfile().ifPresent(p -> runTest(p, null, null, false)));
        var edit = iconButton(EDIT, I18n.t("pm.edit.tip"), SnippetUi.Variant.DEFAULT);
        edit.addActionListener(e -> currentProfile().ifPresent(this::edit));
        var dup = iconButton(COPY, I18n.t("pm.duplicate.tip"), SnippetUi.Variant.DEFAULT);
        dup.addActionListener(e -> currentProfile().ifPresent(this::duplicate));
        var del = iconButton(SnippetUi.TRASH, I18n.t("pm.delete.tip"), SnippetUi.Variant.DANGER);
        del.addActionListener(e -> currentProfile().ifPresent(this::askDelete));
        row.add(viewConnect);
        row.add(Box.createHorizontalStrut(8));
        row.add(viewTest);
        row.add(Box.createHorizontalGlue());
        row.add(edit);
        row.add(Box.createHorizontalStrut(8));
        row.add(dup);
        row.add(Box.createHorizontalStrut(8));
        row.add(del);
        return row;
    }

    private java.util.Optional<HostProfile> currentProfile() {
        return mode == Mode.VIEW && selected != null ? snapshot.find(selected) : java.util.Optional.empty();
    }

    private void buildView(HostProfile p) {
        viewContent.removeAll();
        var title = new JPanel(new BorderLayout(8, 0));
        title.setOpaque(false);
        var texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        var name = new SnippetUi.Paragraph(SnippetUi.font(2, Font.BOLD), SnippetUi.fg()).add(p.name(),
                SnippetUi.fg(), true, false);
        texts.add(name);
        texts.add(Box.createVerticalStrut(4));
        texts.add(SnippetUi.text(ProfileFacts.folderName(p.group()) + " · " + I18n.t("pm.lastUsed",
                ProfileFacts.lastUsed(p.id(), snapshot)), SnippetUi.font(0, Font.PLAIN), SnippetUi.muted()));
        title.add(texts, BorderLayout.CENTER);
        boolean fav = snapshot.isFavorite(p.id());
        var star = new SnippetUi.Button(null, SnippetUi.star(fav, 16), SnippetUi.Variant.ICON);
        star.setToolTipText(I18n.t(fav ? "pm.fav.remove" : "pm.fav.add"));
        star.getAccessibleContext().setAccessibleName(star.getToolTipText());
        star.addActionListener(e -> toggleFavorite(p));
        var starWrap = new JPanel(new BorderLayout());
        starWrap.setOpaque(false);
        starWrap.add(star, BorderLayout.NORTH);
        title.add(starWrap, BorderLayout.EAST);
        add(viewContent, title, 14);

        add(viewContent, sectionLabel(I18n.t("pm.section.connection")), 6);
        var cmd = new JTextArea(ProfileFacts.command(p, snapshot));
        cmd.setEditable(false);
        cmd.setLineWrap(true);
        cmd.setWrapStyleWord(false);
        cmd.setFont(SnippetUi.mono(-1));
        cmd.setForeground(SnippetUi.fg());
        add(viewContent, SnippetUi.codeBox(cmd), 14);

        var facts = new JPanel(new GridBagLayout());
        facts.setOpaque(false);
        int r = 0;
        for (var f : ProfileFacts.facts(p, snapshot, storedFor(p.id()))) {
            var k = new GridBagConstraints();
            k.gridx = 0;
            k.gridy = r;
            k.anchor = GridBagConstraints.FIRST_LINE_START;
            k.insets = new Insets(0, 0, 7, 10);
            var key = SnippetUi.label(f.key(), SnippetUi.font(0, Font.PLAIN), SnippetUi.muted());
            key.setPreferredSize(new Dimension(112, key.getPreferredSize().height));
            facts.add(key, k);
            var v = new GridBagConstraints();
            v.gridx = 1;
            v.gridy = r++;
            v.weightx = 1;
            v.fill = GridBagConstraints.HORIZONTAL;
            v.insets = new Insets(0, 0, 7, 0);
            facts.add(SnippetUi.text(f.value(), f.mono() ? SnippetUi.mono(-1) : SnippetUi.font(0, Font.PLAIN),
                    f.muted() ? SnippetUi.muted() : SnippetUi.fg()), v);
        }
        add(viewContent, facts, 14);
        add(viewContent, snippetLink(p), 0);
        viewContent.add(Box.createVerticalGlue());
        viewConnect.setText(I18n.t(p.isFtp() ? "pm.connect.ftp" : "pm.connect"));
        viewTest.setEnabled(!p.isFtp()); // Test memakai SSH
        viewTest.setToolTipText(p.isFtp() ? I18n.t("pm.test.sshOnly") : null);
        viewContent.revalidate();
        viewContent.repaint();
    }

    /** Kartu "N snippet untuk host ini" yang membuka Snippet manager (prototipe {@code .linkrow}). */
    private JComponent snippetLink(HostProfile p) {
        List<Snippet> snippets = host.snippetsFor(p);
        long all = snippets.stream().filter(s -> s.scope() instanceof SnippetScope.All).count();
        long folder = snippets.stream().filter(s -> s.scope() instanceof SnippetScope.Group).count();
        long only = snippets.stream().filter(s -> s.scope() instanceof SnippetScope.Host).count();
        var parts = new ArrayList<String>();
        parts.add(I18n.t("pm.snippets.all", all));
        if (folder > 0) {
            parts.add(I18n.t("pm.snippets.folder", folder, ProfileFacts.folderName(p.group())));
        }
        if (only > 0) {
            parts.add(I18n.t("pm.snippets.host", only));
        }
        var row = new SnippetUi.RoundPanel(new BorderLayout(10, 0), SnippetUi::chip, SnippetUi::border, 8);
        row.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
        row.add(new JLabel(SnippetUi.stroke(SnippetUi.BOLT, 14, SnippetUi::favorite, 2)), BorderLayout.WEST);
        var texts = new JPanel(new GridLayout(2, 1, 0, 1));
        texts.setOpaque(false);
        texts.add(SnippetUi.label(I18n.t(snippets.size() == 1 ? "pm.snippets.head.one" : "pm.snippets.head",
                snippets.size()), SnippetUi.font(0, Font.BOLD), SnippetUi.fg()));
        texts.add(SnippetUi.label(String.join(" · ", parts), SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted()));
        row.add(texts, BorderLayout.CENTER);
        row.add(new JLabel(SnippetUi.stroke(RIGHT, 12, SnippetUi::muted, 2.2f)), BorderLayout.EAST);
        row.setFocusable(true);
        row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        row.getAccessibleContext().setAccessibleName(I18n.t("pm.snippets.open"));
        row.setToolTipText(I18n.t("pm.snippets.open"));
        Runnable open = () -> guard(() -> {
            dispose();
            host.openSnippets();
        });
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                open.run();
            }
        });
        row.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
        row.getActionMap().put("open", action(open));
        return row;
    }

    private static void add(JPanel column, JComponent c, int gap) {
        PmDialogs.add(column, c, gap);
    }

    static JLabel sectionLabel(String text) {
        return SnippetUi.label(text.toUpperCase(Locale.ROOT), SnippetUi.font(-1, Font.BOLD), SnippetUi.muted());
    }

    private static SnippetUi.Button primary(String text, String path) {
        return new SnippetUi.Button(text, path == null ? null
                : SnippetUi.stroke(path, 14, () -> SnippetUi.onAccent(SnippetUi.accent()), 2.2f),
                SnippetUi.Variant.PRIMARY);
    }

    static SnippetUi.Button iconButton(String path, String tip, SnippetUi.Variant variant) {
        var b = new SnippetUi.Button(null, SnippetUi.stroke(path, 15,
                variant == SnippetUi.Variant.DANGER ? SnippetUi::danger : SnippetUi::fg, 1.9f), variant);
        b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleName(tip);
        // tanpa padding teks 14 px: tombol 30 px lebih sempit dari padding + ikon, sehingga ikon digambar mulai dari
        // padding kiri (tidak di tengah) dan ketiga tombol tampak tidak simetris
        b.setBorder(BorderFactory.createEmptyBorder());
        b.setPreferredSize(new Dimension(30, 30));
        b.setMaximumSize(new Dimension(30, 30));
        return b;
    }

    // --- aksi massal ---

    private void buildBulk() {
        bulkContent.removeAll();
        var picked = list.getSelectedValuesList();
        var ids = picked.stream().map(HostProfile::id).toList();
        var head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        var texts = new JPanel(new GridLayout(2, 1, 0, 4));
        texts.setOpaque(false);
        texts.add(SnippetUi.label(I18n.t("pm.bulk.title", ProfileFacts.profiles(picked.size())),
                SnippetUi.font(2, Font.BOLD), SnippetUi.fg()));
        texts.add(SnippetUi.label(I18n.t("pm.bulk.sub"), SnippetUi.font(0, Font.PLAIN), SnippetUi.muted()));
        head.add(texts, BorderLayout.CENTER);
        var clear = new SnippetUi.Button(I18n.t("pm.bulk.clear"), null, SnippetUi.Variant.DEFAULT);
        clear.addActionListener(e -> list.clearSelection());
        var clearWrap = new JPanel(new BorderLayout());
        clearWrap.setOpaque(false);
        clearWrap.add(clear, BorderLayout.NORTH);
        head.add(clearWrap, BorderLayout.EAST);
        add(bulkContent, head, 14);

        var rows = new SnippetUi.RoundPanel(null, SnippetUi::panel, SnippetUi::border, 8);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        for (var p : picked) {
            var row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.alpha(SnippetUi.border(), 0.6f)),
                    BorderFactory.createEmptyBorder(4, 10, 4, 4)));
            boolean running = bulkRunning.contains(p.id());
            var result = bulkTests.get(p.id());
            Color dot = running ? SnippetUi.favorite() : result == null ? SnippetUi.muted()
                    : result.ok() ? SnippetUi.success() : SnippetUi.danger();
            row.add(new JLabel(SnippetUi.dot(8, () -> dot)), BorderLayout.WEST);
            var t = new JPanel(new GridLayout(2, 1, 0, 1));
            t.setOpaque(false);
            t.add(SnippetUi.label(p.name(), SnippetUi.font(0, Font.BOLD), SnippetUi.fg()));
            t.add(SnippetUi.label(p.address(), SnippetUi.mono(-1.5f), SnippetUi.muted()));
            row.add(t, BorderLayout.CENTER);
            var east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            east.setOpaque(false);
            east.add(SnippetUi.label(running ? I18n.t("pm.bulk.testing") : result == null
                    ? ProfileFacts.folderName(p.group()) : I18n.t(result.ok() ? "pm.bulk.reachable" : "pm.bulk.noResponse"),
                    SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted()));
            var remove = new SnippetUi.Button(null, SnippetUi.stroke(SnippetUi.CLOSE, 10, SnippetUi::muted, 2.6f),
                    SnippetUi.Variant.ICON);
            remove.setToolTipText(I18n.t("pm.bulk.remove", p.name()));
            remove.getAccessibleContext().setAccessibleName(remove.getToolTipText());
            remove.addActionListener(e -> {
                int i = listModel.indexOf(p);
                if (i >= 0) {
                    list.removeSelectionInterval(i, i);
                }
            });
            east.add(remove);
            row.add(east, BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            row.setPreferredSize(new Dimension(0, 40));
            rows.add(row);
        }
        add(bulkContent, rows, 14);

        var notes = new ArrayList<String>();
        var failed = picked.stream().filter(p -> bulkTests.get(p.id()) != null && !bulkTests.get(p.id()).ok())
                .map(HostProfile::name).toList();
        if (!failed.isEmpty()) {
            notes.add(I18n.t("pm.bulk.note.failed", String.join(", ", failed)));
        }
        var asks = picked.stream().filter(p -> !p.isFtp() && p.authMethod() == AuthMethod.PASSWORD
                && !storedFor(p.id()).contains(SecretType.LOGIN_PASSWORD)).map(HostProfile::name).toList();
        if (!asks.isEmpty()) {
            notes.add(I18n.t("pm.bulk.note.asks", String.join(", ", asks)));
        }
        if (!notes.isEmpty()) {
            add(bulkContent, noteBox(String.join(" ", notes)), 14);
        }

        add(bulkContent, sectionLabel(I18n.t("pm.bulk.move")), 5);
        var folder = new JComboBox<FolderChoice>();
        folder.addItem(new FolderChoice(null, I18n.t("pm.bulk.move.choose")));
        folderChoices().forEach(folder::addItem);
        SnippetUi.styleField(folder, 30);
        folder.getAccessibleContext().setAccessibleName(I18n.t("pm.bulk.move"));
        folder.addActionListener(e -> {
            if (folder.getSelectedItem() instanceof FolderChoice c && c.group() != null) {
                moveMany(ids, c.group());
            }
        });
        add(bulkContent, folder, 0);
        bulkContent.add(Box.createVerticalGlue());
        bulkContent.revalidate();
        bulkContent.repaint();

        bulkFooter.removeAll();
        var connect = primary(I18n.t("pm.bulk.connect", picked.size()), PROMPT);
        connect.setToolTipText(I18n.t("pm.bulk.connect.tip"));
        connect.addActionListener(e -> connect(picked));
        var test = new SnippetUi.Button(I18n.t("pm.bulk.test"), null, SnippetUi.Variant.DEFAULT);
        test.addActionListener(e -> testMany(picked));
        var exp = iconButton(EXPORT, I18n.t("pm.bulk.export"), SnippetUi.Variant.DEFAULT);
        exp.addActionListener(e -> openExport(ExportScope.PICKED));
        var del = iconButton(SnippetUi.TRASH, I18n.t("pm.bulk.delete"), SnippetUi.Variant.DANGER);
        del.addActionListener(e -> askDeleteMany(picked));
        bulkFooter.add(connect);
        bulkFooter.add(Box.createHorizontalStrut(8));
        bulkFooter.add(test);
        bulkFooter.add(Box.createHorizontalGlue());
        bulkFooter.add(exp);
        bulkFooter.add(Box.createHorizontalStrut(8));
        bulkFooter.add(del);
        bulkFooter.revalidate();
        bulkFooter.repaint();
    }

    static JComponent noteBox(String text) {
        var box = new SnippetUi.RoundPanel(new BorderLayout(), () -> SnippetUi.alpha(SnippetUi.favorite(), 0.10f),
                () -> SnippetUi.alpha(SnippetUi.favorite(), 0.4f), 8);
        box.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        box.add(SnippetUi.text(text, SnippetUi.font(-0.5f, Font.PLAIN), SnippetUi.fg()), BorderLayout.CENTER);
        return box;
    }

    /** Pilihan folder (prototipe {@code folderOpts}): semua folder, lalu Ungrouped. */
    record FolderChoice(String group, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    List<FolderChoice> folderChoices() {
        var out = new ArrayList<FolderChoice>();
        for (String g : snapshot.allGroups()) {
            int depth = (int) g.chars().filter(c -> c == '/').count();
            out.add(new FolderChoice(g, "  ".repeat(depth) + g.substring(g.lastIndexOf('/') + 1)));
        }
        out.add(new FolderChoice("", I18n.t("pm.folder.ungrouped")));
        return out;
    }

    private void moveMany(List<UUID> ids, String group) {
        UiAsync.run(host.io(), () -> host.store().moveProfiles(ids, group), s -> toast(I18n.t("pm.toast.moved",
                ProfileFacts.profiles(ids.size()), ProfileFacts.folderName(group)), false),
                err -> Dialogs.error(this, I18n.t("main.error.saveProfile"), err));
    }

    private void testMany(List<HostProfile> picked) {
        int run = ++testRun;
        bulkTests.clear();
        bulkRunning.clear();
        picked.stream().filter(p -> !p.isFtp()).forEach(p -> bulkRunning.add(p.id()));
        buildBulk();
        for (var p : picked.stream().filter(x -> !x.isFtp()).toList()) {
            UiAsync.run(host.network(), () -> host.test(p, null, null), result -> {
                if (run != testRun) {
                    return;
                }
                bulkRunning.remove(p.id());
                bulkTests.put(p.id(), result);
                if (mode == Mode.BULK) {
                    buildBulk();
                }
            }, err -> {
                bulkRunning.remove(p.id());
                bulkTests.put(p.id(), new ConnectionTester.Result(ConnectionTester.HostKey.UNREACHED, false, false,
                        0, String.valueOf(err.getMessage())));
                if (mode == Mode.BULK) {
                    buildBulk();
                }
            });
        }
    }

    // --- aksi ---

    private void connect(List<HostProfile> profiles) {
        guard(() -> {
            dispose();
            host.connect(profiles);
        });
    }

    private void toggleFavorite(HostProfile p) {
        boolean on = !snapshot.isFavorite(p.id());
        UiAsync.run(host.io(), () -> host.store().setFavorite(p.id(), on),
                err -> Dialogs.error(this, I18n.t("main.error.saveProfile"), err));
    }

    private void duplicate(HostProfile p) {
        var names = new java.util.HashSet<String>();
        snapshot.profiles().forEach(x -> names.add(x.name()));
        String name = I18n.t("pm.copyName", p.name());
        for (int n = 2; names.contains(name); n++) {
            name = I18n.t("pm.copyNameN", p.name(), n);
        }
        var copy = new HostProfile(UUID.randomUUID(), name, p.group(), p.host(), p.port(), p.username(),
                p.authMethod(), p.privateKeyPath(), p.jumpHostId(), p.environment(), p.initialDirectory(), p.notes(),
                p.autoSudo(), p.os(), p.terminalTheme(), p.protocol(), p.sensitive());
        var order = new ArrayList<>(snapshot.profilesIn(p.group()).stream().map(HostProfile::id).toList());
        order.add(order.indexOf(p.id()) + 1, copy.id());
        UiAsync.run(host.io(), () -> {
            host.store().save(copy);
            return host.store().reorderProfiles(order);
        }, s -> {
            stored.put(copy.id(), Set.of());
            snapshot = s;
            select(copy.id());
            edit(copy);
            toast(I18n.t("pm.toast.duplicated"), false);
        }, err -> Dialogs.error(this, I18n.t("main.error.duplicateProfile"), err));
    }

    private void askDelete(HostProfile p) {
        var notes = new ArrayList<String>();
        var deps = snapshot.profiles().stream().filter(x -> p.id().equals(x.jumpHostId())).map(HostProfile::name)
                .toList();
        if (!deps.isEmpty()) {
            notes.add(I18n.t("pm.delete.note.jump", String.join(", ", deps)));
        }
        long scoped = host.snippetsFor(p).stream().filter(s -> s.scope() instanceof SnippetScope.Host).count();
        if (scoped > 0) {
            notes.add(I18n.t("pm.delete.note.snippets", scoped));
        }
        if (PmDialogs.confirmDanger(this, I18n.t("pm.delete.title", p.name()), I18n.t("pm.delete.body"), notes,
                I18n.t("pm.delete.ok"), I18n.t("common.cancel"))) {
            deleteProfiles(List.of(p.id()), I18n.t("pm.toast.deleted"));
        }
    }

    private void askDeleteMany(List<HostProfile> picked) {
        var ids = picked.stream().map(HostProfile::id).toList();
        var outside = snapshot.profiles().stream().filter(x -> x.jumpHostId() != null && ids.contains(x.jumpHostId())
                && !ids.contains(x.id())).map(HostProfile::name).toList();
        var notes = outside.isEmpty() ? List.<String>of()
                : List.of(I18n.t("pm.delete.note.jump", String.join(", ", outside)));
        if (PmDialogs.confirmDanger(this, I18n.t("pm.deleteMany.title", ProfileFacts.profiles(ids.size())),
                I18n.t("pm.deleteMany.body"), notes, I18n.t("pm.deleteMany.ok", ids.size()),
                I18n.t("common.cancel"))) {
            deleteProfiles(ids, I18n.t("pm.toast.deletedMany", ProfileFacts.profiles(ids.size())));
        }
    }

    private void deleteProfiles(List<UUID> ids, String message) {
        UiAsync.run(host.io(), () -> host.store().deleteAll(ids), s -> {
            host.profilesDeleted(ids);
            ids.forEach(stored::remove);
            snapshot = s;
            mode = Mode.NONE;
            selected = null;
            list.clearSelection();
            reload(null);
            if (!listModel.isEmpty()) {
                select(listModel.get(0).id());
            }
            toast(message, false);
        }, err -> Dialogs.error(this, I18n.t("main.error.deleteProfile"), err));
    }

    // --- edit / baru ---

    private void startNew(String group) {
        draft = ProfileDraft.blank(group);
        openForm(null);
    }

    private void edit(HostProfile p) {
        draft = ProfileDraft.of(p, storedFor(p.id()), snapshot.isFavorite(p.id()));
        openForm(p);
    }

    private void openForm(HostProfile p) {
        if (sorting) {
            setSorting(false);
        }
        mode = Mode.EDIT;
        tried = false;
        clearTest();
        if (p == null) {
            selected = null;
            reloading = true;
            list.clearSelection();
            reloading = false;
        }
        draftOrigin = draft.fingerprint();
        form.build(draft, p);
        detailCards.show(detailArea, Mode.EDIT.name());
        footerCards.show(footerArea, Mode.EDIT.name());
        footerArea.setVisible(true);
        SwingUtilities.invokeLater(() -> {
            form.focusFirst();
            editContent.scrollRectToVisible(new java.awt.Rectangle(0, 0, 1, 1)); // form mulai dari atas
        });
    }

    private boolean dirty() {
        return mode == Mode.EDIT && draft != null && (!draft.fingerprint().equals(draftOrigin) || form.secretsTyped());
    }

    private void leaveEdit() {
        form.clearSecrets();
        draft = null;
        mode = selected != null && snapshot.find(selected).isPresent() ? Mode.VIEW : Mode.NONE;
        tried = false;
        clearTest();
    }

    private void cancelEdit() {
        leaveEdit();
        showDetail();
        list.repaint();
    }

    /** Jalankan {@code then} setelah konfirmasi buang perubahan (kalau form edit berisi perubahan). */
    void guard(Runnable then) {
        if (!dirty()) {
            if (mode == Mode.EDIT) {
                leaveEdit();
                showDetail();
            }
            then.run();
            return;
        }
        if (confirmDiscard()) {
            leaveEdit();
            showDetail();
            then.run();
        }
    }

    private boolean confirmDiscard() {
        return PmDialogs.confirmDanger(this, I18n.t("pm.discard.title"), I18n.t("pm.discard.body"), List.of(),
                I18n.t("pm.discard.ok"), I18n.t("pm.discard.keep"));
    }

    private JComponent editFooter() {
        var row = footerRow();
        var test = new SnippetUi.Button(I18n.t("pm.test"), null, SnippetUi.Variant.DEFAULT);
        test.setToolTipText(I18n.t("pm.test.form.tip"));
        test.addActionListener(e -> testForm());
        var cancel = new SnippetUi.Button(I18n.t("common.cancel"), null, SnippetUi.Variant.DEFAULT);
        cancel.setToolTipText(I18n.t("pm.cancel.tip"));
        cancel.addActionListener(e -> cancelEdit());
        var save = primary(I18n.t("common.save"), null);
        save.setToolTipText(I18n.t("pm.save.tip", Shortcuts.text(Shortcuts.menu(KeyEvent.VK_S))));
        save.addActionListener(e -> save());
        row.add(test);
        row.add(Box.createHorizontalGlue());
        row.add(cancel);
        row.add(Box.createHorizontalStrut(8));
        row.add(save);
        return row;
    }

    private void testForm() {
        form.readInto(draft);
        if (draft.protocol.isFtp()) {
            toast(I18n.t("pm.test.sshOnly"), true);
            return;
        }
        var errs = draft.validate(snapshot, form.sudoTyped());
        if (errs.containsKey(ProfileDraft.Field.HOST) || errs.containsKey(ProfileDraft.Field.PORT)
                || errs.containsKey(ProfileDraft.Field.USER) || errs.containsKey(ProfileDraft.Field.KEY)) {
            tried = true;
            form.showErrors(errs);
            toast(I18n.t("pm.toast.fillConnection"), true);
            return;
        }
        HostProfile p;
        try {
            var d = draft;
            if (d.name.isBlank()) {
                d = copyWithName(d, d.host);
            }
            p = d.toProfile();
        } catch (IllegalArgumentException e) {
            toast(e.getMessage(), true);
            return;
        }
        runTest(p, form.password(), form.passphrase(), draft.isNew());
    }

    private static ProfileDraft copyWithName(ProfileDraft d, String name) {
        var c = ProfileDraft.blank(d.group);
        c.id = d.id;
        c.name = name;
        c.protocol = d.protocol;
        c.host = d.host;
        c.port = d.port;
        c.user = d.user;
        c.jump = d.jump;
        c.auth = d.auth;
        c.keyPath = d.keyPath;
        c.os = d.os;
        return c;
    }

    private void save() {
        form.readInto(draft);
        boolean sudoTyped = form.sudoTyped();
        var errs = draft.validate(snapshot, sudoTyped);
        if (!errs.isEmpty()) {
            tried = true;
            form.showErrors(errs);
            toast(I18n.t("pm.toast.fixFields"), true);
            return;
        }
        HostProfile p;
        try {
            p = draft.toProfile();
        } catch (IllegalArgumentException e) {
            toast(e.getMessage(), true);
            return;
        }
        if (p.protocol() == Protocol.FTP && !snapshot.find(p.id()).map(o -> o.protocol() == Protocol.FTP)
                .orElse(false) && !Dialogs.confirm(this, I18n.t("profile.protocol.ftpConfirmTitle"),
                I18n.t("profile.protocol.ftpConfirm"))) {
            return;
        }
        if (p.autoSudo() && p.needsGuard() && !snapshot.find(p.id()).map(HostProfile::autoSudo).orElse(false)
                && !Dialogs.confirm(this, I18n.t("profile.autoSudo.prodTitle"), I18n.t("profile.autoSudo.prodConfirm"))) {
            return;
        }
        var changes = form.secretChanges(draft);
        boolean copyLogin = draft.sudo == ProfileDraft.Sudo.SAME && !changes.containsKey(SecretType.SUDO_PASSWORD)
                && draft.stored.contains(SecretType.LOGIN_PASSWORD);
        boolean favorite = draft.favorite;
        boolean isNew = draft.isNew();
        String group = p.group();
        form.clearSecrets();
        UiAsync.run(host.io(), () -> {
            var s = host.store().save(p);
            if (s.isFavorite(p.id()) != favorite) {
                s = host.store().setFavorite(p.id(), favorite);
            }
            return s;
        }, s -> {
            if (!changes.isEmpty() || copyLogin) {
                host.saveSecrets(p, changes, copyLogin);
            } else {
                SecretChange.discardAll(changes);
            }
            var now = new java.util.HashSet<>(storedFor(p.id()));
            changes.forEach((type, change) -> {
                if (change instanceof SecretChange.Set) {
                    now.add(type);
                } else {
                    now.remove(type);
                }
            });
            if (copyLogin) {
                now.add(SecretType.SUDO_PASSWORD);
            }
            stored.put(p.id(), Set.copyOf(now));
            snapshot = s;
            draft = null;
            mode = Mode.VIEW;
            selected = p.id();
            boolean keepNav = nav.kind() == Nav.Kind.ALL || nav.kind() == Nav.Kind.FOLDER && nav.group().equals(group)
                    || nav.kind() == Nav.Kind.NONE && group.isEmpty() || !isNew && shown().stream()
                    .anyMatch(x -> x.id().equals(p.id()));
            if (!keepNav) {
                nav = Nav.ALL;
            }
            search.setText("");
            select(p.id());
            toast(I18n.t("pm.toast.saved"), false);
        }, err -> {
            SecretChange.discardAll(changes);
            Dialogs.error(this, I18n.t("main.error.saveProfile"), err);
        });
    }

    // --- test koneksi ---

    /** @param password / passphrase dari form (di-zero setelah dipakai), null = dari vault */
    private void runTest(HostProfile p, char[] password, char[] passphrase, boolean isDraftNew) {
        int run = ++testRun;
        showTest(SnippetUi.favorite(), I18n.t("pm.test.running", p.host() + ":" + p.port()), List.of(), null);
        UiAsync.run(host.network(), () -> host.test(p, password, passphrase), result -> {
            if (run == testRun) {
                showResult(p, result, isDraftNew);
            }
        }, err -> {
            if (run == testRun) {
                showTest(SnippetUi.danger(), I18n.t("pm.test.failed"), List.of(new TestRow(false, true,
                        String.valueOf(err.getMessage()))), null);
            }
        });
    }

    /** Satu baris hasil test: ok (centang hijau), warn (tanda seru kuning), atau gagal (silang merah). */
    record TestRow(boolean ok, boolean bad, String text) {
    }

    private void showResult(HostProfile p, ConnectionTester.Result r, boolean isDraftNew) {
        var rows = new ArrayList<TestRow>();
        String addr = p.host() + ":" + p.port();
        ProfileFacts.jumpOf(p, snapshot).ifPresent(j -> {
            if (r.hostKey() != ConnectionTester.HostKey.UNREACHED) {
                rows.add(new TestRow(true, false, I18n.t("pm.test.jump", j.name())));
            }
        });
        switch (r.hostKey()) {
            case UNREACHED -> {
                rows.add(new TestRow(false, true, Objects.requireNonNullElse(r.error(),
                        I18n.t("pm.test.noResponse", addr))));
                showTest(SnippetUi.danger(), I18n.t("pm.test.failed"), rows, I18n.t("pm.test.failed.hint"));
                return;
            }
            case CHANGED -> {
                rows.add(new TestRow(true, false, I18n.t("pm.test.reached", addr, r.millis())));
                rows.add(new TestRow(false, true, I18n.t("pm.test.keyChanged")));
                showTest(SnippetUi.danger(), I18n.t("pm.test.failed"), rows, I18n.t("pm.test.keyChanged.hint"));
                return;
            }
            case NEW -> {
                rows.add(new TestRow(true, false, I18n.t("pm.test.reached", addr, r.millis())));
                rows.add(new TestRow(false, false, I18n.t("pm.test.keyNew")));
                showTest(SnippetUi.favorite(), I18n.t("pm.test.works"), rows, null);
                return;
            }
            case KNOWN -> {
                rows.add(new TestRow(true, false, I18n.t("pm.test.reached", addr, r.millis())));
                rows.add(new TestRow(true, false, I18n.t("pm.test.keyKnown")));
            }
        }
        if (r.signedIn()) {
            rows.add(new TestRow(true, false, I18n.t(switch (p.isFtp() ? AuthMethod.PASSWORD : p.authMethod()) {
                case KEY -> "pm.test.signedIn.key";
                case AGENT -> "pm.test.signedIn.agent";
                case PASSWORD -> "pm.test.signedIn.password";
            }, p.username())));
            if (p.autoSudo()) {
                rows.add(new TestRow(false, false, I18n.t("pm.test.sudoNotTested")));
            }
            showTest(SnippetUi.success(), I18n.t("pm.test.works"), rows, null);
        } else if (r.passwordMissing()) {
            rows.add(new TestRow(false, false, I18n.t("pm.test.noPassword")));
            showTest(SnippetUi.favorite(), I18n.t("pm.test.works"), rows, null);
        } else {
            rows.add(new TestRow(false, true, Objects.requireNonNullElse(r.error(), I18n.t("pm.test.signInFailed"))));
            showTest(SnippetUi.danger(), I18n.t("pm.test.failed"), rows, I18n.t("pm.test.signInFailed.hint"));
        }
    }

    /** Panel status test di atas tombol (prototipe {@code role=status}). */
    private void showTest(Color dot, String title, List<TestRow> rows, String hint) {
        testPanel.removeAll();
        testPanel.setBackground(SnippetUi.bar());
        testPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        var box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        var head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        head.add(new JLabel(SnippetUi.dot(8, () -> dot)), BorderLayout.WEST);
        head.add(SnippetUi.label(title, SnippetUi.font(0, Font.BOLD), SnippetUi.fg()), BorderLayout.CENTER);
        var close = new SnippetUi.Button(null, SnippetUi.stroke(SnippetUi.CLOSE, 10, SnippetUi::muted, 2.6f),
                SnippetUi.Variant.ICON);
        close.setToolTipText(I18n.t("pm.test.dismiss"));
        close.getAccessibleContext().setAccessibleName(I18n.t("pm.test.dismiss"));
        close.addActionListener(e -> clearTest());
        head.add(close, BorderLayout.EAST);
        add(box, head, 6);
        for (var r : rows) {
            Color c = r.ok() ? SnippetUi.success() : r.bad() ? SnippetUi.danger() : SnippetUi.favorite();
            String path = r.ok() ? SnippetUi.CHECK : r.bad() ? SnippetUi.CLOSE : "M12 5v9M12 19h.01";
            var line = SnippetUi.text(r.text(), SnippetUi.font(0, Font.PLAIN), SnippetUi.fg());
            var row = new JPanel(new BorderLayout(7, 0));
            row.setOpaque(false);
            var icon = new JPanel(new BorderLayout());
            icon.setOpaque(false);
            icon.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
            icon.add(new JLabel(SnippetUi.stroke(path, 12, () -> c, 2.8f)), BorderLayout.NORTH);
            row.add(icon, BorderLayout.WEST);
            row.add(line, BorderLayout.CENTER);
            add(box, row, 6);
        }
        if (hint != null) {
            add(box, SnippetUi.text(hint, SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted()), 0);
        }
        testPanel.add(box, BorderLayout.CENTER);
        testPanel.setVisible(true);
        testPanel.revalidate();
        testPanel.repaint();
    }

    private void clearTest() {
        testRun++;
        testPanel.setVisible(false);
        testPanel.removeAll();
    }

    // --- dialog anak ---

    enum ExportScope { ALL, SHOWN, PICKED, ONE }

    private void openExport(ExportScope initial) {
        var picked = list.getSelectedValuesList();
        var one = mode == Mode.VIEW ? snapshot.find(selected).orElse(null) : null;
        new ProfileExportDialog(this, host, snapshot, shown(), picked.size() > 1 ? picked : List.of(), one,
                initial == null ? ExportScope.ALL : initial, this::toast).setVisible(true);
    }

    private void openImport() {
        new ProfileImportDialog(this, host, snapshot, currentFolder(), (message, first) -> {
            toast(message, false);
            if (first != null) {
                nav = Nav.ALL;
                search.setText("");
                select(first);
            }
        }).setVisible(true);
    }

    private void openFolders() {
        guard(() -> new ProfileFoldersDialog(this, host, this::toast).setVisible(true));
    }

    private void closeManager() {
        guard(this::dispose);
    }

    // --- keyboard ---

    private void bindKeys() {
        var root = getRootPane();
        key(root, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape", () -> {
            if (mode == Mode.EDIT) {
                guard(() -> { });
            } else if (sorting) {
                setSorting(false);
            } else {
                dispose();
            }
        });
        key(root, Shortcuts.menu(KeyEvent.VK_F), "search", () -> {
            if (search.isEnabled()) {
                search.requestFocusInWindow();
                search.selectAll();
            }
        });
        key(root, Shortcuts.menu(KeyEvent.VK_N), "new", () -> guard(() -> startNew(currentFolder())));
        key(root, Shortcuts.menu(KeyEvent.VK_E), "export", () -> openExport(null));
        key(root, Shortcuts.menu(KeyEvent.VK_S), "save", () -> {
            if (mode == Mode.EDIT) {
                save();
            }
        });
        key(root, Shortcuts.menu(KeyEvent.VK_D), "duplicate", () -> currentProfile().ifPresent(this::duplicate));
        key(list, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "edit", () -> currentProfile().ifPresent(this::edit));
        key(list, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete", () -> {
            if (mode == Mode.BULK) {
                askDeleteMany(list.getSelectedValuesList());
            } else {
                currentProfile().ifPresent(this::askDelete);
            }
        });
        key(list, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "connect", () -> {
            var picked = list.getSelectedValuesList();
            if (!picked.isEmpty() && !sorting) {
                connect(picked);
            }
        });
    }

    private static void key(JComponent c, KeyStroke ks, String name, Runnable action) {
        int when = c instanceof javax.swing.JRootPane ? JComponent.WHEN_IN_FOCUSED_WINDOW : JComponent.WHEN_FOCUSED;
        c.getInputMap(when).put(ks, name);
        c.getActionMap().put(name, action(action));
    }

    static javax.swing.Action action(Runnable r) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                r.run();
            }
        };
    }

    static DocumentListener onChange(Runnable r) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    /** Panel bar (header / status bar) yang sudutnya ikut membulat dengan bingkai dialog. */
    static final class EdgePanel extends JPanel {
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

    // --- form edit ---

    /** Form edit / profil baru (prototipe: Name, Folder, Connection, Sign-in, Session, plus Lainnya). */
    private final class ProfileForm {
        private final JTextField name = field(false);
        private final JComboBox<FolderChoice> folder = new JComboBox<>();
        private final JComboBox<Protocol> protocol = new JComboBox<>(Protocol.values());
        private final JTextField hostField = field(true);
        private final JTextField port = field(true);
        private final JTextField user = field(true);
        private final JComboBox<JumpChoice> jump = new JComboBox<>();
        private final Map<AuthMethod, SegButton> authButtons = new EnumMap<>(AuthMethod.class);
        private final SnippetUi.Toggle savePw = SnippetUi.toggle(I18n.t("pm.form.savePw"),
                I18n.t("pm.form.savePw.hint"), true, SnippetUi::accent);
        private final JPasswordField password = secret();
        private final JTextField keyPath = field(true);
        private final JPasswordField passphrase = secret();
        private final JComboBox<SudoChoice> sudo = new JComboBox<>();
        private final JPasswordField sudoPassword = secret();
        private final JTextField startDir = field(true);
        private final SnippetUi.Toggle favorite = SnippetUi.toggle(I18n.t("pm.form.favorite"),
                I18n.t("pm.form.favorite.hint"), false, SnippetUi::accent);
        private final JComboBox<EnvironmentTag> environment = new JComboBox<>(EnvironmentTag.values());
        private final JCheckBox sensitive = new JCheckBox(I18n.t("profile.field.sensitive"));
        private final JComboBox<HostTerminalColors.Choice> colors = new JComboBox<>();
        private final JTextArea notes = new JTextArea(3, 20);
        private final JPasswordField rootPassword = secret();
        private final JCheckBox clearRoot = new JCheckBox(I18n.t("pm.form.root.clear"));
        private final Map<ProfileDraft.Field, JLabel> errors = new EnumMap<>(ProfileDraft.Field.class);
        private final Map<ProfileDraft.Field, JComponent> inputs = new EnumMap<>(ProfileDraft.Field.class);
        private JComponent passwordRow;
        private JComponent keyRows;
        private JComponent agentHint;
        private JComponent sudoPwRow;
        private JComponent signInSection;
        private JComponent jumpRow;
        private boolean sensitiveChosen;
        private boolean filling;

        ProfileForm() {
            for (var f : ProfileDraft.Field.values()) {
                var l = SnippetUi.label("", SnippetUi.font(-0.5f, Font.PLAIN), SnippetUi.danger());
                l.setVisible(false);
                errors.put(f, l);
            }
            inputs.put(ProfileDraft.Field.NAME, name);
            inputs.put(ProfileDraft.Field.HOST, hostField);
            inputs.put(ProfileDraft.Field.PORT, port);
            inputs.put(ProfileDraft.Field.USER, user);
            inputs.put(ProfileDraft.Field.KEY, keyPath);
            inputs.put(ProfileDraft.Field.SUDO, sudoPassword);
            for (var c : new JComponent[] {folder, protocol, jump, sudo, environment, colors}) {
                SnippetUi.styleField(c, 30);
            }
            protocol.setRenderer(new javax.swing.DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel,
                                                              boolean focus) {
                    super.getListCellRendererComponent(l, value, index, sel, focus);
                    if (value instanceof Protocol p) {
                        setText(ProfileFacts.protocolLabel(p));
                    }
                    return this;
                }
            });
            hostField.getDocument().addDocumentListener(onChange(() -> {
                if (!filling && hostField.getText().contains("@") || !filling && hostField.getText().matches(".*:\\d+")) {
                    SwingUtilities.invokeLater(this::splitPaste);
                }
            }));
            protocol.addActionListener(e -> {
                if (!filling) {
                    var p = (Protocol) protocol.getSelectedItem();
                    var old = draft.protocol;
                    if (port.getText().strip().equals(String.valueOf(old.defaultPort()))) {
                        port.setText(String.valueOf(p.defaultPort()));
                    }
                    draft.protocol = p;
                    updateVisibility();
                }
            });
            sudo.addActionListener(e -> {
                if (!filling) {
                    updateVisibility();
                }
            });
            savePw.box().addActionListener(e -> updateVisibility());
            environment.addActionListener(e -> {
                if (!filling && !sensitiveChosen) {
                    sensitive.setSelected(((EnvironmentTag) environment.getSelectedItem()).isProduction());
                }
            });
            sensitive.addActionListener(e -> sensitiveChosen = true);
            sensitive.setOpaque(false);
            clearRoot.setOpaque(false);
            notes.setLineWrap(true);
            notes.setWrapStyleWord(true);
            notes.setFont(SnippetUi.font(0, Font.PLAIN));
        }

        private void splitPaste() {
            if (draft == null) {
                return;
            }
            String before = user.getText();
            var d = ProfileDraft.blank("");
            d.user = before;
            d.port = port.getText();
            if (d.setHost(hostField.getText())) {
                filling = true;
                hostField.setText(d.host);
                user.setText(d.user);
                port.setText(d.port);
                filling = false;
            }
        }

        private JTextField field(boolean mono) {
            var f = SnippetUi.styleField(new JTextField(), 30);
            f.setFont(mono ? SnippetUi.mono(-1.5f) : SnippetUi.font(0, Font.PLAIN));
            return f;
        }

        private JPasswordField secret() {
            var f = SnippetUi.styleField(new JPasswordField(), 30);
            f.putClientProperty("JPasswordField.showRevealButton", true);
            return f;
        }

        void build(ProfileDraft d, HostProfile original) {
            filling = true;
            clearSecrets();
            sensitiveChosen = d.sensitive != null;
            name.setText(d.name);
            folder.removeAllItems();
            folderChoices().forEach(folder::addItem);
            selectFolder(d.group);
            protocol.setSelectedItem(d.protocol);
            hostField.setText(d.host);
            port.setText(d.port);
            user.setText(d.user);
            jump.removeAllItems();
            jump.addItem(new JumpChoice(null, I18n.t("pm.jump.none")));
            snapshot.profiles().stream().filter(p -> !p.isFtp() && !p.id().equals(d.id)
                    && !(d.id != null && usesJump(p.id(), d.id)))
                    .forEach(p -> jump.addItem(new JumpChoice(p.id(), p.name())));
            for (int i = 0; i < jump.getItemCount(); i++) {
                if (Objects.equals(jump.getItemAt(i).id(), d.jump)) {
                    jump.setSelectedIndex(i);
                }
            }
            savePw.box().setSelected(d.savePassword);
            password.putClientProperty("JTextField.placeholderText", I18n.t(d.stored.contains(SecretType.LOGIN_PASSWORD)
                    ? "pm.form.secret.saved" : "pm.form.password"));
            keyPath.setText(d.keyPath);
            passphrase.putClientProperty("JTextField.placeholderText", I18n.t(d.stored.contains(
                    SecretType.KEY_PASSPHRASE) ? "pm.form.secret.saved" : "pm.form.passphrase.ph"));
            sudoPassword.putClientProperty("JTextField.placeholderText", I18n.t(d.stored.contains(
                    SecretType.SUDO_PASSWORD) ? "pm.form.secret.saved" : "pm.form.password"));
            rootPassword.putClientProperty("JTextField.placeholderText", I18n.t(d.stored.contains(
                    SecretType.ROOT_PASSWORD) ? "pm.form.secret.saved" : "pm.form.password"));
            clearRoot.setSelected(false);
            clearRoot.setVisible(d.stored.contains(SecretType.ROOT_PASSWORD));
            startDir.setText(d.startDir);
            favorite.box().setSelected(d.favorite);
            environment.setSelectedItem(d.environment);
            sensitive.setSelected(original != null ? original.needsGuard() : d.environment.isProduction());
            colors.removeAllItems();
            host.colorChoices().forEach(colors::addItem);
            for (int i = 0; i < colors.getItemCount(); i++) {
                if (Objects.equals(colors.getItemAt(i).id(), d.terminalTheme)) {
                    colors.setSelectedIndex(i);
                }
            }
            notes.setText(d.notes);
            errors.values().forEach(l -> l.setVisible(false));
            inputs.values().forEach(c -> c.putClientProperty("JComponent.outline", null));
            layout(d);
            filling = false;
            setAuth(d.auth);
        }

        private boolean usesJump(UUID from, UUID target) {
            var seen = new java.util.HashSet<UUID>();
            var cur = snapshot.find(from).orElse(null);
            while (cur != null && cur.jumpHostId() != null && seen.add(cur.id())) {
                if (cur.jumpHostId().equals(target)) {
                    return true;
                }
                cur = snapshot.find(cur.jumpHostId()).orElse(null);
            }
            return false;
        }

        private void selectFolder(String group) {
            for (int i = 0; i < folder.getItemCount(); i++) {
                if (folder.getItemAt(i).group().equals(group)) {
                    folder.setSelectedIndex(i);
                    return;
                }
            }
            folder.addItem(new FolderChoice(group, group));
            folder.setSelectedIndex(folder.getItemCount() - 1);
        }

        private void layout(ProfileDraft d) {
            editContent.removeAll();
            var heading = SnippetUi.label(I18n.t(d.isNew() ? "pm.form.new" : "pm.form.edit"),
                    SnippetUi.font(2, Font.BOLD), SnippetUi.fg());
            add(editContent, heading, 14);
            add(editContent, fld(I18n.t("pm.form.name"), name, ProfileDraft.Field.NAME, null), 14);
            name.putClientProperty("JTextField.placeholderText", I18n.t("pm.form.name.ph"));
            add(editContent, pair(fld(I18n.t("pm.form.folder"), folder, null, null),
                    fld(I18n.t("profile.field.protocol"), protocol, null, null), 1, 1), 14);

            var conn = section(I18n.t("pm.section.connection"));
            hostField.putClientProperty("JTextField.placeholderText", I18n.t("pm.form.host.ph"));
            var hostPort = pair(fld(I18n.t("pm.form.host"), hostField, null, null),
                    fld(I18n.t("pm.form.port"), port, null, null), 0, 84);
            add(conn, hostPort, 4);
            add(conn, errors.get(ProfileDraft.Field.HOST), 0);
            add(conn, errors.get(ProfileDraft.Field.PORT), 8);
            user.putClientProperty("JTextField.placeholderText", "admin");
            add(conn, fld(I18n.t("pm.form.user"), user, ProfileDraft.Field.USER, I18n.t("pm.form.user.hint")), 12);
            jumpRow = fld(I18n.t("pm.form.jump"), jump, null, null);
            add(conn, jumpRow, 0);
            add(editContent, conn, 14);

            var sign = section(I18n.t("pm.section.signIn"));
            var seg = new JPanel(new GridLayout(1, 3));
            seg.setOpaque(false);
            seg.setBorder(BorderFactory.createLineBorder(SnippetUi.border()));
            authButtons.clear();
            for (var a : new AuthMethod[] {AuthMethod.PASSWORD, AuthMethod.KEY, AuthMethod.AGENT}) {
                var b = new SegButton(I18n.t(switch (a) {
                    case PASSWORD -> "pm.auth.password";
                    case KEY -> "pm.auth.key";
                    case AGENT -> "pm.auth.agent";
                }));
                b.addActionListener(e -> setAuth(a));
                authButtons.put(a, b);
                seg.add(b);
            }
            seg.getAccessibleContext().setAccessibleName(I18n.t("pm.section.signIn"));
            seg.setPreferredSize(new Dimension(0, 30));
            add(sign, seg, 12);
            var pwBox = column();
            pwBox.setOpaque(false);
            add(pwBox, savePw.row(), 6);
            add(pwBox, fld(I18n.t("pm.form.password"), password, null, null), 0);
            passwordRow = pwBox;
            add(sign, passwordRow, 12);
            var keyBox = column();
            keyBox.setOpaque(false);
            var browse = new SnippetUi.Button(I18n.t("pm.form.browse"), null, SnippetUi.Variant.DEFAULT);
            browse.addActionListener(e -> browseKey());
            var keyLine = new JPanel(new BorderLayout(8, 0));
            keyLine.setOpaque(false);
            keyLine.add(keyPath, BorderLayout.CENTER);
            keyLine.add(browse, BorderLayout.EAST);
            keyPath.putClientProperty("JTextField.placeholderText", "%USERPROFILE%\\.ssh\\id_ed25519");
            inputs.put(ProfileDraft.Field.KEY, keyPath);
            add(keyBox, fld(I18n.t("pm.form.key"), keyLine, ProfileDraft.Field.KEY, null), 12);
            add(keyBox, fld(I18n.t("pm.form.passphrase"), passphrase, null, null), 0);
            keyRows = keyBox;
            add(sign, keyRows, 12);
            agentHint = SnippetUi.text(I18n.t("pm.form.agent.hint"), SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted());
            add(sign, agentHint, 12);
            add(sign, fld(I18n.t("pm.form.sudo"), sudo, null, I18n.t("pm.form.sudo.hint")), 12);
            sudoPwRow = fld(I18n.t("pm.form.sudoPw"), sudoPassword, ProfileDraft.Field.SUDO, null);
            add(sign, sudoPwRow, 0);
            signInSection = sign;
            add(editContent, sign, 14);

            var session = section(I18n.t("pm.section.session"));
            startDir.putClientProperty("JTextField.placeholderText", I18n.t("pm.startDir.home"));
            add(session, fld(I18n.t("pm.form.startDir"), startDir, null, null), 10);
            add(session, favorite.row(), 0);
            add(editContent, session, 14);

            var other = section(I18n.t("pm.section.other"));
            add(other, pair(fld(I18n.t("profile.field.environment"), environment, null, null),
                    fld(I18n.t("profile.field.terminalColors"), colors, null, null), 1, 1), 8);
            sensitive.setToolTipText(I18n.t("profile.field.sensitive.tooltip"));
            add(other, sensitive, 10);
            var notesScroll = new JScrollPane(notes);
            notesScroll.setPreferredSize(new Dimension(0, 64));
            add(other, fld(I18n.t("profile.field.notes"), notesScroll, null, null), 10);
            var rootBox = column();
            rootBox.setOpaque(false);
            add(rootBox, fld(I18n.t("pm.form.root"), rootPassword, null, I18n.t("pm.form.root.hint")), 4);
            add(rootBox, clearRoot, 0);
            add(other, rootBox, 0);
            add(editContent, other, 0);
            editContent.add(Box.createVerticalGlue());
            editContent.revalidate();
            editContent.repaint();
        }

        /** Section dengan garis atas dan judul (prototipe {@code .sect}). */
        private JPanel section(String title) {
            var s = column();
            s.setOpaque(false);
            s.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                    BorderFactory.createEmptyBorder(14, 0, 0, 0)));
            add(s, SnippetUi.label(title, SnippetUi.font(0, Font.BOLD), SnippetUi.fg()), 12);
            return s;
        }

        /** Label (uppercase kecil), input, pesan error, keterangan (prototipe {@code .fld}). */
        private JPanel fld(String label, JComponent input, ProfileDraft.Field field, String hint) {
            var p = column();
            p.setOpaque(false);
            add(p, sectionLabel(label), 5);
            add(p, input, 0);
            if (field != null) {
                var err = errors.get(field);
                err.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
                add(p, err, 0);
            }
            if (hint != null) {
                var h = SnippetUi.text(hint, SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted());
                h.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
                add(p, h, 0);
            }
            return p;
        }

        /** Dua field berdampingan; {@code rightWidth} &gt; 1 = lebar tetap (px) untuk yang kanan. */
        private JPanel pair(JComponent left, JComponent right, int leftWeight, int rightWidth) {
            var p = new JPanel(new GridBagLayout());
            p.setOpaque(false);
            var a = new GridBagConstraints();
            a.gridx = 0;
            a.weightx = 1;
            a.fill = GridBagConstraints.HORIZONTAL;
            a.anchor = GridBagConstraints.FIRST_LINE_START;
            a.insets = new Insets(0, 0, 0, 10);
            p.add(left, a);
            var b = new GridBagConstraints();
            b.gridx = 1;
            b.weightx = rightWidth > 1 ? 0 : 1;
            b.fill = GridBagConstraints.HORIZONTAL;
            b.anchor = GridBagConstraints.FIRST_LINE_START;
            if (rightWidth > 1) {
                right.setPreferredSize(new Dimension(rightWidth, right.getPreferredSize().height));
            }
            p.add(right, b);
            return p;
        }

        void setAuth(AuthMethod a) {
            draft.setAuth(a);
            authButtons.forEach((m, b) -> b.setOn(m == a));
            filling = true;
            var current = sudo.getSelectedItem() instanceof SudoChoice c ? c.value() : draft.sudo;
            if (draft.sudo != current && current != ProfileDraft.Sudo.SAME) {
                draft.sudo = current;
            }
            sudo.removeAllItems();
            sudo.addItem(new SudoChoice(ProfileDraft.Sudo.OFF, I18n.t("pm.sudo.off")));
            if (a == AuthMethod.PASSWORD) {
                sudo.addItem(new SudoChoice(ProfileDraft.Sudo.SAME, I18n.t("pm.sudo.same")));
            }
            sudo.addItem(new SudoChoice(ProfileDraft.Sudo.SEPARATE, I18n.t("pm.sudo.separate")));
            for (int i = 0; i < sudo.getItemCount(); i++) {
                if (sudo.getItemAt(i).value() == draft.sudo) {
                    sudo.setSelectedIndex(i);
                }
            }
            filling = false;
            updateVisibility();
        }

        private void updateVisibility() {
            boolean ftp = protocol.getSelectedItem() instanceof Protocol p && p.isFtp();
            var auth = ftp ? AuthMethod.PASSWORD : draft.auth;
            authButtons.forEach((m, b) -> b.setEnabled(!ftp || m == AuthMethod.PASSWORD));
            if (passwordRow != null) {
                passwordRow.setVisible(auth == AuthMethod.PASSWORD);
                password.setEnabled(savePw.box().isSelected());
                keyRows.setVisible(auth == AuthMethod.KEY);
                agentHint.setVisible(auth == AuthMethod.AGENT);
                var s = sudo.getSelectedItem() instanceof SudoChoice c ? c.value() : ProfileDraft.Sudo.OFF;
                sudo.setEnabled(!ftp);
                sudoPwRow.setVisible(!ftp && s == ProfileDraft.Sudo.SEPARATE);
                jumpRow.setVisible(!ftp);
                signInSection.revalidate();
                editContent.revalidate();
                editContent.repaint();
            }
        }

        private void browseKey() {
            var chooser = new JFileChooser(keyPath.getText().isBlank()
                    ? java.nio.file.Path.of(System.getProperty("user.home"), ".ssh").toFile()
                    : new java.io.File(keyPath.getText()));
            chooser.setDialogTitle(I18n.t("profile.key.chooserTitle"));
            chooser.setFileHidingEnabled(false);
            if (chooser.showOpenDialog(ProfileManagerDialog.this) == JFileChooser.APPROVE_OPTION) {
                keyPath.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        }

        void focusFirst() {
            name.requestFocusInWindow();
        }

        /** Salin isi form (tanpa secret) ke draft. */
        void readInto(ProfileDraft d) {
            d.name = name.getText();
            d.group = folder.getSelectedItem() instanceof FolderChoice c ? c.group() : d.group;
            d.protocol = (Protocol) protocol.getSelectedItem();
            d.host = hostField.getText();
            d.port = port.getText();
            d.user = user.getText();
            d.jump = jump.getSelectedItem() instanceof JumpChoice j ? j.id() : null;
            d.savePassword = savePw.box().isSelected();
            d.keyPath = keyPath.getText();
            d.sudo = sudo.getSelectedItem() instanceof SudoChoice c ? c.value() : ProfileDraft.Sudo.OFF;
            d.startDir = startDir.getText();
            d.favorite = favorite.box().isSelected();
            d.environment = (EnvironmentTag) environment.getSelectedItem();
            d.sensitive = ProfileDraft.sensitiveValue(sensitiveChosen, sensitive.isSelected(), d.environment);
            d.terminalTheme = colors.getSelectedItem() instanceof HostTerminalColors.Choice c ? c.id() : null;
            d.notes = notes.getText();
        }

        void showErrors(Map<ProfileDraft.Field, String> errs) {
            for (var f : ProfileDraft.Field.values()) {
                var l = errors.get(f);
                String key = errs.get(f);
                l.setText(key == null ? "" : I18n.t(key));
                l.setVisible(key != null);
                inputs.get(f).putClientProperty("JComponent.outline", key == null ? null : "error");
            }
            editContent.revalidate();
            editContent.repaint();
        }

        boolean sudoTyped() {
            return sudoPassword.getDocument().getLength() > 0;
        }

        boolean secretsTyped() {
            return password.getDocument().getLength() > 0 || passphrase.getDocument().getLength() > 0
                    || sudoTyped() || rootPassword.getDocument().getLength() > 0 || clearRoot.isSelected();
        }

        /** Password login dari form untuk Test (salinan; null kalau kosong). */
        char[] password() {
            return password.getDocument().getLength() > 0 ? password.getPassword() : null;
        }

        char[] passphrase() {
            return passphrase.getDocument().getLength() > 0 ? passphrase.getPassword() : null;
        }

        /**
         * Perubahan secret dari form (prototipe {@code fromDraft}): password hanya disimpan kalau "Save password in
         * Vault" aktif; ganti cara login menghapus secret cara lama; sudo "pakai password login" menyalin password
         * login yang diketik.
         */
        Map<SecretType, SecretChange> secretChanges(ProfileDraft d) {
            var out = new EnumMap<SecretType, SecretChange>(SecretType.class);
            boolean ftp = d.protocol.isFtp();
            var auth = ftp ? AuthMethod.PASSWORD : d.auth;
            if (auth == AuthMethod.PASSWORD && d.savePassword) {
                if (password.getDocument().getLength() > 0) {
                    out.put(SecretType.LOGIN_PASSWORD, new SecretChange.Set(password.getPassword()));
                }
            } else if (d.stored.contains(SecretType.LOGIN_PASSWORD)) {
                out.put(SecretType.LOGIN_PASSWORD, new SecretChange.Clear());
            }
            if (auth == AuthMethod.KEY && passphrase.getDocument().getLength() > 0) {
                out.put(SecretType.KEY_PASSPHRASE, new SecretChange.Set(passphrase.getPassword()));
            } else if (auth != AuthMethod.KEY && d.stored.contains(SecretType.KEY_PASSPHRASE)) {
                out.put(SecretType.KEY_PASSPHRASE, new SecretChange.Clear());
            }
            var s = ftp ? ProfileDraft.Sudo.OFF : d.sudo;
            switch (s) {
                case SEPARATE -> {
                    if (sudoTyped()) {
                        out.put(SecretType.SUDO_PASSWORD, new SecretChange.Set(sudoPassword.getPassword()));
                    }
                }
                case SAME -> {
                    if (password.getDocument().getLength() > 0) {
                        out.put(SecretType.SUDO_PASSWORD, new SecretChange.Set(password.getPassword()));
                    }
                }
                case OFF -> {
                    if (d.stored.contains(SecretType.SUDO_PASSWORD)) {
                        out.put(SecretType.SUDO_PASSWORD, new SecretChange.Clear());
                    }
                }
            }
            if (clearRoot.isSelected()) {
                out.put(SecretType.ROOT_PASSWORD, new SecretChange.Clear());
            } else if (rootPassword.getDocument().getLength() > 0) {
                out.put(SecretType.ROOT_PASSWORD, new SecretChange.Set(rootPassword.getPassword()));
            }
            return out;
        }

        void clearSecrets() {
            for (var f : new JPasswordField[] {password, passphrase, sudoPassword, rootPassword}) {
                f.setText("");
            }
        }
    }

    record JumpChoice(UUID id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    record SudoChoice(ProfileDraft.Sudo value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** Satu tombol segmented control (prototipe {@code .seg}). */
    static final class SegButton extends javax.swing.JButton {
        private boolean on;

        SegButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(true);
            setOpaque(false);
            setRolloverEnabled(true);
            setFont(SnippetUi.font(0, Font.PLAIN));
        }

        void setOn(boolean value) {
            on = value;
            setFont(SnippetUi.font(0, value ? Font.BOLD : Font.PLAIN));
            getAccessibleContext().setAccessibleDescription(value ? "selected" : null);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setColor(on ? SnippetUi.selection() : getModel().isRollover() && isEnabled() ? SnippetUi.hover()
                    : SnippetUi.panel());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(SnippetUi.border());
            g2.fillRect(getWidth() - 1, 0, 1, getHeight());
            g2.dispose();
            setForeground(!isEnabled() ? SnippetUi.alpha(SnippetUi.muted(), 0.5f)
                    : on ? SnippetUi.onSelection() : SnippetUi.fg());
            super.paintComponent(g);
        }
    }
}

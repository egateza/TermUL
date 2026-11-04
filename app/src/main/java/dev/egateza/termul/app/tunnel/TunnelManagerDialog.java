package dev.egateza.termul.app.tunnel;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.snippet.SnippetUi;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.ShortcutAction;
import dev.egateza.termul.app.ui.Shortcuts;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileStore;
import dev.egateza.termul.core.tunnel.Tunnel;
import dev.egateza.termul.core.tunnel.TunnelStore;
import dev.egateza.termul.core.tunnel.TunnelType;
import dev.egateza.termul.ssh.tunnel.TunnelManager;
import dev.egateza.termul.ssh.tunnel.TunnelState;
import dev.egateza.termul.ssh.tunnel.TunnelStatus;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/**
 * Port forwarding (ikon panah di panel bawah, Terminal → Port forwarding…, {@code Ctrl/Cmd+Shift+U}), mengikuti
 * prototipe "TermUL Desktop": header dengan jumlah tunnel aktif, filter status/jenis di kiri, daftar dengan saklar di
 * tengah, detail (alur, alamat, statistik, perintah setara) di kanan, dan status bar. Simpan/hapus di executor I/O;
 * start/stop lewat {@link TunnelManager} (tidak blocking). UI hanya di EDT.
 */
public final class TunnelManagerDialog extends JDialog {

    /** Item navigasi kiri. */
    enum Filter {
        ALL, ACTIVE, STOPPED, ERROR, LOCAL, REMOTE, DYNAMIC;

        boolean includes(Tunnel t, TunnelStatus s) {
            return switch (this) {
                case ALL -> true;
                case ACTIVE -> s.isRunning();
                case STOPPED -> s instanceof TunnelStatus.Stopped;
                case ERROR -> s instanceof TunnelStatus.Failed;
                case LOCAL -> t.type() == TunnelType.LOCAL;
                case REMOTE -> t.type() == TunnelType.REMOTE;
                case DYNAMIC -> t.type() == TunnelType.DYNAMIC;
            };
        }

        String label() {
            return switch (this) {
                case ALL -> I18n.t("tunnel.nav.all");
                case ACTIVE, STOPPED -> I18n.t("tunnel.status." + name().toLowerCase(Locale.ROOT));
                case ERROR -> I18n.t("tunnel.status.error");
                case LOCAL -> TunnelText.type(TunnelType.LOCAL);
                case REMOTE -> TunnelText.type(TunnelType.REMOTE);
                case DYNAMIC -> TunnelText.type(TunnelType.DYNAMIC);
            };
        }
    }

    private static final String LIST = "list";
    private static final String EMPTY = "empty";
    private static final String DETAIL = "detail";
    private static final String NONE = "none";

    private final TunnelStore store;
    private final TunnelManager manager;
    private final ProfileStore profiles;
    private final Executor io;
    private final UUID preferredHost;

    private final JLabel activeChipText = SnippetUi.label("", SnippetUi.font(-1, Font.BOLD), SnippetUi.fg());
    private final JLabel activeChipDot = new JLabel();
    private final JPanel nav = new JPanel();
    private final JTextField search = new JTextField();
    private final JPanel rows = new SnippetUi.TrackingPanel(null);
    private final CardLayout listCards = new CardLayout();
    private final JPanel listArea = new JPanel(listCards);
    private final JLabel emptyText = SnippetUi.label("", SnippetUi.font(0, Font.PLAIN), SnippetUi.muted());
    private final CardLayout detailCards = new CardLayout();
    private final JPanel detail = new JPanel(detailCards);
    private final JPanel detailCard = new JPanel(new BorderLayout());
    private final JLabel footer = new JLabel();
    private final JLabel connections = SnippetUi.label("", SnippetUi.font(1, Font.PLAIN), SnippetUi.fg());
    private final JLabel traffic = SnippetUi.label("", SnippetUi.font(1, Font.PLAIN), SnippetUi.fg());
    private final Timer stats = new Timer(1000, e -> tick());
    /** Tampilan Starting minimal sekian lama setelah tunnel dinyalakan dari dialog ini. */
    static final int MIN_STARTING_MS = 800;
    private final java.util.Map<UUID, Long> startShown = new java.util.HashMap<>(); // EDT; id → System.nanoTime()
    private final java.util.Map<UUID, Long> stopShown = new java.util.HashMap<>();  // EDT; baru dihentikan
    private SnippetUi.Button detailRun;   // EDT; tombol Start/Stop yang sedang beranimasi (Starting/Stopping), atau null
    private boolean detailRunStopping;    // EDT
    private final List<JLabel> startingLabels = new java.util.ArrayList<>();         // EDT; label Starting di daftar
    private JLabel detailStarting;                                                   // EDT; label Starting di detail
    private int frame;                                                               // EDT; langkah animasi
    private final Timer anim = new Timer(300, e -> animate());
    private final Runnable managerChanged = () -> SwingUtilities.invokeLater(this::reload);
    private final Consumer<List<Tunnel>> storeChanged = l -> SwingUtilities.invokeLater(this::reload);
    private final Runnable undim;

    private Filter filter = Filter.ALL; // EDT
    private UUID selected;              // EDT
    private Object detailKey;           // EDT; isi detail terakhir (dibangun ulang hanya kalau berubah)
    private Timer toastTimer;           // EDT

    /** @param preferredHost host awal untuk tunnel baru (host tab aktif), boleh null */
    public TunnelManagerDialog(Window owner, TunnelStore store, TunnelManager manager, ProfileStore profiles,
                               Executor io, UUID preferredHost) {
        super(owner, I18n.t("tunnel.title"), ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.manager = manager;
        this.profiles = profiles;
        this.io = io;
        this.preferredHost = preferredHost;
        setUndecorated(true);
        boolean round = SnippetUi.roundWindow(this);

        var root = new SnippetUi.RoundPanel(new BorderLayout(), SnippetUi::panel, SnippetUi::frame, round ? 16 : 0);
        root.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        root.add(header(round), BorderLayout.NORTH);
        var body = new JPanel(new BorderLayout());
        body.setBackground(SnippetUi.panel());
        body.add(navPane(), BorderLayout.WEST);
        body.add(listPane(), BorderLayout.CENTER);
        detail.setPreferredSize(new Dimension(372, 0));
        detail.setBackground(SnippetUi.panel());
        detailCard.setBackground(SnippetUi.panel());
        detail.add(detailCard, DETAIL);
        detail.add(centered(I18n.t("tunnel.detail.none")), NONE);
        body.add(detail, BorderLayout.EAST);
        root.add(body, BorderLayout.CENTER);
        root.add(statusBar(round), BorderLayout.SOUTH);
        setContentPane(root);
        bindKeys();
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        var all = store.snapshot();
        selected = all.isEmpty() ? null : all.getFirst().id();
        showHint();
        reload();
        manager.addListener(managerChanged);
        store.addListener(storeChanged);
        stats.start();
        anim.start();

        int w = owner == null ? 1120 : Math.min(1120, owner.getWidth() - 32);
        int h = owner == null ? 680 : Math.min(680, owner.getHeight() - 32);
        setSize(Math.max(760, w), Math.max(460, h));
        SnippetUi.keepSizeAcrossScreens(this);
        setLocationRelativeTo(owner);
        undim = SnippetUi.dim(owner);
    }

    @Override
    public void dispose() {
        stats.stop();
        anim.stop();
        if (toastTimer != null) {
            toastTimer.stop();
        }
        manager.removeListener(managerChanged);
        store.removeListener(storeChanged);
        undim.run();
        super.dispose();
    }

    // --- header & status bar ---

    private JComponent header(boolean round) {
        var bar = new TunnelUi.Bar(true, round, 40);
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBorder(BorderFactory.createCompoundBorder(bar.getBorder(), BorderFactory.createEmptyBorder(0, 14, 0, 6)));
        var title = SnippetUi.label(I18n.t("tunnel.title"), SnippetUi.font(1, Font.BOLD), SnippetUi.fg());
        title.setIcon(TunnelUi.arrows(15, SnippetUi::accent));
        title.setIconTextGap(10);
        bar.add(title);
        bar.add(Box.createHorizontalStrut(10));
        var chip = new SnippetUi.Chip(22, 10, activeChipDot, activeChipText);
        chip.setMaximumSize(new Dimension(200, 22));
        bar.add(chip);
        bar.add(Box.createHorizontalGlue());
        bar.add(SnippetUi.mutedLabel(Shortcuts.text(ShortcutAction.TUNNELS)));
        bar.add(Box.createHorizontalStrut(6));
        bar.add(TunnelUi.closeButton(this::dispose));
        TunnelUi.draggable(bar, this);
        return bar;
    }

    private JComponent statusBar(boolean round) {
        var bar = new TunnelUi.Bar(false, round, 28);
        bar.setLayout(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(bar.getBorder(), BorderFactory.createEmptyBorder(0, 14, 0, 14)));
        footer.setFont(SnippetUi.font(-1, Font.PLAIN));
        bar.add(footer, BorderLayout.CENTER);
        return bar;
    }

    private void showHint() {
        footer.setForeground(SnippetUi.muted());
        footer.setText(I18n.t("tunnel.footer.hint"));
    }

    private void toast(String text) {
        footer.setForeground(SnippetUi.success());
        footer.setText(text);
        if (toastTimer != null) {
            toastTimer.stop();
        }
        toastTimer = new Timer(2600, e -> showHint());
        toastTimer.setRepeats(false);
        toastTimer.start();
    }

    // --- navigasi ---

    private JComponent navPane() {
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBackground(SnippetUi.bar());
        nav.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
        nav.getAccessibleContext().setAccessibleName(I18n.t("tunnel.nav.name"));
        var scroll = new JScrollPane(nav);
        scroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, SnippetUi.border()));
        scroll.getViewport().setBackground(SnippetUi.bar());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(176, 0));
        return scroll;
    }

    private void rebuildNav(List<Tunnel> all) {
        nav.removeAll();
        addNav(Filter.ALL, all);
        addSection(I18n.t("tunnel.nav.status"));
        addNav(Filter.ACTIVE, all);
        addNav(Filter.STOPPED, all);
        addNav(Filter.ERROR, all);
        addSection(I18n.t("tunnel.nav.type"));
        addNav(Filter.LOCAL, all);
        addNav(Filter.REMOTE, all);
        addNav(Filter.DYNAMIC, all);
        nav.revalidate();
        nav.repaint();
    }

    private void addSection(String text) {
        nav.add(Box.createVerticalStrut(12));
        var l = SnippetUi.label(text.toUpperCase(Locale.ROOT), SnippetUi.font(-1, Font.BOLD), SnippetUi.muted());
        l.setBorder(BorderFactory.createEmptyBorder(2, 10, 4, 10));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(l);
    }

    private void addNav(Filter f, List<Tunnel> all) {
        int n = (int) all.stream().filter(t -> f.includes(t, status(t))).count();
        nav.add(new TunnelUi.NavItem(f.label(), n, f == filter, () -> {
            filter = f;
            reload();
        }));
        nav.add(Box.createVerticalStrut(2));
    }

    // --- daftar ---

    private JComponent listPane() {
        SnippetUi.styleField(search, 30);
        search.putClientProperty("JTextField.placeholderText", I18n.t("tunnel.search"));
        search.putClientProperty("JTextField.leadingIcon", SnippetUi.search(14));
        search.putClientProperty("JTextField.showClearButton", true);
        search.getAccessibleContext().setAccessibleName(I18n.t("tunnel.search.name"));
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                reload();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                reload();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                reload();
            }
        });
        var newButton = new SnippetUi.Button(I18n.t("tunnel.new"),
                SnippetUi.stroke(SnippetUi.PLUS, 12, () -> SnippetUi.onAccent(SnippetUi.accent()), 3),
                SnippetUi.Variant.PRIMARY);
        newButton.setToolTipText(I18n.t("tunnel.new.tip", Shortcuts.text(Shortcuts.menu(KeyEvent.VK_N))));
        newButton.addActionListener(e -> createNew());
        var toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setBackground(SnippetUi.panel());
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        toolbar.add(search, BorderLayout.CENTER);
        toolbar.add(newButton, BorderLayout.EAST);

        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBackground(SnippetUi.panel());
        var scroll = new JScrollPane(rows);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SnippetUi.panel());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        var empty = new JPanel(new GridBagLayout());
        empty.setBackground(SnippetUi.panel());
        empty.add(emptyText);
        listArea.add(scroll, LIST);
        listArea.add(empty, EMPTY);

        var pane = new JPanel(new BorderLayout());
        pane.setBackground(SnippetUi.panel());
        pane.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, SnippetUi.border()));
        pane.add(toolbar, BorderLayout.NORTH);
        pane.add(listArea, BorderLayout.CENTER);
        return pane;
    }

    /** Tunnel yang tampil untuk filter dan teks cari (nama, host, mapping, port). */
    static List<Tunnel> shown(List<Tunnel> all, Filter filter, String query, Function<UUID, TunnelStatus> status,
                              Function<UUID, String> hostName) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(t -> filter.includes(t, status.apply(t.id())))
                .filter(t -> q.isEmpty() || (t.name() + " " + hostName.apply(t.hostId()) + " " + TunnelText.mapping(t))
                        .toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    /** Satu baris daftar (prototipe {@code .trow}): saklar 52 px, nama + jenis · host, mapping, status. 50 px. */
    private final class Row extends JPanel {
        private final boolean on;
        private boolean hover;

        Row(Tunnel t, TunnelStatus status) {
            super(new BorderLayout());
            this.on = t.id().equals(selected);
            setOpaque(false);
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.alpha(SnippetUi.border(), 0.6f)));
            setPreferredSize(new Dimension(200, 51));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 51));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            boolean running = status.isRunning();
            var sw = TunnelUi.switchButton(() -> running, SnippetUi::success,
                    I18n.t(running ? "tunnel.row.stop" : "tunnel.row.start", t.name()), () -> toggle(t));
            sw.setEnabled(!(status instanceof TunnelStatus.Starting) && !stopping(t.id())); // anti spam start/stop
            var swCell = new JPanel(new GridBagLayout());
            swCell.setOpaque(false);
            swCell.setPreferredSize(new Dimension(52, 50));
            swCell.add(sw);
            add(swCell, BorderLayout.WEST);

            var main = new JPanel(new BorderLayout(12, 0));
            main.setOpaque(false);
            main.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
            var texts = new JPanel(new GridLayout(2, 1, 0, 2));
            texts.setOpaque(false);
            texts.setBorder(BorderFactory.createEmptyBorder(7, 0, 7, 0));
            texts.add(SnippetUi.label(t.name(), SnippetUi.font(1, Font.BOLD), on ? SnippetUi.onSelection()
                    : SnippetUi.fg()));
            texts.add(SnippetUi.label(TunnelText.type(t.type()) + " · " + hostName(t.hostId()),
                    SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted()));
            main.add(texts, BorderLayout.CENTER);
            var east = new JPanel(new BorderLayout(12, 0));
            east.setOpaque(false);
            var mapping = SnippetUi.label(TunnelText.mapping(t), SnippetUi.mono(-2), SnippetUi.fg());
            mapping.setPreferredSize(new Dimension(172, 20));
            mapping.setToolTipText(TunnelText.mapping(t));
            east.add(mapping, BorderLayout.CENTER);
            var st = statusLabel(status, -1, false);
            st.setPreferredSize(new Dimension(76, 20));
            east.add(st, BorderLayout.EAST);
            main.add(east, BorderLayout.EAST);
            add(main, BorderLayout.CENTER);

            setFocusable(true);
            getAccessibleContext().setAccessibleName(t.name() + ", " + TunnelText.status(status));
            var mouse = new MouseAdapter() {
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
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        select(t.id());
                        if (e.getClickCount() == 2) {
                            edit(t);
                        }
                    }
                }
            };
            addMouseListener(mouse);
            main.addMouseListener(mouse);
            TunnelUi.onKeys(this, () -> select(t.id()), KeyEvent.VK_ENTER);
            getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle");
            getActionMap().put("toggle", new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    toggle(t);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (on || hover || isFocusOwner()) {
                var g2 = (Graphics2D) g.create();
                g2.setColor(on ? SnippetUi.selection() : SnippetUi.hover());
                g2.fillRect(0, 0, getWidth(), getHeight());
                if (isFocusOwner()) {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(SnippetUi.accent());
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                }
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    // --- muat ulang ---

    private TunnelStatus status(Tunnel t) {
        return state(t.id()).status();
    }

    /**
     * Keadaan yang ditampilkan: seperti {@link TunnelManager#state}, tapi tunnel yang baru dinyalakan dari dialog ini
     * tetap tampil Starting minimal {@value #MIN_STARTING_MS} ms, supaya perubahannya terlihat walau start-nya instan.
     */
    private TunnelState state(UUID id) {
        var real = manager.state(id);
        Long since = startShown.get(id);
        boolean finished = real.status() instanceof TunnelStatus.Active || real.status() instanceof TunnelStatus.Failed;
        if (since != null && finished && System.nanoTime() - since < MIN_STARTING_MS * 1_000_000L) {
            return new TunnelState(TunnelStatus.STARTING, real.connections(), real.bytes(), null, real.clients());
        }
        return real;
    }

    /** Nyalakan tunnel dari dialog ini (saklar, Start, "Use port N", simpan saat jalan). */
    private void start(Tunnel t) {
        stopShown.remove(t.id());
        startShown.put(t.id(), System.nanoTime());
        manager.start(t);
    }

    /** Hentikan tunnel; tombol "Stopping..." dan saklar terkunci sebentar supaya tidak di-spam start/stop. */
    private void stop(Tunnel t) {
        startShown.remove(t.id());
        stopShown.put(t.id(), System.nanoTime());
        manager.stop(t.id());
    }

    private boolean stopping(UUID id) {
        Long since = stopShown.get(id);
        return since != null && System.nanoTime() - since < MIN_STARTING_MS * 1_000_000L;
    }

    /** Sedang starting atau baru dihentikan: Start/Stop dan saklar tidak bisa ditekan. */
    private boolean busy(Tunnel t) {
        return status(t) instanceof TunnelStatus.Starting || stopping(t.id());
    }

    private String stoppingText() {
        return I18n.t("tunnel.detail.stopping") + ".".repeat(frame % 4);
    }

    /** Label status; Starting beranimasi (titik berdenyut, "Starting..."), diperbarui oleh {@link #animate()}. */
    private JLabel statusLabel(TunnelStatus status, float fontDelta, boolean detailLabel) {
        if (!(status instanceof TunnelStatus.Starting)) {
            return TunnelUi.statusLabel(status, fontDelta);
        }
        var l = SnippetUi.label(startingText(), SnippetUi.font(fontDelta, Font.PLAIN), SnippetUi.fg());
        l.setIcon(SnippetUi.dot(fontDelta > 0 ? 8 : 7, this::pulse));
        l.setIconTextGap(6);
        if (detailLabel) {
            detailStarting = l;
        } else {
            startingLabels.add(l);
        }
        return l;
    }

    private String startingText() {
        return TunnelText.status(TunnelStatus.STARTING) + ".".repeat(frame % 4);
    }

    /** Warna titik Starting: kuning yang memudar dan menguat bergantian. */
    private java.awt.Color pulse() {
        float t = (float) (0.5 + 0.5 * Math.cos(frame * Math.PI / 3));
        return SnippetUi.mix(SnippetUi.variable(), SnippetUi.panel(), 0.65f * t);
    }

    /** Langkah animasi Starting, juga mengakhiri masa tampil minimal Starting. */
    private void animate() {
        frame++;
        long now = System.nanoTime();
        boolean expired = startShown.values().removeIf(since -> now - since >= MIN_STARTING_MS * 1_000_000L);
        expired |= stopShown.values().removeIf(since -> now - since >= MIN_STARTING_MS * 1_000_000L);
        if (expired) {
            reload();
        }
        if (detailRun != null) {
            detailRun.setText(detailRunStopping ? stoppingText() : startingText());
            detailRun.repaint();
        }
        for (var l : startingLabels) {
            l.setText(startingText());
            l.repaint();
        }
        if (detailStarting != null) {
            detailStarting.setText(startingText());
            detailStarting.repaint();
        }
    }

    private Optional<HostProfile> host(UUID id) {
        return profiles.snapshot().find(id);
    }

    private String hostName(UUID id) {
        return TunnelText.hostName(host(id));
    }

    private void reload() {
        var all = store.snapshot();
        if (selected != null && all.stream().noneMatch(t -> t.id().equals(selected))) {
            selected = all.isEmpty() ? null : all.getFirst().id();
        }
        int active = manager.runningCount();
        activeChipText.setText(I18n.t("tunnel.activeCount", active));
        activeChipDot.setIcon(SnippetUi.dot(7, active > 0 ? SnippetUi::success : TunnelUi::stopped));
        rebuildNav(all);

        var list = shown(all, filter, search.getText(), id -> state(id).status(), this::hostName);
        rows.removeAll();
        startingLabels.clear();
        for (var t : list) {
            rows.add(new Row(t, status(t)));
        }
        rows.revalidate();
        rows.repaint();
        emptyText.setText(I18n.t(all.isEmpty() ? "tunnel.empty.none" : "tunnel.empty.filter"));
        listCards.show(listArea, list.isEmpty() ? EMPTY : LIST);
        rebuildDetail(all);
    }

    private void select(UUID id) {
        selected = id;
        reload();
    }

    /** Statistik detail (koneksi, byte) setiap detik; status yang berubah membangun ulang semuanya. */
    private void tick() {
        var t = current();
        if (t.isEmpty()) {
            return;
        }
        var state = state(t.get().id());
        if (!Objects.equals(detailKey, key(t.get(), state))) {
            reload();
            return;
        }
        connections.setText(String.valueOf(state.connections()));
        traffic.setText(TunnelText.bytes(state.bytes()));
    }

    private Optional<Tunnel> current() {
        return selected == null ? Optional.empty() : store.find(selected);
    }

    private Object key(Tunnel t, TunnelState s) {
        return List.of(t, s.status(), Objects.toString(s.issue(), ""), stopping(t.id()));
    }

    // --- detail ---

    private void rebuildDetail(List<Tunnel> all) {
        var t = current();
        if (t.isEmpty()) {
            detailKey = null;
            detailStarting = null;
            detailCards.show(detail, NONE);
            return;
        }
        var state = state(t.get().id());
        connections.setText(String.valueOf(state.connections()));
        traffic.setText(TunnelText.bytes(state.bytes()));
        Object key = key(t.get(), state);
        if (key.equals(detailKey)) {
            return;
        }
        detailKey = key;
        detailStarting = null;
        detailCard.removeAll();
        detailCard.add(detailBody(t.get(), state), BorderLayout.CENTER);
        detailCard.add(detailActions(t.get(), state.status()), BorderLayout.SOUTH);
        detailCard.revalidate();
        detailCard.repaint();
        detailCards.show(detail, DETAIL);
    }

    private JComponent detailBody(Tunnel t, TunnelState state) {
        var host = host(t.hostId());
        String hostName = TunnelText.hostName(host);
        var body = new SnippetUi.TrackingPanel(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(SnippetUi.panel());
        body.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        var head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        var titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(SnippetUi.label(t.name(), SnippetUi.font(3, Font.BOLD), SnippetUi.fg()));
        String env = TunnelText.env(host);
        String sub = I18n.t("tunnel.detail.forward", TunnelText.type(t.type()));
        titles.add(SnippetUi.mutedLabel(env.isEmpty() ? sub : sub + " · " + env));
        head.add(titles, BorderLayout.CENTER);
        var status = statusLabel(state.status(), 1, true);
        status.setVerticalAlignment(JLabel.TOP);
        head.add(status, BorderLayout.EAST);
        addFixed(body, head, 14);

        if (state.status() instanceof TunnelStatus.Failed f) {
            add(body, errorBox(TunnelText.failure(f, hostName), TunnelText.fixPort(f), t), 14);
        } else if (state.issue() != null && state.status().isRunning()) {
            add(body, errorBox(I18n.t("tunnel.error.lastConnection", state.issue()), 0, t), 14);
        }

        add(body, flow(TunnelText.nodes(t, hostName)), 14);

        if (t.type().listensOnThisPc()) {
            var address = new JPanel(new BorderLayout(0, 4));
            address.setOpaque(false);
            address.add(section(I18n.t(t.type() == TunnelType.DYNAMIC ? "tunnel.detail.socksAddress"
                    : "tunnel.detail.address")), BorderLayout.NORTH);
            var row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            var value = SnippetUi.label(TunnelText.localAddress(t), SnippetUi.mono(-1), SnippetUi.fg());
            var box = new SnippetUi.RoundPanel(new BorderLayout(), SnippetUi::field, SnippetUi::border, 8);
            box.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            box.setPreferredSize(new Dimension(40, 30));
            box.add(value);
            row.add(box, BorderLayout.CENTER);
            var copy = new SnippetUi.Button(I18n.t("tunnel.detail.copy"), null, SnippetUi.Variant.DEFAULT);
            copy.addActionListener(e -> copy(TunnelText.localAddress(t)));
            row.add(copy, BorderLayout.EAST);
            address.add(row, BorderLayout.CENTER);
            addFixed(body, address, 14);
        }

        var numbers = new JPanel(new GridLayout(1, 2, 12, 0));
        numbers.setOpaque(false);
        numbers.add(stat(I18n.t("tunnel.detail.connections"), connections));
        numbers.add(stat(I18n.t("tunnel.detail.traffic"), traffic));
        addFixed(body, numbers, 14);

        addFixed(body, TunnelUi.toggleRow(I18n.t("tunnel.detail.autoStart", hostName), t::autoStart, SnippetUi::accent,
                () -> saveThen(t.withAutoStart(!t.autoStart()), saved -> { })), 14);

        var same = new JPanel(new BorderLayout(0, 4));
        same.setOpaque(false);
        same.add(section(I18n.t("tunnel.detail.sameAs")), BorderLayout.NORTH);
        var command = new SnippetUi.Paragraph(SnippetUi.mono(-2), SnippetUi.fg());
        command.add(TunnelText.command(t, host, id -> profiles.snapshot().find(id)));
        same.add(SnippetUi.codeBox(command), BorderLayout.CENTER);
        add(body, same, 0);

        var scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SnippetUi.panel());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /**
     * Baris setinggi isinya (tanpa teks yang dibungkus): BoxLayout membagi sisa tinggi ke anak yang boleh membesar,
     * jadi tingginya dikunci di ukuran prototipe (mis. alamat + Copy 30 px).
     */
    private static void addFixed(JPanel body, JComponent c, int gapAfter) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        add(body, c, gapAfter);
    }

    private static void add(JPanel body, JComponent c, int gapAfter) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(c);
        if (gapAfter > 0) {
            body.add(Box.createVerticalStrut(gapAfter));
        }
    }

    private static JLabel section(String text) {
        return SnippetUi.label(text.toUpperCase(Locale.ROOT), SnippetUi.font(-1, Font.BOLD), SnippetUi.muted());
    }

    private static JComponent stat(String label, JLabel value) {
        var p = new JPanel(new GridLayout(2, 1, 0, 2));
        p.setOpaque(false);
        p.add(SnippetUi.label(label, SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted()));
        p.add(value);
        return p;
    }

    /** Kotak merah (prototipe): pesan + tombol "Use port N instead" kalau port-nya dipakai. */
    private JComponent errorBox(String message, int fixPort, Tunnel t) {
        var content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(new SnippetUi.Paragraph(SnippetUi.font(0, Font.PLAIN), SnippetUi.fg()).add(message),
                BorderLayout.CENTER);
        if (fixPort > 0) {
            var fix = new SnippetUi.Button(I18n.t("tunnel.error.fix", String.valueOf(fixPort)), null,
                    SnippetUi.Variant.DEFAULT);
            fix.addActionListener(e -> saveThen(t.withSourcePort(fixPort), this::start));
            var row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.add(fix, BorderLayout.WEST);
            content.add(row, BorderLayout.SOUTH);
        }
        var box = SnippetUi.dangerBox(content, 1f);
        box.getAccessibleContext().setAccessibleDescription(message);
        return box;
    }

    /** Alur vertikal: kotak, garis (tebal aksen = tunnel SSH, putus-putus = jaringan) dengan keterangan, kotak. */
    private static JComponent flow(List<TunnelText.Node> nodes) {
        var p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        for (var n : nodes) {
            var box = TunnelFormDialog.nodeBox(n, 7);
            box.setPreferredSize(null);
            box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(box);
            if (!n.link().isEmpty()) {
                p.add(link(n.link(), n.sshLink()));
            }
        }
        return p;
    }

    private static JComponent link(String text, boolean ssh) {
        var line = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g.create();
                g2.setColor(ssh ? SnippetUi.accent() : TunnelUi.stopped());
                g2.setStroke(ssh ? new BasicStroke(2) : new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10, new float[] {3, 3}, 0));
                g2.drawLine(getWidth() / 2, 0, getWidth() / 2, getHeight());
                g2.dispose();
            }
        };
        // prototipe: garis 24 px, 20 px dari kiri, teks tepat di sebelahnya (gap 10); garis tidak boleh melebar
        var size = new Dimension(2, 24);
        line.setPreferredSize(size);
        line.setMinimumSize(size);
        line.setMaximumSize(size);
        var row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        row.add(line);
        row.add(Box.createHorizontalStrut(10));
        row.add(SnippetUi.label(text, SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted()));
        row.add(Box.createHorizontalGlue());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        return row;
    }

    private JComponent detailActions(Tunnel t, TunnelStatus status) {
        boolean running = status.isRunning();
        var bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBackground(SnippetUi.panel());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        boolean starting = status instanceof TunnelStatus.Starting;
        boolean stopping = !running && stopping(t.id());
        var run = new SnippetUi.Button(starting ? startingText() : stopping ? stoppingText()
                : I18n.t(running ? "tunnel.detail.stop" : "tunnel.detail.start"), null,
                running || stopping ? SnippetUi.Variant.DEFAULT : SnippetUi.Variant.PRIMARY);
        run.addActionListener(e -> toggle(t));
        // sedang starting / baru dihentikan: tombol beranimasi dan terkunci, supaya start/stop tidak di-spam
        detailRun = starting || stopping ? run : null;
        detailRunStopping = stopping;
        run.setEnabled(!starting && !stopping);
        if (starting || stopping) {
            // lebar tetap selebar teks terpanjang, supaya tombol lain tidak bergeser saat titiknya bertambah
            var fm = run.getFontMetrics(run.getFont());
            int w = fm.stringWidth((starting ? TunnelText.status(TunnelStatus.STARTING)
                    : I18n.t("tunnel.detail.stopping")) + "...") + 28;
            run.setPreferredSize(new Dimension(w, 30));
            run.setMinimumSize(run.getPreferredSize());
        }
        var edit = new SnippetUi.Button(I18n.t("tunnel.detail.edit"), null, SnippetUi.Variant.DEFAULT);
        edit.addActionListener(e -> edit(t));
        var copy = new SnippetUi.Button(I18n.t("tunnel.detail.duplicate"), null, SnippetUi.Variant.DEFAULT);
        copy.setToolTipText(I18n.t("tunnel.detail.duplicate.tip", Shortcuts.text(Shortcuts.menu(KeyEvent.VK_D))));
        copy.addActionListener(e -> duplicate(t));
        var delete = new SnippetUi.Button(I18n.t("tunnel.detail.delete"), null, SnippetUi.Variant.DANGER);
        delete.addActionListener(e -> delete(t));
        bar.add(run);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(edit);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(copy);
        bar.add(Box.createHorizontalGlue());
        bar.add(delete);
        return bar;
    }

    private static JComponent centered(String text) {
        var box = new JPanel(new GridBagLayout());
        box.setBackground(SnippetUi.panel());
        box.add(SnippetUi.label(text, SnippetUi.font(0, Font.PLAIN), SnippetUi.muted()));
        return box;
    }

    // --- aksi ---

    private void toggle(Tunnel t) {
        if (busy(t)) {
            return; // sedang starting / baru dihentikan
        }
        if (status(t).isRunning()) {
            stop(t);
        } else {
            start(t);
        }
    }

    private List<HostProfile> sshHosts() {
        return profiles.snapshot().profiles().stream().filter(p -> !p.isFtp()).toList();
    }

    private UUID defaultHost() {
        var hosts = sshHosts();
        if (preferredHost != null && hosts.stream().anyMatch(h -> h.id().equals(preferredHost))) {
            return preferredHost;
        }
        return hosts.isEmpty() ? null : hosts.getFirst().id();
    }

    /** Test connection di form: langkah yang sama dengan saat tunnel dinyalakan, tanpa membuka tunnel. */
    private TunnelFormDialog.Tester tester() {
        return manager::test;
    }

    /** Test connection bisa beberapa detik (connect, login): virtual thread sendiri, bukan executor I/O disk. */
    private static final Executor CHECKS = r -> Thread.ofVirtual().name("tunnel-check").start(r);

    private void createNew() {
        TunnelFormDialog.show(this, null, null, store.snapshot(), sshHosts(), defaultHost(),
                id -> profiles.snapshot().find(id), tester(), CHECKS).ifPresent(t -> saveThen(t, saved -> {
                    filter = Filter.ALL;
                    search.setText("");
                    select(saved.id());
                    toast(I18n.t("tunnel.toast.created", saved.name()));
                }));
    }

    private void edit(Tunnel t) {
        boolean wasRunning = status(t).isRunning();
        TunnelFormDialog.show(this, t, null, store.snapshot(), sshHosts(), t.hostId(),
                        id -> profiles.snapshot().find(id), tester(), CHECKS)
                .ifPresent(changed -> saveThen(changed, saved -> {
                    if (wasRunning) {
                        start(saved); // jalan lagi dengan pengaturan baru
                    } else {
                        manager.stop(saved.id()); // hapus status error lama
                    }
                    toast(I18n.t("tunnel.toast.saved", saved.name()));
                }));
    }

    /** Form tunnel baru berisi salinan {@code t} (nama "… (copy)", port sumber berikutnya yang kosong). */
    private void duplicate(Tunnel t) {
        var all = store.snapshot();
        TunnelFormDialog.show(this, null, TunnelText.duplicate(t, all), all, sshHosts(), t.hostId(),
                id -> profiles.snapshot().find(id), tester(), CHECKS).ifPresent(copy -> saveThen(copy, saved -> {
                    filter = Filter.ALL;
                    search.setText("");
                    select(saved.id());
                    toast(I18n.t("tunnel.toast.created", saved.name()));
                }));
    }

    private void delete(Tunnel t) {
        // dicek saat tombol ditekan: siapa yang sedang tersambung lewat tunnel ini
        var state = manager.state(t.id());
        if (!TunnelUi.confirmDelete(this, t.name(), TunnelText.deleteText(t, state))) {
            return;
        }
        manager.stop(t.id());
        UiAsync.run(io, () -> store.delete(t.id()), err -> Dialogs.error(this, I18n.t("tunnel.saveFailed"), err));
    }

    private void copy(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            toast(I18n.t("tunnel.toast.copied", text));
        } catch (IllegalStateException e) {
            toast(I18n.t("tunnel.toast.copyFailed"));
        }
    }

    /** Simpan di executor I/O, lalu {@code then} di EDT. */
    private void saveThen(Tunnel t, Consumer<Tunnel> then) {
        UiAsync.run(io, () -> {
            store.save(t);
            return t;
        }, saved -> {
            then.accept(saved);
            reload();
        }, err -> Dialogs.error(this, I18n.t("tunnel.saveFailed"), err));
    }

    private void bindKeys() {
        var root = getRootPane();
        root.registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> createNew(), Shortcuts.menu(KeyEvent.VK_N),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> search.requestFocusInWindow(), Shortcuts.menu(KeyEvent.VK_F),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> current().ifPresent(this::duplicate), Shortcuts.menu(KeyEvent.VK_D),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
}

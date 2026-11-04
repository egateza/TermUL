package dev.egateza.termul.app.tunnel;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.snippet.SnippetUi;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.tunnel.Tunnel;
import dev.egateza.termul.core.tunnel.TunnelChecks;
import dev.egateza.termul.core.tunnel.TunnelType;
import dev.egateza.termul.ssh.tunnel.TunnelManager;
import java.awt.BorderLayout;
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
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Form New tunnel / Edit tunnel (prototipe: kartu 760 px di atas dialog Port forwarding): jenis, nama, host, port
 * sumber dan tujuan, saklar, lalu Preview alur dan perintah {@code ssh} setara. Simpan aktif kalau isian valid. EDT.
 */
final class TunnelFormDialog extends JDialog {

    /** Hasil pemeriksaan port sumber: {@code bad} = tidak bisa disimpan; {@code warn} = pesan kuning, bukan merah. */
    record SourceCheck(boolean bad, String message, boolean warn) {
        static final SourceCheck OK = new SourceCheck(false, "", false);
    }

    private final Tunnel original; // null = tunnel baru
    /** Id hasil form: tetap selama form terbuka, supaya isian yang sama dikenali sebagai yang sudah di-test. */
    private final UUID draftId;
    private final List<Tunnel> all;
    private final List<HostProfile> hosts;
    private final Function<UUID, Optional<HostProfile>> profiles;

    private TunnelType type;
    private boolean autoStart;
    private boolean shared;
    private Tunnel result;

    private final JPanel typeCards = new JPanel(new GridLayout(1, 3, 8, 0));
    private final JTextField name = new JTextField();
    private final JComboBox<HostChoice> host = new JComboBox<>();
    private final JLabel sourceLabel = fieldLabel("");
    private final JTextField source = new JTextField();
    private final JLabel sourceMsg = SnippetUi.label(" ", SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted());
    private final JLabel destinationLabel = fieldLabel("");
    private final JTextField destinationHost = new JTextField();
    private final JTextField destinationPort = new JTextField();
    private final JLabel destinationMsg = SnippetUi.label(" ", SnippetUi.font(-1, Font.PLAIN), SnippetUi.danger());
    private final JPanel destinationCell = new JPanel();
    private final JPanel shareRow;
    private final JPanel shareWarning = new JPanel(new BorderLayout());
    private final JLabel shareWarningText = new JLabel();
    private final JPanel previewNodes = new JPanel(new GridBagLayout());
    private final SnippetUi.Paragraph command = new SnippetUi.Paragraph(SnippetUi.mono(-2), SnippetUi.fg());
    private final SnippetUi.Button save;

    /** Test connection untuk isian form ({@link TunnelManager#test}). Blocking: dipanggil di executor, bukan EDT. */
    @FunctionalInterface
    interface Tester {
        List<TunnelManager.TestStep> test(Tunnel draft);
    }

    private final Tester tester;
    private final java.util.concurrent.Executor checker;
    private final SnippetUi.Button testButton = new SnippetUi.Button(null, null, SnippetUi.Variant.DEFAULT);
    private final JPanel testResults = new JPanel();
    private Tunnel tested;      // EDT; isian yang hasil test-nya sedang tampil
    private boolean testing;    // EDT

    record HostChoice(HostProfile profile) {
        @Override
        public String toString() {
            return TunnelText.hostOption(profile);
        }
    }

    /**
     * @return tunnel yang disimpan, atau kosong kalau dibatalkan
     * @param existing      tunnel yang diedit, atau null untuk tunnel baru
     * @param template      isi awal tunnel baru (Duplicate: salinan dengan nama dan port baru), boleh null; diabaikan
     *                      kalau {@code existing} ada
     * @param preferredHost host awal untuk tunnel baru (mis. host tab aktif), boleh null
     */
    static Optional<Tunnel> show(Window owner, Tunnel existing, Tunnel template, List<Tunnel> all,
                                 List<HostProfile> hosts, UUID preferredHost,
                                 Function<UUID, Optional<HostProfile>> profiles,
                                 Tester tester, java.util.concurrent.Executor checker) {
        var form = new TunnelFormDialog(owner, existing, existing == null ? template : null, all, hosts, preferredHost,
                profiles, tester, checker);
        Runnable undim = SnippetUi.dim(owner);
        try {
            form.setVisible(true);
        } finally {
            undim.run();
        }
        return Optional.ofNullable(form.result);
    }

    private TunnelFormDialog(Window owner, Tunnel existing, Tunnel template, List<Tunnel> all,
                             List<HostProfile> hosts, UUID preferredHost,
                             Function<UUID, Optional<HostProfile>> profiles,
                             Tester tester, java.util.concurrent.Executor checker) {
        super(owner, I18n.t(existing != null ? "tunnel.form.edit"
                : template != null ? "tunnel.form.duplicate" : "tunnel.form.new"), ModalityType.APPLICATION_MODAL);
        this.tester = tester;
        this.checker = checker;
        this.original = existing;
        this.all = all;
        this.hosts = hosts;
        this.profiles = profiles;
        setUndecorated(true);
        boolean round = SnippetUi.roundWindow(this);
        Tunnel initial = existing != null ? existing : template;
        draftId = initial != null ? initial.id() : UUID.randomUUID();
        type = initial == null ? TunnelType.LOCAL : initial.type();
        autoStart = initial == null || initial.autoStart();
        shared = initial != null && initial.shared();
        shareRow = TunnelUi.toggleRow(I18n.t("tunnel.form.share"), () -> shared, SnippetUi::variable, () -> {
            shared = !shared;
            refresh();
        });
        save = new SnippetUi.Button(I18n.t(existing == null ? "tunnel.form.create" : "tunnel.form.save"), null,
                SnippetUi.Variant.PRIMARY);

        var root = new SnippetUi.RoundPanel(new BorderLayout(), SnippetUi::panel, SnippetUi::frame, round ? 16 : 0);
        root.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        root.add(header(round), BorderLayout.NORTH);
        var scroll = new JScrollPane(body());
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SnippetUi.panel());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, BorderLayout.CENTER);
        root.add(footer(), BorderLayout.SOUTH);
        setContentPane(root);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        fill(initial, preferredHost);
        rebuildCards(null);
        refresh();
        setSize(760, Math.min(root.getPreferredSize().height + 8,
                owner == null ? 640 : Math.max(420, owner.getHeight() - 32)));
        SnippetUi.keepSizeAcrossScreens(this);
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(name::requestFocusInWindow);
    }

    // --- tata letak ---

    private JComponent header(boolean round) {
        var bar = new TunnelUi.Bar(true, round, 40);
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBorder(BorderFactory.createCompoundBorder(bar.getBorder(), BorderFactory.createEmptyBorder(0, 14, 0, 6)));
        bar.add(SnippetUi.label(getTitle(), SnippetUi.font(1, Font.BOLD), SnippetUi.fg()));
        bar.add(Box.createHorizontalGlue());
        bar.add(TunnelUi.closeButton(this::dispose));
        TunnelUi.draggable(bar, this);
        return bar;
    }

    private JComponent body() {
        var body = new SnippetUi.TrackingPanel(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(SnippetUi.panel());
        body.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        typeCards.setOpaque(false);
        typeCards.getAccessibleContext().setAccessibleName(I18n.t("tunnel.form.type"));
        add(body, typeCards);
        body.add(Box.createVerticalStrut(14));

        var grid = new JPanel(new GridLayout(2, 2, 12, 12));
        grid.setOpaque(false);
        SnippetUi.styleField(name, 30);
        grid.add(field(fieldLabel(I18n.t("tunnel.form.name")), name, null));
        host.putClientProperty("FlatLaf.style", "arc: 8");
        host.setPreferredSize(new Dimension(40, 30));
        for (var h : hosts) {
            host.addItem(new HostChoice(h));
        }
        host.addActionListener(e -> refresh());
        grid.add(field(fieldLabel(I18n.t("tunnel.form.host")), host, null));
        SnippetUi.styleField(source, 30);
        source.setFont(SnippetUi.mono(-1));
        grid.add(field(sourceLabel, source, sourceMsg));
        SnippetUi.styleField(destinationHost, 30);
        SnippetUi.styleField(destinationPort, 30);
        destinationHost.setFont(SnippetUi.mono(-1));
        destinationPort.setFont(SnippetUi.mono(-1));
        destinationHost.putClientProperty("JTextField.placeholderText", "localhost");
        destinationPort.putClientProperty("JTextField.placeholderText", "5432");
        destinationPort.setPreferredSize(new Dimension(76, 30));
        var pair = new JPanel(new BorderLayout(6, 0));
        pair.setOpaque(false);
        pair.add(destinationHost, BorderLayout.CENTER);
        pair.add(destinationPort, BorderLayout.EAST);
        destinationCell.setOpaque(false);
        destinationCell.setLayout(new BorderLayout());
        destinationCell.add(field(destinationLabel, pair, destinationMsg));
        grid.add(destinationCell);
        add(body, grid);
        body.add(Box.createVerticalStrut(14));

        var toggles = new JPanel();
        toggles.setOpaque(false);
        toggles.setLayout(new BoxLayout(toggles, BoxLayout.Y_AXIS));
        toggles.add(TunnelUi.toggleRow(I18n.t("tunnel.form.autoStart"), () -> autoStart, SnippetUi::accent, () -> {
            autoStart = !autoStart;
            refresh();
        }));
        toggles.add(Box.createVerticalStrut(2));
        toggles.add(shareRow);
        shareWarningText.setFont(SnippetUi.font(0, Font.PLAIN));
        shareWarningText.setForeground(SnippetUi.variable());
        var warnBox = new SnippetUi.RoundPanel(new BorderLayout(), null, SnippetUi::variable, 12);
        warnBox.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        warnBox.add(shareWarningText);
        shareWarning.setOpaque(false);
        shareWarning.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        shareWarning.add(warnBox);
        shareWarning.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggles.add(shareWarning);
        add(body, toggles);
        body.add(Box.createVerticalStrut(14));

        var preview = new JPanel(new BorderLayout(0, 6));
        preview.setOpaque(false);
        preview.add(SnippetUi.section(I18n.t("tunnel.form.preview")), BorderLayout.NORTH);
        previewNodes.setOpaque(false);
        var stack = new JPanel(new BorderLayout(0, 6));
        stack.setOpaque(false);
        stack.add(previewNodes, BorderLayout.NORTH);
        stack.add(SnippetUi.codeBox(command), BorderLayout.CENTER);
        preview.add(stack, BorderLayout.CENTER);
        add(body, preview);

        // hasil Test connection: tampil setelah tombol Test ditekan, hilang lagi saat isian diubah
        testResults.setOpaque(false);
        testResults.setLayout(new BoxLayout(testResults, BoxLayout.Y_AXIS));
        testResults.setVisible(false);
        body.add(Box.createVerticalStrut(14));
        add(body, testResults);

        DocumentListener changed = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        };
        for (var f : new JTextField[] {name, source, destinationHost, destinationPort}) {
            f.getDocument().addDocumentListener(changed);
        }
        return body;
    }

    private static void add(JPanel body, JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(c);
    }

    private static JLabel fieldLabel(String text) {
        return SnippetUi.label(text, SnippetUi.font(0, Font.BOLD), SnippetUi.fg());
    }

    private static JComponent field(JLabel label, JComponent input, JLabel message) {
        var p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        input.setAlignmentX(Component.LEFT_ALIGNMENT);
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        label.setLabelFor(input);
        p.add(label);
        p.add(Box.createVerticalStrut(4));
        p.add(input);
        if (message != null) {
            message.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(Box.createVerticalStrut(4));
            p.add(message);
        }
        return p;
    }

    /** Footer: Test connection di kiri, Cancel + simpan di kanan. */
    private JComponent footer() {
        var bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBackground(SnippetUi.panel());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        testButton.setText(I18n.t("tunnel.test.button"));
        testButton.setToolTipText(I18n.t("tunnel.test.tip"));
        testButton.addActionListener(e -> runTest());
        var cancel = new SnippetUi.Button(I18n.t("tunnel.cancel"), null, SnippetUi.Variant.DEFAULT);
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> trySave());
        bar.add(testButton);
        bar.add(Box.createHorizontalGlue());
        bar.add(cancel);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(save);
        return bar;
    }

    /** Kartu jenis (prototipe {@code .tcard}): judul tebal + keterangan, garis aksen saat dipilih. */
    private JComponent typeCard(TunnelType t) {
        boolean on = t == type;
        var card = new JPanel(new BorderLayout(0, 2)) {
            private boolean hover;

            {
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
                        pickType(t);
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(on ? SnippetUi.selection() : SnippetUi.field());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(on || hover || isFocusOwner() ? SnippetUi.accent() : SnippetUi.border());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setFocusable(true);
        card.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        card.add(SnippetUi.label(TunnelText.type(t), SnippetUi.font(0, Font.BOLD), SnippetUi.fg()), BorderLayout.NORTH);
        card.add(SnippetUi.wrapped(SnippetUi.esc(TunnelText.typeDescription(t)), 190, SnippetUi.font(-1, Font.PLAIN),
                SnippetUi.muted()), BorderLayout.CENTER);
        card.getAccessibleContext().setAccessibleName(TunnelText.type(t));
        TunnelUi.onKeys(card, () -> pickType(t), KeyEvent.VK_ENTER, KeyEvent.VK_SPACE);
        return card;
    }

    private void pickType(TunnelType t) {
        type = t;
        rebuildCards(t);
        refresh();
    }

    /** Kartu jenis dibuat ulang hanya saat jenis berubah; fokus keyboard tetap di kartu yang dipilih. */
    private void rebuildCards(TunnelType focus) {
        typeCards.removeAll();
        for (var t : TunnelType.values()) {
            var card = typeCard(t);
            typeCards.add(card);
            if (t == focus) {
                SwingUtilities.invokeLater(card::requestFocusInWindow);
            }
        }
        typeCards.revalidate();
        typeCards.repaint();
    }

    // --- isi & validasi ---

    private void fill(Tunnel t, UUID preferredHost) {
        UUID hostId = t != null ? t.hostId() : preferredHost;
        for (int i = 0; i < host.getItemCount(); i++) {
            if (host.getItemAt(i).profile().id().equals(hostId)) {
                host.setSelectedIndex(i);
            }
        }
        if (t == null) {
            destinationHost.setText("localhost");
            return;
        }
        name.setText(t.name());
        source.setText(String.valueOf(t.sourcePort()));
        if (t.type().hasDestination()) {
            destinationHost.setText(t.destinationHost());
            destinationPort.setText(String.valueOf(t.destinationPort()));
        } else {
            destinationHost.setText("localhost");
        }
    }

    private Optional<HostProfile> selectedHost() {
        var choice = (HostChoice) host.getSelectedItem();
        return choice == null ? Optional.empty() : Optional.of(choice.profile());
    }

    private String bind() {
        return type.listensOnThisPc() && shared ? Tunnel.ANY_ADDRESS : Tunnel.LOOPBACK;
    }

    /** Pemeriksaan port sumber (prototipe): kosong, bukan port, bentrok dengan tunnel lain, atau di bawah 1024. */
    static SourceCheck checkSource(String text, TunnelType type, UUID hostId, UUID selfId, List<Tunnel> all) {
        if (text == null || text.isBlank()) {
            return new SourceCheck(true, "", false);
        }
        var port = TunnelChecks.parsePort(text);
        if (port.isEmpty()) {
            return new SourceCheck(true, I18n.t("tunnel.form.portRange"), false);
        }
        var clash = TunnelChecks.clash(all, selfId, type, hostId, port.get());
        if (clash.isPresent()) {
            return new SourceCheck(true, I18n.t("tunnel.form.portTaken", clash.get().name()), false);
        }
        if (port.get() < TunnelChecks.PRIVILEGED_BELOW) {
            return new SourceCheck(false, I18n.t("tunnel.form.portPrivileged"), true);
        }
        return SourceCheck.OK;
    }

    private void refresh() {
        String key = type.name().toLowerCase(java.util.Locale.ROOT);
        name.putClientProperty("JTextField.placeholderText", I18n.t("tunnel.form.name." + key + ".placeholder"));
        sourceLabel.setText(I18n.t("tunnel.form.source." + key));
        source.putClientProperty("JTextField.placeholderText", switch (type) {
            case LOCAL -> "5433";
            case REMOTE -> "9000";
            case DYNAMIC -> "1080";
        });
        destinationLabel.setText(I18n.t(type == TunnelType.REMOTE ? "tunnel.form.destination.remote"
                : "tunnel.form.destination.local"));
        destinationHost.getAccessibleContext().setAccessibleName(destinationLabel.getText());
        destinationPort.getAccessibleContext().setAccessibleName(destinationLabel.getText() + " port");
        destinationCell.setVisible(type.hasDestination());
        shareRow.setVisible(type.listensOnThisPc());
        var h = selectedHost();
        String hostName = TunnelText.hostName(h);
        shareWarningText.setText("<html>" + SnippetUi.esc(I18n.t("tunnel.form.share.warning", hostName)) + "</html>");
        shareWarning.setVisible(type.listensOnThisPc() && shared);

        UUID hostId = h.map(HostProfile::id).orElse(null);
        var check = checkSource(source.getText(), type, hostId, original == null ? null : original.id(), all);
        markBad(source, check.bad() && !check.message().isEmpty());
        message(sourceMsg, check.message(), check.warn() ? SnippetUi.variable() : SnippetUi.danger());
        boolean dstBad = type.hasDestination() && !destinationPort.getText().isBlank()
                && TunnelChecks.parsePort(destinationPort.getText()).isEmpty();
        markBad(destinationPort, dstBad);
        message(destinationMsg, dstBad ? I18n.t("tunnel.form.portRange") : "", SnippetUi.danger());

        rebuildPreview(hostName);
        command.setText("");
        command.add(TunnelText.command(type, bind(), source.getText(), destinationHost.getText(),
                destinationPort.getText(), h, profiles));
        var draft = build();
        save.setEnabled(draft.isPresent());
        testButton.setEnabled(draft.isPresent() && !testing);
        if (tested != null && !draft.equals(Optional.of(tested))) {
            tested = null; // isian berubah: hasil test lama tidak berlaku lagi
            testResults.setVisible(false);
        }
        getContentPane().revalidate();
        repaint();
    }

    // --- Test connection (hanya saat tombol ditekan) ---

    /** Jalankan semua langkah test untuk isian sekarang di executor; hasilnya tampil sebagai daftar ✓ / ✗. */
    private void runTest() {
        var draft = build();
        if (draft.isEmpty() || testing) {
            return;
        }
        Tunnel t = draft.get();
        testing = true;
        testButton.setEnabled(false);
        testButton.setText(I18n.t("tunnel.test.running"));
        showResults(List.of(), I18n.t("tunnel.test.running.detail", TunnelText.hostName(selectedHost())),
                SnippetUi.muted());
        checker.execute(() -> {
            List<TunnelManager.TestStep> steps;
            try {
                steps = tester.test(t);
            } catch (RuntimeException e) {
                steps = List.of(new TunnelManager.TestStep(TunnelManager.Step.CONNECT, TunnelManager.Check.PROBLEM,
                        e.toString(), 0));
            }
            var done = steps;
            SwingUtilities.invokeLater(() -> {
                testing = false;
                testButton.setText(I18n.t("tunnel.test.button"));
                if (!isDisplayable()) {
                    return;
                }
                tested = t;
                boolean ok = done.stream().allMatch(s -> s.check() != TunnelManager.Check.PROBLEM);
                showResults(done, I18n.t(ok ? "tunnel.test.passed" : "tunnel.test.failed"),
                        ok ? SnippetUi.success() : SnippetUi.danger());
                refresh();
                if (!build().equals(Optional.of(t))) { // isian diubah selama test berjalan
                    testResults.setVisible(false);
                    tested = null;
                }
            });
        });
    }

    /** Kotak hasil: ringkasan, lalu satu baris per langkah (✓ hijau, ✗ merah, – abu-abu kalau tidak bisa dicek). */
    private void showResults(List<TunnelManager.TestStep> steps, String summary, java.awt.Color summaryColor) {
        testResults.removeAll();
        var title = SnippetUi.label(summary, SnippetUi.font(0, Font.BOLD), summaryColor);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        testResults.add(title);
        String hostName = TunnelText.hostName(selectedHost());
        Tunnel t = build().orElse(null);
        for (var s : steps) {
            testResults.add(Box.createVerticalStrut(6));
            var line = new JLabel("<html>" + SnippetUi.esc(TunnelText.testLine(s, t, hostName)) + "</html>");
            line.setFont(SnippetUi.font(0, Font.PLAIN));
            line.setForeground(SnippetUi.fg());
            line.setIcon(switch (s.check()) {
                case OK -> SnippetUi.stroke(SnippetUi.CHECK, 14, SnippetUi::success, 2.4f);
                case PROBLEM -> SnippetUi.stroke(SnippetUi.CLOSE, 14, SnippetUi::danger, 2.4f);
                case UNKNOWN -> SnippetUi.stroke("M6 12h12", 14, SnippetUi::muted, 2.4f);
            });
            line.setIconTextGap(8);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            testResults.add(line);
        }
        testResults.setVisible(true);
        testResults.revalidate();
        testResults.repaint();
        SwingUtilities.invokeLater(() -> testResults.scrollRectToVisible(
                new java.awt.Rectangle(0, 0, testResults.getWidth(), testResults.getHeight())));
    }

    /** Pesan di bawah isian; teks panjang dibungkus selebar kolom. */
    private static void message(JLabel label, String text, java.awt.Color color) {
        label.setText(text.isBlank() ? " " : "<html>" + SnippetUi.esc(text) + "</html>");
        label.setForeground(color);
    }

    private static void markBad(JTextField f, boolean bad) {
        f.putClientProperty("JComponent.outline", bad ? "error" : null);
    }

    private void rebuildPreview(String hostName) {
        previewNodes.removeAll();
        var nodes = TunnelText.nodes(type, bind(), source.getText(), destinationHost.getText(),
                destinationPort.getText(), hostName);
        var c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        int x = 0;
        for (var n : nodes) {
            c.gridx = x++;
            c.weightx = 1;
            c.insets = new Insets(0, 0, 0, 0);
            previewNodes.add(nodeBox(n, 6), c);
            if (!n.link().isEmpty()) {
                c.gridx = x++;
                c.weightx = 0;
                c.insets = new Insets(0, 6, 0, 6);
                var arrow = new JLabel(SnippetUi.stroke(TunnelUi.ARROW_RIGHT, 16, SnippetUi::accent, 1.8f));
                arrow.setToolTipText(n.link());
                arrow.getAccessibleContext().setAccessibleName(n.link());
                previewNodes.add(arrow, c);
            }
        }
        previewNodes.revalidate();
    }

    /** Kotak satu titik alur: keterangan abu-abu kecil + nilai mono (prototipe). */
    static JComponent nodeBox(TunnelText.Node n, int vpad) {
        var box = new SnippetUi.RoundPanel(new GridLayout(2, 1, 0, 1), SnippetUi::field, SnippetUi::border, 12);
        box.setBorder(BorderFactory.createEmptyBorder(vpad, 10, vpad, 10));
        box.add(SnippetUi.label(n.key(), SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted()));
        var value = SnippetUi.label(n.value(), SnippetUi.mono(-2), SnippetUi.fg());
        value.setToolTipText(n.value());
        box.add(value);
        box.setMinimumSize(new Dimension(60, box.getPreferredSize().height));
        box.setPreferredSize(new Dimension(60, box.getPreferredSize().height));
        return box;
    }

    /** Tunnel dari isian, atau kosong kalau belum valid (tombol simpan nonaktif). */
    private Optional<Tunnel> build() {
        var h = selectedHost();
        if (h.isEmpty() || name.getText().isBlank() || name.getText().strip().length() > Tunnel.MAX_NAME) {
            return Optional.empty();
        }
        var check = checkSource(source.getText(), type, h.get().id(), original == null ? null : original.id(), all);
        if (check.bad()) {
            return Optional.empty();
        }
        int src = TunnelChecks.parsePort(source.getText()).orElseThrow();
        String dstHost = destinationHost.getText().strip();
        int dstPort = 0;
        if (type.hasDestination()) {
            var p = TunnelChecks.parsePort(destinationPort.getText());
            if (p.isEmpty() || !TunnelChecks.isHost(dstHost)) {
                return Optional.empty();
            }
            dstPort = p.get();
        }
        try {
            return Optional.of(new Tunnel(draftId, name.getText(), type, h.get().id(), src, dstHost, dstPort, shared,
                    autoStart));
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // mis. karakter kontrol di nama
        }
    }

    private void trySave() {
        build().ifPresent(t -> {
            result = t;
            dispose();
        });
    }
}

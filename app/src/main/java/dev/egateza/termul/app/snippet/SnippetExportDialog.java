package dev.egateza.termul.app.snippet;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.core.io.AtomicFiles;
import dev.egateza.termul.core.snippet.Snippet;
import dev.egateza.termul.core.snippet.SnippetStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Ekspor snippet ke JSON (prototipe "Export snippets"): pilih semua / tampilan sekarang / favorit, dengan atau tanpa
 * field tambahan TermUL, pratinjau isi file, lalu Salin JSON atau Simpan sebagai…. Format sama dengan yang diimpor
 * TermUL desktop dan mobile (version 2, dicocokkan per id). Nilai variabel yang diingat per host tidak pernah ikut.
 */
final class SnippetExportDialog {

    /** Pilihan isi ekspor. */
    enum Which { ALL, VIEW, FAVORITES }

    /**
     * @param view      snippet di tampilan manager sekarang (kategori aktif, tanpa pencarian)
     * @param viewLabel nama tampilan itu (mis. "Docker")
     * @param start     pilihan awal
     */
    record Sets(List<Snippet> all, List<Snippet> view, String viewLabel, Which start) {
        List<Snippet> of(Which which) {
            return switch (which) {
                case ALL -> all;
                case VIEW -> view;
                case FAVORITES -> all.stream().filter(Snippet::favorite).toList();
            };
        }
    }

    private final SnippetStore store;
    private final Executor io;
    private final Consumer<String> toast;
    private final Sets sets;
    private final JDialog dialog;
    private final JTextArea preview = new JTextArea();
    private final JLabel meta = SnippetUi.label("", SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted());
    private final SnippetUi.Toggle extras;
    private final SnippetUi.Button copy;
    private final SnippetUi.Button save;
    private Which which;          // EDT
    private byte[] json = {};     // EDT; isi file untuk pilihan sekarang

    private SnippetExportDialog(Window owner, SnippetStore store, Executor io, Consumer<String> toast, Sets sets) {
        this.store = store;
        this.io = io;
        this.toast = toast;
        this.sets = sets;
        this.which = sets.start();
        dialog = new JDialog(owner, I18n.t("snippet.export.title"), JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        boolean round = SnippetUi.roundWindow(dialog);
        extras = SnippetUi.toggle(I18n.t("snippet.export.extras"), I18n.t("snippet.export.extrasHint"), true,
                SnippetUi::accent);
        extras.box().addActionListener(e -> refresh());
        copy = new SnippetUi.Button(I18n.t("snippet.export.copy"), null, SnippetUi.Variant.DEFAULT);
        copy.addActionListener(e -> copyJson());
        save = new SnippetUi.Button(I18n.t("snippet.export.save"), null, SnippetUi.Variant.PRIMARY);
        save.addActionListener(e -> saveAs());

        var card = new SnippetUi.RoundPanel(new BorderLayout(), SnippetUi::panel, SnippetUi::frame, round ? 16 : 0);
        card.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        card.add(top(), BorderLayout.NORTH);
        card.add(middle(), BorderLayout.CENTER);
        card.add(buttons(round), BorderLayout.SOUTH);

        var root = dialog.getRootPane();
        root.setOpaque(false);
        root.registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.setDefaultButton(save);
        dialog.setContentPane(card);
        refresh();
        int h = owner == null ? 640 : Math.min(640, owner.getHeight() - 32);
        dialog.setSize(640, Math.max(420, h));
        SnippetUi.keepSizeAcrossScreens(dialog);
        dialog.setLocationRelativeTo(owner);
    }

    static void show(Window owner, SnippetStore store, Executor io, Consumer<String> toast, Sets sets) {
        new SnippetExportDialog(owner, store, io, toast, sets).dialog.setVisible(true);
    }

    /** Nama file bawaan: {@code termul-snippets-<tanggal>.json}. */
    static String fileName(LocalDate date) {
        return "termul-snippets-" + date + ".json";
    }

    private JComponent top() {
        var box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(16, 18, 12, 18)));
        var title = SnippetUi.label(I18n.t("snippet.export.title"), SnippetUi.font(1, Font.BOLD), SnippetUi.fg());
        add(box, title, 12);
        var group = new ButtonGroup();
        add(box, choice(group, Which.ALL, I18n.t("snippet.nav.all")), 6);
        add(box, choice(group, Which.VIEW, I18n.t("snippet.export.view", sets.viewLabel())), 6);
        add(box, choice(group, Which.FAVORITES, I18n.t("snippet.nav.favorites")), 12);
        add(box, extras.row(), 0);
        return box;
    }

    private static void add(JPanel box, JComponent c, int gap) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        box.add(c);
        box.add(Box.createVerticalStrut(gap));
    }

    /** Pilihan radio 32 px bergaris tepi; garis dan latar aksen saat dipilih (prototipe). */
    private JComponent choice(ButtonGroup group, Which value, String label) {
        int n = sets.of(value).size();
        var radio = new JRadioButton(label, value == which);
        radio.setOpaque(false);
        radio.setFont(SnippetUi.font(0, Font.PLAIN));
        radio.setForeground(SnippetUi.fg());
        group.add(radio);
        var row = new SnippetUi.RoundPanel(new BorderLayout(10, 0),
                () -> radio.isSelected() ? SnippetUi.selection() : SnippetUi.panel(),
                () -> radio.isSelected() ? SnippetUi.accent() : SnippetUi.border(), 8);
        row.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 10));
        row.setPreferredSize(new Dimension(10, 32));
        row.add(radio, BorderLayout.CENTER);
        row.add(SnippetUi.mutedLabel(snippets(n)), BorderLayout.EAST);
        radio.addActionListener(e -> {
            which = value;
            refresh();
            row.getParent().repaint();
        });
        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                radio.doClick();
            }
        });
        return row;
    }

    private JComponent middle() {
        preview.setEditable(false);
        preview.setFont(SnippetUi.mono(-0.5f));
        preview.setForeground(SnippetUi.fg());
        preview.setBackground(SnippetUi.field());
        preview.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        preview.getAccessibleContext().setAccessibleName(I18n.t("snippet.export.preview"));
        var scroll = new JScrollPane(preview);
        scroll.setBorder(BorderFactory.createLineBorder(SnippetUi.border()));

        var head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        var left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(SnippetUi.section(I18n.t("snippet.export.preview")));
        left.add(SnippetUi.label(fileName(LocalDate.now()), SnippetUi.mono(-1), SnippetUi.muted()));
        head.add(left, BorderLayout.WEST);
        head.add(meta, BorderLayout.EAST);

        var box = new JPanel(new BorderLayout(0, 6));
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        box.add(head, BorderLayout.NORTH);
        box.add(scroll, BorderLayout.CENTER);
        box.add(SnippetUi.text(I18n.t("snippet.export.note"), SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted()),
                BorderLayout.SOUTH);
        return box;
    }

    private JComponent buttons(boolean round) {
        var cancel = new SnippetUi.Button(I18n.t("snippet.cancel"), null, SnippetUi.Variant.DEFAULT);
        cancel.addActionListener(e -> dialog.dispose());
        var bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SnippetUi.panel());
                int arc = round ? 14 : 0;
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, -arc, getWidth(), getHeight() + arc, arc, arc));
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(12, 10, 12, 10)));
        bar.add(cancel);
        bar.add(copy);
        bar.add(save);
        return bar;
    }

    private static String snippets(int n) {
        return I18n.t(n == 1 ? "snippet.cats.snippets1" : "snippet.cats.snippets", n);
    }

    /** "version 2 · 3 snippet · 1.2 KB" */
    static String meta(int count, int bytes) {
        return I18n.t("snippet.export.meta", snippets(count), String.format(Locale.ROOT, "%.1f", bytes / 1024.0));
    }

    private void refresh() {
        var list = sets.of(which);
        json = store.export(list, extras.box().isSelected(), which == Which.ALL);
        preview.setText(new String(json, StandardCharsets.UTF_8));
        preview.setCaretPosition(0);
        meta.setText(meta(list.size(), json.length));
        copy.setEnabled(!list.isEmpty());
        save.setEnabled(!list.isEmpty());
    }

    private void copyJson() {
        int n = sets.of(which).size();
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(new String(json, StandardCharsets.UTF_8)), null);
        dialog.dispose();
        toast.accept(I18n.t("snippet.export.copied", snippets(n)));
    }

    private void saveAs() {
        var chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
        chooser.setSelectedFile(new File(fileName(LocalDate.now())));
        if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        File file = chosen.getName().toLowerCase(Locale.ROOT).endsWith(".json") ? chosen
                : new File(chosen.getParentFile(), chosen.getName() + ".json");
        if (file.exists() && !Dialogs.confirm(dialog, I18n.t("snippet.export.title"),
                I18n.t("snippet.export.overwrite", file.getName()))) {
            return;
        }
        byte[] content = json;
        int n = sets.of(which).size();
        UiAsync.run(io, () -> {
            try {
                AtomicFiles.write(file.toPath(), content);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, err -> Dialogs.error(dialog, I18n.t("snippet.export.failed", file.getName(), err.getMessage()), err))
                .thenRun(() -> javax.swing.SwingUtilities.invokeLater(() -> {
                    dialog.dispose();
                    toast.accept(I18n.t("snippet.export.saved", snippets(n), file.getName()));
                }));
    }
}

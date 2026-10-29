package dev.egateza.termul.app.snippet;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.core.snippet.SnippetCategory;
import dev.egateza.termul.core.snippet.SnippetStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Kelola kategori snippet (prototipe "Manage categories"): tambah, ganti nama (id tetap, jadi file ekspor/impor tetap
 * cocok), geser naik/turun, dan hapus dengan konfirmasi di barisnya (snippet pindah ke Tanpa kategori, tidak ada yang
 * terhapus). Urutan di sini dipakai di navigasi, editor, dan menu snippet terminal. Simpan di executor I/O; UI di EDT.
 */
final class SnippetCategoriesDialog {

    static final String PENCIL = "M4 20h4L19 9l-4-4L4 16z M13.5 6.5l4 4";
    static final String UP = "M6 15l6-6 6 6";

    private final SnippetStore store;
    private final Executor io;
    private final Consumer<String> toast;
    private final JDialog dialog;
    private final RowsPanel rows = new RowsPanel();
    /** Baris kategori yang sedang tampil, urutan sama dengan {@code store.categories()}. EDT. */
    private final List<JComponent> categoryRows = new java.util.ArrayList<>();
    private int dragFrom = -1;  // EDT; indeks kategori yang sedang diseret, −1 = tidak menyeret
    private final JLabel countLabel = SnippetUi.mutedLabel("");
    private final JTextField newName = SnippetUi.styleField(new JTextField(), 30);
    private final JLabel addError = SnippetUi.label("", SnippetUi.font(-0.5f, Font.PLAIN), SnippetUi.danger());
    private String editId;      // EDT; kategori yang sedang diganti namanya
    private String editName = ""; // EDT
    private String editError = ""; // EDT
    private String deleteId;    // EDT; kategori yang menunggu konfirmasi hapus

    private SnippetCategoriesDialog(Window owner, SnippetStore store, Executor io, Consumer<String> toast) {
        this.store = store;
        this.io = io;
        this.toast = toast;
        dialog = new JDialog(owner, I18n.t("snippet.cats.title"), JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        boolean round = SnippetUi.roundWindow(dialog);
        var card = new SnippetUi.RoundPanel(new BorderLayout(), SnippetUi::panel, SnippetUi::frame, round ? 16 : 0);
        card.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        card.add(header(), BorderLayout.NORTH);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBackground(SnippetUi.panel());
        rows.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var scroll = new JScrollPane(rows);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SnippetUi.panel());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        card.add(scroll, BorderLayout.CENTER);
        card.add(bottom(round), BorderLayout.SOUTH);

        var root = dialog.getRootPane();
        root.setOpaque(false);
        root.registerKeyboardAction(e -> escape(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setContentPane(card);
        rebuild();
        int h = owner == null ? 560 : Math.min(560, owner.getHeight() - 32);
        dialog.setSize(520, Math.max(360, h));
        SnippetUi.keepSizeAcrossScreens(dialog);
        dialog.setLocationRelativeTo(owner);
    }

    /** Buka dialog (modal) di atas {@code owner}; {@code toast} menerima pesan hasil untuk status bar manager. */
    static void show(Window owner, SnippetStore store, Executor io, Consumer<String> toast) {
        var d = new SnippetCategoriesDialog(owner, store, io, toast);
        SwingUtilities.invokeLater(d.newName::requestFocusInWindow);
        d.dialog.setVisible(true);
    }

    private void escape() {
        if (editId != null || deleteId != null) {
            editId = null;
            deleteId = null;
            rebuild();
        } else {
            dialog.dispose();
        }
    }

    // --- bagian tetap ---

    private JComponent header() {
        var texts = new JPanel(new BorderLayout(0, 4));
        texts.setOpaque(false);
        texts.add(SnippetUi.label(I18n.t("snippet.cats.title"), SnippetUi.font(1, Font.BOLD), SnippetUi.fg()),
                BorderLayout.NORTH);
        texts.add(SnippetUi.text(I18n.t("snippet.cats.subtitle"), SnippetUi.font(-0.5f, Font.PLAIN),
                SnippetUi.muted()), BorderLayout.CENTER);
        var head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        head.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(16, 18, 12, 18)));
        head.add(texts, BorderLayout.CENTER);
        var count = new JPanel(new BorderLayout());
        count.setOpaque(false);
        count.add(countLabel, BorderLayout.NORTH);
        head.add(count, BorderLayout.EAST);
        return head;
    }

    private JComponent bottom(boolean round) {
        newName.setFont(SnippetUi.font(0, Font.PLAIN));
        newName.putClientProperty("JTextField.placeholderText", I18n.t("snippet.cats.newPlaceholder"));
        newName.getDocument().addDocumentListener(SnippetQuickPick.onChange(() -> {
            if (!addError.getText().isEmpty()) {
                addError.setText("");
                markError(newName, false);
            }
        }));
        newName.addActionListener(e -> add());
        var label = SnippetUi.section(I18n.t("snippet.cats.new"));
        label.setLabelFor(newName);
        var addButton = new SnippetUi.Button(I18n.t("snippet.cats.add"),
                SnippetUi.stroke(SnippetUi.PLUS, 13, SnippetUi::fg, 2.4f), SnippetUi.Variant.DEFAULT);
        addButton.addActionListener(e -> add());
        var line = new JPanel(new BorderLayout(8, 0));
        line.setOpaque(false);
        line.add(newName, BorderLayout.CENTER);
        line.add(addButton, BorderLayout.EAST);

        var form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));
        for (JComponent c : List.<JComponent>of(label, line, addError)) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(c);
            form.add(Box.createVerticalStrut(6));
        }
        var hint = SnippetUi.text(I18n.t("snippet.cats.hint", SnippetStore.MAX_CATEGORY_NAME),
                SnippetUi.font(-0.5f, Font.PLAIN), SnippetUi.muted());
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(hint);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        var done = new SnippetUi.Button(I18n.t("snippet.cats.done"), null, SnippetUi.Variant.PRIMARY);
        done.addActionListener(e -> dialog.dispose());
        var foot = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SnippetUi.bar());
                int arc = round ? 14 : 0;
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, -arc, getWidth(), getHeight() + arc, arc, arc));
                g2.dispose();
            }
        };
        foot.setOpaque(false);
        foot.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));
        foot.add(done, BorderLayout.EAST);

        var out = new JPanel(new BorderLayout());
        out.setOpaque(false);
        out.add(form, BorderLayout.CENTER);
        out.add(foot, BorderLayout.SOUTH);
        return out;
    }

    // --- daftar ---

    private void rebuild() {
        var cats = store.categories();
        var all = store.snapshot();
        rows.removeAll();
        categoryRows.clear();
        for (int i = 0; i < cats.size(); i++) {
            var c = cats.get(i);
            int n = (int) all.stream().filter(s -> s.category().equals(c.id())).count();
            var row = row(c, n, i);
            categoryRows.add(row);
            rows.add(row);
            rows.add(Box.createVerticalStrut(2));
        }
        if (cats.isEmpty()) {
            var empty = SnippetUi.text(I18n.t("snippet.cats.empty"), SnippetUi.font(0, Font.PLAIN), SnippetUi.muted());
            empty.setBorder(BorderFactory.createEmptyBorder(16, 8, 16, 8));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            rows.add(empty);
        }
        var ids = cats.stream().map(SnippetCategory::id).toList();
        rows.add(uncategorizedRow((int) all.stream().filter(s -> !ids.contains(s.category())).count()));
        countLabel.setText(I18n.t(cats.size() == 1 ? "snippet.cats.count1" : "snippet.cats.count", cats.size()));
        rows.revalidate();
        rows.repaint();
    }

    private static String snippets(int n) {
        return I18n.t(n == 1 ? "snippet.cats.snippets1" : "snippet.cats.snippets", n);
    }

    private JComponent row(SnippetCategory c, int n, int index) {
        boolean first = index == 0;
        boolean last = index == store.categories().size() - 1;
        boolean editing = c.id().equals(editId);
        boolean deleting = c.id().equals(deleteId);
        var line = new JPanel();
        line.setLayout(new BoxLayout(line, BoxLayout.X_AXIS));
        line.setOpaque(false);
        line.add(grip(index, c.name()));
        line.add(Box.createHorizontalStrut(8));
        if (editing) {
            var field = SnippetUi.styleField(new JTextField(editName), 28);
            field.setFont(SnippetUi.font(0, Font.PLAIN));
            field.getAccessibleContext().setAccessibleName(I18n.t("snippet.cats.name"));
            markError(field, !editError.isEmpty());
            field.getDocument().addDocumentListener(SnippetQuickPick.onChange(() -> editName = field.getText()));
            field.addActionListener(e -> rename(c));
            field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            line.add(field);
            line.add(Box.createHorizontalStrut(8));
            line.add(button(I18n.t("snippet.cancel"), SnippetUi.Variant.DEFAULT, () -> {
                editId = null;
                rebuild();
            }));
            line.add(Box.createHorizontalStrut(8));
            line.add(button(I18n.t("snippet.save"), SnippetUi.Variant.PRIMARY, () -> rename(c)));
            SwingUtilities.invokeLater(() -> {
                field.requestFocusInWindow();
                field.selectAll();
            });
        } else if (deleting) {
            line.add(SnippetUi.text(I18n.t(n == 0 ? "snippet.cats.deleteEmpty" : "snippet.cats.deleteMove",
                    c.name(), snippets(n)), SnippetUi.font(0, Font.PLAIN), SnippetUi.fg()));
            line.add(Box.createHorizontalStrut(8));
            var cancel = button(I18n.t("snippet.cancel"), SnippetUi.Variant.PRIMARY, () -> {
                deleteId = null;
                rebuild();
            });
            line.add(cancel);
            line.add(Box.createHorizontalStrut(8));
            line.add(button(I18n.t("snippet.action.delete"), SnippetUi.Variant.DANGER, () -> delete(c, n)));
            SwingUtilities.invokeLater(cancel::requestFocusInWindow);
        } else {
            var name = SnippetUi.label(c.name(), SnippetUi.font(0, Font.BOLD), SnippetUi.fg());
            name.setIcon(SnippetUi.folder(14, SnippetUi::accent));
            name.setIconTextGap(8);
            name.setMinimumSize(new Dimension(0, name.getPreferredSize().height));
            line.add(name);
            line.add(Box.createHorizontalGlue());
            line.add(Box.createHorizontalStrut(8));
            line.add(SnippetUi.label(snippets(n), SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted()));
            line.add(Box.createHorizontalStrut(4));
            line.add(icon(UP, I18n.t("snippet.cats.up", c.name()), SnippetUi::muted, !first, () -> move(c, -1)));
            line.add(icon(SnippetUi.CHEVRON, I18n.t("snippet.cats.down", c.name()), SnippetUi::muted, !last,
                    () -> move(c, 1)));
            line.add(icon(PENCIL, I18n.t("snippet.cats.rename", c.name()), SnippetUi::muted, true, () -> {
                editId = c.id();
                editName = c.name();
                editError = "";
                deleteId = null;
                rebuild();
            }));
            line.add(icon(SnippetUi.TRASH, I18n.t("snippet.cats.delete", c.name()), SnippetUi::danger, true, () -> {
                deleteId = c.id();
                editId = null;
                rebuild();
            }));
        }

        Color bg = deleting ? SnippetUi.alpha(SnippetUi.danger(), 0.08f) : editing ? SnippetUi.selection() : null;
        var row = new SnippetUi.RoundPanel(new BorderLayout(0, 6), bg == null ? null : () -> bg, null, 12);
        row.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        row.add(line, BorderLayout.CENTER);
        if (editing && !editError.isEmpty()) {
            var err = SnippetUi.label(editError, SnippetUi.font(-0.5f, Font.PLAIN), SnippetUi.danger());
            err.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
            row.add(err, BorderLayout.SOUTH);
        }
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /**
     * Baris "Tanpa kategori" (prototipe): tanpa pegangan (jarak selebar pegangan), ikon folder bergaris putus-putus,
     * nama + keterangan, jumlah di kanan. Padding dan tinggi sama dengan baris kategori supaya ikon dan teks sejajar.
     */
    private JComponent uncategorizedRow(int n) {
        var line = new JPanel();
        line.setLayout(new BoxLayout(line, BoxLayout.X_AXIS));
        line.setOpaque(false);
        line.add(Box.createHorizontalStrut(new GripIcon().getIconWidth() + 8));
        var name = SnippetUi.label(I18n.t("snippet.nav.uncategorized"), SnippetUi.font(0, Font.PLAIN),
                SnippetUi.muted());
        name.setIcon(new DashedFolderIcon());
        name.setIconTextGap(8);
        line.add(name);
        line.add(Box.createHorizontalStrut(6));
        var note = SnippetUi.label(I18n.t("snippet.cats.uncategorizedNote"), SnippetUi.font(-1, Font.PLAIN),
                SnippetUi.muted());
        note.setMinimumSize(new Dimension(0, note.getPreferredSize().height));
        line.add(note);
        line.add(Box.createHorizontalGlue());
        line.add(Box.createHorizontalStrut(8));
        line.add(SnippetUi.label(snippets(n), SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted()));
        line.add(Box.createHorizontalStrut(4));
        line.setPreferredSize(new Dimension(10, 28)); // setinggi tombol ikon di baris kategori
        var row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        row.add(line);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    // --- seret untuk mengubah urutan ---

    /** Pegangan 2×3 titik: seret untuk memindah kategori; tombol naik/turun tetap ada untuk keyboard. */
    private JComponent grip(int index, String name) {
        var grip = new JLabel(new GripIcon());
        grip.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
        grip.setToolTipText(I18n.t("snippet.cats.drag", name));
        boolean idle = editId == null && deleteId == null;
        var mouse = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (idle && SwingUtilities.isLeftMouseButton(e)) {
                    dragFrom = index;
                }
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (dragFrom >= 0) {
                    int y = SwingUtilities.convertPoint(grip, e.getPoint(), rows).y;
                    rows.showLine(lineY(insertBefore(y)));
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                int from = dragFrom;
                dragFrom = -1;
                rows.showLine(-1);
                if (from < 0) {
                    return;
                }
                int y = SwingUtilities.convertPoint(grip, e.getPoint(), rows).y;
                int to = SnippetManagerDialog.dropIndex(from, insertBefore(y));
                if (to != from) {
                    var c = store.categories().get(from);
                    move(c, to - from);
                }
            }
        };
        grip.addMouseListener(mouse);
        grip.addMouseMotionListener(mouse);
        return grip;
    }

    /** Celah sisipan untuk posisi {@code y} (koordinat daftar): sebelum baris ke-n, atau {@code size} = paling akhir. */
    private int insertBefore(int y) {
        for (int i = 0; i < categoryRows.size(); i++) {
            var b = categoryRows.get(i).getBounds();
            if (y < b.y + b.height / 2) {
                return i;
            }
        }
        return categoryRows.size();
    }

    private int lineY(int before) {
        if (categoryRows.isEmpty()) {
            return -1;
        }
        if (before < categoryRows.size()) {
            return categoryRows.get(before).getY() - 1;
        }
        var last = categoryRows.getLast().getBounds();
        return last.y + last.height + 1;
    }

    /** Daftar baris (lebar mengikuti viewport) yang bisa menggambar garis sisipan saat menyeret. */
    private static final class RowsPanel extends JPanel implements javax.swing.Scrollable {
        private int lineY = -1;

        void showLine(int y) {
            if (y != lineY) {
                lineY = y;
                repaint();
            }
        }

        @Override
        protected void paintChildren(Graphics g) {
            super.paintChildren(g);
            if (lineY >= 0) {
                var g2 = (Graphics2D) g.create();
                g2.setColor(SnippetUi.accent());
                var in = getInsets();
                g2.fillRect(in.left, lineY - 1, getWidth() - in.left - in.right, 2);
                g2.dispose();
            }
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getPreferredSize().height < getParent().getHeight();
        }
    }

    /** Folder bergaris putus-putus untuk "Tanpa kategori" (prototipe), viewBox 16. */
    private static final class DashedFolderIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.scale(14 / 16.0, 14 / 16.0);
            g2.setColor(SnippetUi.muted());
            g2.setStroke(new java.awt.BasicStroke(1.2f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER,
                    10, new float[] {2, 2}, 0));
            g2.draw(SnippetUi.parse("M1.5 3.5h4.5l1 1h7.5v8h-13z"));
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    private static SnippetUi.Button button(String text, SnippetUi.Variant variant, Runnable run) {
        var b = new SnippetUi.Button(text, null, variant).height(28);
        b.addActionListener(e -> run.run());
        return b;
    }

    private static SnippetUi.Button icon(String path, String name, java.util.function.Supplier<Color> color,
                                         boolean enabled, Runnable run) {
        var b = new SnippetUi.Button(null, SnippetUi.stroke(path, 13, color, 2.1f), SnippetUi.Variant.ICON);
        b.setToolTipText(name);
        b.getAccessibleContext().setAccessibleName(name);
        b.setEnabled(enabled);
        b.addActionListener(e -> run.run());
        return b;
    }

    private static void markError(JTextField field, boolean error) {
        field.putClientProperty("FlatLaf.style", "background: " + SnippetUi.hex(SnippetUi.field())
                + "; borderColor: " + SnippetUi.hex(error ? SnippetUi.danger() : SnippetUi.border())
                + "; focusedBorderColor: " + SnippetUi.hex(error ? SnippetUi.danger() : SnippetUi.accent())
                + "; arc: 8");
    }

    // --- aksi ---

    /** Pesan untuk masalah nama, atau "" kalau boleh. */
    static String problemText(SnippetStore.CategoryProblem problem) {
        if (problem == null) {
            return "";
        }
        return switch (problem) {
            case EMPTY -> I18n.t("snippet.cats.err.empty");
            case TOO_LONG -> I18n.t("snippet.cats.err.long", SnippetStore.MAX_CATEGORY_NAME);
            case DUPLICATE -> I18n.t("snippet.cats.err.taken");
        };
    }

    private String problem(String name, String selfId) {
        return problemText(SnippetStore.categoryProblem(store.categories(), name, selfId,
                I18n.t("snippet.nav.uncategorized"), "Uncategorized"));
    }

    private void add() {
        String name = newName.getText().strip();
        String err = problem(name, null);
        if (!err.isEmpty()) {
            addError.setText(err);
            markError(newName, true);
            return;
        }
        UiAsync.run(io, () -> store.addCategory(name), c -> {
            newName.setText("");
            rebuild();
            toast.accept(I18n.t("snippet.cats.added", c.name()));
        }, this::failed);
    }

    private void rename(SnippetCategory c) {
        String name = editName.strip();
        String err = problem(name, c.id());
        if (!err.isEmpty()) {
            editError = err;
            rebuild();
            return;
        }
        UiAsync.run(io, () -> store.renameCategory(c.id(), name), this::failed).thenRun(() ->
                SwingUtilities.invokeLater(() -> {
                    editId = null;
                    editError = "";
                    rebuild();
                    if (!name.equals(c.name())) {
                        toast.accept(I18n.t("snippet.cats.renamed", c.name(), name));
                    }
                }));
    }

    private void move(SnippetCategory c, int delta) {
        UiAsync.run(io, () -> store.moveCategory(c.id(), delta), this::failed)
                .thenRun(() -> SwingUtilities.invokeLater(this::rebuild));
    }

    private void delete(SnippetCategory c, int n) {
        UiAsync.run(io, () -> store.deleteCategory(c.id()), moved -> {
            deleteId = null;
            rebuild();
            toast.accept(moved == 0 ? I18n.t("snippet.cats.deleted", c.name())
                    : I18n.t("snippet.cats.deletedMoved", c.name(), snippets(moved)));
        }, this::failed);
    }

    private void failed(Throwable err) {
        Dialogs.error(dialog, I18n.t("snippet.saveFailed", err.getMessage()), err);
    }

    /** Titik pegangan 2×3 (prototipe); urutan diubah dengan tombol naik/turun. */
    private static final class GripIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(SnippetUi.muted());
            for (int col = 0; col < 2; col++) {
                for (int r = 0; r < 3; r++) {
                    g2.fillOval(x + 1 + col * 5, y + 1 + r * 5, 3, 3);
                }
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 10;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }
}

package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

/**
 * Pengaturan → Shortcut: daftar aksi dengan shortcut efektif dan default-nya. Ganti dengan menekan kombinasi tombol
 * (double-click / Ubah), kosongkan, reset per baris, atau reset semua. Perubahan berupa draft sampai Simpan; yang
 * disimpan hanya override ({@link KeyBindings#overrides()}). Aturan dan peringatan di {@link ShortcutRules}.
 */
public final class ShortcutsDialog extends JDialog {

    private static final int COL_CATEGORY = 0;
    private static final int COL_ACTION = 1;
    private static final int COL_KEY = 2;
    private static final int COL_DEFAULT = 3;

    private final KeyBindings saved;
    private KeyBindings draft;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final TableRowSorter<Model> sorter = new TableRowSorter<>(model);
    private final JTextField search = new JTextField(20);
    private final JButton change = new JButton(I18n.t("shortcut.change"));
    private final JButton clear = new JButton(I18n.t("shortcut.clear"));
    private final JButton reset = new JButton(I18n.t("shortcut.reset"));

    /** @param save menerima override baru (dipanggil di EDT; penyimpanan ke disk urusan pemanggil) */
    public ShortcutsDialog(Window owner, KeyBindings current, Consumer<Map<String, String>> save) {
        super(owner, I18n.t("shortcut.title"), ModalityType.APPLICATION_MODAL);
        this.saved = current;
        this.draft = current;

        table.setRowSorter(sorter);
        sorter.setSortable(COL_CATEGORY, false);
        sorter.setSortable(COL_ACTION, false);
        sorter.setSortable(COL_KEY, false);
        sorter.setSortable(COL_DEFAULT, false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(COL_CATEGORY).setPreferredWidth(110);
        table.getColumnModel().getColumn(COL_ACTION).setPreferredWidth(240);
        table.getColumnModel().getColumn(COL_KEY).setPreferredWidth(120);
        table.getColumnModel().getColumn(COL_DEFAULT).setPreferredWidth(120);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                           int row, int col) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, col);
                var action = model.actionAt(t.convertRowIndexToModel(row));
                boolean changed = draft.isOverridden(action);
                setFont(getFont().deriveFont(col == COL_KEY && changed ? Font.BOLD : Font.PLAIN));
                if (col == COL_DEFAULT && !selected) {
                    setForeground(UIManager.getColor("Label.disabledForeground"));
                }
                setToolTipText(col == COL_KEY && changed ? I18n.t("shortcut.changedTip") : null);
                return this;
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) {
                    changeSelected();
                }
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "change");
        table.getActionMap().put("change", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                changeSelected();
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());

        search.putClientProperty("JTextField.placeholderText", I18n.t("shortcut.search"));
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }
        });

        var hint = new JLabel("<html><div style='width:520px'>" + I18n.t("shortcut.hint") + "</div></html>");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        var top = new JPanel(new BorderLayout(0, 6));
        top.add(search, BorderLayout.NORTH);
        top.add(hint, BorderLayout.CENTER);

        change.addActionListener(e -> changeSelected());
        clear.addActionListener(e -> selected().ifPresent(a -> assign(a, null)));
        reset.addActionListener(e -> selected().ifPresent(a -> assign(a, draft.defaultOf(a))));
        var resetAll = new JButton(I18n.t("shortcut.resetAll"));
        resetAll.addActionListener(e -> {
            if (!draft.overriddenActions().isEmpty()
                    && Dialogs.confirm(this, I18n.t("shortcut.title"), I18n.t("shortcut.resetAll.confirm"))) {
                draft = KeyBindings.defaults(draft.mac());
                model.fireTableDataChanged();
                updateButtons();
            }
        });
        var rowButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rowButtons.add(change);
        rowButtons.add(clear);
        rowButtons.add(reset);
        rowButtons.add(resetAll);

        var cancel = new JButton(I18n.t("shortcut.cancel"));
        cancel.addActionListener(e -> cancel());
        var ok = new JButton(I18n.t("shortcut.save"));
        ok.addActionListener(e -> {
            if (!draft.equals(saved)) {
                save.accept(draft.overrides());
            }
            dispose();
        });
        var dialogButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        dialogButtons.add(cancel);
        dialogButtons.add(ok);
        var bottom = new JPanel(new BorderLayout());
        bottom.add(rowButtons, BorderLayout.WEST);
        bottom.add(dialogButtons, BorderLayout.EAST);

        var scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(640, 420));
        var content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(top, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> cancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancel();
            }
        });
        updateButtons();
        pack();
        setLocationRelativeTo(owner);
    }

    private void cancel() {
        if (!draft.equals(saved) && !Dialogs.confirm(this, I18n.t("shortcut.title"), I18n.t("shortcut.discard"))) {
            return;
        }
        dispose();
    }

    private Optional<ShortcutAction> selected() {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(model.actionAt(table.convertRowIndexToModel(row)));
    }

    private void updateButtons() {
        var action = selected();
        change.setEnabled(action.isPresent());
        clear.setEnabled(action.map(a -> draft.get(a) != null).orElse(false));
        reset.setEnabled(action.map(draft::isOverridden).orElse(false));
    }

    private void filter() {
        String q = search.getText().strip().toLowerCase(Locale.ROOT);
        sorter.setRowFilter(q.isEmpty() ? null : new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends Model, ? extends Integer> entry) {
                for (int c = 0; c < entry.getValueCount(); c++) {
                    if (entry.getStringValue(c).toLowerCase(Locale.ROOT).contains(q)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void changeSelected() {
        selected().flatMap(a -> KeyCapture.capture(this, a, draft.mac())).ifPresent(ks ->
                selected().ifPresent(a -> assign(a, ks)));
    }

    /** Pasang {@code ks} ke aksi; aksi lain yang memakai kombinasi sama dikosongkan setelah konfirmasi. */
    private void assign(ShortcutAction action, KeyStroke ks) {
        List<ShortcutAction> taken = conflicts(draft, action, ks);
        if (!taken.isEmpty()) {
            String names = String.join(", ", taken.stream().map(ShortcutAction::label).toList());
            Object[] options = {I18n.t("shortcut.conflict.move"), I18n.t("shortcut.cancel")};
            int answer = JOptionPane.showOptionDialog(this,
                    I18n.t("shortcut.conflict.message", Shortcuts.text(ks, draft.mac()), names, action.label()),
                    I18n.t("shortcut.conflict.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0]);
            if (answer != 0) {
                return;
            }
        }
        draft = reassign(draft, action, ks);
        model.fireTableDataChanged();
        selectAction(action);
        updateButtons();
    }

    /** Aksi lain yang sudah memakai {@code ks}. */
    static List<ShortcutAction> conflicts(KeyBindings b, ShortcutAction action, KeyStroke ks) {
        if (ks == null) {
            return List.of();
        }
        return java.util.Arrays.stream(ShortcutAction.values())
                .filter(a -> a != action && ks.equals(b.get(a)))
                .toList();
    }

    /** {@code ks} untuk {@code action}; aksi lain yang memakainya jadi tanpa shortcut. */
    static KeyBindings reassign(KeyBindings b, ShortcutAction action, KeyStroke ks) {
        for (var other : conflicts(b, action, ks)) {
            b = b.with(other, null);
        }
        return b.with(action, ks);
    }

    private void selectAction(ShortcutAction action) {
        int view = table.convertRowIndexToView(action.ordinal());
        if (view >= 0) {
            table.setRowSelectionInterval(view, view);
            table.scrollRectToVisible(table.getCellRect(view, 0, true));
        }
    }

    private String keyText(KeyStroke ks) {
        return ks == null ? "—" : Shortcuts.text(ks, draft.mac());
    }

    private final class Model extends AbstractTableModel {
        private final ShortcutAction[] actions = ShortcutAction.values();

        ShortcutAction actionAt(int row) {
            return actions[row];
        }

        @Override
        public int getRowCount() {
            return actions.length;
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int col) {
            return I18n.t(switch (col) {
                case COL_CATEGORY -> "shortcut.col.category";
                case COL_ACTION -> "shortcut.col.action";
                case COL_KEY -> "shortcut.col.key";
                default -> "shortcut.col.default";
            });
        }

        @Override
        public Object getValueAt(int row, int col) {
            var a = actions[row];
            return switch (col) {
                case COL_CATEGORY -> a.category().label();
                case COL_ACTION -> a.label();
                case COL_KEY -> keyText(draft.get(a));
                default -> keyText(draft.defaultOf(a));
            };
        }
    }

    /**
     * Popup "tekan kombinasi baru": menangkap tombol lewat {@link KeyEventDispatcher} (sebelum menu dan traversal
     * fokus, supaya Tab dan ⌘/Ctrl+huruf ikut terekam). Esc batal; Enter memakai kombinasi yang terekam.
     */
    static final class KeyCapture extends JDialog {
        private final ShortcutAction action;
        private final boolean mac;
        private final JLabel keys = new JLabel(I18n.t("shortcut.capture.waiting"), SwingConstants.CENTER);
        private final JLabel verdict = new JLabel(" ");
        private final JButton use = new JButton(I18n.t("shortcut.capture.use"));
        private KeyStroke captured;
        private KeyStroke result;

        private KeyCapture(Window owner, ShortcutAction action, boolean mac) {
            super(owner, I18n.t("shortcut.capture.title"), ModalityType.APPLICATION_MODAL);
            this.action = action;
            this.mac = mac;
            keys.setFont(keys.getFont().deriveFont(Font.BOLD, keys.getFont().getSize2D() * 1.8f));
            keys.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
            var prompt = new JLabel(I18n.t("shortcut.capture.prompt", action.label()));
            verdict.setPreferredSize(new Dimension(380, verdict.getPreferredSize().height * 3));
            verdict.setVerticalAlignment(SwingConstants.TOP);
            use.setEnabled(false);
            use.addActionListener(e -> accept());
            var cancel = new JButton(I18n.t("shortcut.cancel"));
            cancel.addActionListener(e -> dispose());
            var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            buttons.add(cancel);
            buttons.add(use);
            var south = new JPanel(new BorderLayout(0, 8));
            south.add(verdict, BorderLayout.CENTER);
            south.add(buttons, BorderLayout.SOUTH);
            var content = new JPanel(new BorderLayout(0, 6));
            content.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            content.add(prompt, BorderLayout.NORTH);
            content.add(keys, BorderLayout.CENTER);
            content.add(south, BorderLayout.SOUTH);
            setContentPane(content);
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            pack();
            setLocationRelativeTo(owner);
        }

        static Optional<KeyStroke> capture(Window owner, ShortcutAction action, boolean mac) {
            var dialog = new KeyCapture(owner, action, mac);
            KeyEventDispatcher dispatcher = e -> dialog.isActive() && dialog.onKey(e);
            var focus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            focus.addKeyEventDispatcher(dispatcher);
            try {
                dialog.setVisible(true);
            } finally {
                focus.removeKeyEventDispatcher(dispatcher);
            }
            return Optional.ofNullable(dialog.result);
        }

        /** @return true = event dimakan (tidak sampai ke tombol/menu) */
        private boolean onKey(KeyEvent e) {
            if (e.getID() != KeyEvent.KEY_PRESSED) {
                return true;
            }
            int code = e.getKeyCode();
            if (e.getModifiersEx() == 0 && code == KeyEvent.VK_ESCAPE) {
                dispose();
                return true;
            }
            if (e.getModifiersEx() == 0 && code == KeyEvent.VK_ENTER) {
                accept();
                return true;
            }
            if (ShortcutRules.isModifierOnly(code)) {
                return true;
            }
            captured = KeyStroke.getKeyStroke(code, e.getModifiersEx());
            keys.setText(Shortcuts.text(captured, mac));
            var v = ShortcutRules.check(action, captured, mac);
            verdict.setText(v.message().isEmpty() ? " " : "<html>" + v.message() + "</html>");
            verdict.setForeground(UIManager.getColor(switch (v.level()) {
                case ERROR -> "Component.error.focusedBorderColor";
                case WARNING -> "Component.warning.focusedBorderColor";
                case OK -> "Label.foreground";
            }));
            use.setEnabled(v.allowed());
            return true;
        }

        private void accept() {
            if (captured != null && ShortcutRules.check(action, captured, mac).allowed()) {
                result = captured;
                dispose();
            }
        }
    }
}

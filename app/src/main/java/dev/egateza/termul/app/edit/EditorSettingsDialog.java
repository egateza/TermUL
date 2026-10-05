package dev.egateza.termul.app.edit;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.core.Os;
import dev.egateza.termul.core.config.EditorConfig;
import dev.egateza.termul.core.config.EditorConfig.NamedEditor;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

/**
 * Dialog daftar editor lokal: berurutan, editor untuk sebuah file adalah yang pertama dengan mask cocok
 * (urutan diatur dengan Naik/Turun). Editor yang sama juga tampil di klik kanan → "Edit dengan".
 */
public final class EditorSettingsDialog {

    private EditorSettingsDialog() {
    }

    /** @return konfigurasi baru, atau kosong kalau dibatalkan */
    public static Optional<EditorConfig> show(Component parent, EditorConfig current) {
        var editors = new ArrayList<>(current.editors().isEmpty() ? fromLegacy(current) : current.editors());
        var model = new EditorTableModel(editors);
        var table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(330);

        var add = new JButton(I18n.t("edit.settings.add"));
        var edit = new JButton(I18n.t("edit.settings.change"));
        var up = new JButton(I18n.t("edit.settings.up"));
        var remove = new JButton(I18n.t("edit.settings.remove"));
        var down = new JButton(I18n.t("edit.settings.down"));
        Runnable refreshButtons = () -> {
            int row = table.getSelectedRow();
            edit.setEnabled(row >= 0);
            remove.setEnabled(row >= 0);
            up.setEnabled(row > 0);
            down.setEnabled(row >= 0 && row < editors.size() - 1);
        };
        table.getSelectionModel().addListSelectionListener(e -> refreshButtons.run());

        Runnable doEdit = () -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                editOne(table, editors, editors.get(row)).ifPresent(updated -> {
                    editors.set(row, updated);
                    model.fireTableRowsUpdated(row, row);
                });
            }
        };
        add.addActionListener(e -> editOne(table, editors, null).ifPresent(added -> {
            editors.add(added);
            model.fireTableRowsInserted(editors.size() - 1, editors.size() - 1);
            table.setRowSelectionInterval(editors.size() - 1, editors.size() - 1);
        }));
        edit.addActionListener(e -> doEdit.run());
        remove.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                editors.remove(row);
                model.fireTableRowsDeleted(row, row);
                if (!editors.isEmpty()) {
                    int next = Math.min(row, editors.size() - 1);
                    table.setRowSelectionInterval(next, next);
                }
                refreshButtons.run();
            }
        });
        up.addActionListener(e -> move(table, model, editors, -1));
        down.addActionListener(e -> move(table, model, editors, 1));
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    doEdit.run();
                }
            }
        });
        refreshButtons.run();

        var buttons = new JPanel(new java.awt.GridLayout(0, 1, 0, 6));
        for (var b : new JButton[] {add, edit, remove, up, down}) {
            buttons.add(b);
        }
        var side = new JPanel(new BorderLayout());
        side.add(buttons, BorderLayout.NORTH);

        var body = new JPanel(new BorderLayout(8, 6));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        body.add(new JLabel(I18n.t("edit.settings.intro")),
                BorderLayout.NORTH);
        var scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(560, 260));
        body.add(scroll, BorderLayout.CENTER);
        body.add(side, BorderLayout.EAST);
        body.add(new JLabel(I18n.t("edit.settings.waitHint")), BorderLayout.SOUTH);

        var window = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        var dialog = new JDialog(window == null && parent instanceof java.awt.Window w ? w : window,
                I18n.t("edit.settings.title"), java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        var result = new EditorConfig[1];
        var ok = new JButton(I18n.t("edit.settings.ok"));
        var cancel = new JButton(I18n.t("edit.settings.cancel"));
        ok.addActionListener(e -> {
            result[0] = new EditorConfig(current.defaultCommand(), java.util.Map.of(), editors);
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());
        var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(ok);
        footer.add(cancel);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.add(body, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return Optional.ofNullable(result[0]);
    }

    /** Pengaturan lama (default + per ekstensi) dijadikan daftar editor: ekstensi dulu, default terakhir. */
    static List<NamedEditor> fromLegacy(EditorConfig legacy) {
        var list = new ArrayList<NamedEditor>();
        legacy.byExtension().forEach((ext, cmd) -> list.add(new NamedEditor(I18n.t("edit.settings.legacyName", ext), "*." + ext, cmd)));
        list.add(new NamedEditor(I18n.t("edit.settings.legacyDefault"), NamedEditor.ALL, legacy.defaultCommand()));
        return list;
    }

    private static void move(JTable table, EditorTableModel model, List<NamedEditor> editors, int delta) {
        int row = table.getSelectedRow();
        int target = row + delta;
        if (row < 0 || target < 0 || target >= editors.size()) {
            return;
        }
        java.util.Collections.swap(editors, row, target);
        model.fireTableRowsUpdated(Math.min(row, target), Math.max(row, target));
        table.setRowSelectionInterval(target, target);
    }

    /**
     * Command untuk program yang dipilih dari file chooser. Bundle {@code .app} macOS dibuka lewat {@code open -a}
     * (langsung kembali, sesi edit tetap dipantau); selain itu program dijalankan langsung.
     */
    static String programCommand(String path) {
        String quoted = "\"" + path + "\" " + EditorConfig.FILE_PLACEHOLDER;
        return path.endsWith(".app") || path.endsWith(".app/") ? "open -a " + quoted : quoted;
    }

    /** Form tambah/ubah satu editor (seksi Editor + Pemilihan otomatis). Diulang sampai valid atau dibatalkan. */
    private static Optional<NamedEditor> editOne(Component parent, List<NamedEditor> all, NamedEditor existing) {
        var name = new JTextField(existing == null ? "" : existing.name());
        var command = new JTextField(existing == null ? "" : existing.command(), 34);
        var mask = new JTextField(existing == null ? NamedEditor.ALL : existing.mask());
        var browse = new JButton(I18n.t("edit.form.browse"));
        boolean mac = Os.current().isMac();
        browse.addActionListener(e -> {
            var chooser = new JFileChooser();
            chooser.setDialogTitle(I18n.t("edit.form.chooseTitle"));
            if (mac) {
                chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES); // bundle .app adalah folder
            } else {
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        I18n.t("edit.form.programFilter"), "exe", "cmd", "bat"));
            }
            if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                command.setText(programCommand(chooser.getSelectedFile().getAbsolutePath()));
                if (name.getText().isBlank()) {
                    String file = chooser.getSelectedFile().getName();
                    int dot = file.lastIndexOf('.');
                    name.setText(dot > 0 ? file.substring(0, dot) : file);
                }
            }
        });
        var notepad = new JButton(I18n.t(mac ? "edit.form.textEdit" : "edit.form.notepad"));
        notepad.addActionListener(e -> {
            // TextEdit lewat "open -e": langsung kembali (tanpa menunggu), sesi edit tetap dipantau
            command.setText((mac ? "open -e " : "notepad ") + EditorConfig.FILE_PLACEHOLDER);
            if (name.getText().isBlank()) {
                name.setText(mac ? "TextEdit" : "Notepad");
            }
        });

        var editorSection = section(I18n.t("edit.form.section.editor"));
        row(editorSection, 0, I18n.t("edit.form.name"), name, null);
        row(editorSection, 1, I18n.t("edit.form.program"), command, browse);
        var g = new GridBagConstraints();
        g.gridx = 1;
        g.gridy = 2;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(6, 3, 3, 3);
        editorSection.add(notepad, g);
        g.gridy = 3;
        g.insets = new Insets(0, 3, 3, 3);
        editorSection.add(new JLabel(I18n.t("edit.form.commandHint")), g);

        var autoSection = section(I18n.t("edit.form.section.auto"));
        var hint = new JLabel(I18n.t("edit.form.maskLabel"));
        var ag = new GridBagConstraints();
        ag.gridx = 0;
        ag.gridy = 0;
        ag.anchor = GridBagConstraints.WEST;
        ag.insets = new Insets(3, 3, 3, 3);
        autoSection.add(hint, ag);
        ag.gridy = 1;
        ag.fill = GridBagConstraints.HORIZONTAL;
        ag.weightx = 1;
        autoSection.add(mask, ag);
        ag.gridy = 2;
        autoSection.add(new JLabel(I18n.t("edit.form.maskHint")), ag);

        var form = new JPanel(new BorderLayout(0, 8));
        form.add(editorSection, BorderLayout.NORTH);
        form.add(autoSection, BorderLayout.CENTER);
        form.setPreferredSize(new Dimension(520, form.getPreferredSize().height));

        while (true) {
            int choice = JOptionPane.showConfirmDialog(parent, form,
                    existing == null ? I18n.t("edit.form.addTitle") : I18n.t("edit.form.editTitle"), JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return Optional.empty();
            }
            try {
                var editor = new NamedEditor(name.getText(), mask.getText(), command.getText());
                boolean taken = all.stream().anyMatch(o -> o != existing && o.name().equalsIgnoreCase(editor.name()));
                if (taken) {
                    Dialogs.error(parent, I18n.t("edit.settings.title"), I18n.t("edit.form.nameTaken", editor.name()));
                    continue;
                }
                return Optional.of(editor);
            } catch (IllegalArgumentException e) {
                Dialogs.error(parent, I18n.t("edit.settings.title"), e.getMessage());
            }
        }
    }

    private static JPanel section(String title) {
        var panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    /** Baris form: label, field melebar, dan tombol opsional di kanan. */
    private static void row(JPanel panel, int y, String label, JTextField field, JButton button) {
        var c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.gridy = y;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        panel.add(field, c);
        if (button != null) {
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            panel.add(button, c);
        }
    }

    private static final class EditorTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"edit.settings.col.editor", "edit.settings.col.mask", "edit.settings.col.command"};
        private final List<NamedEditor> editors;

        EditorTableModel(List<NamedEditor> editors) {
            this.editors = editors;
        }

        @Override
        public int getRowCount() {
            return editors.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return I18n.t(COLUMNS[column]);
        }

        @Override
        public Object getValueAt(int row, int column) {
            var e = editors.get(row);
            return switch (column) {
                case 0 -> e.name();
                case 1 -> e.mask();
                default -> e.command();
            };
        }
    }
}

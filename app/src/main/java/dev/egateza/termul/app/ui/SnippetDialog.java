package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.snippet.Snippet;
import dev.egateza.termul.core.snippet.SnippetStore;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * Terminal → Snippet: tambah, ubah, dan hapus snippet. Snippet berlaku untuk semua host atau satu grup host (beserta
 * subgrupnya), dan selalu diketik ke prompt tanpa Enter. Simpan/hapus berjalan di executor I/O; UI hanya di EDT.
 */
public final class SnippetDialog extends JDialog {

    private final SnippetStore store;
    private final Executor io;
    private final DefaultListModel<Snippet> model = new DefaultListModel<>();
    private final JList<Snippet> list = new JList<>(model);
    private final JTextField name = new JTextField(28);
    private final JTextField command = new JTextField(28);
    private final JComboBox<String> group;
    private final JLabel error = new JLabel(" ");
    private final JButton delete = new JButton(I18n.t("snippet.delete"));
    private UUID editing; // EDT; null = snippet baru

    /**
     * @param groups       grup host yang ada (untuk pilihan "Berlaku untuk")
     * @param initialGroup grup awal untuk snippet baru (mis. grup host tab yang aktif); kosong = semua host
     */
    public SnippetDialog(Window owner, SnippetStore store, Executor io, List<String> groups, String initialGroup) {
        super(owner, I18n.t("snippet.title"), ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.io = io;
        var choices = new ArrayList<String>();
        choices.add("");
        choices.addAll(groups);
        group = new JComboBox<>(new DefaultComboBoxModel<>(choices.toArray(String[]::new)));
        group.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                String g = (String) value;
                return super.getListCellRendererComponent(l, g == null || g.isEmpty() ? I18n.t("snippet.allHosts") : g,
                        index, selected, focus);
            }
        });

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                var s = (Snippet) value;
                String scope = s.isGlobal() ? I18n.t("snippet.allHosts") : s.group();
                String html = "<html><b>" + escape(s.name()) + "</b><br><span style='font-size:90%'>" + escape(scope)
                        + "</span></html>";
                var c = (JLabel) super.getListCellRendererComponent(l, html, index, selected, focus);
                c.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                c.setToolTipText(s.command());
                return c;
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                edit(list.getSelectedValue());
            }
        });
        command.setFont(new Font(Font.MONOSPACED, Font.PLAIN, name.getFont().getSize()));
        error.setForeground(UIManager.getColor("Component.error.focusedBorderColor") == null
                ? new java.awt.Color(0xE5484D) : UIManager.getColor("Component.error.focusedBorderColor"));

        var form = new JPanel(new GridBagLayout());
        var gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        addRow(form, gc, 0, I18n.t("snippet.name"), name);
        addRow(form, gc, 1, I18n.t("snippet.command"), command);
        addRow(form, gc, 2, I18n.t("snippet.scope"), group);
        var hint = new JLabel("<html><div style='width:320px'>" + escape(I18n.t("snippet.hint")) + "</div></html>");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        gc.gridx = 1;
        gc.gridy = 3;
        gc.fill = GridBagConstraints.HORIZONTAL;
        form.add(hint, gc);
        gc.gridy = 4;
        form.add(error, gc);
        gc.gridy = 5;
        gc.weighty = 1;
        form.add(new JPanel(), gc);

        var listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(240, 300));
        var newButton = new JButton(I18n.t("snippet.new"));
        newButton.addActionListener(e -> startNew(initialGroup));
        delete.addActionListener(e -> deleteSelected());
        var save = new JButton(I18n.t("snippet.save"));
        save.addActionListener(e -> save());
        var close = new JButton(I18n.t("snippet.close"));
        close.addActionListener(e -> dispose());
        var left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(newButton);
        left.add(javax.swing.Box.createHorizontalStrut(6));
        left.add(delete);
        var right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(save);
        right.add(close);
        var buttons = new JPanel(new BorderLayout());
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);

        var content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(listScroll, BorderLayout.WEST);
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(save);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        reload(null);
        if (model.isEmpty()) {
            startNew(initialGroup);
        } else {
            list.setSelectedIndex(0);
        }
        pack();
        setLocationRelativeTo(owner);
    }

    private static void addRow(JPanel form, GridBagConstraints gc, int row, String label, Component field) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, gc);
    }

    private void reload(UUID select) {
        model.clear();
        store.snapshot().forEach(model::addElement);
        if (select != null) {
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).id().equals(select)) {
                    list.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void edit(Snippet s) {
        editing = s.id();
        name.setText(s.name());
        command.setText(s.command());
        group.setSelectedItem(s.group());
        if (group.getSelectedItem() == null || !s.group().equals(group.getSelectedItem())) {
            group.addItem(s.group()); // grup sudah tidak ada di daftar host: tetap bisa disimpan apa adanya
            group.setSelectedItem(s.group());
        }
        delete.setEnabled(true);
        error.setText(" ");
    }

    private void startNew(String initialGroup) {
        editing = null;
        list.clearSelection();
        name.setText("");
        command.setText("");
        group.setSelectedItem(initialGroup == null ? "" : initialGroup);
        delete.setEnabled(false);
        error.setText(" ");
        name.requestFocusInWindow();
    }

    private void save() {
        Snippet snippet;
        try {
            String g = (String) group.getSelectedItem();
            snippet = editing == null ? Snippet.create(name.getText(), command.getText(), g)
                    : new Snippet(editing, name.getText(), command.getText(), g);
        } catch (IllegalArgumentException e) {
            error.setText(e.getMessage());
            return;
        }
        UiAsync.run(io, () -> store.save(snippet), err -> error.setText(I18n.t("snippet.saveFailed", err.getMessage())))
                .thenRun(() -> javax.swing.SwingUtilities.invokeLater(() -> {
                    reload(snippet.id());
                    error.setText(" ");
                }));
    }

    private void deleteSelected() {
        Snippet s = list.getSelectedValue();
        if (s == null || !Dialogs.confirm(this, I18n.t("snippet.title"), I18n.t("snippet.deleteConfirm", s.name()))) {
            return;
        }
        UiAsync.run(io, () -> store.delete(s.id()), err -> error.setText(I18n.t("snippet.saveFailed", err.getMessage())))
                .thenRun(() -> javax.swing.SwingUtilities.invokeLater(() -> {
                    reload(null);
                    startNew("");
                }));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

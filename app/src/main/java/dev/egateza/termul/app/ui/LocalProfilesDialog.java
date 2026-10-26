package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.terminal.LocalProfiles;
import dev.egateza.termul.core.localshell.CommandLine;
import dev.egateza.termul.core.localshell.LocalProfile;
import dev.egateza.termul.core.localshell.LocalProfileStore;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * Pengaturan → Profil terminal lokal (ADR 0005 tahap 2): shell yang terdeteksi (hanya-baca; duplikat untuk mengubah)
 * dan profil buatan user (perintah, argumen, direktori awal, environment, warna terminal), plus pilihan default.
 * Simpan/hapus berjalan di executor I/O; UI hanya di EDT. Profil bukan tempat secret.
 */
public final class LocalProfilesDialog extends JDialog {

    private final LocalProfileStore store;
    private final Supplier<List<LocalProfiles.Choice>> choices;
    private final Executor io;
    private final DefaultListModel<LocalProfiles.Choice> model = new DefaultListModel<>();
    private final JList<LocalProfiles.Choice> list = new JList<>(model);
    private final JTextField name = new JTextField(30);
    private final JTextField executable = new JTextField(30);
    private final JTextField arguments = new JTextField(30);
    private final JTextField directory = new JTextField(30);
    private final JTextArea environment = new JTextArea(5, 30);
    private final JComboBox<HostTerminalColors.Choice> colors;
    private final JLabel error = new JLabel(" ");
    private final JLabel readOnlyNote = new JLabel(" ");
    private final JButton duplicate = new JButton(I18n.t("localProfiles.duplicate"));
    private final JButton delete = new JButton(I18n.t("localProfiles.delete"));
    private final JButton makeDefault = new JButton(I18n.t("localProfiles.makeDefault"));
    private final JButton toggleDisabled = new JButton(I18n.t("localProfiles.disable"));
    private final JButton save = new JButton(I18n.t("localProfiles.save"));
    private final List<JComponent> editable = new ArrayList<>();
    private UUID editing; // EDT; null = profil baru (belum disimpan)

    /**
     * @param choices pilihan terkini (shell terdeteksi + profil, default dulu), dibaca ulang setelah setiap perubahan
     * @param colors  pilihan warna terminal (sama dengan dialog host)
     */
    public LocalProfilesDialog(Window owner, LocalProfileStore store, Supplier<List<LocalProfiles.Choice>> choices,
                               List<HostTerminalColors.Choice> colors, Executor io) {
        super(owner, I18n.t("localProfiles.title"), ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.choices = choices;
        this.io = io;
        this.colors = new JComboBox<>(colors.toArray(HostTerminalColors.Choice[]::new));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                var c = (LocalProfiles.Choice) value;
                var tags = new ArrayList<String>();
                if (c.isDefault()) {
                    tags.add(I18n.t("tree.local.default"));
                }
                if (c.detected()) {
                    tags.add(I18n.t("localProfiles.detected"));
                }
                boolean off = isDisabled(c);
                if (off) {
                    tags.add(I18n.t("localProfiles.disabled"));
                }
                String html = "<html>" + (off ? "<s>" : "<b>") + escape(c.name()) + (off ? "</s>" : "</b>")
                        + (tags.isEmpty() ? "" : " <font color='gray'>· " + escape(String.join(" · ", tags)) + "</font>")
                        + "<br><span style='font-size:90%'>" + escape(c.commandLine()) + "</span></html>";
                var label = (JLabel) super.getListCellRendererComponent(l, html, index, selected, focus);
                label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                if (off && !selected) {
                    label.setForeground(UIManager.getColor("Label.disabledForeground"));
                }
                return label;
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                show(list.getSelectedValue());
            }
        });
        var mono = new Font(Font.MONOSPACED, Font.PLAIN, name.getFont().getSize());
        executable.setFont(mono);
        arguments.setFont(mono);
        environment.setFont(mono);
        error.setForeground(UIManager.getColor("Component.error.focusedBorderColor") == null
                ? new java.awt.Color(0xE5484D) : UIManager.getColor("Component.error.focusedBorderColor"));
        readOnlyNote.setForeground(UIManager.getColor("Label.disabledForeground"));

        var browseExe = new JButton(I18n.t("localProfiles.browse"));
        browseExe.addActionListener(e -> browse(executable, false));
        var browseDir = new JButton(I18n.t("localProfiles.browse"));
        browseDir.addActionListener(e -> browse(directory, true));
        editable.addAll(List.of(name, executable, arguments, directory, environment, this.colors, browseExe, browseDir));

        var form = new JPanel(new GridBagLayout());
        var gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        int row = 0;
        addRow(form, gc, row++, I18n.t("localProfiles.name"), name);
        addRow(form, gc, row++, I18n.t("localProfiles.executable"), withButton(executable, browseExe));
        addRow(form, gc, row++, I18n.t("localProfiles.arguments"), arguments);
        addRow(form, gc, row++, I18n.t("localProfiles.directory"), withButton(directory, browseDir));
        addRow(form, gc, row++, I18n.t("localProfiles.environment"), new JScrollPane(environment));
        addRow(form, gc, row++, I18n.t("localProfiles.colors"), this.colors);
        var hint = new JLabel("<html><div style='width:340px'>" + escape(I18n.t("localProfiles.hint")) + "</div></html>");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        gc.gridx = 1;
        gc.gridy = row++;
        gc.fill = GridBagConstraints.HORIZONTAL;
        form.add(hint, gc);
        gc.gridy = row++;
        form.add(readOnlyNote, gc);
        gc.gridy = row++;
        form.add(error, gc);
        gc.gridy = row;
        gc.weighty = 1;
        form.add(new JPanel(), gc);

        var listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(260, 360));
        var newButton = new JButton(I18n.t("localProfiles.new"));
        newButton.addActionListener(e -> startNew());
        duplicate.addActionListener(e -> duplicateSelected());
        delete.addActionListener(e -> deleteSelected());
        makeDefault.addActionListener(e -> makeDefaultSelected());
        toggleDisabled.addActionListener(e -> toggleDisabledSelected());
        toggleDisabled.setToolTipText(I18n.t("localProfiles.disable.tooltip"));
        save.addActionListener(e -> save());
        var close = new JButton(I18n.t("localProfiles.close"));
        close.addActionListener(e -> dispose());
        var left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(newButton);
        left.add(duplicate);
        left.add(delete);
        left.add(makeDefault);
        left.add(toggleDisabled);
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
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        reload(null);
        list.setSelectedIndex(model.isEmpty() ? -1 : 0);
        pack();
        setLocationRelativeTo(owner);
    }

    private static JPanel withButton(JTextField field, JButton button) {
        var p = new JPanel(new BorderLayout(6, 0));
        p.add(field, BorderLayout.CENTER);
        p.add(button, BorderLayout.EAST);
        return p;
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
        choices.get().forEach(model::addElement);
        if (select != null) {
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).id().equals(select)) {
                    list.setSelectedIndex(i);
                    return;
                }
            }
        }
    }

    /** Tampilkan pilihan: profil buatan user bisa diedit, shell terdeteksi hanya-baca. */
    private void show(LocalProfiles.Choice c) {
        editing = c.detected() ? null : c.id();
        name.setText(c.name());
        executable.setText(c.command().getFirst());
        arguments.setText(CommandLine.join(c.command().subList(1, c.command().size())));
        directory.setText(c.directory());
        environment.setText(formatEnv(c.environment()));
        selectColor(c.terminalTheme());
        setEditable(!c.detected());
        readOnlyNote.setText(c.detected() ? I18n.t("localProfiles.detectedNote") : " ");
        duplicate.setEnabled(true);
        delete.setEnabled(!c.detected());
        boolean off = isDisabled(c);
        makeDefault.setEnabled(!c.isDefault() && !off);
        toggleDisabled.setEnabled(true);
        toggleDisabled.setText(I18n.t(off ? "localProfiles.enable" : "localProfiles.disable"));
        error.setText(" ");
    }

    private boolean isDisabled(LocalProfiles.Choice c) {
        return store.snapshot().isDisabled(c.id());
    }

    private void startNew() {
        list.clearSelection();
        editing = null;
        name.setText("");
        executable.setText("");
        arguments.setText("");
        directory.setText("");
        environment.setText("");
        selectColor(null);
        setEditable(true);
        readOnlyNote.setText(" ");
        duplicate.setEnabled(false);
        delete.setEnabled(false);
        makeDefault.setEnabled(false);
        toggleDisabled.setEnabled(false);
        error.setText(" ");
        name.requestFocusInWindow();
    }

    /** Salinan pilihan sebagai profil baru yang bisa diedit (cara mengubah shell terdeteksi). */
    private void duplicateSelected() {
        LocalProfiles.Choice c = list.getSelectedValue();
        if (c == null) {
            return;
        }
        show(c);
        list.clearSelection();
        editing = null;
        name.setText(I18n.t("localProfiles.copyName", c.name()));
        setEditable(true);
        readOnlyNote.setText(" ");
        duplicate.setEnabled(false);
        delete.setEnabled(false);
        makeDefault.setEnabled(false);
        toggleDisabled.setEnabled(false);
        name.requestFocusInWindow();
        name.selectAll();
    }

    private void setEditable(boolean on) {
        editable.forEach(c -> c.setEnabled(on));
        save.setEnabled(on);
        if (on) {
            getRootPane().setDefaultButton(save);
        } else {
            getRootPane().setDefaultButton(null);
        }
    }

    private void save() {
        LocalProfile profile;
        try {
            var command = new ArrayList<String>();
            command.add(executable.getText().strip());
            command.addAll(CommandLine.split(arguments.getText()));
            var color = (HostTerminalColors.Choice) colors.getSelectedItem();
            profile = new LocalProfile(editing == null ? UUID.randomUUID() : editing, name.getText(), command,
                    directory.getText(), parseEnv(environment.getText()), color == null ? null : color.id());
        } catch (IllegalArgumentException e) {
            error.setText(e.getMessage());
            return;
        }
        UiAsync.run(io, () -> store.save(profile),
                        err -> error.setText(I18n.t("localProfiles.saveFailed", err.getMessage())))
                .thenRun(() -> SwingUtilities.invokeLater(() -> {
                    reload(profile.id());
                    error.setText(" ");
                }));
    }

    private void deleteSelected() {
        LocalProfiles.Choice c = list.getSelectedValue();
        if (c == null || c.detected()
                || !Dialogs.confirm(this, I18n.t("localProfiles.title"), I18n.t("localProfiles.deleteConfirm", c.name()))) {
            return;
        }
        UiAsync.run(io, () -> store.delete(c.id()),
                        err -> error.setText(I18n.t("localProfiles.saveFailed", err.getMessage())))
                .thenRun(() -> SwingUtilities.invokeLater(() -> {
                    reload(null);
                    list.setSelectedIndex(model.isEmpty() ? -1 : 0);
                }));
    }

    private void makeDefaultSelected() {
        LocalProfiles.Choice c = list.getSelectedValue();
        if (c == null) {
            return;
        }
        UiAsync.run(io, () -> store.setDefault(c.id()),
                        err -> error.setText(I18n.t("localProfiles.saveFailed", err.getMessage())))
                .thenRun(() -> SwingUtilities.invokeLater(() -> reload(c.id())));
    }

    /** Nonaktifkan/aktifkan pilihan terpilih; minimal satu pilihan harus tetap aktif. */
    private void toggleDisabledSelected() {
        LocalProfiles.Choice c = list.getSelectedValue();
        if (c == null) {
            return;
        }
        boolean disable = !isDisabled(c);
        if (disable && !canDisable(c, choices.get(), store.snapshot())) {
            error.setText(I18n.t("localProfiles.lastEnabled"));
            return;
        }
        UiAsync.run(io, () -> store.setDisabled(c.id(), disable),
                        err -> error.setText(I18n.t("localProfiles.saveFailed", err.getMessage())))
                .thenRun(() -> SwingUtilities.invokeLater(() -> reload(c.id())));
    }

    /** @return false kalau {@code c} satu-satunya pilihan yang masih aktif */
    static boolean canDisable(LocalProfiles.Choice c, List<LocalProfiles.Choice> all, LocalProfileStore.State state) {
        return all.stream().anyMatch(o -> !o.id().equals(c.id()) && !state.isDisabled(o.id()));
    }

    private void selectColor(String id) {
        for (int i = 0; i < colors.getItemCount(); i++) {
            if (Objects.equals(colors.getItemAt(i).id(), id)) {
                colors.setSelectedIndex(i);
                return;
            }
        }
        colors.setSelectedIndex(0); // tema sudah dihapus: ikut pengaturan
    }

    private void browse(JTextField target, boolean directories) {
        var chooser = new JFileChooser(target.getText().isBlank() ? null : target.getText());
        chooser.setFileSelectionMode(directories ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /** Satu variabel per baris, {@code NAMA=nilai}; {@code NAMA=} menghapus variabel; baris kosong dan {@code #} dilewati. */
    static Map<String, String> parseEnv(String text) {
        var env = new LinkedHashMap<String, String>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException(I18n.t("localProfiles.envFormat", line));
            }
            env.put(line.substring(0, eq).strip(), line.substring(eq + 1));
        }
        return env;
    }

    static String formatEnv(Map<String, String> env) {
        var sb = new StringBuilder();
        env.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sb.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

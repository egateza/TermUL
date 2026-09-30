package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.terminal.TerminalSettings;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Dialog pilihan font aplikasi dan font terminal, masing-masing dengan pratinjau. Pilihan kosong = font bawaan. */
public final class FontSettingsDialog {

    /** @param uiFamily       font aplikasi, null = bawaan tema
     *  @param terminalFamily font terminal, null = otomatis */
    public record Choice(String uiFamily, String terminalFamily) {
    }

    private FontSettingsDialog() {
    }

    /** Item combo; {@code family} null = bawaan. */
    private record Entry(String family, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** @return pilihan baru, atau kosong kalau dibatalkan */
    public static Optional<Choice> show(Component parent, String currentUi, String currentTerminal) {
        var uiBox = combo(I18n.t("font.default.ui"), FontCatalog.readable(), currentUi);
        var terminalBox = combo(I18n.t("font.default.terminal", TerminalSettings.automaticFamily()),
                FontCatalog.monospaced(), currentTerminal);

        var uiPreview = preview(I18n.t("font.preview.ui"));
        var terminalPreview = preview("user@server:~$ ls -la  0O 1lI {}[]");
        Runnable refreshPreview = () -> {
            var ui = (Entry) uiBox.getSelectedItem();
            var terminal = (Entry) terminalBox.getSelectedItem();
            uiPreview.setFont(ui.family() == null ? new JLabel().getFont() : new Font(ui.family(), Font.PLAIN, 14));
            terminalPreview.setFont(new Font(terminal.family() == null
                    ? TerminalSettings.automaticFamily() : terminal.family(), Font.PLAIN, 14));
        };
        uiBox.addActionListener(e -> refreshPreview.run());
        terminalBox.addActionListener(e -> refreshPreview.run());
        refreshPreview.run();

        var form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        addRow(form, 0, I18n.t("font.ui"), uiBox, uiPreview);
        addRow(form, 2, I18n.t("font.terminal"), terminalBox, terminalPreview);
        var hint = new JLabel(I18n.t("font.hint"));
        var g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 4;
        g.gridwidth = 2;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(10, 3, 3, 3);
        form.add(hint, g);

        var window = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        var dialog = new JDialog(window == null && parent instanceof Window w ? w : window,
                I18n.t("font.title"), Dialog.ModalityType.APPLICATION_MODAL);
        var result = new Choice[1];
        var ok = new JButton(I18n.t("common.save"));
        var cancel = new JButton(I18n.t("common.cancel"));
        ok.addActionListener(e -> {
            result[0] = new Choice(((Entry) uiBox.getSelectedItem()).family(),
                    ((Entry) terminalBox.getSelectedItem()).family());
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());
        var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(ok);
        footer.add(cancel);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return Optional.ofNullable(result[0]);
    }

    /** Combo berisi "bawaan" lalu {@code families}; font terpilih yang sudah tidak terpasang tampil sebagai bawaan. */
    private static JComboBox<Entry> combo(String defaultLabel, java.util.List<String> families, String selected) {
        var entries = new ArrayList<Entry>();
        var defaultEntry = new Entry(null, defaultLabel);
        entries.add(defaultEntry);
        Entry current = defaultEntry;
        for (var family : families) {
            var entry = new Entry(family, family);
            entries.add(entry);
            if (family.equals(selected)) {
                current = entry;
            }
        }
        var box = new JComboBox<>(entries.toArray(Entry[]::new));
        box.setSelectedItem(current);
        box.setMaximumRowCount(16);
        return box;
    }

    private static JLabel preview(String text) {
        var label = new JLabel(text);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(javax.swing.UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        // ukuran tetap: kalau mengikuti font yang dipilih, tata letak dialog (sudah di-pack) kekurangan ruang
        // dan baris paling atas terdorong hilang
        label.setPreferredSize(new java.awt.Dimension(460, 46));
        label.setMinimumSize(new java.awt.Dimension(120, 46));
        return label;
    }

    /** Dua baris: label + combo, lalu pratinjau di bawahnya. */
    private static void addRow(JPanel form, int y, String label, JComboBox<Entry> box, JLabel preview) {
        var c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 8);
        c.gridy = y;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(3, 3, 3, 3);
        form.add(box, c);
        c.gridy = y + 1;
        c.insets = new Insets(0, 3, 10, 3);
        form.add(preview, c);
    }
}

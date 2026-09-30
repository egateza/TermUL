package dev.egateza.myterm.app.edit;

import dev.egateza.myterm.app.ui.Dialogs;
import dev.egateza.myterm.core.config.EditorConfig;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Dialog pengaturan editor lokal (default + mapping per ekstensi). */
public final class EditorSettingsDialog {

    private EditorSettingsDialog() {
    }

    public static Optional<EditorConfig> show(Component parent, EditorConfig current) {
        var defaultField = new JTextField(current.defaultCommand(), 40);
        var mapping = new JTextArea(current.mappingText(), 8, 40);
        var panel = new JPanel(new BorderLayout(0, 6));
        var top = new JPanel(new BorderLayout(0, 4));
        top.add(new JLabel("<html>Editor default (<code>{file}</code> = path file; kosong → di akhir):</html>"),
                BorderLayout.NORTH);
        top.add(defaultField, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);
        var center = new JPanel(new BorderLayout(0, 4));
        center.add(new JLabel("<html>Per ekstensi, satu baris <code>ext = command</code>, mis. "
                + "<code>sql = notepad++ {file}</code></html>"), BorderLayout.NORTH);
        center.add(new JScrollPane(mapping), BorderLayout.CENTER);
        center.add(new JLabel("<html><small>Gunakan mode \"wait\" (mis. <code>code --wait</code>) supaya sesi edit "
                + "otomatis selesai saat file ditutup di editor.</small></html>"), BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        while (true) {
            int choice = JOptionPane.showConfirmDialog(parent, panel, "Pengaturan editor",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return Optional.empty();
            }
            try {
                return Optional.of(EditorConfig.parse(defaultField.getText(), mapping.getText()));
            } catch (IllegalArgumentException e) {
                Dialogs.error(parent, "Pengaturan editor", e.getMessage());
            }
        }
    }
}

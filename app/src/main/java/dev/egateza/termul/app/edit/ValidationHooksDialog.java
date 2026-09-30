package dev.egateza.termul.app.edit;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.core.config.ValidationHooks;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Dialog hook validasi edit file root: satu baris {@code pola = command}. Command dijalankan sebagai root setelah file
 * dipasang; kalau gagal, file dikembalikan dari backup. Panggil di EDT.
 */
public final class ValidationHooksDialog {

    private ValidationHooksDialog() {
    }

    /** @return konfigurasi baru, atau kosong kalau dibatalkan */
    public static Optional<ValidationHooks> show(Component parent, ValidationHooks current) {
        var text = new JTextArea(current.text(), 12, 60);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, text.getFont().getSize()));
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel(I18n.t("edit.hooks.intro")), BorderLayout.NORTH);
        panel.add(new JScrollPane(text), BorderLayout.CENTER);
        Object[] options = {I18n.t("edit.settings.ok"), I18n.t("edit.hooks.defaults"), I18n.t("edit.settings.cancel")};
        while (true) {
            int choice = JOptionPane.showOptionDialog(parent, panel, I18n.t("edit.hooks.title"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            switch (choice) {
                case 0 -> {
                    try {
                        return Optional.of(ValidationHooks.parse(text.getText()));
                    } catch (IllegalArgumentException e) {
                        Dialogs.error(parent, I18n.t("edit.hooks.title"), e.getMessage());
                    }
                }
                case 1 -> text.setText(ValidationHooks.defaults().text());
                default -> {
                    return Optional.empty();
                }
            }
        }
    }
}

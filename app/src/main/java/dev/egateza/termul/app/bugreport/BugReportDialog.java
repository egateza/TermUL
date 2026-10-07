package dev.egateza.termul.app.bugreport;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.log.LogPanel;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.UiAsync;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dialog "Laporkan bug": user mengisi judul dan deskripsi, melihat pratinjau isi issue, lalu form issue GitHub dibuka
 * di browser dalam keadaan terisi. Login dan Submit terjadi di GitHub. EDT.
 */
public final class BugReportDialog {

    private static final Logger log = LoggerFactory.getLogger(BugReportDialog.class);

    private BugReportDialog() {
    }

    /** @param io executor untuk membuka folder log (bukan EDT) */
    public static void show(Component parent, Path logDir, Executor io) {
        var window = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        var dialog = new JDialog(window == null && parent instanceof Window w ? w : window,
                I18n.t("bugReport.title"), Dialog.ModalityType.APPLICATION_MODAL);

        var titleField = new JTextField(48);
        titleField.putClientProperty("JTextField.placeholderText", I18n.t("bugReport.field.title.placeholder"));
        var description = textArea(6);
        var steps = textArea(4);
        steps.putClientProperty("JTextField.placeholderText", I18n.t("bugReport.field.steps.placeholder"));
        var includeSystem = new JCheckBox(I18n.t("bugReport.systemInfo"), true);
        var system = SystemInfo.current();
        includeSystem.setToolTipText("<html>" + String.join("<br>", system.lines()) + "</html>");

        var preview = new JTextArea(9, 48);
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, preview.getFont().getSize()));
        var truncatedLabel = new JLabel(I18n.t("bugReport.truncated"));
        truncatedLabel.setForeground(UIManager.getColor("Component.warning.focusedBorderColor"));

        var submit = new JButton(I18n.t("bugReport.submit"));
        var report = new BugReport[1];
        Runnable refresh = () -> {
            report[0] = new BugReport(titleField.getText(), description.getText(), steps.getText(),
                    includeSystem.isSelected() ? system : null);
            var link = report[0].link();
            preview.setText(link.body());
            preview.setCaretPosition(0);
            truncatedLabel.setVisible(link.truncated());
            submit.setEnabled(report[0].isSubmittable());
        };
        DocumentListener onChange = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh.run();
            }
        };
        titleField.getDocument().addDocumentListener(onChange);
        description.getDocument().addDocumentListener(onChange);
        steps.getDocument().addDocumentListener(onChange);
        includeSystem.addActionListener(e -> refresh.run());
        refresh.run();

        var form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        var notice = new JLabel("<html><div style='width:420px'>" + I18n.t("bugReport.public") + "</div></html>");
        int row = 0;
        add(form, row++, notice, 0);
        add(form, row++, new JLabel(I18n.t("bugReport.field.title")), 8);
        add(form, row++, titleField, 0);
        add(form, row++, new JLabel(I18n.t("bugReport.field.description")), 8);
        add(form, row++, new JScrollPane(description), 0);
        add(form, row++, new JLabel(I18n.t("bugReport.field.steps")), 8);
        add(form, row++, new JScrollPane(steps), 0);
        add(form, row++, includeSystem, 6);
        add(form, row++, new JLabel(I18n.t("bugReport.preview")), 8);
        add(form, row++, new JScrollPane(preview), 0);
        add(form, row, truncatedLabel, 4);

        var openLogs = new JButton(I18n.t("bugReport.openLogDir"));
        openLogs.setToolTipText(I18n.t("bugReport.openLogDir.tooltip"));
        openLogs.addActionListener(e -> UiAsync.run(io, () -> LogPanel.openFolder(logDir),
                err -> Dialogs.error(dialog, I18n.t("main.error.openLogDir"), err)));
        submit.addActionListener(e -> {
            var uri = report[0].link().uri();
            browse(uri, dialog::dispose, () -> {
                dialog.dispose();
                Dialogs.input(parent, I18n.t("bugReport.browseFailed.title"), I18n.t("bugReport.browseFailed"),
                        uri.toString());
            });
        });
        var cancel = new JButton(I18n.t("common.cancel"));
        cancel.addActionListener(e -> dialog.dispose());

        var left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(openLogs);
        var right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(submit);
        right.add(cancel);
        var footer = new JPanel(new BorderLayout());
        footer.add(left, BorderLayout.WEST);
        footer.add(right, BorderLayout.EAST);

        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    /** Buka di browser default di virtual thread (bukan EDT); callback dijalankan di EDT. */
    private static void browse(URI uri, Runnable onOpened, Runnable onFailed) {
        Thread.ofVirtual().name("open-bug-report").start(() -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new IOException("browser tidak tersedia");
                }
                Desktop.getDesktop().browse(uri);
                SwingUtilities.invokeLater(onOpened);
            } catch (IOException | RuntimeException e) {
                log.warn("Form issue GitHub tidak bisa dibuka: {}", e.toString());
                SwingUtilities.invokeLater(onFailed);
            }
        });
    }

    private static JTextArea textArea(int rows) {
        var area = new JTextArea(rows, 48);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static void add(JPanel form, int row, JComponent c, int top) {
        var g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = row;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(top, 3, 2, 3);
        form.add(c, g);
    }
}

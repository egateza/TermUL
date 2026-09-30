package dev.egateza.termul.app.ssh;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Edt;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import java.awt.BorderLayout;
import java.awt.Component;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/** Prompt password/passphrase manual (sebelum vault ada). Secret hanya sebagai char[]. */
public final class SwingCredentialProvider implements CredentialProvider {

    private final Supplier<Component> parent;

    public SwingCredentialProvider(Supplier<Component> parent) {
        this.parent = parent;
    }

    @Override
    public char[] password(HostProfile profile, int attempt) {
        String msg = attempt > 1 ? I18n.t("ssh.password.rejected", profile.address())
                : I18n.t("ssh.password.prompt", profile.address());
        return ask(I18n.t("ssh.password.title"), msg);
    }

    @Override
    public char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt) {
        String msg = attempt > 1 ? I18n.t("ssh.passphrase.rejected", String.valueOf(keyFile.getFileName()))
                : I18n.t("ssh.passphrase.prompt", String.valueOf(keyFile.getFileName()));
        return ask(I18n.t("ssh.passphrase.title"), msg);
    }

    private char[] ask(String title, String message) {
        return askPassword(parent, title, message);
    }

    /** Dialog satu field password (dijalankan di EDT, pemanggil menunggu). @return null kalau dibatalkan */
    public static char[] askPassword(Supplier<Component> parent, String title, String message) {
        var result = new AtomicReference<char[]>();
        Edt.runAndWait(() -> {
            var field = new JPasswordField(24);
            var panel = new JPanel(new BorderLayout(0, 6));
            panel.add(new JLabel(message), BorderLayout.NORTH);
            panel.add(field, BorderLayout.CENTER);
            var pane = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
            var dialog = pane.createDialog(parent.get(), title);
            dialog.addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowGainedFocus(java.awt.event.WindowEvent e) {
                    field.requestFocusInWindow();
                }
            });
            field.addActionListener(e -> {
                pane.setValue(JOptionPane.OK_OPTION);
                dialog.dispose();
            });
            dialog.setVisible(true);
            if (Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue())) {
                result.set(field.getPassword());
            }
            field.setText("");
            dialog.dispose();
        });
        return result.get();
    }
}

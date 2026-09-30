package dev.egateza.myterm.app.ssh;

import dev.egateza.myterm.app.ui.Edt;
import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.ssh.auth.CredentialProvider;
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
        String msg = attempt > 1 ? "Password ditolak. Password untuk " + profile.address() + ":"
                : "Password untuk " + profile.address() + ":";
        return ask("Login SSH", msg);
    }

    @Override
    public char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt) {
        String msg = attempt > 1 ? "Passphrase salah. Passphrase untuk " + keyFile.getFileName() + ":"
                : "Passphrase untuk key " + keyFile.getFileName() + ":";
        return ask("Passphrase private key", msg);
    }

    private char[] ask(String title, String message) {
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

package dev.egateza.termul.app.ssh;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Edt;
import dev.egateza.termul.ssh.hostkey.HostKeyInfo;
import dev.egateza.termul.ssh.hostkey.HostKeyPrompt;
import java.awt.Component;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.swing.JOptionPane;

/** Dialog TOFU host key. Dipanggil dari thread MINA; dialog ditampilkan di EDT (blocking). */
public final class SwingHostKeyPrompt implements HostKeyPrompt {

    private final Supplier<Component> parent;

    public SwingHostKeyPrompt(Supplier<Component> parent) {
        this.parent = parent;
    }

    @Override
    public boolean confirmUnknownHost(HostKeyInfo info) {
        var accepted = new AtomicBoolean();
        Edt.runAndWait(() -> {
            String message = I18n.t("ssh.hostkey.message", info.hostLabel(), info.algorithm(), info.fingerprint());
            Object[] options = {I18n.t("ssh.hostkey.trust"), I18n.t("common.cancel")};
            int choice = JOptionPane.showOptionDialog(parent.get(), message, I18n.t("ssh.hostkey.title", info.hostLabel()),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
            accepted.set(choice == 0);
        });
        return accepted.get();
    }

    @Override
    public void hostKeyChanged(HostKeyInfo presented, List<String> knownFingerprints) {
        // Koneksi selalu ditolak; peringatan keras ditampilkan dari HostKeyRejectedException di tab terminal.
    }
}

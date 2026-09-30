package dev.egateza.myterm.app.ssh;

import dev.egateza.myterm.app.ui.Edt;
import dev.egateza.myterm.ssh.hostkey.HostKeyInfo;
import dev.egateza.myterm.ssh.hostkey.HostKeyPrompt;
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
            String message = """
                    Host %s belum pernah dikenal.

                    Tipe key:     %s
                    Fingerprint:  %s

                    Cocokkan fingerprint ini dengan server (mis. `ssh-keygen -lf /etc/ssh/ssh_host_*_key.pub`)
                    sebelum melanjutkan. Percayai host ini dan simpan ke known_hosts?"""
                    .formatted(info.hostLabel(), info.algorithm(), info.fingerprint());
            Object[] options = {"Percaya & sambungkan", "Batal"};
            int choice = JOptionPane.showOptionDialog(parent.get(), message, "Host baru: " + info.hostLabel(),
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

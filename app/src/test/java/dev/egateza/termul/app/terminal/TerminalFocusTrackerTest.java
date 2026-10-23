package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TerminalFocusTrackerTest {

    /** Panel yang berperan sebagai satu terminal (TermPane). */
    private static final class Pane extends JPanel {
    }

    private final Pane prod = new Pane();
    private final Pane dev = new Pane();
    private final Component prodTerminal = add(prod, new JLabel("prod"));
    private final Component prodSftp = add(prod, new JLabel("sftp prod"));
    private final Component devTerminal = add(dev, new JLabel("dev"));
    private final Component tree = new JLabel("tree");
    private final TerminalFocusTracker tracker =
            new TerminalFocusTracker(c -> SwingUtilities.getAncestorOfClass(Pane.class, c));

    private static Component add(JPanel pane, Component c) {
        pane.add(c);
        return c;
    }

    @Test
    void sftpTreeDanFokusKosongDiSesiYangSamaBukanPerpindahan() {
        tracker.focusOwnerChanged(prodTerminal);
        var leave = tracker.mark();

        tracker.focusOwnerChanged(null);     // terminal dicabut saat panel SFTP dibuka
        tracker.focusOwnerChanged(prodSftp); // panel SFTP host yang sama
        tracker.focusOwnerChanged(tree);
        tracker.windowLostFocus(false);      // dialog konfirmasi milik aplikasi
        tracker.focusOwnerChanged(prodTerminal);

        assertThat(tracker.mark()).isEqualTo(leave);
    }

    @Test
    void terminalLainDiantaranyaAdalahPerpindahan() {
        tracker.focusOwnerChanged(prodTerminal);
        var leave = tracker.mark();

        tracker.focusOwnerChanged(devTerminal);
        tracker.focusOwnerChanged(tree);
        tracker.focusOwnerChanged(prodTerminal);

        assertThat(tracker.mark()).isNotEqualTo(leave);
    }

    @Test
    void aplikasiLainAktifAdalahPerpindahan() {
        tracker.focusOwnerChanged(prodTerminal);
        var leave = tracker.mark();

        tracker.windowLostFocus(true);
        tracker.focusOwnerChanged(prodTerminal);

        assertThat(tracker.mark()).isNotEqualTo(leave);
    }
}

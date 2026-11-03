package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.BorderLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Seperti window utama: kolom cari host di kiri, tab terminal di kanan. */
class TermTabbedPaneTest {

    private static boolean searchFocusedWhileSwitching(JTabbedPane tabs) throws Exception {
        var frame = new JFrame[1];
        var terminals = new JTextField[2];
        var searchFocused = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new JFrame();
            var search = new JTextField(10);
            search.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    searchFocused.set(true);
                }
            });
            for (int i = 0; i < 2; i++) {
                terminals[i] = new JTextField("terminal " + i);
                var pane = new JPanel(new BorderLayout());
                pane.add(terminals[i]);
                tabs.addTab("tab " + i, pane);
            }
            // seperti MainFrame: terminal tab baru difokuskan setelah tab berganti
            tabs.addChangeListener(e -> SwingUtilities.invokeLater(
                    () -> terminals[tabs.getSelectedIndex()].requestFocusInWindow()));
            frame[0].add(search, BorderLayout.WEST);
            frame[0].add(tabs, BorderLayout.CENTER);
            frame[0].setSize(500, 200);
            frame[0].setVisible(true);
            frame[0].toFront();
        });
        try {
            waitFor(() -> frame[0].isFocused(), "window fokus");
            SwingUtilities.invokeAndWait(() -> terminals[0].requestFocusInWindow());
            waitFor(terminals[0]::isFocusOwner, "terminal 0 fokus");
            searchFocused.set(false);

            SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(1));
            waitFor(terminals[1]::isFocusOwner, "terminal 1 fokus");
            return searchFocused.get();
        } finally {
            SwingUtilities.invokeAndWait(frame[0]::dispose);
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier condition, String what) throws Exception {
        long end = System.currentTimeMillis() + 5000;
        while (true) {
            var ok = new boolean[1];
            SwingUtilities.invokeAndWait(() -> ok[0] = condition.getAsBoolean());
            if (ok[0]) {
                return;
            }
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("tidak tercapai: " + what);
            }
            Thread.sleep(20);
        }
    }

    @Test
    void pindahTabTidakMemfokuskanKolomCariHost() throws Exception {
        var tabs = new TermTabbedPane[1];
        SwingUtilities.invokeAndWait(() -> tabs[0] = new TermTabbedPane());

        assertThat(searchFocusedWhileSwitching(tabs[0])).isFalse();
    }

    /** Pembanding: JTabbedPane biasa memang memindahkan fokus ke kolom cari (bug yang dilaporkan). */
    @Test
    void jTabbedPaneBiasaMemfokuskanKolomCariHost() throws Exception {
        var tabs = new JTabbedPane[1];
        SwingUtilities.invokeAndWait(() -> tabs[0] = new JTabbedPane());

        assertThat(searchFocusedWhileSwitching(tabs[0])).isTrue();
    }
}

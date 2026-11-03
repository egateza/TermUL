package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Ctrl+F di tab terminal (lewat {@code TermPane.openFind}): kotak cari dibuka sekali, berikutnya hanya difokuskan. */
class TerminalFindTest {

    private static List<TerminalSearchBar> searchBars(Container root) {
        var found = new ArrayList<TerminalSearchBar>();
        for (Component c : root.getComponents()) {
            if (c instanceof TerminalSearchBar bar) {
                found.add(bar);
            }
            if (c instanceof Container child) {
                found.addAll(searchBars(child));
            }
        }
        return found;
    }

    @Test
    void openFindBerulangTidakMembuatKotakCariBaru() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            assertThat(searchBars(widget)).isEmpty();

            widget.openFind();
            widget.openFind(); // kotak cari sudah terbuka: hanya difokuskan lagi

            assertThat(searchBars(widget)).hasSize(1);
            assertThat(SwingUtilities.isDescendingFrom(searchBars(widget).getFirst(), widget))
                    .as("kotak cari bagian dari widget, bukan panel SFTP").isTrue();
        });
    }

    /** Batas bawah kotak cari diumumkan saat dibuka, supaya tombol Snippet di pojok yang sama bisa menyingkir. */
    @Test
    void batasBawahKotakCariDiumumkanSaatDibuka() throws Exception {
        var frame = new javax.swing.JFrame[1];
        var widget = new ZoomableTermWidget[1];
        var bottoms = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
        SwingUtilities.invokeAndWait(() -> {
            widget[0] = new ZoomableTermWidget(new TerminalSettings(14f));
            widget[0].addPropertyChangeListener(ZoomableTermWidget.FIND_BAR_BOTTOM_PROPERTY,
                    e -> bottoms.add((Integer) e.getNewValue()));
            frame[0] = new javax.swing.JFrame();
            frame[0].add(widget[0]);
            frame[0].setSize(800, 400);
            frame[0].setVisible(true);
        });
        try {
            SwingUtilities.invokeAndWait(() -> widget[0].openFind());
            SwingUtilities.invokeAndWait(() -> { }); // layout setelah revalidate
            SwingUtilities.invokeAndWait(() -> assertThat(widget[0].findBarBottom()).isPositive());
            assertThat(bottoms).anyMatch(b -> b > 0);
        } finally {
            SwingUtilities.invokeAndWait(frame[0]::dispose);
        }
    }
}

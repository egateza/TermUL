package dev.egateza.termul.app.terminal;

import com.jediterm.terminal.model.TerminalHistoryBufferListener;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.TextBufferChangesListener;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.fab.FloatingButton;
import dev.egateza.termul.app.ui.fab.FloatingOverlay;
import dev.egateza.termul.app.ui.fab.FloatingStyle;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BoundedRangeModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

/**
 * Tombol mengambang di atas satu terminal:
 * <ul>
 *   <li><b>Sambung ulang</b> (kanan bawah): hanya saat sesi berakhir atau reconnect otomatis menyerah. Labelnya
 *       menyusut selama user membaca history, supaya tidak menutupi teks.</li>
 *   <li><b>Ke bawah</b> (tengah bawah): hanya saat terminal di-scroll ke atas, dengan jumlah baris baru.</li>
 * </ul>
 * Model scroll JediTerm: {@code value = 0} berarti layar live paling bawah, negatif berarti sedang membaca history.
 * Dibuat per widget dan dilepas lewat {@link #dispose()} saat widget ditutup. EDT, kecuali listener buffer.
 */
final class TerminalFloats {

    private final ZoomableTermWidget widget;
    private final FloatingOverlay overlay;
    private final BoundedRangeModel scroll;
    private final TerminalTextBuffer buffer;
    final FloatingButton reconnect;
    final FloatingButton toBottom;
    private final NewLinesCounter counter = new NewLinesCounter();
    /** Satu pembaruan label tertunda di EDT; mencegah banjir invokeLater saat output deras. */
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private final ChangeListener scrollListener = e -> onScroll();
    private final TerminalHistoryBufferListener historyListener = this::queueRefresh;
    private final TextBufferChangesListener changesListener = new TextBufferChangesListener() {
        @Override
        public void linesDiscardedFromHistory(List<TerminalLine> lines) {
            counter.discarded(lines.size());
            queueRefresh();
        }

        @Override
        public void historyCleared() {
            counter.reset(0);
            queueRefresh();
        }
    };
    private boolean sessionEnded; // EDT

    TerminalFloats(ZoomableTermWidget widget, FloatingOverlay overlay, Runnable onReconnect) {
        this.widget = widget;
        this.overlay = overlay;
        this.scroll = widget.getTerminalPanel().getVerticalScrollModel();
        this.buffer = widget.getTerminalTextBuffer();
        reconnect = new FloatingButton(AppIcon.RECONNECT, I18n.t("fab.reconnect"), FloatingStyle.Kind.PRIMARY,
                FloatingButton.Size.NORMAL);
        reconnect.setToolTipText(I18n.t("fab.reconnect.tooltip"));
        reconnect.addActionListener(e -> onReconnect.run());
        reconnect.setVisible(false);
        toBottom = new FloatingButton(AppIcon.ARROW_DOWN, I18n.t("fab.toBottom"), FloatingStyle.Kind.SECONDARY,
                FloatingButton.Size.MINI);
        toBottom.setToolTipText(I18n.t("fab.toBottom.tooltip"));
        toBottom.addActionListener(e -> scrollToBottom());
        toBottom.setVisible(false);
        overlay.addFloating(reconnect, FloatingOverlay.Anchor.BOTTOM_RIGHT);
        overlay.addFloating(toBottom, FloatingOverlay.Anchor.BOTTOM_CENTER);
        scroll.addChangeListener(scrollListener);
        buffer.addHistoryBufferListener(historyListener);
        buffer.addChangesListener(changesListener);
    }

    /** Sesi berakhir (shell exit / reconnect menyerah) atau tersambung lagi. EDT. */
    void setSessionEnded(boolean ended) {
        if (sessionEnded == ended) {
            return;
        }
        sessionEnded = ended;
        reconnect.setVisible(ended);
        reconnect.setCollapsed(readingHistory());
        overlay.revalidate();
        overlay.repaint();
    }

    boolean readingHistory() {
        return scroll.getValue() < 0;
    }

    private void onScroll() {
        boolean reading = readingHistory();
        if (reading && !counter.active()) {
            counter.start(historyLines());
        } else if (!reading && counter.active()) {
            counter.stop();
        }
        if (sessionEnded) {
            reconnect.setCollapsed(reading);
        }
        refresh();
    }

    private void scrollToBottom() {
        scroll.setValue(0);
        widget.getTerminalPanel().repaint();
    }

    /** Dari thread emulator: perbarui label di EDT, paling banyak satu antrean sekaligus. */
    private void queueRefresh() {
        if (refreshQueued.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                refreshQueued.set(false);
                refresh();
            });
        }
    }

    private void refresh() {
        boolean reading = readingHistory();
        int n = counter.count(historyLines());
        String text = n == 0 ? I18n.t("fab.toBottom") : I18n.t("fab.toBottom.count", n > 999 ? "999+" : String.valueOf(n));
        if (!text.equals(toBottom.getText())) {
            toBottom.setText(text);
        }
        if (toBottom.isVisible() != reading) {
            toBottom.setVisible(reading);
            overlay.revalidate();
            overlay.repaint();
        }
    }

    private int historyLines() {
        buffer.lock();
        try {
            return buffer.getHistoryLinesCount();
        } finally {
            buffer.unlock();
        }
    }

    /** Lepas listener dan tombol; dipanggil saat widget ditutup. EDT. */
    void dispose() {
        scroll.removeChangeListener(scrollListener);
        buffer.removeHistoryBufferListener(historyListener);
        buffer.removeChangesListener(changesListener);
        overlay.removeFloating(reconnect);
        overlay.removeFloating(toBottom);
    }
}

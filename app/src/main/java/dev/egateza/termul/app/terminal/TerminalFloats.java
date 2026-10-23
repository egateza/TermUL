package dev.egateza.termul.app.terminal;

import com.jediterm.terminal.model.TerminalHistoryBufferListener;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalModelListener;
import com.jediterm.terminal.model.TerminalSelectionChangesListener;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.TextBufferChangesListener;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.fab.FloatingButton;
import dev.egateza.termul.app.ui.fab.FloatingOverlay;
import dev.egateza.termul.app.ui.fab.FloatingStyle;
import dev.egateza.termul.app.ui.fab.FloatingToolbar;
import dev.egateza.termul.terminal.PromptResponder;
import dev.egateza.termul.vault.SecretType;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.BoundedRangeModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

/**
 * Tombol mengambang di atas satu terminal:
 * <ul>
 *   <li><b>Sambung ulang</b> (kanan bawah): hanya saat sesi berakhir atau reconnect otomatis menyerah. Labelnya
 *       menyusut selama user membaca history, supaya tidak menutupi teks.</li>
 *   <li><b>Ke bawah</b> (tengah bawah): hanya saat terminal di-scroll ke atas, dengan jumlah baris baru.</li>
 *   <li><b>Toolbar</b> (tengah bawah): Cari dan Salin saat ada seleksi; Password sudo/root saat baris kursor berupa
 *       prompt password. Inject di sini manual (diklik user), sama persis dengan hotkey; guard auto-sudo tidak
 *       berubah.</li>
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
    final FloatingToolbar toolbar = new FloatingToolbar();
    private final JButton find;
    private final JButton copy;
    private final JButton sudo;
    private final JButton root;
    /** Pemeriksaan prompt tertunda di EDT (dipicu perubahan layar dari thread emulator). */
    private final AtomicBoolean promptCheckQueued = new AtomicBoolean();
    private final TerminalModelListener modelListener = this::queuePromptCheck;
    /**
     * JediTerm memanggil listener seleksi juga dari thread emulator (output menggulung layar saat ada seleksi), sambil
     * memegang lock buffer. Operasi Swing di sana bisa deadlock dengan EDT yang sedang menggambar terminal, jadi
     * pembaruan toolbar selalu dipindah ke EDT.
     */
    private final TerminalSelectionChangesListener selectionListener = sel -> queueToolbarRefresh();
    private final AtomicBoolean toolbarRefreshQueued = new AtomicBoolean();
    private PromptResponder.Kind prompt; // EDT; prompt password di baris kursor, null kalau tidak ada
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
    /** false tanpa injector (tab lokal): tombol password tidak pernah tampil, termasuk di prompt sudo lokal. */
    private final boolean canInject;

    /** @param inject inject password manual (sama dengan hotkey); null = tanpa tombol password (tab lokal) */
    TerminalFloats(ZoomableTermWidget widget, FloatingOverlay overlay, Runnable onReconnect,
                   Consumer<SecretType> inject) {
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
        find = toolbar.addAction(AppIcon.SEARCH, I18n.t("fab.find"), I18n.t("fab.find.tooltip"), () -> {
            widget.openFind();
            refreshToolbar();
        });
        copy = toolbar.addAction(AppIcon.COPY, I18n.t("fab.copy"), I18n.t("fab.copy.tooltip"), () -> {
            widget.copySelection();
            refreshToolbar();
        });
        sudo = toolbar.addAction(AppIcon.PERMISSION, I18n.t("fab.sudo"), I18n.t("fab.sudo.tooltip"),
                () -> injectAndRefresh(inject, SecretType.SUDO_PASSWORD));
        root = toolbar.addAction(AppIcon.PERMISSION, I18n.t("fab.root"), I18n.t("fab.root.tooltip"),
                () -> injectAndRefresh(inject, SecretType.ROOT_PASSWORD));
        canInject = inject != null;
        toolbar.setVisible(false);
        overlay.addFloating(reconnect, FloatingOverlay.Anchor.BOTTOM_RIGHT);
        overlay.addFloating(toolbar, FloatingOverlay.Anchor.BOTTOM_CENTER); // paling bawah; "Ke bawah" di atasnya
        overlay.addFloating(toBottom, FloatingOverlay.Anchor.BOTTOM_CENTER);
        scroll.addChangeListener(scrollListener);
        buffer.addHistoryBufferListener(historyListener);
        buffer.addChangesListener(changesListener);
        buffer.addModelListener(modelListener);
        widget.getTerminalPanel().addSelectionListener(selectionListener);
        refreshToolbar();
    }

    private void injectAndRefresh(Consumer<SecretType> inject, SecretType type) {
        if (inject != null) {
            inject.accept(type);
        }
        prompt = null; // prompt terjawab; pemeriksaan berikutnya menilai ulang dari layar
        refreshToolbar();
    }

    /** Dari thread emulator: periksa baris kursor di EDT, paling banyak satu antrean sekaligus. */
    private void queuePromptCheck() {
        if (promptCheckQueued.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                promptCheckQueued.set(false);
                PromptResponder.Kind now = PromptResponder.passwordPrompt(cursorLine());
                if (now != prompt) {
                    prompt = now;
                    refreshToolbar();
                }
            });
        }
    }

    private String cursorLine() {
        buffer.lock();
        try {
            int y = widget.getTerminal().getCursorY() - 1; // cursorY 1-based
            return y < 0 || y >= buffer.getHeight() ? "" : buffer.getLine(y).getText();
        } finally {
            buffer.unlock();
        }
    }

    /** Dari thread mana pun: {@link #refreshToolbar()} di EDT, paling banyak satu antrean sekaligus. */
    private void queueToolbarRefresh() {
        if (SwingUtilities.isEventDispatchThread()) {
            refreshToolbar();
        } else if (toolbarRefreshQueued.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                toolbarRefreshQueued.set(false);
                refreshToolbar();
            });
        }
    }

    /** Tombol toolbar mengikuti seleksi dan prompt; toolbar tanpa tombol disembunyikan. EDT. */
    void refreshToolbar() {
        boolean selected = widget.hasSelection();
        find.setVisible(selected);
        copy.setVisible(selected);
        sudo.setVisible(canInject && prompt == PromptResponder.Kind.SUDO);
        root.setVisible(canInject && prompt == PromptResponder.Kind.SU);
        boolean show = toolbar.hasVisibleActions();
        if (toolbar.isVisible() != show) {
            toolbar.setVisible(show);
        }
        toolbar.revalidate();
        overlay.revalidate();
        overlay.repaint();
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
        buffer.removeModelListener(modelListener);
        widget.getTerminalPanel().removeSelectionListener(selectionListener);
        overlay.removeFloating(reconnect);
        overlay.removeFloating(toolbar);
        overlay.removeFloating(toBottom);
    }
}

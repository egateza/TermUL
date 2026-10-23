package dev.egateza.termul.app.terminal;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.lang.ref.WeakReference;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/**
 * Menghitung perpindahan konteks yang relevan untuk kunci profil sensitif ({@link SensitiveLock}): fokus pindah ke
 * terminal lain ({@link TermPane} lain), atau aplikasi lain menjadi aktif. Fokus yang hanya mampir ke komponen non-terminal
 * (tree host, toolbar, panel SFTP milik tab yang sama) atau berpindah karena susunan panel berubah (mis. panel SFTP
 * dibuka sehingga terminal dicabut dan dipasang ulang) tidak dihitung. Satu instance global, EDT.
 */
final class TerminalFocusTracker {

    /** Posisi hitungan pada satu saat; dua mark berbeda = ada perpindahan konteks di antaranya. */
    record Mark(long terminalSwitches, long appLeaves) {
    }

    private static TerminalFocusTracker instance; // EDT

    private final Function<Component, Object> paneOf;
    private WeakReference<Object> current = new WeakReference<>(null); // terminal yang terakhir memegang fokus
    private long terminalSwitches;
    private long appLeaves;

    /** @param paneOf panel terminal tempat komponen berada, atau null kalau bukan bagian terminal */
    TerminalFocusTracker(Function<Component, Object> paneOf) {
        this.paneOf = paneOf;
    }

    /** Tracker global, dipasang ke {@link KeyboardFocusManager} saat pertama dipakai. EDT. */
    static TerminalFocusTracker get() {
        if (instance == null) {
            var tracker = new TerminalFocusTracker(c -> SwingUtilities.getAncestorOfClass(TermPane.class, c));
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner",
                    e -> tracker.focusOwnerChanged((Component) e.getNewValue()));
            Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
                if (e.getID() == WindowEvent.WINDOW_LOST_FOCUS) {
                    tracker.windowLostFocus(((WindowEvent) e).getOppositeWindow() == null);
                }
            }, AWTEvent.WINDOW_FOCUS_EVENT_MASK);
            instance = tracker;
        }
        return instance;
    }

    /** Pemilik fokus permanen berubah; null (fokus dikosongkan) diabaikan. */
    void focusOwnerChanged(Component owner) {
        Object pane = owner == null ? null : paneOf.apply(owner);
        if (pane != null && pane != current.get()) {
            current = new WeakReference<>(pane);
            terminalSwitches++;
        }
    }

    /** @param toOtherApp jendela tujuan bukan milik aplikasi ini (opposite window null) */
    void windowLostFocus(boolean toOtherApp) {
        if (toOtherApp) {
            appLeaves++;
        }
    }

    Mark mark() {
        return new Mark(terminalSwitches, appLeaves);
    }
}

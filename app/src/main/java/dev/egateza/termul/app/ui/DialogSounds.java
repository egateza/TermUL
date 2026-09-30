package dev.egateza.termul.app.ui;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

/**
 * Memutar suara sistem Windows saat {@link JOptionPane} muncul (FlatLaf tidak melakukannya sendiri).
 * Suara mengikuti skema Sound Windows: exclamation untuk warning dan question, hand untuk error, asterisk untuk info.
 */
public final class DialogSounds {

    private DialogSounds() {
    }

    /** Harus dipanggil di EDT, sekali saja saat startup. */
    public static void install() {
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == WindowEvent.WINDOW_OPENED && event.getSource() instanceof JDialog dialog) {
                var pane = findOptionPane(dialog);
                if (pane != null) {
                    play(pane.getMessageType());
                }
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
    }

    static String soundProperty(int messageType) {
        return switch (messageType) {
            case JOptionPane.ERROR_MESSAGE -> "win.sound.hand";
            // Suara "question" kosong di skema bawaan Windows, padahal konfirmasi tutup perlu terdengar
            case JOptionPane.WARNING_MESSAGE, JOptionPane.QUESTION_MESSAGE -> "win.sound.exclamation";
            case JOptionPane.INFORMATION_MESSAGE -> "win.sound.asterisk";
            default -> null;
        };
    }

    private static void play(int messageType) {
        String property = soundProperty(messageType);
        if (property == null) {
            return;
        }
        // Di Windows nilainya Runnable yang memutar suara skema; di OS lain null.
        if (Toolkit.getDefaultToolkit().getDesktopProperty(property) instanceof Runnable sound) {
            sound.run();
        }
    }

    private static JOptionPane findOptionPane(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JOptionPane pane) {
                return pane;
            }
            if (child instanceof Container inner) {
                var found = findOptionPane(inner);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}

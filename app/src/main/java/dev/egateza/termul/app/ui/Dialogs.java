package dev.egateza.termul.app.ui;

import java.awt.Component;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dialog standar (pesan untuk user dalam Bahasa Indonesia). Panggil di EDT. */
public final class Dialogs {

    private static final Logger log = LoggerFactory.getLogger(Dialogs.class);

    private Dialogs() {
    }

    public static void error(Component parent, String title, Throwable error) {
        log.warn("{}", title, error);
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public static void error(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public static void info(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String title, String message) {
        return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** @return teks yang di-strip, atau null kalau dibatalkan/kosong */
    public static String input(Component parent, String title, String message, String initial) {
        Object value = JOptionPane.showInputDialog(parent, message, title, JOptionPane.PLAIN_MESSAGE,
                null, null, initial);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString().strip();
    }
}

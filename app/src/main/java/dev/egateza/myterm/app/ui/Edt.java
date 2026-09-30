package dev.egateza.myterm.app.ui;

import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;

/** Helper menjalankan kode di EDT dari thread lain dan menunggu selesai (untuk dialog blocking). */
public final class Edt {

    private Edt() {
    }

    public static void runAndWait(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(r);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }
}

package dev.egateza.termul.app.ui;

import java.awt.Component;
import java.awt.Point;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Efek layar bergetar singkat (mis. saat terminal menerima BEL). Yang digeser adalah layered pane window
 * (menu + konten), bukan window-nya, jadi tetap bekerja saat maximized dan tidak mengganggu window manager.
 */
public final class ShakeEffect {

    static final int STEPS = 26;
    static final int AMPLITUDE_PX = 14;
    private static final int STEP_DELAY_MS = 16;
    /** Satu siklus kiri-kanan = 6 langkah (~100 ms); lebih cepat dari itu terlihat sebagai flicker, bukan getar. */
    private static final int STEPS_PER_CYCLE = 6;

    private static Timer running; // EDT; null = tidak sedang bergetar
    private static int step; // EDT

    private ShakeEffect() {
    }

    /** Offset horizontal (px) pada langkah ke-{@code step}; sinus teredam linear, 0 di langkah terakhir. Murni, bisa dites. */
    static int offset(int step) {
        if (step <= 0 || step >= STEPS) {
            return 0;
        }
        double decay = 1.0 - (double) step / STEPS;
        return (int) Math.round(Math.sin(2 * Math.PI * step / STEPS_PER_CYCLE) * AMPLITUDE_PX * decay);
    }

    /**
     * Getarkan window yang memuat {@code source}. Harus dipanggil di EDT; diam saja kalau tidak ada window.
     * BEL yang datang saat masih bergetar memulai getaran dari awal, jadi setiap BEL terlihat.
     */
    public static void shake(Component source) {
        if (running != null) {
            step = 0;
            return;
        }
        var root = SwingUtilities.getRootPane(source);
        if (root == null || !root.isShowing()) {
            return;
        }
        JComponent target = root.getLayeredPane();
        Point origin = target.getLocation();
        step = 0;
        running = new Timer(STEP_DELAY_MS, null);
        running.addActionListener(e -> {
            if (++step >= STEPS) {
                target.setLocation(origin);
                running.stop();
                running = null;
                return;
            }
            target.setLocation(origin.x + offset(step), origin.y);
        });
        running.start();
    }
}

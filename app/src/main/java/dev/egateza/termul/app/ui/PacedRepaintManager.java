package dev.egateza.termul.app.ui;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Membatasi gambar ulang window beralpha per piksel (transparan/blur, ADR 0008) ke sekitar 60 kali per detik.
 *
 * <p>Setiap gambar ulang window beralpha diakhiri {@code Window.updateWindow}: Java menyerahkan gambar <em>seluruh</em>
 * window ke Windows ({@code UpdateLayeredWindow}), sekecil apa pun area yang berubah. Block text dan scroll memicu
 * repaint mengikuti event mouse (ratusan per detik), dan diukur sekitar 70% waktu EDT habis di sana. Repaint yang datang
 * kurang dari {@link #INTERVAL_MS} setelah gambar terakhir ditahan, digabung, lalu diteruskan sekaligus. Window solid
 * tidak dibatasi.
 *
 * <p>{@code addDirtyRegion} boleh dipanggil dari thread mana pun; region yang ditahan dijaga {@code this}.
 */
public final class PacedRepaintManager extends RepaintManager {

    /** Jarak minimum antar gambar window beralpha (~60 fps). */
    static final long INTERVAL_MS = 16;

    private final LongSupplier clock;
    private final Predicate<Component> paced;
    private final Map<JComponent, Rectangle> held = new LinkedHashMap<>(); // guarded by this
    private boolean scheduled; // guarded by this: timer pelepas sudah dijadwalkan
    private volatile long lastPaint = Long.MIN_VALUE / 2; // ms menurut clock
    private boolean bypass; // EDT: paintNow

    public PacedRepaintManager() {
        this(System::currentTimeMillis, PacedRepaintManager::translucent);
    }

    /** @param paced komponen yang gambarnya dibatasi (bawaan: berada di window beralpha per piksel) */
    PacedRepaintManager(LongSupplier clock, Predicate<Component> paced) {
        this.clock = clock;
        this.paced = paced;
    }

    /** Pasang sebagai RepaintManager aplikasi. EDT, sebelum window pertama dibuat. */
    public static void install() {
        RepaintManager.setCurrentManager(new PacedRepaintManager());
    }

    /**
     * Gambar {@code c} sekarang juga, tanpa ditahan (mis. latar gelap yang harus tampil sebelum dialog modal), termasuk
     * mengirimnya ke window beralpha. EDT.
     */
    public static void paintNow(JComponent c) {
        var manager = RepaintManager.currentManager(c);
        if (manager instanceof PacedRepaintManager paced) {
            paced.bypass = true;
            try {
                c.repaint();
                paced.release();
            } finally {
                paced.bypass = false;
            }
        } else {
            c.repaint();
        }
        manager.paintDirtyRegions();
    }

    @Override
    public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
        long wait = bypass ? 0 : lastPaint + INTERVAL_MS - clock.getAsLong();
        if (wait <= 0 || w <= 0 || h <= 0 || !paced.test(c)) {
            super.addDirtyRegion(c, x, y, w, h);
            return;
        }
        synchronized (this) {
            var r = held.get(c);
            if (r == null) {
                held.put(c, new Rectangle(x, y, w, h));
            } else {
                r.add(new Rectangle(x, y, w, h));
            }
            if (scheduled) {
                return;
            }
            scheduled = true;
        }
        var timer = new Timer((int) Math.min(wait, INTERVAL_MS), e -> release());
        timer.setRepeats(false);
        timer.start();
    }

    @Override
    public void paintDirtyRegions() {
        lastPaint = clock.getAsLong();
        super.paintDirtyRegions();
    }

    /** Teruskan region yang ditahan ke antrean gambar biasa. */
    private void release() {
        Map<JComponent, Rectangle> out;
        synchronized (this) {
            scheduled = false;
            if (held.isEmpty()) {
                return;
            }
            out = new LinkedHashMap<>(held);
            held.clear();
        }
        out.forEach((c, r) -> super.addDirtyRegion(c, r.x, r.y, r.width, r.height));
    }

    /** Region yang sedang ditahan (untuk test). */
    synchronized int heldCount() {
        return held.size();
    }

    private static boolean translucent(Component c) {
        Window w = c instanceof Window window ? window : SwingUtilities.getWindowAncestor(c);
        return w != null && !w.isOpaque();
    }
}

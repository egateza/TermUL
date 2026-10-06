package dev.egateza.termul.app.terminal;

import javax.swing.BoundedRangeModel;
import javax.swing.Timer;

/**
 * Scroll wheel terminal yang dianimasikan, supaya terasa sama dengan panel lain (FlatLaf smooth scrolling). Terminal
 * hanya bisa digeser per baris, jadi animasinya per baris dengan ease-out: sisa scroll dikurangi sebagian setiap
 * frame (minimal satu baris). Rotasi wheel presisi yang kecil (touchpad) dikumpulkan sampai genap satu baris, bukan
 * dibuang. Semua method dipanggil di EDT.
 */
final class SmoothWheel {

    static final int FRAME_MS = 25;
    /** Bagian dari sisa scroll yang digeser per frame. */
    private static final double EASE = 0.25;

    private final BoundedRangeModel model;
    private final Timer timer;
    private double pending; // baris yang belum digeser; negatif = ke atas

    SmoothWheel(BoundedRangeModel model) {
        this(model, true);
    }

    /** @param animate false = tanpa timer (test memanggil {@link #tick()} sendiri) */
    SmoothWheel(BoundedRangeModel model, boolean animate) {
        this.model = model;
        this.timer = animate ? new Timer(FRAME_MS, e -> {
            if (!tick()) {
                ((Timer) e.getSource()).stop();
            }
        }) : null;
    }

    /**
     * Tambah scroll sebanyak {@code lines} baris (boleh pecahan). Arah berlawanan dengan sisa scroll membatalkan sisa
     * itu, supaya berbalik arah terasa langsung.
     */
    void scroll(double lines) {
        if (lines == 0) {
            return;
        }
        if (Math.signum(lines) != Math.signum(pending)) {
            pending = 0;
        }
        pending += lines;
        if (timer != null && Math.abs(pending) >= 1 && !timer.isRunning()) {
            if (tick()) { // frame pertama langsung, tanpa menunggu timer
                timer.start();
            }
        }
    }

    /** @return true kalau masih ada scroll yang perlu dianimasikan */
    boolean tick() {
        int whole = (int) pending; // dibulatkan ke arah 0; pecahan disimpan untuk rotasi berikutnya
        if (whole == 0) {
            return false;
        }
        int step = (int) Math.signum(whole) * Math.max(1, (int) Math.round(Math.abs(whole) * EASE));
        int before = model.getValue();
        model.setValue(before + step);
        if (model.getValue() == before) {
            pending = 0; // sudah di ujung atas/bawah
            return false;
        }
        pending -= step;
        return Math.abs(pending) >= 1;
    }

    /** Sisa scroll, untuk test. */
    double pending() {
        return pending;
    }
}

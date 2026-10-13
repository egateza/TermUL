package dev.egateza.termul.app.ui.streak;

/** Menghitung klik beruntun (mis. triple klik): jeda antar klik harus di bawah {@link #WINDOW_MS}. EDT. */
final class TapCounter {

    static final long WINDOW_MS = 500;

    private final int needed;
    private int taps;
    private long last;

    TapCounter(int needed) {
        this.needed = needed;
        reset();
    }

    /** @return true kalau klik ini menggenapi {@code needed} klik beruntun (hitungan lalu mulai dari nol) */
    boolean tap(long nowMillis) {
        taps = nowMillis - last < WINDOW_MS ? taps + 1 : 1;
        last = nowMillis;
        if (taps >= needed) {
            reset();
            return true;
        }
        return false;
    }

    void reset() {
        taps = 0;
        last = Long.MIN_VALUE / 2; // jauh di masa lalu tanpa overflow saat dikurangkan
    }
}

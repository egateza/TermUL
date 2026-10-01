package dev.egateza.termul.app.monitor;

/** Tingkat pemakaian untuk warna grafik: mendekati batas terlihat kuning, hampir habis merah. */
enum Level {
    NORMAL, WARN, CRITICAL;

    static final double WARN_AT = 0.75;
    static final double CRITICAL_AT = 0.90;

    /** @param ratio 0..1; NaN = normal (tidak diketahui) */
    static Level of(double ratio) {
        if (ratio >= CRITICAL_AT) {
            return CRITICAL;
        }
        return ratio >= WARN_AT ? WARN : NORMAL;
    }
}

package dev.egateza.termul.app.terminal;

/**
 * Menghitung baris output baru selama user membaca history (terminal di-scroll ke atas). Baris baru terlihat dari
 * bertambahnya history; kalau history sudah penuh, baris lama dibuang dan jumlahnya ikut dihitung. Dipanggil dari
 * EDT (mulai/berhenti) dan dari thread emulator JediTerm (perubahan buffer).
 */
final class NewLinesCounter {

    private boolean active;      // guarded by this
    private int baseline;        // guarded by this; jumlah history saat mulai membaca
    private long discarded;      // guarded by this; baris history yang dibuang sejak mulai membaca

    /** User mulai membaca history. */
    synchronized void start(int historyLines) {
        active = true;
        baseline = historyLines;
        discarded = 0;
    }

    /** User kembali ke bawah. */
    synchronized void stop() {
        active = false;
    }

    synchronized boolean active() {
        return active;
    }

    /** History penuh: {@code n} baris tertua dibuang. */
    synchronized void discarded(int n) {
        if (active) {
            discarded += n;
        }
    }

    /** History dikosongkan (mis. {@code clear}): hitung ulang dari nol. */
    synchronized void reset(int historyLines) {
        if (active) {
            baseline = historyLines;
            discarded = 0;
        }
    }

    /** @return baris baru sejak mulai membaca; 0 kalau tidak sedang membaca */
    synchronized int count(int historyLines) {
        if (!active) {
            return 0;
        }
        long n = historyLines - baseline + discarded;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, n));
    }
}

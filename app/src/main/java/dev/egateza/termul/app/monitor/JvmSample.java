package dev.egateza.termul.app.monitor;

/**
 * Satu pengukuran resource JVM aplikasi ini (bukan server remote).
 *
 * @param processCpu    CPU proses TermUL, 0..1 dari total semua core; NaN = belum/tidak diketahui
 * @param systemCpu     CPU seluruh mesin, 0..1; NaN = tidak diketahui
 * @param heapUsed      heap terpakai (byte)
 * @param heapCommitted heap yang sudah diambil dari OS (byte)
 * @param heapMax       batas heap ({@code -Xmx}); -1 = tidak ditentukan
 * @param nonHeapUsed   non-heap terpakai (metaspace, code cache), byte
 * @param threads       jumlah thread hidup
 * @param gcCount       total GC sejak start
 * @param gcMillis      total waktu GC sejak start
 * @param physicalTotal RAM fisik mesin (byte); -1 = tidak diketahui
 * @param physicalFree  RAM fisik bebas (byte); -1 = tidak diketahui
 */
public record JvmSample(double processCpu, double systemCpu, long heapUsed, long heapCommitted, long heapMax,
                        long nonHeapUsed, int threads, long gcCount, long gcMillis, long physicalTotal,
                        long physicalFree) {

    /** Batas heap yang tidak boleh dilewati: {@code -Xmx}, atau committed kalau batasnya tidak diketahui. */
    public long heapLimit() {
        return heapMax > 0 ? heapMax : heapCommitted;
    }

    /** Heap terpakai terhadap {@link #heapLimit()}, 0..1. */
    public double heapRatio() {
        long limit = heapLimit();
        return limit <= 0 ? 0 : Math.min(1.0, (double) heapUsed / limit);
    }
}

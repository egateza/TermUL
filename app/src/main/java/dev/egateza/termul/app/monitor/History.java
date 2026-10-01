package dev.egateza.termul.app.monitor;

/**
 * Riwayat nilai dengan kapasitas tetap (ring buffer): nilai terlama dibuang saat penuh, jadi memory tidak bertambah
 * walau aplikasi jalan berhari-hari. NaN = sampel tidak diketahui. Tidak thread-safe (dipakai di EDT).
 */
final class History {

    private final double[] values;
    private int start;
    private int size;
    private long added;

    History(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity < 1");
        }
        values = new double[capacity];
    }

    void add(double value) {
        added++;
        if (size < values.length) {
            values[(start + size++) % values.length] = value;
        } else {
            values[start] = value;
            start = (start + 1) % values.length;
        }
    }

    int size() {
        return size;
    }

    /** Total nilai yang pernah ditambahkan (untuk menggeser grid grafik). */
    long added() {
        return added;
    }

    int capacity() {
        return values.length;
    }

    /** @param index 0 = paling lama */
    double get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
        return values[(start + index) % values.length];
    }
}

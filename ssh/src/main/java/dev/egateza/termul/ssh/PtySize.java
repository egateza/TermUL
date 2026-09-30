package dev.egateza.termul.ssh;

/** Ukuran terminal dalam karakter. */
public record PtySize(int columns, int rows) {

    public PtySize {
        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("Ukuran PTY tidak valid: " + columns + "x" + rows);
        }
    }
}

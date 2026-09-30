package dev.egateza.termul.ssh;

import java.io.ByteArrayOutputStream;

/** Membuang output di atas batas (server tidak bisa membanjiri memory). */
final class LimitedOutput extends ByteArrayOutputStream {

    private final int limit;

    LimitedOutput(int limit) {
        this.limit = limit;
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        int room = limit - count;
        if (room > 0) {
            super.write(b, off, Math.min(room, len));
        }
    }

    @Override
    public synchronized void write(int b) {
        if (count < limit) {
            super.write(b);
        }
    }
}

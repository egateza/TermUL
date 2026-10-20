package dev.egateza.termul.localterm;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Proses shell di PTY lokal. Lapisan tipis di atas {@code com.pty4j.PtyProcess} supaya {@link LocalTtyConnector}
 * bisa di-unit test tanpa library native.
 */
public interface PtyHandle {

    /** Output PTY (stdout + stderr shell). */
    InputStream input();

    /** Input PTY (ketikan ke shell). */
    OutputStream output();

    void resize(int columns, int rows);

    boolean isAlive();

    /** Blocking sampai shell berakhir. @return kode keluar */
    int waitFor() throws InterruptedException;

    /** PID shell; pohon prosesnya dibaca lewat {@code ProcessHandle.of(pid)} ({@code toHandle()} pty4j tidak didukung). */
    long pid();

    /**
     * Tutup PTY seperti menutup window terminal: shell dan job-nya mendapat SIGHUP (Unix) atau console ditutup
     * (Windows). Shell interaktif mengabaikan SIGTERM, jadi ini cara mematikan yang pertama.
     */
    void hangUp();

    /** Matikan paksa shell (cadangan kalau {@link #hangUp()} tidak cukup). */
    void destroyForcibly();
}

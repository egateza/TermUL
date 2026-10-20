package dev.egateza.termul.localterm;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/** {@link PtyHandle} di atas pty4j. */
final class Pty4jHandle implements PtyHandle {

    private final PtyProcess process;

    Pty4jHandle(PtyProcess process) {
        this.process = Objects.requireNonNull(process);
    }

    @Override
    public InputStream input() {
        return process.getInputStream();
    }

    @Override
    public OutputStream output() {
        return process.getOutputStream();
    }

    @Override
    public void resize(int columns, int rows) {
        if (process.isAlive()) {
            process.setWinSize(new WinSize(columns, rows));
        }
    }

    @Override
    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    @Override
    public long pid() {
        return process.pid();
    }

    @Override
    public void hangUp() {
        closeQuietly(process.getOutputStream());
        closeQuietly(process.getInputStream());
        if (process.isAlive()) {
            process.destroy(); // Windows: menutup pseudo console; Unix: SIGTERM (diabaikan shell interaktif)
        }
    }

    @Override
    public void destroyForcibly() {
        process.destroyForcibly();
    }

    private static void closeQuietly(java.io.Closeable c) {
        try {
            c.close();
        } catch (IOException e) {
            // stream sudah tertutup / proses sudah berakhir
        }
    }
}

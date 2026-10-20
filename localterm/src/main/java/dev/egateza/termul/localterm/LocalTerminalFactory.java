package dev.egateza.termul.localterm;

import com.pty4j.PtyProcessBuilder;
import dev.egateza.termul.core.Os;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Menjalankan {@link LocalShell} di PTY lokal (pty4j) → {@link LocalTtyConnector}. */
public final class LocalTerminalFactory {

    private static final Logger log = LoggerFactory.getLogger(LocalTerminalFactory.class);

    public static final int DEFAULT_COLUMNS = 80;
    public static final int DEFAULT_ROWS = 24;

    private final Os os;
    private final AtomicInteger counter = new AtomicInteger();

    public LocalTerminalFactory(Os os) {
        this.os = os;
    }

    /** Blocking (start proses + memuat library native saat pertama kali): jangan dipanggil di EDT. */
    public LocalTtyConnector open(LocalShell shell) throws LocalShellException {
        if (!Files.isDirectory(shell.directory())) {
            throw new LocalShellException("Direktori awal tidak ditemukan: " + shell.directory(), null);
        }
        try {
            var process = new PtyProcessBuilder(shell.command().toArray(String[]::new))
                    .setEnvironment(shell.environment())
                    .setDirectory(shell.directory().toString())
                    .setInitialColumns(DEFAULT_COLUMNS)
                    .setInitialRows(DEFAULT_ROWS)
                    .setConsole(false)
                    .setUseWinConPty(os.isWindows()) // default pty4j WinPty; jatuh ke WinPty sendiri kalau ConPTY gagal
                    .start();
            var connector = new LocalTtyConnector(new Pty4jHandle(process),
                    shell.label() + "#" + counter.incrementAndGet());
            log.info("Shell lokal {} dijalankan (pid {}) di {}", connector.getName(), process.pid(), shell.directory());
            return connector;
        } catch (IOException | RuntimeException | LinkageError e) {
            throw new LocalShellException("Gagal menjalankan shell lokal " + String.join(" ", shell.command())
                    + ": " + e.getMessage(), e);
        }
    }
}

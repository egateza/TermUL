package dev.egateza.termul.localterm;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link TtyConnector} JediTerm di atas shell lokal ({@link PtyHandle}), dengan pola yang sama seperti koneksi SSH:
 *
 * <ul>
 *   <li>{@link #read} dipanggil thread emulator JediTerm (blocking).</li>
 *   <li>Write + resize berurutan di writer thread sendiri, karena write ke PTY bisa blocking (buffer penuh) dan EDT
 *       tidak boleh ikut tertahan.</li>
 *   <li>Shell yang berakhir (exit, dimatikan) memanggil close listener tepat sekali, dengan kode keluar.</li>
 *   <li>{@link #closeByUser()} mematikan shell beserta pohon prosesnya di thread lain: PTY ditutup (SIGHUP), lalu
 *       proses yang masih hidup setelah {@link #HANGUP_GRACE_MILLIS} dimatikan paksa. Tidak ada proses yatim.</li>
 * </ul>
 */
public final class LocalTtyConnector implements TtyConnector {

    private static final Logger log = LoggerFactory.getLogger(LocalTtyConnector.class);

    /** Lihat {@code SshTtyConnector.RESIZE_DEBOUNCE_MILLIS}: shell menggambar ulang prompt di setiap ukuran sementara. */
    static final long RESIZE_DEBOUNCE_MILLIS = 150;
    /** Tenggat setelah PTY ditutup sebelum proses yang tersisa dimatikan paksa. */
    static final long HANGUP_GRACE_MILLIS = 2000;
    private static final ScheduledExecutorService RESIZE_TIMER = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("local-term-resize").daemon().factory());

    private final PtyHandle pty;
    private final String name;
    private final Reader reader;
    private final ExecutorService writer;
    private final CopyOnWriteArrayList<Consumer<LocalTtyConnector>> closeListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean ended = new AtomicBoolean();
    private volatile boolean closedByUser;
    private volatile Integer exitCode;
    private final AtomicReference<TermSize> pendingSize = new AtomicReference<>();
    private ScheduledFuture<?> resizeTask; // guarded by this

    public LocalTtyConnector(PtyHandle pty, String name) {
        this.pty = Objects.requireNonNull(pty, "pty");
        this.name = Objects.requireNonNull(name, "name");
        this.reader = new InputStreamReader(pty.input(), StandardCharsets.UTF_8);
        this.writer = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("local-writer-" + name).factory());
        Thread.ofVirtual().name("local-wait-" + name).start(this::awaitExit);
    }

    /** Dipanggil sekali saat shell berakhir (exit, dimatikan, atau tab ditutup); langsung kalau sudah berakhir. */
    public void addCloseListener(Consumer<LocalTtyConnector> listener) {
        closeListeners.add(Objects.requireNonNull(listener));
        if (ended.get()) {
            fireClosed();
        }
    }

    /** Kode keluar shell; kosong selama masih berjalan atau kalau tidak diketahui. */
    public Optional<Integer> exitCode() {
        return Optional.ofNullable(exitCode);
    }

    /** true kalau shell dimatikan lewat {@link #closeByUser()} (tab ditutup), bukan exit sendiri. */
    public boolean isClosedByUser() {
        return closedByUser;
    }

    /**
     * Nama proses yang sedang berjalan di bawah shell (perintah yang belum selesai), tanpa argumen. Kosong kalau shell
     * diam di prompt. Untuk konfirmasi tutup tab; boleh dipanggil di EDT (hanya membaca tabel proses).
     */
    public List<String> runningCommands() {
        if (!pty.isAlive()) {
            return List.of();
        }
        var names = new ArrayList<String>();
        ProcessHandle.of(pty.pid()).ifPresent(shell -> shell.children().filter(ProcessHandle::isAlive)
                .forEach(child -> names.add(child.info().command().map(LocalTtyConnector::baseName).orElse("?"))));
        return names;
    }

    long pid() {
        return pty.pid();
    }

    static String baseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(slash + 1);
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        try {
            return reader.read(buf, offset, length);
        } catch (IOException e) {
            if (!isConnected()) {
                return -1; // PTY ditutup saat membaca: akhir sesi normal
            }
            throw e;
        }
    }

    @Override
    public void write(byte[] bytes) {
        byte[] copy = bytes.clone();
        submit(() -> {
            pty.output().write(copy);
            pty.output().flush();
        });
    }

    @Override
    public void write(String string) {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void resize(TermSize size) {
        if (size.getColumns() < 1 || size.getRows() < 1) {
            return;
        }
        pendingSize.set(size);
        synchronized (this) {
            if (resizeTask != null) {
                resizeTask.cancel(false);
            }
            resizeTask = RESIZE_TIMER.schedule(this::flushResize, RESIZE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private void flushResize() {
        TermSize size = pendingSize.getAndSet(null);
        if (size != null && !closed.get()) {
            submit(() -> pty.resize(size.getColumns(), size.getRows()));
        }
    }

    @Override
    public boolean isConnected() {
        return !closed.get() && pty.isAlive();
    }

    @Override
    public int waitFor() throws InterruptedException {
        return pty.waitFor();
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Dipanggil juga oleh JediTerm sendiri setelah output berakhir (shell exit), jadi <b>tidak</b> dianggap penutupan
     * oleh user; pakai {@link #closeByUser()} untuk itu. Mematikan shell kalau masih hidup.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        writer.shutdown();
        Thread.ofVirtual().name("local-close-" + name).start(this::terminate);
    }

    /** Tab ditutup: matikan shell dan pohon prosesnya; close listener tahu tidak perlu menawarkan jalankan ulang. */
    public void closeByUser() {
        closedByUser = true;
        close();
    }

    /** Blocking (sampai {@link #HANGUP_GRACE_MILLIS}): jangan dipanggil di EDT. */
    private void terminate() {
        if (!pty.isAlive()) {
            return;
        }
        // catat pohonnya dulu: setelah shell mati, anak-anaknya pindah ke init dan tidak bisa ditelusuri lagi
        var tree = new ArrayList<ProcessHandle>();
        ProcessHandle.of(pty.pid()).ifPresent(shell -> shell.descendants().forEach(tree::add));
        pty.hangUp();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(HANGUP_GRACE_MILLIS);
        while (System.nanoTime() < deadline && (pty.isAlive() || tree.stream().anyMatch(ProcessHandle::isAlive))) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        long survivors = tree.stream().filter(ProcessHandle::isAlive).peek(ProcessHandle::destroyForcibly).count();
        if (pty.isAlive()) {
            pty.destroyForcibly();
            survivors++;
        }
        if (survivors > 0) {
            log.info("Shell lokal {}: {} proses dimatikan paksa setelah PTY ditutup", name, survivors);
        }
    }

    private void awaitExit() {
        try {
            exitCode = pty.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!ended.compareAndSet(false, true)) {
            return;
        }
        log.info("Shell lokal {} berakhir (kode {}, {})", name, exitCode, closedByUser ? "tab ditutup" : "exit");
        writer.shutdown();
        fireClosed();
    }

    /** Memanggil setiap listener tepat sekali (listener dilepas sebelum dipanggil). */
    private void fireClosed() {
        for (var l : closeListeners) {
            if (closeListeners.remove(l)) {
                try {
                    l.accept(this);
                } catch (RuntimeException e) {
                    log.warn("Close listener gagal", e);
                }
            }
        }
    }

    private interface IoAction {
        void run() throws IOException;
    }

    private void submit(IoAction action) {
        try {
            writer.execute(() -> {
                try {
                    action.run();
                } catch (IOException e) {
                    if (isConnected()) {
                        log.warn("Gagal menulis ke shell lokal {}: {}", name, e.toString());
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.debug("Write diabaikan: shell lokal {} sudah berakhir", name);
        }
    }
}

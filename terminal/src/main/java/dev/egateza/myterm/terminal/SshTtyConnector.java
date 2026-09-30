package dev.egateza.myterm.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import dev.egateza.myterm.ssh.SshLease;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link TtyConnector} JediTerm di atas {@link ChannelShell} MINA.
 *
 * <ul>
 *   <li>{@link #read} dipanggil thread emulator JediTerm (blocking). Output ikut diteruskan ke
 *       {@link OutputListener} (mis. PromptResponder) di thread yang sama; listener tidak boleh blocking.</li>
 *   <li>Semua write + resize dijalankan berurutan di writer thread sendiri, supaya pemanggil
 *       (termasuk EDT) tidak pernah terblokir saat SSH window penuh.</li>
 *   <li>{@link #close()} menutup channel dan melepas {@link SshLease} (koneksi ikut tutup kalau tidak ada pemakai lain).</li>
 * </ul>
 */
public final class SshTtyConnector implements TtyConnector {

    private static final Logger log = LoggerFactory.getLogger(SshTtyConnector.class);

    /** Pengamat output terminal (teks sudah di-decode, ANSI belum di-strip). */
    @FunctionalInterface
    public interface OutputListener {
        void onOutput(char[] buf, int offset, int length);
    }

    private final SshLease lease;
    private final ChannelShell channel;
    private final String name;
    private final Reader reader;
    private final OutputStream stdin;
    private final ExecutorService writer;
    private final CopyOnWriteArrayList<OutputListener> outputListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<SshTtyConnector>> closeListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean closeNotified = new AtomicBoolean();
    private volatile boolean closedByUser;

    public SshTtyConnector(SshLease lease, ChannelShell channel, String name) {
        this.lease = Objects.requireNonNull(lease, "lease");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.name = Objects.requireNonNull(name, "name");
        this.reader = new InputStreamReader(channel.getInvertedOut(), StandardCharsets.UTF_8);
        this.stdin = channel.getInvertedIn();
        this.writer = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("term-writer-" + name).factory());
        channel.addCloseFutureListener(f -> notifyClosed());
    }

    public void addOutputListener(OutputListener listener) {
        outputListeners.add(Objects.requireNonNull(listener));
    }

    public void removeOutputListener(OutputListener listener) {
        outputListeners.remove(listener);
    }

    /** Dipanggil sekali saat channel tertutup (exit, koneksi putus, atau {@link #close()}). */
    public void addCloseListener(Consumer<SshTtyConnector> listener) {
        closeListeners.add(Objects.requireNonNull(listener));
        if (closeNotified.get()) {
            fireClosed(); // sudah tertutup sebelum listener didaftarkan
        } else if (!channel.isOpen()) {
            notifyClosed();
        }
    }

    /** true kalau ditutup lewat {@link #closeByUser()} (bukan exit/logout/putus). */
    public boolean isClosedByUser() {
        return closedByUser;
    }

    public SshLease lease() {
        return lease;
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        int n;
        try {
            n = reader.read(buf, offset, length);
        } catch (IOException e) {
            if (!isConnected()) {
                return -1; // channel ditutup saat membaca: akhir sesi normal
            }
            throw e;
        }
        if (n > 0) {
            for (var l : outputListeners) {
                try {
                    l.onOutput(buf, offset, n);
                } catch (RuntimeException e) {
                    log.warn("OutputListener gagal", e);
                }
            }
        }
        return n;
    }

    @Override
    public void write(byte[] bytes) {
        byte[] copy = bytes.clone();
        submit(() -> {
            stdin.write(copy);
            stdin.flush();
        });
    }

    @Override
    public void write(String string) {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Menulis secret ke stdin remote. Array milik pemanggil di-zero segera; salinan internal
     * di-zero setelah terkirim. Tidak pernah dikonversi ke String.
     */
    public void writeSecret(byte[] secret) {
        byte[] copy = secret.clone();
        Arrays.fill(secret, (byte) 0);
        submit(() -> {
            try {
                stdin.write(copy);
                stdin.flush();
            } finally {
                Arrays.fill(copy, (byte) 0);
            }
        });
    }

    @Override
    public void resize(TermSize size) {
        int cols = size.getColumns();
        int rows = size.getRows();
        if (cols < 1 || rows < 1) {
            return;
        }
        submit(() -> channel.sendWindowChange(cols, rows));
    }

    @Override
    public boolean isConnected() {
        return channel.isOpen() && !channel.isClosing();
    }

    @Override
    public int waitFor() throws InterruptedException {
        channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0L);
        Integer status = channel.getExitStatus();
        return status == null ? -1 : status;
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
     * Menutup channel. Dipanggil juga oleh JediTerm sendiri setelah stream berakhir (mis. user
     * {@code logout}), jadi <b>tidak</b> dianggap penutupan oleh user — pakai {@link #closeByUser()} untuk itu.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        writer.shutdown();
        channel.close(false).addListener(f -> lease.close());
    }

    /** Penutupan oleh user (tab ditutup): close listener tahu tidak perlu menawarkan reconnect. */
    public void closeByUser() {
        closedByUser = true;
        close();
    }

    private void notifyClosed() {
        if (!closeNotified.compareAndSet(false, true)) {
            return;
        }
        log.info("Shell {} ditutup ({})", name, closedByUser ? "tab ditutup" : "exit/logout/putus");
        writer.shutdown();
        lease.close();
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
                        log.warn("Gagal menulis ke terminal {}: {}", name, e.toString());
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.debug("Write diabaikan: terminal {} sudah ditutup", name);
        }
    }
}

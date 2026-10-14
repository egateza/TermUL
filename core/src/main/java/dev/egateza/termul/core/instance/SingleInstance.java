package dev.egateza.termul.core.instance;

import dev.egateza.termul.core.io.AtomicFiles;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Satu instance TermUL per folder data. Instance pertama memegang {@link FileLock} di {@value #LOCK_FILE} dan
 * mendengarkan di socket loopback; instance berikutnya (mis. user membuka shortcut lagi saat TermUL ada di tray) cukup
 * meminta instance pertama menampilkan window, lalu keluar. Dengan begitu vault, known_hosts, dan profil tidak dipakai
 * dua proses sekaligus.
 *
 * <p>Port dan token acak ditulis di {@value #PORT_FILE} (folder data milik user). Permintaan tanpa token yang cocok
 * diabaikan, jadi proses lain di mesin yang sama tidak bisa sembarangan mengirim perintah. Token bukan credential
 * user: hanya berlaku selama proses hidup, dibuat ulang setiap start.
 */
public final class SingleInstance implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SingleInstance.class);

    static final String LOCK_FILE = "instance.lock";
    static final String PORT_FILE = "instance.port";
    static final String CMD_SHOW = "show";
    static final String REPLY_OK = "ok";
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_REQUEST_BYTES = 256;
    static final int IO_TIMEOUT_MS = 2000;
    /** Instance pertama mungkin baru memegang lock tapi belum menulis file port: tunggu sebentar. */
    static final int PORT_WAIT_MS = 3000;
    private static final int PORT_POLL_MS = 100;

    /** Hasil {@link #acquire}. */
    public sealed interface Result {
    }

    /** Proses ini instance pertama; panggil {@link SingleInstance#listen} setelah window siap. */
    public record Primary(SingleInstance instance) implements Result {
    }

    /** Instance lain sudah ada dan sudah diminta menampilkan window; proses ini sebaiknya keluar. */
    public record Signaled() implements Result {
    }

    /**
     * Instance lain memegang lock tapi tidak bisa dihubungi (mis. sedang hang).
     *
     * @param reason penjelasan teknis untuk log (tanpa token)
     */
    public record Unreachable(String reason) implements Result {
    }

    private final FileChannel lockChannel;
    private final FileLock lock;
    private final ServerSocket server;
    private final byte[] token;
    private final Path portFile;
    private final Object handlerLock = new Object();
    private Runnable onShow; // guarded by handlerLock
    private boolean pendingShow; // guarded by handlerLock

    private SingleInstance(FileChannel lockChannel, FileLock lock, ServerSocket server, byte[] token, Path portFile) {
        this.lockChannel = lockChannel;
        this.lock = lock;
        this.server = server;
        this.token = token;
        this.portFile = portFile;
    }

    /**
     * Ambil lock instance di {@code dir}, atau minta instance yang sudah berjalan menampilkan window-nya.
     *
     * @throws IOException kalau folder/lock/socket tidak bisa dibuat sama sekali
     */
    public static Result acquire(Path dir) throws IOException {
        Objects.requireNonNull(dir, "dir");
        Files.createDirectories(dir);
        var channel = FileChannel.open(dir.resolve(LOCK_FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null; // dipegang instance lain di JVM yang sama (test)
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
        if (lock == null) {
            channel.close();
            return signal(dir.resolve(PORT_FILE));
        }
        try {
            var server = new ServerSocket();
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 4);
            byte[] token = new byte[TOKEN_BYTES];
            new SecureRandom().nextBytes(token);
            Path portFile = dir.resolve(PORT_FILE);
            writePortFile(portFile, server.getLocalPort(), token);
            var instance = new SingleInstance(channel, lock, server, token, portFile);
            Thread.ofPlatform().name("single-instance").daemon().start(instance::acceptLoop);
            return new Primary(instance);
        } catch (IOException | RuntimeException e) {
            channel.close(); // melepas lock juga
            throw e;
        }
    }

    /**
     * Pasang handler permintaan "tampilkan window". Permintaan yang datang sebelum handler terpasang (selama aplikasi
     * masih memuat) dijalankan sekali saat handler dipasang. Handler dipanggil di thread socket, bukan EDT.
     */
    public void listen(Runnable handler) {
        Objects.requireNonNull(handler, "handler");
        boolean deliver;
        synchronized (handlerLock) {
            onShow = handler;
            deliver = pendingShow;
            pendingShow = false;
        }
        if (deliver) {
            handler.run();
        }
    }

    int port() {
        return server.getLocalPort();
    }

    @Override
    public void close() {
        try {
            server.close();
        } catch (IOException e) {
            log.debug("Socket single-instance tidak bisa ditutup: {}", e.toString());
        }
        try {
            Files.deleteIfExists(portFile);
        } catch (IOException e) {
            log.debug("{} tidak bisa dihapus: {}", portFile, e.toString());
        }
        try {
            lock.release();
            lockChannel.close();
        } catch (IOException e) {
            log.debug("Lock instance tidak bisa dilepas: {}", e.toString());
        }
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try (Socket client = server.accept()) {
                handle(client);
            } catch (SocketException e) {
                if (!server.isClosed()) {
                    log.debug("Koneksi single-instance gagal: {}", e.toString());
                }
            } catch (IOException | RuntimeException e) {
                log.debug("Koneksi single-instance gagal: {}", e.toString());
            }
        }
    }

    private void handle(Socket client) throws IOException {
        client.setSoTimeout(IO_TIMEOUT_MS);
        String[] lines = readRequest(client.getInputStream());
        if (lines == null || !tokenMatches(lines[0])) {
            log.warn("Permintaan single-instance dengan token salah diabaikan");
            return;
        }
        if (!CMD_SHOW.equals(lines[1])) {
            log.debug("Perintah single-instance tidak dikenal diabaikan");
            return;
        }
        Runnable handler;
        synchronized (handlerLock) {
            handler = onShow;
            if (handler == null) {
                pendingShow = true;
            }
        }
        OutputStream out = client.getOutputStream();
        out.write((REPLY_OK + "\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        if (handler != null) {
            handler.run();
        }
    }

    private boolean tokenMatches(String hex) {
        byte[] given;
        try {
            given = HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException e) {
            return false;
        }
        try {
            return MessageDigest.isEqual(given, token); // waktu konstan
        } finally {
            Arrays.fill(given, (byte) 0);
        }
    }

    /** @return {token, command}, atau null kalau format tidak sesuai / terlalu panjang */
    static String[] readRequest(InputStream in) throws IOException {
        var buf = new ByteArrayOutputStream();
        int newlines = 0;
        while (newlines < 2) {
            int b = in.read();
            if (b < 0 || buf.size() >= MAX_REQUEST_BYTES) {
                return null;
            }
            if (b == '\n') {
                newlines++;
            }
            buf.write(b);
        }
        String[] lines = buf.toString(StandardCharsets.US_ASCII).split("\n", -1);
        return lines.length >= 2 ? new String[] {lines[0].strip(), lines[1].strip()} : null;
    }

    private static void writePortFile(Path file, int port, byte[] token) throws IOException {
        byte[] content = (port + "\n" + HexFormat.of().formatHex(token) + "\n").getBytes(StandardCharsets.US_ASCII);
        AtomicFiles.write(file, content);
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException e) {
            // Windows: %APPDATA% sudah hanya bisa dibaca user (ACL bawaan profil)
        }
    }

    /** Hubungi instance yang sedang berjalan lewat data di {@code portFile}. */
    static Result signal(Path portFile) {
        long deadline = System.nanoTime() + PORT_WAIT_MS * 1_000_000L;
        String lastError;
        while (true) {
            try {
                return send(portFile);
            } catch (NoSuchFileException e) {
                lastError = "file port belum ada";
            } catch (IOException | RuntimeException e) {
                lastError = e.toString();
            }
            if (System.nanoTime() >= deadline) {
                return new Unreachable(lastError);
            }
            try {
                Thread.sleep(PORT_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Unreachable("dihentikan");
            }
        }
    }

    private static Result send(Path portFile) throws IOException {
        String[] parts = Files.readString(portFile, StandardCharsets.US_ASCII).strip().split("\n");
        if (parts.length != 2) {
            throw new IOException("format file port tidak sah");
        }
        int port;
        try {
            port = Integer.parseInt(parts[0].strip());
        } catch (NumberFormatException e) {
            throw new IOException("port tidak sah");
        }
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), IO_TIMEOUT_MS);
            socket.setSoTimeout(IO_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write((parts[1].strip() + "\n" + CMD_SHOW + "\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            byte[] reply = socket.getInputStream().readNBytes(REPLY_OK.length());
            if (!REPLY_OK.equals(new String(reply, StandardCharsets.US_ASCII))) {
                throw new IOException("instance lain tidak menjawab");
            }
            return new Signaled();
        }
    }
}

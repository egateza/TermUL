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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Satu instance TermUL per folder data. Instance pertama memegang {@link FileLock} di {@value #LOCK_FILE} dan
 * mendengarkan di socket loopback; instance berikutnya (mis. user membuka shortcut lagi saat TermUL ada di tray) cukup
 * meminta instance pertama menampilkan window (atau menjalankan {@link Request} lain, mis. membuka tab dari menu klik
 * kanan Explorer), lalu keluar. Dengan begitu vault, known_hosts, dan profil tidak dipakai dua proses sekaligus.
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
    /** Cukup untuk token + perintah + argumen berisi path panjang (Windows long path, Base64). */
    private static final int MAX_REQUEST_BYTES = 16 * 1024;
    /** Batas argumen sebelum di-encode (karakter UTF-16). */
    static final int MAX_ARGUMENT_CHARS = 8 * 1024;
    /** Permintaan yang diantre selama aplikasi masih memuat; sisanya dibuang. */
    static final int MAX_PENDING = 8;
    private static final Pattern COMMAND = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    static final int IO_TIMEOUT_MS = 2000;
    /** Instance pertama mungkin baru memegang lock tapi belum menulis file port: tunggu sebentar. */
    static final int PORT_WAIT_MS = 3000;
    private static final int PORT_POLL_MS = 100;

    /**
     * Permintaan dari instance kedua. Makna perintah ditentukan aplikasi; modul ini hanya mengantarkannya.
     *
     * @param command  huruf kecil, angka, dan {@code -}, mis. {@value #CMD_SHOW}
     * @param argument teks bebas (boleh kosong), mis. path folder; dikirim sebagai Base64 UTF-8
     */
    public record Request(String command, String argument) {

        public Request {
            Objects.requireNonNull(command, "command");
            if (!COMMAND.matcher(command).matches()) {
                throw new IllegalArgumentException("Perintah single-instance tidak sah");
            }
            argument = argument == null ? "" : argument;
            if (argument.length() > MAX_ARGUMENT_CHARS) {
                throw new IllegalArgumentException("Argumen single-instance terlalu panjang");
            }
        }

        /** Tampilkan window instance yang sedang berjalan. */
        public static Request show() {
            return new Request(CMD_SHOW, "");
        }

        String encode() {
            return argument.isEmpty() ? command
                    : command + " " + Base64.getEncoder().encodeToString(argument.getBytes(StandardCharsets.UTF_8));
        }

        /** @return null kalau baris perintah tidak sah */
        static Request decode(String line) {
            int space = line.indexOf(' ');
            String command = space < 0 ? line : line.substring(0, space);
            try {
                String argument = space < 0 ? ""
                        : new String(Base64.getDecoder().decode(line.substring(space + 1)), StandardCharsets.UTF_8);
                return new Request(command, argument);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Hasil {@link #acquire}. */
    public sealed interface Result {
    }

    /** Proses ini instance pertama; panggil {@link SingleInstance#listen} setelah window siap. */
    public record Primary(SingleInstance instance) implements Result {
    }

    /** Instance lain sudah ada dan sudah menerima permintaan; proses ini sebaiknya keluar. */
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
    private Consumer<Request> handler; // guarded by handlerLock
    private final List<Request> pending = new ArrayList<>(); // guarded by handlerLock

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
        return acquire(dir, Request.show());
    }

    /**
     * Ambil lock instance di {@code dir}, atau kirim {@code request} ke instance yang sudah berjalan.
     *
     * @throws IOException kalau folder/lock/socket tidak bisa dibuat sama sekali
     */
    public static Result acquire(Path dir, Request request) throws IOException {
        Objects.requireNonNull(dir, "dir");
        Objects.requireNonNull(request, "request");
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
            return signal(dir.resolve(PORT_FILE), request);
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
     * Pasang handler permintaan dari instance kedua. Permintaan yang datang sebelum handler terpasang (selama aplikasi
     * masih memuat) diantre lalu dijalankan berurutan saat handler dipasang; permintaan yang sama persis cukup sekali.
     * Handler dipanggil di thread socket (atau thread pemanggil method ini), bukan EDT.
     */
    public void listen(Consumer<Request> handler) {
        Objects.requireNonNull(handler, "handler");
        List<Request> deliver;
        synchronized (handlerLock) {
            this.handler = handler;
            deliver = List.copyOf(pending);
            pending.clear();
        }
        deliver.forEach(handler);
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
        Request request = Request.decode(lines[1]);
        if (request == null) {
            log.debug("Perintah single-instance tidak sah diabaikan");
            return;
        }
        Consumer<Request> current;
        synchronized (handlerLock) {
            current = handler;
            if (current == null && pending.size() < MAX_PENDING && !pending.contains(request)) {
                pending.add(request);
            }
        }
        OutputStream out = client.getOutputStream();
        out.write((REPLY_OK + "\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        if (current != null) {
            current.accept(request);
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
    static Result signal(Path portFile, Request request) {
        long deadline = System.nanoTime() + PORT_WAIT_MS * 1_000_000L;
        String lastError;
        while (true) {
            try {
                return send(portFile, request);
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

    private static Result send(Path portFile, Request request) throws IOException {
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
            out.write((parts[1].strip() + "\n" + request.encode() + "\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            byte[] reply = socket.getInputStream().readNBytes(REPLY_OK.length());
            if (!REPLY_OK.equals(new String(reply, StandardCharsets.US_ASCII))) {
                throw new IOException("instance lain tidak menjawab");
            }
            return new Signaled();
        }
    }
}

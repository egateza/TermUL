package dev.egateza.termul.core.wsl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** {@link WslRunner} dengan {@code %SystemRoot%\System32\wsl.exe}. Hanya bermakna di Windows. */
public final class ProcessWslRunner implements WslRunner {

    /** Batas output yang dibaca per perintah; output {@code wsl.exe} yang dipakai di sini hanya beberapa baris. */
    private static final int MAX_OUTPUT = 64 * 1024;

    private final Path exe;

    public ProcessWslRunner() {
        this(Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"), "System32", "wsl.exe"));
    }

    ProcessWslRunner(Path exe) {
        this.exe = exe;
    }

    @Override
    public boolean available() {
        return Files.isRegularFile(exe);
    }

    @Override
    public Result run(List<String> args, Duration timeout) throws IOException {
        var process = builder(args).redirectErrorStream(true).start();
        process.getOutputStream().close(); // tidak ada input: perintah yang menunggu stdin langsung dapat EOF
        var output = CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream()),
                command -> Thread.ofVirtual().name("wsl-output").start(command));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("wsl.exe tidak selesai dalam " + timeout.toSeconds() + " detik");
            }
            return new Result(process.exitValue(), WslOutput.decode(output.join()));
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Dibatalkan", e);
        }
    }

    @Override
    public Handle spawn(List<String> args) throws IOException {
        var process = builder(args)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        return new Handle() {
            @Override
            public boolean isAlive() {
                return process.isAlive();
            }

            @Override
            public void stop() {
                process.destroy();
            }
        };
    }

    private ProcessBuilder builder(List<String> args) {
        var command = new ArrayList<String>(args.size() + 1);
        command.add(exe.toString());
        command.addAll(args);
        var pb = new ProcessBuilder(command);
        pb.environment().put("WSL_UTF8", "1"); // WSL baru: --list dalam UTF-8; versi lama mengabaikannya
        return pb;
    }

    private static byte[] readLimited(InputStream in) {
        var out = new ByteArrayOutputStream();
        var buf = new byte[4096];
        try (in) {
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (out.size() < MAX_OUTPUT) {
                    out.write(buf, 0, Math.min(n, MAX_OUTPUT - out.size()));
                } // sisanya tetap dibaca (dibuang) supaya proses tidak macet karena pipe penuh
            }
        } catch (IOException e) {
            // proses dihentikan; pakai output yang sudah terbaca
        }
        return out.toByteArray();
    }
}

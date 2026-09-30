package dev.egateza.myterm.core.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Utilitas tulis file secara atomic: tulis ke temp file di direktori yang sama, fsync, lalu rename. */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /**
     * Menulis {@code content} ke {@code target} secara atomic. Kalau proses mati di tengah jalan,
     * file lama tetap utuh (paling buruk tertinggal file {@code .tmp}).
     */
    public static void write(Path target, byte[] content) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "." + target.getFileName(), ".tmp");
        try {
            try (var ch = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                var buf = ByteBuffer.wrap(content);
                while (buf.hasRemaining()) {
                    ch.write(buf);
                }
                ch.force(true);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}

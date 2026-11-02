package dev.egateza.termul.core.wsl;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** Menjalankan {@code wsl.exe}. Dipisah dari {@link WslDistros} supaya logikanya bisa diuji tanpa WSL. */
public interface WslRunner {

    /** @param output stdout + stderr yang sudah di-decode (lihat {@link WslOutput#decode}) */
    record Result(int exitCode, String output) {
        public boolean ok() {
            return exitCode == 0;
        }
    }

    /** true kalau {@code wsl.exe} ada di sistem ini. */
    boolean available();

    /** Jalankan {@code wsl.exe args...}, tunggu selesai. Blocking. */
    Result run(List<String> args, Duration timeout) throws IOException;
}

package dev.egateza.termul.core.wsl;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** Menjalankan {@code wsl.exe}. Dipisah dari {@link WslManager} supaya logikanya bisa diuji tanpa WSL. */
public interface WslRunner {

    /** @param output stdout + stderr yang sudah di-decode (lihat {@link WslOutput#decode}) */
    record Result(int exitCode, String output) {
        public boolean ok() {
            return exitCode == 0;
        }
    }

    /** Proses {@code wsl.exe} yang berjalan lama (keep-alive distro). */
    interface Handle {
        boolean isAlive();

        /** Hentikan proses; tidak melempar exception. */
        void stop();
    }

    /** true kalau {@code wsl.exe} ada di sistem ini. */
    boolean available();

    /** Jalankan {@code wsl.exe args...}, tunggu selesai. Blocking. */
    Result run(List<String> args, Duration timeout) throws IOException;

    /** Jalankan {@code wsl.exe args...} di background tanpa menunggu; output dibuang. */
    Handle spawn(List<String> args) throws IOException;
}

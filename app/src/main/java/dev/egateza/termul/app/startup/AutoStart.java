package dev.egateza.termul.app.startup;

import dev.egateza.termul.core.Os;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Jalankan TermUL saat user login ke OS. Sumber kebenarannya entri milik OS (registry {@code Run} di Windows,
 * LaunchAgent di macOS), bukan {@code config.json}, supaya status di menu sama dengan yang dilihat user di pengaturan
 * OS. Aplikasi yang dijalankan otomatis menerima {@link #ARG_TRAY}: start tersembunyi, tanpa meminta master password
 * dan tanpa konek ke server.
 *
 * <p>Hanya untuk TermUL hasil instalasi/app-image (launcher jpackage). Dari JAR atau IDE tidak ada path executable
 * yang stabil, jadi statusnya {@link Status#UNSUPPORTED}. Semua method boleh blocking ringan (registry/disk): jangan
 * panggil di EDT.
 */
public interface AutoStart {

    /** Argumen command line: start tersembunyi di tray (atau ter-minimize kalau ikon tray mati). */
    String ARG_TRAY = "--tray";

    /** Diset launcher jpackage: path {@code TermUL.exe} / {@code TermUL.app/Contents/MacOS/TermUL}. */
    String JPACKAGE_APP_PATH = "jpackage.app-path";

    enum Status {
        ENABLED,
        DISABLED,
        /** Terdaftar, tapi dimatikan user di pengaturan OS (Windows: Settings → Apps → Startup). */
        DISABLED_BY_SYSTEM,
        /** Bukan TermUL hasil instalasi (dijalankan dari JAR/IDE). */
        UNSUPPORTED
    }

    Status status();

    /** Daftarkan atau hapus entri startup. Menyalakan juga membatalkan "dimatikan di pengaturan OS". */
    void setEnabled(boolean on) throws IOException;

    /** Untuk OS yang sedang berjalan; null kalau OS ini tidak didukung sama sekali (opsi disembunyikan). */
    static AutoStart forCurrentOs() {
        return of(Os.current(), System::getProperty, Path.of(System.getProperty("user.home")), WinRegistry::jna);
    }

    /**
     * @param props    pembaca system property ({@value #JPACKAGE_APP_PATH})
     * @param registry pembuat akses registry (hanya dipanggil di Windows)
     */
    static AutoStart of(Os os, Function<String, String> props, Path userHome,
                        Supplier<WinRegistry> registry) {
        String appPath = props.apply(JPACKAGE_APP_PATH);
        if (appPath != null && appPath.isBlank()) {
            appPath = null;
        }
        return switch (os) {
            case WINDOWS -> appPath == null ? Unsupported.INSTANCE : new WindowsAutoStart(registry.get(), appPath);
            case MAC -> appPath == null ? Unsupported.INSTANCE : new MacAutoStart(userHome, appPath);
            case OTHER -> null;
        };
    }

    /** Dijalankan dari JAR/IDE: tidak ada executable yang bisa didaftarkan. */
    enum Unsupported implements AutoStart {
        INSTANCE;

        @Override
        public Status status() {
            return Status.UNSUPPORTED;
        }

        @Override
        public void setEnabled(boolean on) throws IOException {
            throw new IOException("Run at startup hanya tersedia untuk TermUL hasil instalasi");
        }
    }
}

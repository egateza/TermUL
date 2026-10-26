package dev.egateza.termul.app.startup;

import dev.egateza.termul.core.Os;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Menu klik kanan Explorer "TermUL ▸ Buka &lt;shell&gt; di sini" (Windows): membuka tab terminal lokal di folder yang
 * diklik, lewat {@link OpenLocalRequest}. Sumber kebenarannya registry milik user, bukan {@code config.json}, sama
 * seperti {@link AutoStart}.
 *
 * <p>Hanya untuk TermUL hasil instalasi/app-image (launcher jpackage); dari JAR atau IDE statusnya
 * {@link Status#UNSUPPORTED}. Semua method boleh blocking ringan (registry): jangan panggil di EDT.
 */
public interface ExplorerMenu {

    /** Batas item submenu; Explorer membatasi jumlah item cascading menu. */
    int MAX_ENTRIES = 16;

    /**
     * Satu item submenu.
     *
     * @param shell id pilihan terminal lokal
     * @param label teks item, mis. "Buka Git Bash di sini"
     */
    record Entry(UUID shell, String label) {

        public Entry {
            Objects.requireNonNull(shell, "shell");
            Objects.requireNonNull(label, "label");
        }
    }

    enum Status {
        ENABLED,
        DISABLED,
        /** Bukan TermUL hasil instalasi (dijalankan dari JAR/IDE). */
        UNSUPPORTED
    }

    Status status();

    /** Pasang (dengan daftar shell {@code entries}) atau hapus menu. */
    void setEnabled(boolean on, List<Entry> entries) throws IOException;

    /** Tulis ulang daftar shell kalau menu sedang terpasang (profil lokal atau distro WSL berubah). */
    void sync(List<Entry> entries) throws IOException;

    /** Untuk OS yang sedang berjalan; null kalau OS ini tidak didukung (opsi disembunyikan). */
    static ExplorerMenu forCurrentOs() {
        return of(Os.current(), System::getProperty, WinRegistry::jna);
    }

    /**
     * @param props    pembaca system property ({@value AutoStart#JPACKAGE_APP_PATH})
     * @param registry pembuat akses registry (hanya dipanggil di Windows)
     */
    static ExplorerMenu of(Os os, Function<String, String> props, Supplier<WinRegistry> registry) {
        if (!os.isWindows()) {
            return null;
        }
        String appPath = props.apply(AutoStart.JPACKAGE_APP_PATH);
        return appPath == null || appPath.isBlank() ? Unsupported.INSTANCE
                : new WindowsExplorerMenu(registry.get(), appPath);
    }

    /** Dijalankan dari JAR/IDE: tidak ada executable yang bisa didaftarkan. */
    enum Unsupported implements ExplorerMenu {
        INSTANCE;

        @Override
        public Status status() {
            return Status.UNSUPPORTED;
        }

        @Override
        public void setEnabled(boolean on, List<Entry> entries) throws IOException {
            throw new IOException("Menu klik kanan Explorer hanya tersedia untuk TermUL hasil instalasi");
        }

        @Override
        public void sync(List<Entry> entries) {
        }
    }
}

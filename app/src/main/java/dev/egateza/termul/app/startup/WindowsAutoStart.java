package dev.egateza.termul.app.startup;

import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Run at startup Windows lewat {@code HKCU\...\CurrentVersion\Run} (tanpa admin). Windows menyimpan toggle di Settings
 * → Apps → Startup terpisah di {@code ...\Explorer\StartupApproved\Run}: byte pertama ganjil = dimatikan user.
 */
final class WindowsAutoStart implements AutoStart {

    private static final Logger log = LoggerFactory.getLogger(WindowsAutoStart.class);

    static final String RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    static final String APPROVED_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run";
    static final String VALUE_NAME = "TermUL";

    private final WinRegistry registry;
    private final String appPath;

    WindowsAutoStart(WinRegistry registry, String appPath) {
        this.registry = Objects.requireNonNull(registry);
        this.appPath = Objects.requireNonNull(appPath);
    }

    /** Command yang didaftarkan: path di-quote (bisa berisi spasi, mis. {@code C:\Program Files\TermUL}). */
    String command() {
        return "\"" + appPath + "\" " + ARG_TRAY;
    }

    @Override
    public Status status() {
        try {
            String registered = registry.readString(RUN_KEY, VALUE_NAME);
            // entri dari lokasi instalasi lain dianggap belum aktif: mencentang lagi memperbaikinya
            if (registered == null || !registered.strip().equalsIgnoreCase(command())) {
                return Status.DISABLED;
            }
            byte[] approved = registry.readBinary(APPROVED_KEY, VALUE_NAME);
            return approved != null && approved.length > 0 && (approved[0] & 1) == 1
                    ? Status.DISABLED_BY_SYSTEM : Status.ENABLED;
        } catch (IOException e) {
            log.warn("Status run at startup tidak terbaca: {}", e.getMessage());
            return Status.DISABLED;
        }
    }

    @Override
    public void setEnabled(boolean on) throws IOException {
        if (on) {
            registry.writeString(RUN_KEY, VALUE_NAME, command());
        } else {
            registry.delete(RUN_KEY, VALUE_NAME);
        }
        // tanpa value ini Windows menganggap entri aktif; user baru saja memilih lewat TermUL
        registry.delete(APPROVED_KEY, VALUE_NAME);
    }
}

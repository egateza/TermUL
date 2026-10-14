package dev.egateza.termul.app.startup;

import com.sun.jna.Library;
import com.sun.jna.Native;
import dev.egateza.termul.core.Os;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Windows hanya mengizinkan proses yang sedang di depan (baru dibuka user) memindahkan window ke depan. Instance kedua
 * TermUL memberikan izin itu sebelum meminta instance pertama menampilkan window-nya; tanpa ini Windows hanya
 * mengedipkan tombol taskbar. Di OS lain tidak melakukan apa-apa.
 */
public final class ForegroundPermission {

    private static final Logger log = LoggerFactory.getLogger(ForegroundPermission.class);
    private static final int ASFW_ANY = -1;

    /** Dimuat malas supaya OS lain tidak pernah menyentuh user32. */
    interface User32 extends Library {
        boolean AllowSetForegroundWindow(int processId);
    }

    private ForegroundPermission() {
    }

    public static void allowAny() {
        if (!Os.current().isWindows()) {
            return;
        }
        try {
            Native.load("user32", User32.class).AllowSetForegroundWindow(ASFW_ANY);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            log.debug("AllowSetForegroundWindow gagal: {}", e.toString());
        }
    }
}

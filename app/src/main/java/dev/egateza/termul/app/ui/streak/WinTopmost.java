package dev.egateza.termul.app.ui.streak;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import java.awt.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Menegaskan ulang posisi window di urutan paling atas (Windows). Taskbar juga window "topmost": saat tray atau taskbar
 * diklik, taskbar naik ke atas semua window topmost lain, termasuk widget yang ditaruh di atasnya, dan Java tidak
 * menaikkan widget lagi karena status always-on-top-nya tidak berubah. Tidak mengambil fokus. Hanya dipanggil di
 * Windows.
 */
final class WinTopmost {

    private static final Logger log = LoggerFactory.getLogger(WinTopmost.class);
    private static final Pointer HWND_TOPMOST = new Pointer(-1);
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOACTIVATE = 0x0010;

    /** Fungsi Win32 yang dipakai; dimuat malas supaya OS lain tidak pernah menyentuh user32. */
    interface User32 extends Library {
        boolean SetWindowPos(Pointer hwnd, Pointer insertAfter, int x, int y, int cx, int cy, int flags);
    }

    private static User32 user32; // EDT
    private static boolean failed; // EDT; jangan coba terus kalau API tidak tersedia

    private WinTopmost() {
    }

    static void raise(Window window) {
        if (failed || !window.isDisplayable()) {
            return;
        }
        try {
            if (user32 == null) {
                user32 = Native.load("user32", User32.class);
            }
            Pointer hwnd = Native.getWindowPointer(window);
            if (hwnd != null) {
                user32.SetWindowPos(hwnd, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOSIZE | SWP_NOMOVE | SWP_NOACTIVATE);
            }
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            failed = true;
            log.debug("SetWindowPos topmost gagal: {}", e.toString());
        }
    }
}

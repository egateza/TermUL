package dev.egateza.termul.app.ui.streak;

import com.sun.jna.Library;
import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Status tombol mouse langsung dari Windows, tidak lewat event window. Dipakai saat menyeret widget: window widget
 * dibuat ulang ketika pindah monitor, dan mouse capture window lama ikut hilang, jadi event "dilepas" belum tentu
 * datang ke Java. Hanya dipanggil di Windows.
 */
final class WinMouse {

    private static final Logger log = LoggerFactory.getLogger(WinMouse.class);
    private static final int VK_LBUTTON = 0x01;
    private static final int VK_RBUTTON = 0x02;
    private static final int SM_SWAPBUTTON = 23;

    /** Fungsi Win32 yang dipakai; dimuat malas supaya OS lain tidak pernah menyentuh user32. */
    interface User32 extends Library {
        short GetAsyncKeyState(int key);

        int GetSystemMetrics(int index);
    }

    private static User32 user32; // EDT

    private WinMouse() {
    }

    /**
     * Tombol utama (kiri, atau kanan kalau tombol ditukar di pengaturan mouse) sedang ditekan.
     * Kalau API tidak bisa dipanggil, dianggap sudah dilepas supaya seret tidak menggantung.
     */
    static boolean primaryDown() {
        try {
            if (user32 == null) {
                user32 = Native.load("user32", User32.class);
            }
            int key = user32.GetSystemMetrics(SM_SWAPBUTTON) != 0 ? VK_RBUTTON : VK_LBUTTON;
            return (user32.GetAsyncKeyState(key) & 0x8000) != 0;
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            log.debug("Status tombol mouse tidak terbaca: {}", e.toString());
            return false;
        }
    }
}

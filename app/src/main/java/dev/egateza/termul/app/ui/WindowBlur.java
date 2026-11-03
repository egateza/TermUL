package dev.egateza.termul.app.ui;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import dev.egateza.termul.core.Os;
import java.awt.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blur dari Windows untuk apa pun yang ada di belakang window (desktop, window lain), terlihat di piksel window yang
 * tembus (ADR 0008). Lewat {@code SetWindowCompositionAttribute} + {@code ACCENT_ENABLE_BLURBEHIND}: API Windows yang
 * tidak terdokumentasi tetapi dipakai luas, dan bekerja di window beralpha per piksel (layered) milik Java. Animasi
 * maximize/restore/Snap DWM tetap jalan: selama animasi latar tampil tembus tanpa blur, lalu blur muncul di tempat
 * yang benar. Backdrop acrylic sistem ({@code DWMWA_SYSTEMBACKDROP_TYPE}) tidak dipakai, lihat ADR. Kekuatan blur diatur
 * Windows. Hanya Windows; di OS lain tidak melakukan apa-apa. EDT.
 */
final class WindowBlur {

    private static final Logger log = LoggerFactory.getLogger(WindowBlur.class);

    private static final int WCA_ACCENT_POLICY = 19;
    static final int ACCENT_DISABLED = 0;
    static final int ACCENT_ENABLE_BLURBEHIND = 3;
    private static final int ACCENT_POLICY_SIZE = 16; // AccentState, AccentFlags, GradientColor, AnimationId (int)

    interface User32 extends Library {
        boolean SetWindowCompositionAttribute(Pointer hwnd, Pointer data);
    }

    private static User32 user32; // EDT
    private static boolean failed; // EDT
    private static Window blurred; // window yang blurnya menyala, lihat isOn; EDT

    private WindowBlur() {
    }

    /** @return true kalau OS ini bisa memakai blur di belakang window */
    static boolean supported() {
        return Os.current().isWindows();
    }

    /** @return true kalau blur sedang menyala di {@code window} */
    static boolean isOn(Window window) {
        return window != null && window == blurred;
    }

    /**
     * Nyalakan atau matikan blur. Butuh HWND: kalau window belum {@code displayable}, panggil lagi setelah
     * {@code addNotify}.
     *
     * @return true kalau Windows menerima perubahannya
     */
    static boolean set(Window window, boolean on) {
        if (failed || !supported() || !window.isDisplayable()) {
            return false;
        }
        try {
            if (user32 == null) {
                user32 = Native.load("user32", User32.class);
            }
            Pointer hwnd = Native.getWindowPointer(window);
            if (hwnd == null) {
                return false;
            }
            try (var policy = new Memory(ACCENT_POLICY_SIZE); var data = new Memory(dataSize(Native.POINTER_SIZE))) {
                policy.clear();
                policy.setInt(0, on ? ACCENT_ENABLE_BLURBEHIND : ACCENT_DISABLED);
                writeData(data, Native.POINTER_SIZE, policy);
                boolean ok = user32.SetWindowCompositionAttribute(hwnd, data);
                if (ok) {
                    blurred = on ? window : null;
                }
                return ok;
            }
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            failed = true; // mis. Windows lama tanpa fungsi ini: transparansi tetap jalan, hanya tanpa blur
            log.warn("Blur di belakang window tidak tersedia: {}", e.toString());
            return false;
        }
    }

    /** @return ukuran struct {@code WINDOWCOMPOSITIONATTRIBDATA}: int (+ padding), pointer, SIZE_T */
    static int dataSize(int pointerSize) {
        return pointerSize * 3;
    }

    /** Isi {@code WINDOWCOMPOSITIONATTRIBDATA} {Attrib = WCA_ACCENT_POLICY, pvData = policy, cbData = 16}. */
    static void writeData(Pointer data, int pointerSize, Pointer policy) {
        data.setInt(0, WCA_ACCENT_POLICY);
        data.setPointer(pointerSize, policy);
        if (pointerSize == 8) {
            data.setLong(16, ACCENT_POLICY_SIZE);
        } else {
            data.setInt(8, ACCENT_POLICY_SIZE);
        }
    }
}

package dev.egateza.termul.app.terminal;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Kunci terminal profil sensitif: setiap kali fokus kembali ke terminal setelah pindah ke tab, panel, atau aplikasi
 * lain, input ditahan sampai user menekan Enter. Mencegah perintah yang dimaksudkan untuk server dev terketik di server
 * produksi karena salah tab. Kembali dalam {@link #GRACE_MILLIS} (mis. klik singkat di tempat lain) tidak mengunci.
 *
 * <p>Logika murni tanpa Swing; waktu diberikan pemanggil. Dipakai di EDT.
 */
final class SensitiveLock {

    /** Pergi sesingkat ini dianggap tidak pindah konteks. */
    static final long GRACE_MILLIS = 1_500;

    /** Perlakuan untuk satu KEY_PRESSED selama terkunci. */
    enum KeyAction {
        /** Diteruskan seperti biasa (tidak terkunci, atau shortcut aplikasi). */
        PASS,
        /** Ditelan: tidak sampai ke server. */
        SWALLOW,
        /** Enter: buka kunci, Enter-nya sendiri ditelan. */
        UNLOCK
    }

    private long leftAt;    // 0 = fokus tidak sedang di luar terminal ini
    private boolean locked;

    /** Fokus pindah ke luar terminal ini (tab/panel/aplikasi lain). */
    void left(long now) {
        if (leftAt == 0) {
            leftAt = now;
        }
    }

    /**
     * Fokus kembali ke terminal.
     *
     * @param sensitive profil saat ini sensitif (dibaca saat itu, profil bisa sudah diedit)
     * @return true kalau terminal (sekarang) terkunci
     */
    boolean returned(long now, boolean sensitive) {
        boolean away = leftAt != 0 && now - leftAt >= GRACE_MILLIS;
        leftAt = 0;
        if (!sensitive) {
            locked = false;
        } else if (away) {
            locked = true;
        }
        return locked;
    }

    boolean isLocked() {
        return locked;
    }

    void unlock() {
        locked = false;
    }

    /**
     * @param appShortcut key ini shortcut aplikasi (pindah/tutup tab, dll.) yang tetap boleh jalan saat terkunci
     */
    KeyAction onKeyPressed(int keyCode, int modifiersEx, boolean appShortcut) {
        if (!locked) {
            return KeyAction.PASS;
        }
        if (keyCode == KeyEvent.VK_ENTER && modifiersEx == 0) {
            locked = false;
            return KeyAction.UNLOCK;
        }
        if (appShortcut || isTabSwitch(keyCode, modifiersEx)) {
            return KeyAction.PASS;
        }
        return KeyAction.SWALLOW;
    }

    /** Ctrl+Tab / Ctrl+Shift+Tab: pindah tab (lihat CLAUDE.md, juga di Mac). */
    private static boolean isTabSwitch(int keyCode, int modifiersEx) {
        int mods = modifiersEx & ~InputEvent.SHIFT_DOWN_MASK;
        return keyCode == KeyEvent.VK_TAB && mods == InputEvent.CTRL_DOWN_MASK;
    }
}

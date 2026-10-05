package dev.egateza.termul.app.ui;

import dev.egateza.termul.core.Os;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;

/**
 * Shortcut aplikasi sesuai OS. Windows/Linux memakai Ctrl; macOS memakai Cmd (⌘) seperti aplikasi Mac lain.
 * Di Mac, Ctrl tetap milik terminal (Ctrl+C, Ctrl+D, Ctrl+R, ...), jadi shortcut aplikasi tidak bentrok dengan shell.
 */
public final class Shortcuts {

    private static final boolean MAC = Os.current().isMac();

    /** Modifier utama: Ctrl, atau Cmd di macOS. */
    public static final int MENU = menuMask(MAC);
    public static final int MENU_SHIFT = MENU | InputEvent.SHIFT_DOWN_MASK;

    private Shortcuts() {
    }

    static int menuMask(boolean mac) {
        return mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
    }

    /** Modifier utama + {@code keyCode}, mis. Ctrl+N / Cmd+N. */
    public static KeyStroke menu(int keyCode) {
        return KeyStroke.getKeyStroke(keyCode, MENU);
    }

    /** Modifier utama + Shift + {@code keyCode}, mis. Ctrl+Shift+P / Cmd+Shift+P. */
    public static KeyStroke menuShift(int keyCode) {
        return KeyStroke.getKeyStroke(keyCode, MENU_SHIFT);
    }

    /** Sambung ulang: Ctrl+F5; di Mac Cmd+R (tombol F di MacBook butuh fn). */
    public static KeyStroke reconnect() {
        return reconnect(MAC);
    }

    static KeyStroke reconnect(boolean mac) {
        return mac ? KeyStroke.getKeyStroke(KeyEvent.VK_R, menuMask(true))
                : KeyStroke.getKeyStroke(KeyEvent.VK_F5, menuMask(false));
    }

    /** Tutup tab: Ctrl+Shift+W (Ctrl+W milik shell); di Mac Cmd+W seperti aplikasi Mac lain. */
    public static KeyStroke closeTab() {
        return closeTab(MAC);
    }

    static KeyStroke closeTab(boolean mac) {
        return mac ? KeyStroke.getKeyStroke(KeyEvent.VK_W, menuMask(true))
                : KeyStroke.getKeyStroke(KeyEvent.VK_W, menuMask(false) | InputEvent.SHIFT_DOWN_MASK);
    }

    /** Keluar: Alt+F4; di Mac null, karena Cmd+Q sudah ditangani menu aplikasi macOS (lihat {@link MacIntegration}). */
    public static KeyStroke exit() {
        return MAC ? null : KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK);
    }

    /** True kalau modifier utama ditekan (mis. Ctrl+klik / Cmd+klik tab). Di Mac, Ctrl+klik adalah klik kanan. */
    public static boolean isMenuDown(InputEvent e) {
        return (e.getModifiersEx() & MENU) != 0;
    }

    /**
     * Shortcut aplikasi yang diambil sebelum terminal (supaya tetap jalan saat fokus di terminal dan tidak terkirim
     * ke remote): modifier utama + Shift, atau shortcut tanpa Shift yang tidak dipakai shell ({@link #reconnect()},
     * {@link #closeTab()}). Kombinasi dengan Alt tidak pernah diambil (AltGr di Windows = Ctrl+Alt).
     */
    public static boolean isAppCombo(KeyStroke ks) {
        return isAppCombo(ks, MAC);
    }

    static boolean isAppCombo(KeyStroke ks, boolean mac) {
        int mods = ks.getModifiers();
        int menu = menuMask(mac);
        if ((mods & menu) == 0 || (mods & InputEvent.ALT_DOWN_MASK) != 0) {
            return false;
        }
        if (mac && (mods & InputEvent.CTRL_DOWN_MASK) != 0) {
            return false; // Cmd+Ctrl = shortcut sistem macOS (mis. layar penuh)
        }
        if ((mods & InputEvent.SHIFT_DOWN_MASK) != 0) {
            return true;
        }
        var plain = KeyStroke.getKeyStroke(ks.getKeyCode(), menu);
        return plain.equals(reconnect(mac)) || plain.equals(closeTab(mac));
    }

    /** Teks shortcut untuk pesan di UI: "Ctrl+Shift+P" di Windows, "⇧⌘P" di Mac. */
    public static String text(KeyStroke ks) {
        return text(ks, MAC);
    }

    static String text(KeyStroke ks, boolean mac) {
        int mods = ks.getModifiers();
        var sb = new StringBuilder();
        if (mac) {
            // urutan simbol standar macOS: ⌃ ⌥ ⇧ ⌘
            appendIf(sb, mods, InputEvent.CTRL_DOWN_MASK, "⌃");
            appendIf(sb, mods, InputEvent.ALT_DOWN_MASK, "⌥");
            appendIf(sb, mods, InputEvent.SHIFT_DOWN_MASK, "⇧");
            appendIf(sb, mods, InputEvent.META_DOWN_MASK, "⌘");
        } else {
            appendIf(sb, mods, InputEvent.CTRL_DOWN_MASK, "Ctrl+");
            appendIf(sb, mods, InputEvent.ALT_DOWN_MASK, "Alt+");
            appendIf(sb, mods, InputEvent.SHIFT_DOWN_MASK, "Shift+");
        }
        return sb.append(keyText(ks.getKeyCode())).toString();
    }

    /** Nama modifier utama untuk teks seperti "Ctrl+klik": "Ctrl", atau "⌘" di Mac. */
    public static String menuKeyName() {
        return MAC ? "⌘" : "Ctrl";
    }

    private static void appendIf(StringBuilder sb, int mods, int mask, String text) {
        if ((mods & mask) != 0) {
            sb.append(text);
        }
    }

    private static String keyText(int code) {
        if ((code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) || (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9)) {
            return String.valueOf((char) code);
        }
        if (code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12) {
            return "F" + (code - KeyEvent.VK_F1 + 1);
        }
        return switch (code) {
            case KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD -> "+";
            case KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> "-";
            default -> KeyEvent.getKeyText(code);
        };
    }
}

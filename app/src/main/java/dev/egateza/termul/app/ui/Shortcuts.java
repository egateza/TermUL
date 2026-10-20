package dev.egateza.termul.app.ui;

import dev.egateza.termul.core.Os;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;

/**
 * Shortcut aplikasi sesuai OS. Windows/Linux memakai Ctrl; macOS memakai Cmd (⌘) seperti aplikasi Mac lain. Shortcut
 * per aksi bisa diganti user ({@link ShortcutAction}, {@link KeyBindings}, Pengaturan → Shortcut).
 * Di Mac, Ctrl tetap milik terminal (Ctrl+C, Ctrl+D, Ctrl+R, ...), jadi shortcut aplikasi tidak bentrok dengan shell;
 * satu-satunya pengecualian ⌃Tab / ⌃⇧Tab untuk pindah tab ({@link #nextTab()}).
 */
public final class Shortcuts {

    private static final boolean MAC = Os.current().isMac();

    /** Modifier utama: Ctrl, atau Cmd di macOS. */
    public static final int MENU = menuMask(MAC);
    public static final int MENU_SHIFT = MENU | InputEvent.SHIFT_DOWN_MASK;

    private static volatile KeyBindings bindings = KeyBindings.defaults(MAC); // ditulis di EDT, dibaca di mana saja

    private Shortcuts() {
    }

    static int menuMask(boolean mac) {
        return KeyBindings.menuMask(mac);
    }

    /** Pasang shortcut efektif (default + override user). EDT. */
    public static void use(KeyBindings next) {
        bindings = next;
    }

    public static KeyBindings bindings() {
        return bindings;
    }

    /** @return shortcut aksi saat ini, atau null kalau dikosongkan user */
    public static KeyStroke get(ShortcutAction action) {
        return bindings.get(action);
    }

    /** Teks shortcut aksi untuk pesan UI, mis. "⇧⌘P"; "—" kalau aksi tanpa shortcut. */
    public static String text(ShortcutAction action) {
        KeyStroke ks = get(action);
        return ks == null ? "—" : text(ks);
    }

    /** Modifier utama + {@code keyCode}, mis. Ctrl+N / Cmd+N. Untuk shortcut lokal panel yang tidak bisa diganti. */
    public static KeyStroke menu(int keyCode) {
        return KeyStroke.getKeyStroke(keyCode, MENU);
    }

    /** Modifier utama + Shift + {@code keyCode}, mis. Ctrl+Shift+P / Cmd+Shift+P. */
    public static KeyStroke menuShift(int keyCode) {
        return KeyStroke.getKeyStroke(keyCode, MENU_SHIFT);
    }

    /** Nomor tab (1..9) kalau {@code ks} shortcut "Pindah ke tab N", selain itu 0. */
    public static int tabNumber(KeyStroke ks) {
        return bindings.actionFor(ks).map(ShortcutAction::tabNumber).orElse(0);
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
     * ke remote). Hanya kombinasi yang memang dipakai suatu aksi, dan salah satu dari: modifier utama + Shift; aksi
     * yang default-nya sengaja diambil tanpa Shift (Ctrl+F5 / ⌘R, ⌘W, ⌘T, Ctrl+Tab, Ctrl/⌘+1..9); atau shortcut yang
     * diganti user sendiri (dialog sudah memperingatkan kalau kombinasinya biasa dipakai shell). Shortcut default tanpa
     * Shift lainnya (Ctrl+N, Ctrl+F, ...) tetap milik shell saat fokus di terminal. Kombinasi dengan Alt tidak pernah
     * diambil (AltGr di Windows = Ctrl+Alt).
     */
    public static boolean isAppCombo(KeyStroke ks) {
        return isAppCombo(ks, bindings);
    }

    static boolean isAppCombo(KeyStroke ks, KeyBindings b) {
        int mods = ks.getModifiers();
        if ((mods & InputEvent.ALT_DOWN_MASK) != 0) {
            return false;
        }
        var action = b.actionFor(ks);
        if (action.isEmpty()) {
            return false;
        }
        if (b.isOverridden(action.get()) || action.get().grabbedPlain()) {
            return true;
        }
        int menu = menuMask(b.mac());
        boolean systemMac = b.mac() && (mods & InputEvent.CTRL_DOWN_MASK) != 0; // Cmd+Ctrl / Ctrl di Mac
        return (mods & menu) != 0 && (mods & InputEvent.SHIFT_DOWN_MASK) != 0 && !systemMac;
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
            case KeyEvent.VK_TAB -> "Tab"; // getKeyText bergantung OS (macOS: ⇥)
            default -> KeyEvent.getKeyText(code);
        };
    }
}

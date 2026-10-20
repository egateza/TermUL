package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Set;
import javax.swing.KeyStroke;

/**
 * Aturan shortcut custom (dialog Shortcut). Ditolak: tanpa modifier, kombinasi Alt/Option (AltGr di Windows, karakter
 * khusus di Mac), shortcut sistem OS, dan inject password tanpa modifier utama. Diperingatkan: kombinasi yang biasa
 * dipakai shell / program terminal (Ctrl+R, Ctrl+C, ...) atau copy/paste terminal, karena shortcut custom selalu
 * diambil sebelum terminal.
 */
final class ShortcutRules {

    enum Level { OK, WARNING, ERROR }

    /** @param message kosong kalau {@link Level#OK} */
    record Verdict(Level level, String message) {
        static final Verdict OK = new Verdict(Level.OK, "");

        boolean allowed() {
            return level != Level.ERROR;
        }
    }

    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    private static final int ALT = InputEvent.ALT_DOWN_MASK;
    private static final int META = InputEvent.META_DOWN_MASK;

    private static final Set<KeyStroke> MAC_SYSTEM = Set.of(
            KeyStroke.getKeyStroke(KeyEvent.VK_Q, META),
            KeyStroke.getKeyStroke(KeyEvent.VK_H, META),
            KeyStroke.getKeyStroke(KeyEvent.VK_M, META),
            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, META),
            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, META | SHIFT),
            KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, META),
            KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, CTRL),
            KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, META),
            KeyStroke.getKeyStroke(KeyEvent.VK_3, META | SHIFT),
            KeyStroke.getKeyStroke(KeyEvent.VK_4, META | SHIFT),
            KeyStroke.getKeyStroke(KeyEvent.VK_5, META | SHIFT));
    private static final Set<KeyStroke> WINDOWS_SYSTEM = Set.of(
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, CTRL),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, CTRL | SHIFT));

    private ShortcutRules() {
    }

    /** true kalau tombol yang ditekan baru modifier (dialog masih menunggu tombol utama). */
    static boolean isModifierOnly(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META, KeyEvent.VK_ALT_GRAPH,
                 KeyEvent.VK_WINDOWS, KeyEvent.VK_CAPS_LOCK, KeyEvent.VK_UNDEFINED -> true;
            default -> false;
        };
    }

    static Verdict check(ShortcutAction action, KeyStroke ks, boolean mac) {
        int mods = ks.getModifiers();
        int code = ks.getKeyCode();
        String text = Shortcuts.text(ks, mac);
        if ((mods & ALT) != 0) {
            return error(mac ? "shortcut.rule.option" : "shortcut.rule.alt", text);
        }
        boolean ctrl = (mods & CTRL) != 0;
        boolean meta = (mods & META) != 0;
        if (!ctrl && !(mac && meta)) {
            return error("shortcut.rule.modifier", text);
        }
        if (!mac && meta) {
            return error("shortcut.rule.windowsKey", text);
        }
        if (mac && ctrl && meta) {
            return error("shortcut.rule.system", text);
        }
        KeyStroke plain = KeyStroke.getKeyStroke(code, mods & (CTRL | SHIFT | META));
        if ((mac ? MAC_SYSTEM : WINDOWS_SYSTEM).contains(plain)) {
            return error("shortcut.rule.system", text);
        }
        if (action.sendsSecret() && (mods & KeyBindings.menuMask(mac)) == 0) {
            return error("shortcut.rule.secret", text, mac ? "⌘" : "Ctrl");
        }
        boolean shift = (mods & SHIFT) != 0;
        if (isTerminalCopyPaste(code, mods, mac)) {
            return warning("shortcut.rule.copyPaste", text);
        }
        if (mac ? ctrl && code != KeyEvent.VK_TAB : !shift && !isNavigationKey(code)) {
            return warning("shortcut.rule.shell", text);
        }
        return Verdict.OK;
    }

    /** Copy/paste milik JediTerm: Ctrl+Shift+C/V, Ctrl/Shift+Insert di Windows; ⌘C/⌘V di Mac. */
    private static boolean isTerminalCopyPaste(int code, int mods, boolean mac) {
        int m = mods & (CTRL | SHIFT | META);
        if (mac) {
            return m == META && (code == KeyEvent.VK_C || code == KeyEvent.VK_V);
        }
        return m == (CTRL | SHIFT) && (code == KeyEvent.VK_C || code == KeyEvent.VK_V)
                || code == KeyEvent.VK_INSERT;
    }

    /** Ctrl + tombol ini jarang dipakai shell (F1–F12, Tab, PageUp/Down), jadi tidak perlu peringatan. */
    private static boolean isNavigationKey(int code) {
        return code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12 || code == KeyEvent.VK_TAB
                || code == KeyEvent.VK_PAGE_UP || code == KeyEvent.VK_PAGE_DOWN;
    }

    private static Verdict error(String key, Object... args) {
        return new Verdict(Level.ERROR, I18n.t(key, args));
    }

    private static Verdict warning(String key, Object... args) {
        return new Verdict(Level.WARNING, I18n.t(key, args));
    }
}

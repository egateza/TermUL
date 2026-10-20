package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;

/**
 * Aksi yang shortcut-nya bisa diganti user (Pengaturan → Shortcut). Default ditulis dalam notasi {@link KeyBindings}
 * ({@code mod} = Ctrl di Windows, ⌘ di Mac). {@link #id()} disimpan di {@code shortcuts.json}: jangan diganti.
 */
public enum ShortcutAction {

    NEW_TAB("tab.new", Category.HOST, "mod+shift+T", "mod+T", true),
    NEW_HOST("host.new", Category.HOST, "mod+N"),
    NEW_LOCAL("local.new", Category.HOST, "mod+shift+O"),
    FIND_HOST("host.find", Category.HOST, "mod+F"),
    TOGGLE_HOST_LIST("host.toggleList", Category.HOST, "mod+shift+B"),

    DUPLICATE_TAB("tab.duplicate", Category.TAB, "mod+shift+D"),
    RECONNECT("tab.reconnect", Category.TAB, "mod+F5", "mod+R", true),
    CLOSE_TAB("tab.close", Category.TAB, "mod+shift+W", "mod+W", true),
    NEXT_TAB("tab.next", Category.TAB, "ctrl+TAB", "ctrl+TAB", true),
    PREVIOUS_TAB("tab.previous", Category.TAB, "ctrl+shift+TAB", "ctrl+shift+TAB", true),
    TAB_1("tab.go1", Category.TAB, "mod+1", "mod+1", true),
    TAB_2("tab.go2", Category.TAB, "mod+2", "mod+2", true),
    TAB_3("tab.go3", Category.TAB, "mod+3", "mod+3", true),
    TAB_4("tab.go4", Category.TAB, "mod+4", "mod+4", true),
    TAB_5("tab.go5", Category.TAB, "mod+5", "mod+5", true),
    TAB_6("tab.go6", Category.TAB, "mod+6", "mod+6", true),
    TAB_7("tab.go7", Category.TAB, "mod+7", "mod+7", true),
    TAB_8("tab.go8", Category.TAB, "mod+8", "mod+8", true),
    TAB_9("tab.go9", Category.TAB, "mod+9", "mod+9", true),

    SFTP("terminal.sftp", Category.TERMINAL, "mod+shift+F"),
    ZOOM_IN("zoom.in", Category.TERMINAL, "mod+EQUALS"),
    ZOOM_OUT("zoom.out", Category.TERMINAL, "mod+MINUS"),
    ZOOM_RESET("zoom.reset", Category.TERMINAL, "mod+0"),
    EDIT_TRACKER("terminal.editTracker", Category.TERMINAL, "mod+shift+E"),
    INJECT_SUDO("inject.sudo", Category.TERMINAL, "mod+shift+P"),
    INJECT_ROOT("inject.root", Category.TERMINAL, "mod+shift+R"),

    GROUP("split.group", Category.SPLIT, "mod+G"),
    UNGROUP("split.ungroup", Category.SPLIT, "mod+shift+G"),
    NEXT_PANE("split.nextPane", Category.SPLIT, "mod+shift+N"),
    CLOSE_PANE("split.closePane", Category.SPLIT, "mod+shift+X"),

    IDLE_NOW("app.idle", Category.APP, "mod+shift+I"),
    SHOW_LOG("app.showLog", Category.APP, "mod+shift+L");

    /** Kelompok di dialog Shortcut, urut sesuai deklarasi. */
    public enum Category {
        HOST, TAB, TERMINAL, SPLIT, APP;

        public String label() {
            return I18n.t("shortcut.category." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private final String id;
    private final Category category;
    private final String windowsDefault;
    private final String macDefault;
    private final boolean grabbedPlain;

    ShortcutAction(String id, Category category, String bothDefault) {
        this(id, category, bothDefault, bothDefault, false);
    }

    /**
     * @param grabbedPlain default-nya diambil sebelum terminal walaupun tanpa Shift (mis. Ctrl+F5, ⌘W, Ctrl+Tab,
     *                     Ctrl+1..9). Aksi lain tanpa Shift (Ctrl+N, Ctrl+F, ...) tetap milik shell saat fokus di
     *                     terminal, kecuali user menggantinya sendiri.
     */
    ShortcutAction(String id, Category category, String windowsDefault, String macDefault, boolean grabbedPlain) {
        this.id = id;
        this.category = category;
        this.windowsDefault = windowsDefault;
        this.macDefault = macDefault;
        this.grabbedPlain = grabbedPlain;
    }

    public String id() {
        return id;
    }

    public Category category() {
        return category;
    }

    String defaultNotation(boolean mac) {
        return mac ? macDefault : windowsDefault;
    }

    boolean grabbedPlain() {
        return grabbedPlain;
    }

    /** Inject password: wajib modifier utama, supaya password tidak terkirim karena tombol tak sengaja. */
    public boolean sendsSecret() {
        return this == INJECT_SUDO || this == INJECT_ROOT;
    }

    /** 1..9 untuk aksi pindah ke tab bernomor, selain itu 0. */
    public int tabNumber() {
        return name().startsWith("TAB_") ? name().charAt(4) - '0' : 0;
    }

    public String label() {
        int n = tabNumber();
        return n > 0 ? I18n.t("shortcut.action.tabNumber", n) : I18n.t("shortcut.action." + id);
    }

    public static ShortcutAction byId(String id) {
        for (var a : values()) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }
}

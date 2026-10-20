package dev.egateza.termul.app.ui;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.KeyStroke;

/**
 * Shortcut efektif per aksi: default OS ditimpa override user (immutable). Notasi di file: modifier dipisah {@code +},
 * diakhiri nama tombol {@link KeyEvent} tanpa {@code VK_}, mis. {@code mod+shift+T}, {@code ctrl+TAB}, {@code mod+F5}.
 * {@code mod} = modifier utama (Ctrl di Windows, ⌘ di Mac), supaya file tetap masuk akal kalau JAR portable dipindah
 * antar-OS; {@code ctrl} di Mac = ⌃, {@code meta} di Windows = tombol Windows. String kosong = tanpa shortcut.
 */
public final class KeyBindings {

    private static final Map<String, Integer> CODES = new HashMap<>();
    private static final Map<Integer, String> NAMES = new HashMap<>();

    static {
        for (var f : KeyEvent.class.getFields()) {
            if (f.getName().startsWith("VK_") && f.getType() == int.class && Modifier.isStatic(f.getModifiers())
                    && !f.getName().equals("VK_SEPARATER")) {
                try {
                    String name = f.getName().substring(3);
                    int code = f.getInt(null);
                    CODES.put(name, code);
                    NAMES.putIfAbsent(code, name);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private final boolean mac;
    private final EnumMap<ShortcutAction, KeyStroke> keys;   // value null = tanpa shortcut
    private final EnumSet<ShortcutAction> overridden;

    private KeyBindings(boolean mac, EnumMap<ShortcutAction, KeyStroke> keys, EnumSet<ShortcutAction> overridden) {
        this.mac = mac;
        this.keys = keys;
        this.overridden = overridden;
    }

    public static KeyBindings defaults(boolean mac) {
        return of(Map.of(), mac);
    }

    /** Override dengan id tidak dikenal atau notasi rusak diabaikan (aksi itu memakai default). */
    public static KeyBindings of(Map<String, String> overrides, boolean mac) {
        var keys = new EnumMap<ShortcutAction, KeyStroke>(ShortcutAction.class);
        var changed = EnumSet.noneOf(ShortcutAction.class);
        for (var a : ShortcutAction.values()) {
            keys.put(a, parse(a.defaultNotation(mac), mac));
        }
        overrides.forEach((id, notation) -> {
            var action = ShortcutAction.byId(id);
            if (action == null) {
                return;
            }
            KeyStroke ks = notation.isEmpty() ? null : parse(notation, mac);
            if (!notation.isEmpty() && ks == null) {
                return;
            }
            if (!java.util.Objects.equals(ks, keys.get(action))) {
                keys.put(action, ks);
                changed.add(action);
            }
        });
        return new KeyBindings(mac, keys, changed);
    }

    public boolean mac() {
        return mac;
    }

    /** @return shortcut aksi, atau null kalau dikosongkan user */
    public KeyStroke get(ShortcutAction action) {
        return keys.get(action);
    }

    public KeyStroke defaultOf(ShortcutAction action) {
        return parse(action.defaultNotation(mac), mac);
    }

    public boolean isOverridden(ShortcutAction action) {
        return overridden.contains(action);
    }

    public Set<ShortcutAction> overriddenActions() {
        return EnumSet.copyOf(overridden);
    }

    /** Aksi yang memakai {@code ks}; yang diganti user menang kalau bentrok dengan default aksi lain. */
    public Optional<ShortcutAction> actionFor(KeyStroke ks) {
        if (ks == null) {
            return Optional.empty();
        }
        ShortcutAction byDefault = null;
        for (var e : keys.entrySet()) {
            if (ks.equals(e.getValue())) {
                if (overridden.contains(e.getKey())) {
                    return Optional.of(e.getKey());
                }
                if (byDefault == null) {
                    byDefault = e.getKey();
                }
            }
        }
        return Optional.ofNullable(byDefault);
    }

    /** @param ks null = tanpa shortcut; sama dengan default = override dihapus */
    public KeyBindings with(ShortcutAction action, KeyStroke ks) {
        var nextKeys = new EnumMap<>(keys);
        var nextOverridden = EnumSet.copyOf(overridden);
        nextKeys.put(action, ks);
        if (java.util.Objects.equals(ks, defaultOf(action))) {
            nextOverridden.remove(action);
        } else {
            nextOverridden.add(action);
        }
        return new KeyBindings(mac, nextKeys, nextOverridden);
    }

    public KeyBindings reset(ShortcutAction action) {
        return with(action, defaultOf(action));
    }

    /** Override untuk disimpan: id → notasi, kosong = tanpa shortcut. */
    public Map<String, String> overrides() {
        var out = new TreeMap<String, String>();
        overridden.forEach(a -> out.put(a.id(), keys.get(a) == null ? "" : format(keys.get(a), mac)));
        return out;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof KeyBindings b && b.mac == mac && b.keys.equals(keys);
    }

    @Override
    public int hashCode() {
        return keys.hashCode();
    }

    // ---------------------------------------------------------------- notasi

    static int menuMask(boolean mac) {
        return mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
    }

    /** @return KeyStroke, atau null kalau notasi tidak valid */
    public static KeyStroke parse(String notation, boolean mac) {
        if (notation == null || notation.isBlank()) {
            return null;
        }
        String[] parts = notation.strip().split("\\+", -1);
        int mods = 0;
        for (int i = 0; i < parts.length - 1; i++) {
            switch (parts[i].strip().toLowerCase(Locale.ROOT)) {
                case "mod" -> mods |= menuMask(mac);
                case "ctrl" -> mods |= InputEvent.CTRL_DOWN_MASK;
                case "shift" -> mods |= InputEvent.SHIFT_DOWN_MASK;
                case "alt" -> mods |= InputEvent.ALT_DOWN_MASK;
                case "meta", "cmd" -> mods |= InputEvent.META_DOWN_MASK;
                default -> {
                    return null;
                }
            }
        }
        Integer code = CODES.get(parts[parts.length - 1].strip().toUpperCase(Locale.ROOT));
        return code == null || ShortcutRules.isModifierOnly(code) ? null : KeyStroke.getKeyStroke(code, mods);
    }

    public static String format(KeyStroke ks, boolean mac) {
        int mods = ks.getModifiers();
        int menu = menuMask(mac);
        var sb = new StringBuilder();
        if ((mods & menu) != 0) {
            sb.append("mod+");
        }
        if ((mods & InputEvent.CTRL_DOWN_MASK) != 0 && menu != InputEvent.CTRL_DOWN_MASK) {
            sb.append("ctrl+");
        }
        if ((mods & InputEvent.META_DOWN_MASK) != 0 && menu != InputEvent.META_DOWN_MASK) {
            sb.append("meta+");
        }
        if ((mods & InputEvent.ALT_DOWN_MASK) != 0) {
            sb.append("alt+");
        }
        if ((mods & InputEvent.SHIFT_DOWN_MASK) != 0) {
            sb.append("shift+");
        }
        return sb.append(NAMES.getOrDefault(ks.getKeyCode(), "UNDEFINED")).toString();
    }
}

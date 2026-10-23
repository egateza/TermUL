package dev.egateza.termul.app.ui;

import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cache font turunan untuk kode paint/render yang berjalan berulang (animasi, renderer).
 *
 * <p>Font UI (FlatLaf, mis. Segoe UI) adalah font komposit. Di JDK, setiap {@link Font#deriveFont} yang mengganti style
 * font komposit membuat {@code CompositeFont} baru beserta cache glyph-nya sendiri. {@code deriveFont} di
 * {@code paintComponent} yang berjalan tiap frame menumpuk ribuan font (ratusan MB) yang baru dibuang saat heap hampir
 * penuh, karena glyph-nya ditahan lewat soft reference. Font turunan yang sama dipakai ulang dari sini.
 */
public final class DerivedFonts {

    private static final int MAX = 64;

    private record Key(Font base, int style, float size) {
    }

    // guarded by CACHE; LRU kecil, karena ukuran yang dihitung dari tinggi komponen bisa bervariasi
    private static final Map<Key, Font> CACHE = new LinkedHashMap<>(16, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Font> eldest) {
            return size() > MAX;
        }
    };

    private DerivedFonts() {
    }

    /** {@code base.deriveFont(style)}, dipakai ulang. */
    public static Font derive(Font base, int style) {
        return derive(base, style, base.getSize2D());
    }

    /** {@code base.deriveFont(style, size)}, dipakai ulang. */
    public static Font derive(Font base, int style, float size) {
        if (base.getStyle() == style && base.getSize2D() == size) {
            return base;
        }
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(new Key(base, style, size), k -> k.base().deriveFont(k.style(), k.size()));
        }
    }

    /** {@code base.deriveFont(size)} (style tetap), dipakai ulang. */
    public static Font derive(Font base, float size) {
        return derive(base, base.getStyle(), size);
    }
}

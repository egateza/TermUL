package dev.egateza.termul.app.ui.fab;

import java.awt.Color;
import java.util.function.Function;

/**
 * Gaya {@link FloatingButton} yang dibaca dari tema aktif. Tema boleh mengisi key {@code FloatingButton.*} di file
 * properties-nya (lihat tema Neobrutalism); tema yang tidak mengisinya mendapat tombol datar berwarna aksen dengan
 * bayangan lembut. Dibaca setiap kali tombol digambar, jadi ganti tema langsung berlaku.
 *
 * <pre>
 * FloatingButton.background / foreground / hoverBackground      tombol utama
 * FloatingButton.secondaryBackground / secondaryForeground      tombol kecil (mis. "Ke bawah")
 * FloatingButton.borderColor / borderWidth                      garis tepi
 * FloatingButton.shadowColor / shadowOffset                     shadowOffset &gt; 0 = bayangan tegas ke kanan-bawah
 * FloatingButton.arc                                            999 = bulat/pil
 * </pre>
 *
 * @param shadowOffset bayangan tegas (px) ke kanan-bawah; 0 = bayangan lembut di bawah tombol
 */
public record FloatingStyle(Color background, Color foreground, Color hoverBackground, Color borderColor,
                            int borderWidth, Color shadowColor, int shadowOffset, int arc) {

    public enum Kind {
        /** Aksi utama: warna aksen tema. */
        PRIMARY,
        /** Tombol bantu yang lebih tenang: warna panel. */
        SECONDARY
    }

    static final Color FALLBACK_ACCENT = new Color(0x2675BF);
    static final int SOFT_SHADOW_ALPHA = 70;

    public boolean hardShadow() {
        return shadowOffset > 0;
    }

    /** Dari {@code UIManager} tema yang aktif. */
    public static FloatingStyle current(Kind kind) {
        return resolve(kind, javax.swing.UIManager::get);
    }

    /** @param lookup pembaca nilai tema (UIManager.get), null kalau key tidak ada */
    static FloatingStyle resolve(Kind kind, Function<String, Object> lookup) {
        Color panel = color(lookup, "Panel.background", Color.WHITE);
        Color text = color(lookup, "Label.foreground", Color.BLACK);
        Color bg;
        Color fg;
        if (kind == Kind.PRIMARY) {
            bg = color(lookup, "FloatingButton.background",
                    color(lookup, "Component.accentColor", color(lookup, "Button.default.background", FALLBACK_ACCENT)));
            fg = color(lookup, "FloatingButton.foreground", readableOn(bg));
        } else {
            bg = color(lookup, "FloatingButton.secondaryBackground", panel);
            fg = color(lookup, "FloatingButton.secondaryForeground", text);
        }
        Color hover = kind == Kind.PRIMARY
                ? color(lookup, "FloatingButton.hoverBackground", blend(bg, fg, 0.12f))
                : blend(bg, fg, 0.10f);
        Integer borderWidthKey = integer(lookup, "FloatingButton.borderWidth");
        // tanpa gaya dari tema, tombol bantu tetap diberi garis tipis supaya terlihat di atas terminal
        int borderWidth = borderWidthKey != null ? borderWidthKey : kind == Kind.SECONDARY ? 1 : 0;
        Color border = color(lookup, "FloatingButton.borderColor", color(lookup, "Component.borderColor", text));
        Integer offset = integer(lookup, "FloatingButton.shadowOffset");
        int shadowOffset = offset == null ? 0 : Math.max(0, offset);
        Color shadow = color(lookup, "FloatingButton.shadowColor",
                shadowOffset > 0 ? Color.BLACK : new Color(0, 0, 0, SOFT_SHADOW_ALPHA));
        Integer arc = integer(lookup, "FloatingButton.arc");
        return new FloatingStyle(bg, fg, hover, border, Math.max(0, borderWidth), shadow, shadowOffset,
                arc == null ? 999 : Math.max(0, arc));
    }

    /** Hitam atau putih, mana yang lebih terbaca di atas {@code bg}. */
    static Color readableOn(Color bg) {
        double luminance = (0.2126 * bg.getRed() + 0.7152 * bg.getGreen() + 0.0722 * bg.getBlue()) / 255;
        return luminance > 0.55 ? Color.BLACK : Color.WHITE;
    }

    static Color blend(Color from, Color to, float amount) {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount),
                from.getAlpha());
    }

    private static Color color(Function<String, Object> lookup, String key, Color fallback) {
        return lookup.apply(key) instanceof Color c ? c : fallback;
    }

    private static Integer integer(Function<String, Object> lookup, String key) {
        return lookup.apply(key) instanceof Integer i ? i : null;
    }
}

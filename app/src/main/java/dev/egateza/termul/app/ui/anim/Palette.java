package dev.egateza.termul.app.ui.anim;

import dev.egateza.termul.app.ui.FontCatalog;
import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;

/**
 * Warna animasi, diturunkan dari tema UI aktif supaya ikut terang/gelap. Warna karakter (Pac-Man, hantu, roket)
 * tetap dan tidak lewat sini.
 *
 * @param fg     teks/garis utama
 * @param dot    titik makanan Pac-Man (fg agak transparan)
 * @param muted  teks/garis sekunder
 * @param accent warna aksen tema
 * @param green  hijau "terminal" (prompt, LED, EKG)
 * @param amber  kuning/oranye (ledakan, makanan Snake)
 * @param line   garis tipis / LED mati
 * @param fill   isi kotak kecil (layar laptop, rak server)
 */
public record Palette(Color fg, Color dot, Color muted, Color accent, Color green, Color amber, Color line, Color fill) {

    private static String monoFamily; // EDT; dicari sekali

    /** Palet dari {@link UIManager} saat ini. EDT. */
    public static Palette current() {
        Color fg = or(UIManager.getColor("Label.foreground"), Color.GRAY);
        Color bg = or(UIManager.getColor("Panel.background"), Color.WHITE);
        boolean dark = luminance(bg) < 0.5;
        Color muted = or(UIManager.getColor("Label.disabledForeground"), fg.darker());
        return new Palette(fg, alpha(fg, 150 / 255.0), muted,
                or(UIManager.getColor("Component.accentColor"), new Color(0x3574F0)),
                dark ? new Color(0x3DDC84) : new Color(0x1C9550),
                dark ? new Color(0xF2B035) : new Color(0xB87400),
                or(UIManager.getColor("Component.borderColor"), muted),
                or(UIManager.getColor("TextField.background"), bg));
    }

    static Color alpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(Math.clamp(a, 0, 1) * c.getAlpha()));
    }

    static double luminance(Color c) {
        return (0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue()) / 255.0;
    }

    /** Font monospace yang enak dibaca (terpasang), dengan fallback logical {@code Monospaced}. */
    static Font mono(float size) {
        if (monoFamily == null) {
            monoFamily = Font.MONOSPACED;
            var installed = java.util.Set.of(FontCatalog.allFamilies());
            for (String f : new String[] {"JetBrains Mono", "Cascadia Mono", "Consolas", "Menlo", "SF Mono", "DejaVu Sans Mono"}) {
                if (installed.contains(f)) {
                    monoFamily = f;
                    break;
                }
            }
        }
        return new Font(monoFamily, Font.PLAIN, 1).deriveFont(size);
    }

    private static Color or(Color c, Color fallback) {
        return c != null ? c : fallback;
    }
}

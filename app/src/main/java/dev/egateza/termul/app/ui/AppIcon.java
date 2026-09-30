package dev.egateza.termul.app.ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Ikon aplikasi, masing-masing tersedia di semua {@link IconSet} (SVG di {@code resources/.../icons/<set>/}).
 * Set aktif dan warna dibaca saat ikon digambar, jadi ganti set/tema cukup dengan repaint.
 *
 * <p><b>Menambah/mengganti ikon</b>: jangan edit daftar di bawah secara manual, jalankan dari root project
 * {@code java tools/AddIcon.java NAMA fa-nama material_nama} (lihat javadoc tool tersebut).
 */
public enum AppIcon {
    // <icons> (dikelola oleh tools/AddIcon.java)
    ANGLES_LEFT,
    ANGLES_RIGHT,
    ARROW_UP,
    BROOM,
    DOWNLOAD,
    EXIT,
    FILE,
    FOLDER,
    FOLDER_OPEN,
    FOLDER_PLUS,
    HISTORY,
    HOME,
    INFO,
    LINK,
    PEN,
    PERMISSION,
    RECONNECT,
    REFRESH,
    SERVER,
    SFTP,
    STAR,
    TERMINAL,
    TRASH,
    UPLOAD,
    XMARK;
    // </icons>

    public static final int SIZE = 16;
    static final String DIR = "dev/egateza/termul/app/icons/";

    /** Warna folder di panel SFTP/host tree; cukup kontras di tema gelap maupun terang. */
    public static final Color FOLDER_COLOR = new Color(0xD9A441);

    private static volatile IconSet current = IconSet.FONT_AWESOME;

    /** Ganti set ikon untuk seluruh aplikasi. Panggil di EDT, lalu repaint window. */
    public static void use(IconSet set) {
        current = set;
    }

    public static IconSet current() {
        return current;
    }

    String resource(IconSet set) {
        return DIR + set.dir() + "/" + name().toLowerCase(Locale.ROOT) + ".svg";
    }

    /** Ikon 16×16 dengan warna teks tema. */
    public Icon icon() {
        return icon(SIZE);
    }

    public Icon icon(int size) {
        return icon(size, AppIcon::foreground);
    }

    /** Ikon dengan warna tertentu (dibaca saat digambar). */
    public Icon icon(int size, Supplier<Color> color) {
        return new SetIcon(this, size, color, false);
    }

    /** SVG untuk set tertentu (dipakai {@link SetIcon} dan test). */
    FlatSVGIcon svg(IconSet set, int size, Supplier<Color> color) {
        var svg = new FlatSVGIcon(resource(set), size, size);
        svg.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color.get()));
        return svg;
    }

    private static Color foreground() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : Color.GRAY;
    }

    /** Mendelegasikan ke SVG dari set yang sedang aktif; SVG per set dibuat sekali saat pertama dipakai. */
    private static final class SetIcon implements Icon, FlatLaf.DisabledIconProvider {
        private final AppIcon icon;
        private final int size;
        private final Supplier<Color> color;
        private final boolean disabled;
        private final Map<IconSet, Icon> cache = new EnumMap<>(IconSet.class); // EDT

        SetIcon(AppIcon icon, int size, Supplier<Color> color, boolean disabled) {
            this.icon = icon;
            this.size = size;
            this.color = color;
            this.disabled = disabled;
        }

        private Icon delegate() {
            return cache.computeIfAbsent(current, set -> {
                var svg = icon.svg(set, size, color);
                return disabled ? svg.getDisabledIcon() : svg;
            });
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            delegate().paintIcon(c, g, x, y);
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public Icon getDisabledIcon() {
            return new SetIcon(icon, size, color, true);
        }
    }
}

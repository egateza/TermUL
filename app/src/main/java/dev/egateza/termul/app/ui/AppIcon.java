package dev.egateza.termul.app.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.util.function.Supplier;
import javax.swing.UIManager;

/**
 * Ikon aplikasi dari Font Awesome Free 7.3.1 (SVG di {@code resources/.../icons/fa}, lisensi ikon CC BY 4.0,
 * lihat {@code LICENSE.txt} di folder itu). {@code viewBox} sudah dibuat persegi supaya tidak gepeng di 16×16.
 * Warna dibaca dari {@link UIManager} saat digambar, jadi ikon ikut tema tanpa perlu dibuat ulang.
 * Tambah ikon: unduh SVG ke folder tersebut, lalu tambahkan konstanta di sini.
 */
public enum AppIcon {
    ARROW_UP("arrow-up"),
    BROOM("broom"),
    DOWNLOAD("download"),
    FILE("file"),
    FOLDER("folder"),
    FOLDER_OPEN("folder-open"),
    FOLDER_PLUS("folder-plus"),
    HOME("house"),
    INFO("circle-info"),
    LINK("link"),
    PEN("pen"),
    PERMISSION("user-lock"),
    RECONNECT("rotate-right"),
    REFRESH("arrows-rotate"),
    SERVER("server"),
    TRASH("trash-can"),
    UPLOAD("upload"),
    XMARK("xmark");

    public static final int SIZE = 16;
    static final String DIR = "dev/egateza/termul/app/icons/fa/";

    /** Warna folder di panel SFTP/host tree; cukup kontras di tema gelap maupun terang. */
    public static final Color FOLDER_COLOR = new Color(0xD9A441);

    private final String file;

    AppIcon(String file) {
        this.file = file;
    }

    String resource() {
        return DIR + file + ".svg";
    }

    /** Ikon 16×16 dengan warna teks tema. */
    public FlatSVGIcon icon() {
        return icon(SIZE);
    }

    public FlatSVGIcon icon(int size) {
        return icon(size, AppIcon::foreground);
    }

    /** Ikon dengan warna tertentu (dibaca saat digambar). */
    public FlatSVGIcon icon(int size, Supplier<Color> color) {
        var icon = new FlatSVGIcon(resource(), size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color.get()));
        return icon;
    }

    private static Color foreground() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : Color.GRAY;
    }
}

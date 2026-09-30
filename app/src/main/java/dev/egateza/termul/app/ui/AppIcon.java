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
    CLONE("clone"),
    CODE("code"),
    COPY("copy"),
    DOWNLOAD("download"),
    EDIT("pen-to-square"),
    EXIT("right-from-bracket"),
    FILE("file"),
    FOLDER("folder"),
    FOLDER_OPEN("folder-open"),
    FOLDER_PLUS("folder-plus"),
    FOLDER_TREE("folder-tree"),
    FONT_SIZE("text-height"),
    GEAR("gear"),
    HOME("house"),
    INFO("circle-info"),
    KEY("key"),
    LINK("link"),
    LIST("list-check"),
    LOCK("lock"),
    PEN("pen"),
    PERMISSION("user-lock"),
    PLUS("plus"),
    RECONNECT("rotate-right"),
    REFRESH("arrows-rotate"),
    ROOT("user-shield"),
    SEARCH("magnifying-glass"),
    SERVER("server"),
    TERMINAL("terminal"),
    TRASH("trash-can"),
    UNLOCK("lock-open"),
    UPLOAD("upload"),
    XMARK("xmark"),
    ZOOM_IN("magnifying-glass-plus"),
    ZOOM_OUT("magnifying-glass-minus");

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

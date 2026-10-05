package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.config.AppConfig;
import java.awt.Rectangle;

/**
 * Gaya tombol buka/tutup panel host (Pengaturan → Tampilan → Panel host → Gaya tombol). Semua gaya kecuali
 * {@link #TAB_BAR} adalah tombol melayang ({@link HostDrawer.Toggle}) di tepi kiri area terminal.
 */
public enum HostToggleStyle {
    /** Lingkaran kecil berbayangan di atas garis pembatas, dekat bagian atas. */
    EDGE_CIRCLE("edgeCircle"),
    /** Kapsul menempel di tepi, sudut kanan membulat (gaya lama). */
    EDGE_TAB("edgeTab"),
    /** Garis tipis samar yang melebar menjadi tombol saat disorot. */
    GRABBER("grabber"),
    /** Tidak terlihat sampai kursor mendekati tepi kiri. */
    HOVER_REVEAL("hoverReveal"),
    /** Ikon di ujung kiri baris tab; tidak melayang di atas terminal. */
    TAB_BAR("tabBar"),
    /** Lingkaran warna aksen di pojok kiri bawah. */
    CORNER("corner");

    static final int TAB_WIDTH = 22;
    static final int TAB_HEIGHT = 48;
    /** Diameter lingkaran plus ruang bayangan di sekelilingnya. */
    static final int CIRCLE_BOX = 30;
    static final int CIRCLE_TOP = 40;
    static final int GRABBER_WIDTH = 22;
    static final int GRABBER_HEIGHT = 48;
    static final int REVEAL_SIZE = 28;
    /** Lebar area di kanan tepi yang memunculkan tombol {@link #HOVER_REVEAL}. */
    static final int REVEAL_ZONE = 44;
    static final int CORNER_BOX = 36;
    static final int CORNER_MARGIN = 8;

    private final String id;

    HostToggleStyle(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String label() {
        return I18n.t("hostToggle." + id);
    }

    public String tooltip() {
        return I18n.t("hostToggle." + id + ".tip");
    }

    /** Gaya yang digambar tombol melayang: {@link #TAB_BAR} memakai lingkaran saat ikon tab bar tidak bisa dipakai. */
    HostToggleStyle floating() {
        return this == TAB_BAR ? EDGE_CIRCLE : this;
    }

    /**
     * Posisi tombol melayang.
     *
     * @param area     area di kanan panel host (area terminal yang terlihat), koordinat layered pane
     * @param lineX    x tengah garis pembatas panel host
     * @param openLeft panel host sedang terbuka di kiri {@code area} (tombol lingkaran boleh menumpang di garis)
     */
    Rectangle bounds(Rectangle area, int lineX, boolean openLeft) {
        return switch (floating()) {
            case EDGE_TAB -> new Rectangle(area.x, area.y + (area.height - TAB_HEIGHT) / 2, TAB_WIDTH, TAB_HEIGHT);
            case GRABBER -> new Rectangle(area.x + 2, area.y + (area.height - GRABBER_HEIGHT) / 2,
                    GRABBER_WIDTH, GRABBER_HEIGHT);
            case HOVER_REVEAL -> new Rectangle(area.x + 6, area.y + (area.height - REVEAL_SIZE) / 2,
                    REVEAL_SIZE, REVEAL_SIZE);
            case CORNER -> new Rectangle(area.x + CORNER_MARGIN,
                    area.y + area.height - CORNER_BOX - CORNER_MARGIN, CORNER_BOX, CORNER_BOX);
            default -> new Rectangle(openLeft ? lineX - CIRCLE_BOX / 2 : area.x + 2, area.y + CIRCLE_TOP,
                    CIRCLE_BOX, CIRCLE_BOX);
        };
    }

    /** Area yang memunculkan tombol {@link #HOVER_REVEAL} saat kursor ada di dalamnya. */
    static Rectangle revealZone(Rectangle area) {
        return new Rectangle(area.x - 4, area.y, REVEAL_ZONE + 4, area.height);
    }

    public static HostToggleStyle fromId(String id) {
        for (var style : values()) {
            if (style.id.equals(id)) {
                return style;
            }
        }
        return EDGE_CIRCLE; // = AppConfig.DEFAULT_HOST_TOGGLE
    }
}

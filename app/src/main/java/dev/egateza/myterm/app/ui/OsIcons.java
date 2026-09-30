package dev.egateza.myterm.app.ui;

import dev.egateza.myterm.core.profile.OsInfo;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.Icon;

/**
 * Ikon OS: badge bulat berwarna khas distro dengan huruf awal (digambar, tanpa file logo).
 * Distro tak dikenal → badge abu-abu dengan huruf awal ID; belum terdeteksi → lingkaran kosong.
 */
public final class OsIcons {

    /** Warna + huruf per ID os-release. */
    record Style(Color color, String letter) {
    }

    static final Map<String, Style> STYLES = Map.ofEntries(
            Map.entry("ubuntu", new Style(new Color(0xE95420), "U")),
            Map.entry("debian", new Style(new Color(0xA80030), "D")),
            Map.entry("alpine", new Style(new Color(0x0D597F), "A")),
            Map.entry("centos", new Style(new Color(0x932279), "C")),
            Map.entry("rhel", new Style(new Color(0xCC0000), "R")),
            Map.entry("rocky", new Style(new Color(0x10B981), "R")),
            Map.entry("almalinux", new Style(new Color(0x0F4266), "A")),
            Map.entry("fedora", new Style(new Color(0x51A2DA), "F")),
            Map.entry("arch", new Style(new Color(0x1793D1), "A")),
            Map.entry("opensuse-leap", new Style(new Color(0x73BA25), "S")),
            Map.entry("opensuse-tumbleweed", new Style(new Color(0x73BA25), "S")),
            Map.entry("sles", new Style(new Color(0x30BA78), "S")),
            Map.entry("amzn", new Style(new Color(0xFF9900), "A")),
            Map.entry("ol", new Style(new Color(0xC74634), "O")),
            Map.entry("raspbian", new Style(new Color(0xC51A4A), "R")),
            Map.entry("linuxmint", new Style(new Color(0x87CF3E), "M")),
            Map.entry("kali", new Style(new Color(0x367BF0), "K")),
            Map.entry("freebsd", new Style(new Color(0xAB2B28), "B")));

    private static final Color UNKNOWN = new Color(0x6B7280);
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();
    private static final Icon UNDETECTED = new Badge(null, null, 14);

    private OsIcons() {
    }

    /** Ikon untuk OS (null = belum terdeteksi). */
    public static Icon of(OsInfo os) {
        if (os == null) {
            return UNDETECTED;
        }
        return CACHE.computeIfAbsent(os.id(), id -> {
            Style s = STYLES.get(id);
            return s != null ? new Badge(s.color(), s.letter(), 14)
                    : new Badge(UNKNOWN, id.substring(0, 1).toUpperCase(java.util.Locale.ROOT), 14);
        });
    }

    /** Teks tooltip OS. */
    public static String label(OsInfo os) {
        return os == null ? "OS belum terdeteksi (connect sekali untuk mendeteksi)" : os.label();
    }

    /** Badge bulat; {@code color == null} = lingkaran outline (belum terdeteksi). */
    private record Badge(Color color, String letter, int size) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            var g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                if (color == null) {
                    g2.setColor(UNKNOWN);
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawOval(x + 1, y + 1, size - 3, size - 3);
                    return;
                }
                g2.setColor(color);
                g2.fillOval(x, y, size - 1, size - 1);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size - 5));
                var fm = g2.getFontMetrics();
                int tx = x + (size - 1 - fm.stringWidth(letter)) / 2;
                int ty = y + (size - 1 - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(letter, tx, ty);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}

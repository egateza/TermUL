package dev.egateza.termul.app.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;

/**
 * Ikon tab sesi: logo OS host, lalu ikon terminal (ada shell) dan/atau ikon SFTP (panel SFTP terbuka atau tab SFTP
 * saja), berjajar dari kiri ke kanan.
 */
public record TabIcon(Icon os, boolean terminal, boolean sftp) implements Icon {

    static final int GAP = 3;
    /** Ikon terminal/SFTP sedikit lebih kecil dari ikon menu supaya tinggi tab tidak berubah. */
    static final int PART_SIZE = 14;
    private static final Icon TERMINAL_ICON = AppIcon.TERMINAL.icon(PART_SIZE);
    private static final Icon SFTP_ICON = AppIcon.SFTP.icon(PART_SIZE);

    /** Ikon yang digambar, urut dari kiri. */
    List<Icon> parts() {
        var parts = new ArrayList<Icon>(3);
        parts.add(os);
        if (terminal) {
            parts.add(TERMINAL_ICON);
        }
        if (sftp && os != OsIcons.SFTP_ONLY) { // ikon host sudah ikon SFTP: jangan dobel
            parts.add(SFTP_ICON);
        }
        return parts;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        int height = getIconHeight();
        for (Icon part : parts()) {
            part.paintIcon(c, g, x, y + (height - part.getIconHeight()) / 2);
            x += part.getIconWidth() + GAP;
        }
    }

    @Override
    public int getIconWidth() {
        var parts = parts();
        return parts.stream().mapToInt(Icon::getIconWidth).sum() + GAP * (parts.size() - 1);
    }

    @Override
    public int getIconHeight() {
        return parts().stream().mapToInt(Icon::getIconHeight).max().orElse(0);
    }
}

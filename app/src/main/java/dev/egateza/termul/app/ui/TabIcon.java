package dev.egateza.termul.app.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;

/**
 * Ikon tab sesi: logo OS host, lalu ikon SFTP kalau panel SFTP terbuka (atau tab SFTP saja), berjajar dari kiri ke
 * kanan. Terminal tidak diberi ikon sendiri: tab tanpa ikon SFTP berarti terminal.
 */
public record TabIcon(Icon os, boolean sftp) implements Icon {

    static final int GAP = 3;
    /** Ikon SFTP sedikit lebih kecil dari ikon menu supaya tinggi tab tidak berubah. */
    static final int PART_SIZE = 14;
    private static final Icon SFTP_ICON = AppIcon.SFTP.icon(PART_SIZE);

    /** Ikon yang digambar, urut dari kiri. */
    List<Icon> parts() {
        var parts = new ArrayList<Icon>(2);
        parts.add(os);
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

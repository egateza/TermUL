package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class TabIconTest {

    private static final javax.swing.Icon OS = OsIcons.of(null);

    /** Terminal tidak diberi ikon sendiri: hanya logo OS. */
    @Test
    void terminalSajaHanyaMenampilkanOs() {
        var icon = new TabIcon(OS, false);

        assertThat(icon.parts()).containsExactly(OS);
        assertThat(icon.getIconWidth()).isEqualTo(OS.getIconWidth());
    }

    @Test
    void sftpAktifMenampilkanOsDanSftp() {
        var icon = new TabIcon(OS, true);

        assertThat(icon.parts()).hasSize(2).startsWith(OS);
        assertThat(icon.getIconWidth()).isEqualTo(OS.getIconWidth() + TabIcon.GAP + TabIcon.PART_SIZE);
        var img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        icon.paintIcon(null, g, 0, 0); // tidak error
        g.dispose();
    }

    @Test
    void samaKalauIsinyaSama() {
        assertThat(new TabIcon(OS, true)).isEqualTo(new TabIcon(OS, true)).isNotEqualTo(new TabIcon(OS, false));
    }

    @Test
    void hostSftpTidakMenampilkanIkonSftpDobel() {
        assertThat(new TabIcon(OsIcons.SFTP_ONLY, true).parts()).containsExactly(OsIcons.SFTP_ONLY);
    }
}

package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class TabIconTest {

    private static final javax.swing.Icon OS = OsIcons.of(null);

    @Test
    void terminalSajaMenampilkanOsDanTerminal() {
        var icon = new TabIcon(OS, true, false);

        assertThat(icon.parts()).hasSize(2);
        assertThat(icon.getIconWidth()).isEqualTo(OS.getIconWidth() + TabIcon.GAP + TabIcon.PART_SIZE);
    }

    @Test
    void sftpSajaMenampilkanOsDanSftp() {
        var icon = new TabIcon(OS, false, true);

        assertThat(icon.parts()).hasSize(2).doesNotContain(new TabIcon(OS, true, false).parts().get(1));
    }

    @Test
    void terminalDanSftpMenampilkanKeduanya() {
        var icon = new TabIcon(OS, true, true);

        assertThat(icon.parts()).hasSize(3);
        assertThat(icon.getIconWidth()).isEqualTo(OS.getIconWidth() + 2 * (TabIcon.GAP + TabIcon.PART_SIZE));
        assertThat(icon.getIconHeight()).isEqualTo(TabIcon.PART_SIZE);
        var img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        icon.paintIcon(null, g, 0, 0); // tidak error
        g.dispose();
    }

    @Test
    void samaKalauIsinyaSama() {
        assertThat(new TabIcon(OS, true, true)).isEqualTo(new TabIcon(OS, true, true))
                .isNotEqualTo(new TabIcon(OS, true, false));
    }

    @Test
    void hostSftpTidakMenampilkanIkonSftpDobel() {
        assertThat(new TabIcon(OsIcons.SFTP_ONLY, false, true).parts()).containsExactly(OsIcons.SFTP_ONLY);
        assertThat(new TabIcon(OsIcons.SFTP_ONLY, true, true).parts()).hasSize(2);
    }
}

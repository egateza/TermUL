package dev.egateza.termul.app.terminal;

import static dev.egateza.termul.app.terminal.TerminalTab.DUAL_SFTP_WIDTH;
import static dev.egateza.termul.app.terminal.TerminalTab.MIN_TERMINAL_WIDTH;
import static dev.egateza.termul.app.terminal.TerminalTab.sftpWidth;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SftpWidthTest {

    @Test
    void duaPanelMelebarDiWindowLebar() {
        assertThat(sftpWidth(true, 380, 1650)).isEqualTo(DUAL_SFTP_WIDTH);
        assertThat(sftpWidth(false, 380, 1650)).isEqualTo(380);
    }

    @Test
    void terminalTetapPunyaLebarMinimal() {
        // kasus user: window tidak di-maximize, tab ±1000 px -> dulu terminal tinggal ±9 kolom
        int width = sftpWidth(true, 380, 1000);
        assertThat(1000 - width).isEqualTo(MIN_TERMINAL_WIDTH);
    }

    @Test
    void tabSempitTetapMenyisakanPanelSatuKolom() {
        assertThat(sftpWidth(true, 380, 700)).isEqualTo(350);
        assertThat(sftpWidth(false, 380, 900)).isEqualTo(380);
    }

    @Test
    void belumDiLayoutTanpaBatas() {
        assertThat(sftpWidth(true, 380, 0)).isEqualTo(DUAL_SFTP_WIDTH);
    }
}

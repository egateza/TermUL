package dev.egateza.termul.app.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UploadToTest {

    @Test
    void serverBerbedaSelaluBoleh() {
        assertThat(SftpPanel.copyProblem(false, "/home/a/app", true, "/home/a/app/sub")).isNull();
        assertThat(SftpPanel.copyProblem(false, "/home/a/x.txt", false, "/home/a")).isNull();
    }

    @Test
    void diServerYangSamaTolakLokasiSendiriDanFolderKeDalamDirinya() {
        assertThat(SftpPanel.copyProblem(true, "/home/a/x.txt", false, "/home/a")).isEqualTo("sftp.copy.sameLocation");
        assertThat(SftpPanel.copyProblem(true, "/home/a/app", true, "/home/a")).isEqualTo("sftp.copy.sameLocation");
        assertThat(SftpPanel.copyProblem(true, "/home/a/app", true, "/home/a/app")).isEqualTo("sftp.copy.intoItself");
        assertThat(SftpPanel.copyProblem(true, "/home/a/app", true, "/home/a/app/sub/dalam"))
                .isEqualTo("sftp.copy.intoItself");
        // nama mirip bukan berarti di dalamnya
        assertThat(SftpPanel.copyProblem(true, "/home/a/app", true, "/home/a/app2")).isNull();
        assertThat(SftpPanel.copyProblem(true, "/home/a/x.txt", false, "/tmp")).isNull();
    }

    @Test
    void pathPanjangDipotongDiDepan() {
        assertThat(SftpPanel.shortDir("/home/hamid")).isEqualTo("/home/hamid");
        String longDir = "/var/www/html/aplikasi-rekonsiliasi/storage/logs/2026/10";
        assertThat(SftpPanel.shortDir(longDir)).hasSize(40).startsWith("…").endsWith("logs/2026/10");
    }
}

package dev.egateza.termul.app.wsl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.wsl.SshCheck;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SshCheckReportTest {

    @BeforeEach
    void indonesia() {
        I18n.use("id");
    }

    @AfterEach
    void reset() {
        I18n.use(I18n.FALLBACK);
    }

    /** Kasus Ubuntu 22.04 dengan ssh.socket: Port 2211 di config, socket listen di 22. */
    private static SshCheck socket() {
        return new SshCheck(true, true, true, true, Set.of(2211), Set.of(22, 53));
    }

    @Test
    void sshSocketDiberiPerbaikanPindahKeSshService() {
        var report = new SshCheckReport("Ubuntu-22.04", 2211, socket(), false);

        assertThat(report.ok()).isFalse();
        assertThat(report.firstProblem()).contains("ssh.socket").contains("22").contains("2211");
        assertThat(report.fixCommands())
                .contains("systemctl disable --now ssh.socket")
                .contains("systemctl restart ssh")
                .contains("grep :2211")
                .doesNotContain("sed -i"); // Port di config sudah benar
        assertThat(report.checklist()).contains("✘").contains("ssh.socket");
    }

    @Test
    void portBelumDiubahDiberiPerintahSedDanRestart() {
        var check = new SshCheck(true, false, false, true, Set.of(22), Set.of(22));
        var report = new SshCheckReport("Debian", 2222, check, false);

        assertThat(report.fixCommands())
                .contains("Port 2222")
                .contains("service ssh restart")
                .doesNotContain("ssh.socket");
    }

    @Test
    void belumTerpasangMemakaiPanduanLengkap() {
        var check = new SshCheck(false, true, false, false, Set.of(), Set.of());
        var report = new SshCheckReport("Ubuntu", 2222, check, false);

        assertThat(report.firstProblem()).contains("OpenSSH");
        assertThat(report.fixCommands()).contains("apt install -y openssh-server").contains("Port 2222");
    }

    @Test
    void semuaLolos() {
        var check = new SshCheck(true, true, false, true, Set.of(2211), Set.of(2211));
        var report = new SshCheckReport("Ubuntu-22.04", 2211, check, true);

        assertThat(report.ok()).isTrue();
        assertThat(report.firstProblem()).isNull();
        assertThat(report.fixCommands()).isEmpty();
        assertThat(report.checklist()).doesNotContain("✘");
    }

    @Test
    void listenDiDistroTapiTidakTerjangkauDariWindows() {
        var check = new SshCheck(true, true, false, true, Set.of(2211), Set.of(2211));
        var report = new SshCheckReport("Ubuntu-22.04", 2211, check, false);

        assertThat(report.ok()).isFalse();
        assertThat(report.unreachableOnly()).isTrue();
        assertThat(report.fixCommands()).isEmpty();
    }

    @Test
    void socketDenganOverridePortProfilBukanMasalahTapiAdaInstruksiOpsional() {
        // ssh.socket dengan ListenStream=2211 (override): SSH sudah jalan
        var check = new SshCheck(true, true, true, true, Set.of(2211), Set.of(53, 2211));
        var report = new SshCheckReport("Ubuntu-22.04", 2211, check, true);

        assertThat(report.ok()).isTrue();
        assertThat(report.checklist()).doesNotContain("\u2718").contains("\u2139").contains("ssh.socket");
        assertThat(report.fixCommands()).isEmpty();
        assertThat(report.optionalCommands())
                .contains("systemctl disable --now ssh.socket && sudo systemctl enable --now ssh.service")
                .contains("grep :2211")
                .doesNotContain("sed -i");
    }

    @Test
    void tanpaSocketTidakAdaInstruksiOpsional() {
        var check = new SshCheck(true, true, false, true, Set.of(2211), Set.of(2211));

        assertThat(new SshCheckReport("Ubuntu-22.04", 2211, check, true).optionalCommands()).isEmpty();
        assertThat(new SshCheckReport("Ubuntu-22.04", 2211, socketTanpaListen(), false).optionalCommands()).isEmpty();
    }

    private static SshCheck socketTanpaListen() {
        return new SshCheck(true, true, true, true, Set.of(2211), Set.of(22));
    }
}

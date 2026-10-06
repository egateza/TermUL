package dev.egateza.termul.core.wsl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.wsl.SshCheck.Problem;
import org.junit.jupiter.api.Test;

class SshCheckTest {

    /** Ubuntu 22.04 dengan ssh.socket: sshd_config Port 2211, tapi socket listen di 22. */
    private static final String SOCKET = """
            installed=yes
            systemd=yes
            socket=active
            configtest=ok
            config_port=2211
            listen_tool=ss
            listen=53
            listen=22
            listen=22
            """;

    @Test
    void parseOutputScript() {
        var c = SshCheck.parse(SOCKET);

        assertThat(c.installed()).isTrue();
        assertThat(c.systemd()).isTrue();
        assertThat(c.socketActive()).isTrue();
        assertThat(c.configValid()).isTrue();
        assertThat(c.configPorts()).containsExactly(2211);
        assertThat(c.listening()).containsExactlyInAnyOrder(53, 22);
    }

    @Test
    void sshSocketMengabaikanPortDiConfig() {
        assertThat(SshCheck.parse(SOCKET).problems(2211)).containsExactly(Problem.SOCKET_ACTIVATION);
        assertThat(SshCheck.parse(SOCKET).problems(22)).containsExactly(Problem.PORT_NOT_CONFIGURED);
    }

    @Test
    void sshdSiapTanpaMasalah() {
        var c = SshCheck.parse(SOCKET.replace("socket=active", "socket=inactive").replace("listen=22", "listen=2211"));

        assertThat(c.problems(2211)).isEmpty();
        assertThat(c.isListening(2211)).isTrue();
    }

    @Test
    void sshdMatiAtauBelumDiRestart() {
        var c = SshCheck.parse(SOCKET.replace("socket=active", "socket=inactive"));

        assertThat(c.problems(2211)).containsExactly(Problem.NOT_LISTENING);
    }

    @Test
    void portBelumDiubahDanBelumListen() {
        var c = SshCheck.parse("installed=yes\nsystemd=no\nconfigtest=ok\nconfig_port=22\nlisten_tool=ss\nlisten=22\n");

        assertThat(c.problems(2222)).containsExactly(Problem.PORT_NOT_CONFIGURED, Problem.NOT_LISTENING);
    }

    @Test
    void belumTerpasangDanConfigRusakBerhentiDiMasalahItu() {
        assertThat(SshCheck.parse("installed=no\nconfigtest=fail\n").problems(2222))
                .containsExactly(Problem.NOT_INSTALLED);
        assertThat(SshCheck.parse("installed=yes\nconfigtest=fail\nlisten_tool=ss\n").problems(2222))
                .containsExactly(Problem.CONFIG_INVALID);
    }

    @Test
    void portListenTidakDiketahuiTidakDianggapMasalah() {
        var c = SshCheck.parse("installed=yes\nconfigtest=ok\nconfig_port=2222\nlisten_tool=none\n");

        assertThat(c.listening()).isNull();
        assertThat(c.isListening(2222)).isNull();
        assertThat(c.problems(2222)).isEmpty();
    }

    @Test
    void outputKosongBerartiBelumTerpasang() {
        assertThat(SshCheck.parse("").problems(2222)).containsExactly(Problem.NOT_INSTALLED);
    }
}

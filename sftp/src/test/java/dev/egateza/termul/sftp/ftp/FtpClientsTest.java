package dev.egateza.termul.sftp.ftp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;

class FtpClientsTest {

    /**
     * Cache sesi TLS JDK berkunci host + {@code getPort()} socket di bawahnya, jadi socket data harus melaporkan port
     * kanal kontrol agar sesi kontrol di-resume; operasi lain tetap ke socket asli.
     */
    @Test
    void socketDataMelaporkanPortKontrolDanMeneruskanSisanya() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var plain = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
             var accepted = server.accept()) {
            var wrapped = new FtpClients.SessionKeySocket(plain, 21);

            assertThat(wrapped.getPort()).isEqualTo(21);
            assertThat(wrapped.getInetAddress()).isEqualTo(plain.getInetAddress());
            assertThat(wrapped.getLocalPort()).isEqualTo(plain.getLocalPort());
            assertThat(wrapped.isConnected()).isTrue();

            wrapped.getOutputStream().write('x');
            assertThat(accepted.getInputStream().read()).isEqualTo('x');

            wrapped.close();
            assertThat(plain.isClosed()).isTrue();
            assertThat(wrapped.isClosed()).isTrue();
        }
    }
}

package dev.egateza.termul.ssh.tunnel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServerChecksTest {

    @Test
    void portDariSs() {
        String ss = """
                LISTEN 0      4096   127.0.0.53%lo:53         0.0.0.0:*
                LISTEN 0      128          0.0.0.0:22         0.0.0.0:*
                LISTEN 0      244        127.0.0.1:5432       0.0.0.0:*
                LISTEN 0      511       10.0.55.22:8080       0.0.0.0:*
                LISTEN 0      128             [::]:22            [::]:*
                LISTEN 0      4096           [::1]:9000          [::]:*
                LISTEN 0      511                *:3000             *:*
                """;

        // 8080 hanya di IP lain: tidak bentrok dengan forward di loopback server
        assertThat(ServerChecks.parseListening(ss)).containsExactlyInAnyOrder(53, 22, 5432, 9000, 3000);
    }

    @Test
    void portDariNetstat() {
        String netstat = """
                Active Internet connections (only servers)
                Proto Recv-Q Send-Q Local Address           Foreign Address         State
                tcp        0      0 0.0.0.0:22              0.0.0.0:*               LISTEN
                tcp        0      0 127.0.0.1:6379          0.0.0.0:*               LISTEN
                tcp        0      0 192.168.1.5:8443        0.0.0.0:*               LISTEN
                tcp6       0      0 :::80                   :::*                    LISTEN
                """;

        assertThat(ServerChecks.parseListening(netstat)).containsExactlyInAnyOrder(22, 6379, 80);
    }

    @Test
    void outputKosongAtauAneh() {
        assertThat(ServerChecks.parseListening("")).isEmpty();
        assertThat(ServerChecks.parseListening("sh: ss: not found\nbukan tabel")).isEmpty();
    }
}

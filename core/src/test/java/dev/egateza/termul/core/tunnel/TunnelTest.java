package dev.egateza.termul.core.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TunnelTest {

    private final UUID db = UUID.randomUUID();
    private final UUID gateway = UUID.randomUUID();

    @Test
    void localDenganTujuanLengkap() {
        var t = Tunnel.create("  PostgreSQL ", TunnelType.LOCAL, db, 5433, " localhost ", 5432, false, true);

        assertThat(t.name()).isEqualTo("PostgreSQL");
        assertThat(t.destinationHost()).isEqualTo("localhost");
        assertThat(t.bindAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void dynamicTanpaTujuanDanRemoteTidakPernahDibagi() {
        var socks = Tunnel.create("Proxy", TunnelType.DYNAMIC, gateway, 1080, "abaikan", 99, true, false);
        var remote = Tunnel.create("Webhook", TunnelType.REMOTE, gateway, 9000, "localhost", 8080, true, false);

        assertThat(socks.destinationHost()).isEmpty();
        assertThat(socks.destinationPort()).isZero();
        assertThat(socks.bindAddress()).isEqualTo("0.0.0.0");
        assertThat(remote.shared()).isFalse();
    }

    @Test
    void isianTidakValidDitolak() {
        assertThatThrownBy(() -> Tunnel.create(" ", TunnelType.LOCAL, db, 1, "h", 1, false, false))
                .hasMessageContaining("Nama");
        assertThatThrownBy(() -> Tunnel.create("x", TunnelType.LOCAL, db, 0, "h", 1, false, false))
                .hasMessageContaining("Port sumber");
        assertThatThrownBy(() -> Tunnel.create("x", TunnelType.LOCAL, db, 1, "h", 70000, false, false))
                .hasMessageContaining("Port tujuan");
        assertThatThrownBy(() -> Tunnel.create("x", TunnelType.LOCAL, db, 1, "a b", 1, false, false))
                .hasMessageContaining("Host tujuan");
        assertThatThrownBy(() -> Tunnel.create("a\u001bb", TunnelType.LOCAL, db, 1, "h", 1, false, false))
                .hasMessageContaining("kontrol");
    }

    @Test
    void parsePortHanyaAngka1Sampai65535() {
        assertThat(TunnelChecks.parsePort(" 5433 ")).contains(5433);
        assertThat(TunnelChecks.parsePort("65535")).contains(65535);
        assertThat(TunnelChecks.parsePort("0")).isEmpty();
        assertThat(TunnelChecks.parsePort("65536")).isEmpty();
        assertThat(TunnelChecks.parsePort("-1")).isEmpty();
        assertThat(TunnelChecks.parsePort("54a")).isEmpty();
        assertThat(TunnelChecks.parsePort("")).isEmpty();
        assertThat(TunnelChecks.parsePort("99999999999")).isEmpty();
    }

    @Test
    void bentrokPortDiPcIniAtauDiServerYangSama() {
        var pg = Tunnel.create("PostgreSQL", TunnelType.LOCAL, db, 5433, "localhost", 5432, false, true);
        var socks = Tunnel.create("Proxy", TunnelType.DYNAMIC, gateway, 1080, "", 0, false, false);
        var hook = Tunnel.create("Webhook", TunnelType.REMOTE, gateway, 9000, "localhost", 8080, false, false);
        var all = List.of(pg, socks, hook);

        assertThat(TunnelChecks.clash(all, null, TunnelType.DYNAMIC, gateway, 5433)).contains(pg);
        assertThat(TunnelChecks.clash(all, pg.id(), TunnelType.LOCAL, db, 5433)).isEmpty(); // dirinya sendiri
        assertThat(TunnelChecks.clash(all, null, TunnelType.LOCAL, db, 9000)).isEmpty(); // 9000 di server, bukan PC
        assertThat(TunnelChecks.clash(all, null, TunnelType.REMOTE, gateway, 9000)).contains(hook);
        assertThat(TunnelChecks.clash(all, null, TunnelType.REMOTE, db, 9000)).isEmpty(); // server lain
        assertThat(TunnelChecks.clash(all, null, TunnelType.REMOTE, gateway, 1080)).isEmpty();
    }

    @Test
    void portKosongBerikutnyaUntukDuplikat() {
        var pg = Tunnel.create("PostgreSQL", TunnelType.LOCAL, db, 5433, "localhost", 5432, false, true);
        var pg2 = Tunnel.create("PostgreSQL 2", TunnelType.DYNAMIC, gateway, 5434, "", 0, false, false);
        var hook = Tunnel.create("Webhook", TunnelType.REMOTE, gateway, 9000, "localhost", 8080, false, false);
        var all = List.of(pg, pg2, hook);

        assertThat(TunnelChecks.nextFreePort(all, TunnelType.LOCAL, db, 5433)).isEqualTo(5435); // 5434 dipakai socks
        assertThat(TunnelChecks.nextFreePort(all, TunnelType.REMOTE, gateway, 9000)).isEqualTo(9001);
        assertThat(TunnelChecks.nextFreePort(all, TunnelType.REMOTE, db, 8999)).isEqualTo(9000); // server lain
        assertThat(TunnelChecks.nextFreePort(all, TunnelType.LOCAL, db, 65535)).isEqualTo(65535);
    }

    @Test
    void perintahSshSetara() {
        var pg = Tunnel.create("PostgreSQL", TunnelType.LOCAL, db, 5433, "localhost", 5432, false, true);
        var hook = Tunnel.create("Webhook", TunnelType.REMOTE, gateway, 9000, "localhost", 8080, false, false);
        var socks = Tunnel.create("Proxy", TunnelType.DYNAMIC, gateway, 1080, "", 0, true, false);

        assertThat(TunnelChecks.command(pg, "admin@10.0.55.22", 22, null))
                .isEqualTo("ssh -N -L 127.0.0.1:5433:localhost:5432 admin@10.0.55.22");
        assertThat(TunnelChecks.command(hook, "admin@10.0.54.11", 22, null))
                .isEqualTo("ssh -N -R 9000:localhost:8080 admin@10.0.54.11");
        assertThat(TunnelChecks.command(socks, "admin@gw", 2222, "ops@bastion"))
                .isEqualTo("ssh -N -D 0.0.0.0:1080 -J ops@bastion -p 2222 admin@gw");
    }

    @Test
    void perintahDraftMemakaiPlaceholderDanKurungIpv6() {
        assertThat(TunnelChecks.command(TunnelType.LOCAL, "127.0.0.1", "", "::1", " ", "u@h", 22, null))
                .isEqualTo("ssh -N -L 127.0.0.1:<port>:[::1]:<port> u@h");
        assertThat(TunnelChecks.command(TunnelType.REMOTE, "127.0.0.1", "9000", "", "8080", "u@h", 22, null))
                .isEqualTo("ssh -N -R 9000:localhost:8080 u@h");
    }
}

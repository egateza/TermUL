package dev.egateza.termul.app.tunnel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.tunnel.Tunnel;
import dev.egateza.termul.core.tunnel.TunnelType;
import dev.egateza.termul.ssh.tunnel.TunnelManager;
import dev.egateza.termul.ssh.tunnel.TunnelState;
import dev.egateza.termul.ssh.tunnel.TunnelStatus;
import dev.egateza.termul.ssh.tunnel.TunnelStatus.Failed;
import dev.egateza.termul.ssh.tunnel.TunnelStatus.Reason;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TunnelTextTest {

    private final HostProfile bastion = new HostProfile(UUID.randomUUID(), "Bastion", "", "10.0.0.1", 22, "ops",
            AuthMethod.PASSWORD, null, null, EnvironmentTag.NONE, null, null, false);
    private final HostProfile db = new HostProfile(UUID.randomUUID(), "db-server-01", "", "10.0.55.22", 2222,
            "admin", AuthMethod.PASSWORD, null, bastion.id(), EnvironmentTag.PROD, null, null, false);
    private final Map<UUID, HostProfile> byId = Map.of(bastion.id(), bastion, db.id(), db);

    private final Tunnel pg = Tunnel.create("PostgreSQL", TunnelType.LOCAL, db.id(), 5433, "localhost", 5432, false,
            true);
    private final Tunnel socks = Tunnel.create("Proxy", TunnelType.DYNAMIC, bastion.id(), 1080, "", 0, false, false);
    private final Tunnel hook = Tunnel.create("Webhook", TunnelType.REMOTE, db.id(), 9000, "localhost", 8080, false,
            false);

    @BeforeEach
    void english() {
        I18n.use("en");
    }

    @AfterEach
    void reset() {
        I18n.use(I18n.FALLBACK);
    }

    @Test
    void mappingSepertiPrototipe() {
        assertThat(TunnelText.mapping(pg)).isEqualTo("5433 → localhost:5432");
        assertThat(TunnelText.mapping(socks)).isEqualTo("1080 → SOCKS proxy");
    }

    @Test
    void alurLocalRemoteDynamic() {
        assertThat(TunnelText.nodes(pg, "db-server-01")).extracting(TunnelText.Node::value)
                .containsExactly("127.0.0.1:5433", "db-server-01", "localhost:5432");
        assertThat(TunnelText.nodes(hook, "db-server-01")).extracting(TunnelText.Node::key)
                .containsExactly("On db-server-01", "This PC", "Your service");
        var dynamic = TunnelText.nodes(socks, "Bastion");
        assertThat(dynamic.getFirst().value()).isEqualTo("SOCKS5 127.0.0.1:1080");
        assertThat(dynamic.getFirst().sshLink()).isTrue();
        assertThat(dynamic.getLast().link()).isEmpty();
    }

    @Test
    void alurDraftDenganIsianKosong() {
        assertThat(TunnelText.nodes(TunnelType.LOCAL, "0.0.0.0", "", " ", "", "h"))
                .extracting(TunnelText.Node::value).containsExactly("0.0.0.0:…", "h", "localhost:…");
    }

    @Test
    void perintahSetaraTermasukJumpHostDanPort() {
        assertThat(TunnelText.command(pg, Optional.of(db), id -> Optional.ofNullable(byId.get(id))))
                .isEqualTo("ssh -N -L 127.0.0.1:5433:localhost:5432 -J ops@10.0.0.1 -p 2222 admin@10.0.55.22");
        assertThat(TunnelText.command(socks, Optional.of(bastion), id -> Optional.empty()))
                .isEqualTo("ssh -N -D 127.0.0.1:1080 ops@10.0.0.1");
    }

    @Test
    void hostDanEnvironment() {
        assertThat(TunnelText.hostOption(db)).isEqualTo("db-server-01 · PROD");
        assertThat(TunnelText.hostOption(bastion)).isEqualTo("Bastion");
        assertThat(TunnelText.hostName(Optional.empty())).isEqualTo("(deleted host)");
    }

    @Test
    void pesanErrorDanTombolPortPengganti() {
        var busy = new Failed(Reason.PORT_IN_USE, 6380, "Address already in use", 6381);
        var lost = new Failed(Reason.DISCONNECTED, 5433, "", 0);

        assertThat(TunnelText.failure(busy, "gw")).isEqualTo("Port 6380 is already used by another program on this PC.");
        assertThat(TunnelText.fixPort(busy)).isEqualTo(6381);
        assertThat(TunnelText.failure(lost, "gw")).contains("gw").contains("starts again");
        assertThat(TunnelText.fixPort(lost)).isZero();
        assertThat(TunnelText.fixPort(TunnelStatus.ACTIVE)).isZero();
        var serverBusy = new Failed(Reason.REMOTE_PORT_IN_USE, 9000, "", 9001);
        assertThat(TunnelText.failure(serverBusy, "stg-web"))
                .isEqualTo("Port 9000 is already used by another program on stg-web.");
        assertThat(TunnelText.fixPort(serverBusy)).isEqualTo(9001);
        var unreachable = new Failed(Reason.DESTINATION_UNREACHABLE, 5433, "localhost:5432 — Connection refused", 0);
        assertThat(TunnelText.failure(unreachable, "development"))
                .startsWith("The tunnel was not started")
                .contains("from development (localhost:5432 — Connection refused)");
        assertThat(TunnelText.fixPort(unreachable)).isZero();
    }

    @Test
    void konfirmasiHapusMenyebutKoneksiYangTerbuka() {
        var stopped = TunnelState.STOPPED;
        var idle = new TunnelState(TunnelStatus.ACTIVE, 0, 0, null, List.of());
        var one = new TunnelState(TunnelStatus.ACTIVE, 1, 10, null, List.of("127.0.0.1"));
        var many = new TunnelState(TunnelStatus.ACTIVE, 3, 10, null,
                List.of("127.0.0.1", "192.168.1.20", "127.0.0.1"));

        assertThat(TunnelText.deleteText(pg, stopped)).isEqualTo("The tunnel settings will be removed from this profile.");
        assertThat(TunnelText.deleteText(pg, idle)).contains("port 5433");
        assertThat(TunnelText.deleteText(pg, one))
                .isEqualTo("1 connection is open through this tunnel right now: 127.0.0.1 (this PC). "
                        + "Deleting the tunnel cuts it off.");
        assertThat(TunnelText.deleteText(pg, many))
                .startsWith("3 connections are open")
                .contains("127.0.0.1 (this PC) ×2, 192.168.1.20")
                .endsWith("cuts them off.");
        assertThat(TunnelText.clients(List.of("", ""))).isEqualTo("from the server ×2");
    }

    @Test
    void barisHasilTestConnection() {
        var ok = TunnelManager.Check.OK;
        var bad = TunnelManager.Check.PROBLEM;

        assertThat(TunnelText.testLine(new TunnelManager.TestStep(TunnelManager.Step.LOCAL_PORT, ok, "", 0), pg,
                "db-server-01")).isEqualTo("Port 5433 on this PC is free");
        assertThat(TunnelText.testLine(new TunnelManager.TestStep(TunnelManager.Step.LOCAL_PORT, bad, "", 5434), pg,
                "db-server-01")).isEqualTo("Port 5433 on this PC is in use by another program (port 5434 is free)");
        assertThat(TunnelText.testLine(new TunnelManager.TestStep(TunnelManager.Step.CONNECT, ok,
                "admin@10.0.55.22:2222", 0), pg, "db-server-01"))
                .isEqualTo("Connected to db-server-01 (admin@10.0.55.22:2222)");
        assertThat(TunnelText.testLine(new TunnelManager.TestStep(TunnelManager.Step.DESTINATION, bad,
                "Connection refused", 0), pg, "db-server-01"))
                .isEqualTo("localhost:5432 cannot be reached from db-server-01: Connection refused");
        assertThat(TunnelText.testLine(new TunnelManager.TestStep(TunnelManager.Step.SERVER_PORT,
                TunnelManager.Check.UNKNOWN, "", 0), hook, "stg-web")).startsWith("Port 9000 on stg-web could not");
        assertThat(TunnelText.testLine(new TunnelManager.TestStep(TunnelManager.Step.CONNECT, bad, "", 0), pg, "x"))
                .isEqualTo(I18n.t("tunnel.error.hostMissing"));
    }

    @Test
    void duplikatMemakaiNamaDanPortBaru() {
        var copy = TunnelText.duplicate(pg, List.of(pg, socks, hook));

        assertThat(copy.id()).isNotEqualTo(pg.id());
        assertThat(copy.name()).isEqualTo("PostgreSQL (copy)");
        assertThat(copy.sourcePort()).isEqualTo(5434);
        assertThat(copy).usingRecursiveComparison().ignoringFields("id", "name", "sourcePort").isEqualTo(pg);

        var again = TunnelText.duplicate(pg, List.of(pg, copy));
        assertThat(again.name()).isEqualTo("PostgreSQL (copy 2)");
        assertThat(again.sourcePort()).isEqualTo(5435);

        String longName = "x".repeat(Tunnel.MAX_NAME);
        assertThat(TunnelText.copyName(longName, 12)).hasSize(Tunnel.MAX_NAME).endsWith(" (copy 12)");
    }

    @Test
    void pemeriksaanPortSumberDiForm() {
        var all = List.of(pg, socks, hook);

        assertThat(TunnelFormDialog.checkSource("", TunnelType.LOCAL, db.id(), null, all).bad()).isTrue();
        assertThat(TunnelFormDialog.checkSource("70000", TunnelType.LOCAL, db.id(), null, all).message())
                .isEqualTo("Enter a port from 1 to 65535.");
        assertThat(TunnelFormDialog.checkSource("5433", TunnelType.DYNAMIC, db.id(), null, all).message())
                .isEqualTo("Already used by the tunnel \"PostgreSQL\".");
        assertThat(TunnelFormDialog.checkSource("5433", TunnelType.LOCAL, db.id(), pg.id(), all).bad()).isFalse();
        var low = TunnelFormDialog.checkSource("80", TunnelType.LOCAL, db.id(), null, all);
        assertThat(low.bad()).isFalse();
        assertThat(low.warn()).isTrue();
        assertThat(TunnelFormDialog.checkSource("6380", TunnelType.LOCAL, db.id(), null, all))
                .isEqualTo(TunnelFormDialog.SourceCheck.OK);
    }

    @Test
    void filterStatusJenisDanCari() {
        var all = List.of(pg, socks, hook);
        Map<UUID, TunnelStatus> status = Map.of(pg.id(), TunnelStatus.ACTIVE, socks.id(), TunnelStatus.STOPPED,
                hook.id(), new Failed(Reason.REMOTE_REFUSED, 9000, "", 0));
        java.util.function.Function<UUID, String> names = id -> byId.get(id).name();

        assertThat(TunnelManagerDialog.shown(all, TunnelManagerDialog.Filter.ACTIVE, "", status::get, names))
                .containsExactly(pg);
        assertThat(TunnelManagerDialog.shown(all, TunnelManagerDialog.Filter.ERROR, "", status::get, names))
                .containsExactly(hook);
        assertThat(TunnelManagerDialog.shown(all, TunnelManagerDialog.Filter.DYNAMIC, "", status::get, names))
                .containsExactly(socks);
        assertThat(TunnelManagerDialog.shown(all, TunnelManagerDialog.Filter.ALL, "bastion", status::get, names))
                .containsExactly(socks);
        assertThat(TunnelManagerDialog.shown(all, TunnelManagerDialog.Filter.ALL, "5432", status::get, names))
                .containsExactly(pg);
    }
}

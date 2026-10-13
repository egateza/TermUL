package dev.egateza.termul.app.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NetworkProbeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T07:00:00Z"), ZoneOffset.UTC);
    private static final InetSocketAddress A = new InetSocketAddress(InetAddress.ofLiteral("192.0.2.1"), 443);
    private static final InetSocketAddress B = new InetSocketAddress(InetAddress.ofLiteral("192.0.2.2"), 443);

    @Test
    void onlineKalauAlamatPertamaTersambung() {
        var tried = new ArrayList<InetSocketAddress>();
        var probe = new NetworkProbe(List.of(A, B), 100, (address, timeout) -> tried.add(address), CLOCK);

        var status = probe.probe();

        assertThat(status).isInstanceOfSatisfying(NetworkProbe.Status.Online.class, o -> {
            assertThat(o.target()).isEqualTo("192.0.2.1:443");
            assertThat(o.latency().isNegative()).isFalse();
            assertThat(o.at()).isEqualTo(CLOCK.instant());
        });
        assertThat(tried).containsExactly(A);
    }

    @Test
    void cobaAlamatBerikutnyaKalauGagal() {
        var probe = new NetworkProbe(List.of(A, B), 100, (address, timeout) -> {
            if (address.equals(A)) {
                throw new ConnectException("refused");
            }
        }, CLOCK);

        assertThat(probe.probe()).isInstanceOfSatisfying(NetworkProbe.Status.Online.class,
                o -> assertThat(o.target()).isEqualTo("192.0.2.2:443"));
    }

    @Test
    void offlineKalauSemuaGagalTermasukRuntimeException() {
        var probe = new NetworkProbe(List.of(A, B), 100, (address, timeout) -> {
            if (address.equals(A)) {
                throw new IOException("unreachable");
            }
            throw new SecurityException("diblokir");
        }, CLOCK);

        assertThat(probe.probe()).isEqualTo(new NetworkProbe.Status.Offline("192.0.2.1:443, 192.0.2.2:443",
                CLOCK.instant()));
    }

    @Test
    void connectorAsliTersambungKeServerLokal() throws IOException {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var target = new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort());
            var probe = new NetworkProbe(List.of(target), 2000, defaultConnector(), CLOCK);
            assertThat(probe.probe()).isInstanceOf(NetworkProbe.Status.Online.class);
        }
    }

    @Test
    void connectorAsliOfflineKalauPortTertutup() throws IOException {
        int port;
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = server.getLocalPort();
        }
        var target = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        var probe = new NetworkProbe(List.of(target), 2000, defaultConnector(), CLOCK);
        assertThat(probe.probe()).isInstanceOf(NetworkProbe.Status.Offline.class);
    }

    @Test
    void targetBawaanBerupaIpLiteral() {
        assertThat(NetworkProbe.TARGETS).isNotEmpty().allSatisfy(t -> assertThat(t.isUnresolved()).isFalse());
        assertThatThrownBy(() -> new NetworkProbe(List.of(), 100, (a, t) -> { }, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Connector yang sama dengan bawaan {@link NetworkProbe#NetworkProbe()}. */
    private static NetworkProbe.Connector defaultConnector() {
        return (address, timeout) -> {
            try (var socket = new java.net.Socket()) {
                socket.connect(address, timeout);
            }
        };
    }
}

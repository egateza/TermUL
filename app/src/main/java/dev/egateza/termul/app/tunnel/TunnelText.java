package dev.egateza.termul.app.tunnel;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.sftp.Formats;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.tunnel.Tunnel;
import dev.egateza.termul.core.tunnel.TunnelChecks;
import dev.egateza.termul.core.tunnel.TunnelType;
import dev.egateza.termul.ssh.tunnel.TunnelManager;
import dev.egateza.termul.ssh.tunnel.TunnelState;
import dev.egateza.termul.ssh.tunnel.TunnelStatus;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Teks tampilan tunnel (label, mapping, alur, perintah setara) mengikuti prototipe Port forwarding. */
final class TunnelText {

    /** Satu kotak di alur tunnel; {@code link} = keterangan garis ke kotak berikutnya ({@code ""} = kotak terakhir). */
    record Node(String key, String value, String link, boolean sshLink) {
    }

    private TunnelText() {
    }

    static String type(TunnelType type) {
        return I18n.t("tunnel.type." + type.name().toLowerCase(Locale.ROOT));
    }

    static String typeDescription(TunnelType type) {
        return I18n.t("tunnel.type." + type.name().toLowerCase(Locale.ROOT) + ".desc");
    }

    /** {@code 5433 → localhost:5432}, atau {@code 1080 → SOCKS proxy}. */
    static String mapping(Tunnel t) {
        return t.type() == TunnelType.DYNAMIC ? I18n.t("tunnel.mapping.socks", String.valueOf(t.sourcePort()))
                : t.sourcePort() + " → " + hostPort(t.destinationHost(), String.valueOf(t.destinationPort()));
    }

    static String status(TunnelStatus status) {
        return I18n.t(switch (status) {
            case TunnelStatus.Active _ -> "tunnel.status.active";
            case TunnelStatus.Starting _ -> "tunnel.status.starting";
            case TunnelStatus.Stopped _ -> "tunnel.status.stopped";
            case TunnelStatus.Failed _ -> "tunnel.status.error";
        });
    }

    /** Pesan untuk kotak error di detail. */
    static String failure(TunnelStatus.Failed f, String hostName) {
        String port = String.valueOf(f.port());
        return switch (f.reason()) {
            case PORT_IN_USE -> I18n.t("tunnel.error.portInUse", port);
            case PORT_DENIED -> I18n.t("tunnel.error.portDenied", port);
            case DESTINATION_UNREACHABLE -> I18n.t("tunnel.error.destinationUnreachable", hostName, f.detail());
            case SERVICE_UNREACHABLE -> I18n.t("tunnel.error.serviceUnreachable", f.detail());
            case REMOTE_PORT_IN_USE -> I18n.t("tunnel.error.remotePortInUse", port, hostName);
            case REMOTE_REFUSED -> I18n.t("tunnel.error.remoteRefused", port, hostName, f.detail());
            case HOST_MISSING -> I18n.t("tunnel.error.hostMissing");
            case CONNECT_FAILED -> I18n.t("tunnel.error.connect", f.detail());
            case DISCONNECTED -> I18n.t("tunnel.error.disconnected", hostName);
            case OTHER -> I18n.t("tunnel.error.other", f.detail());
        };
    }

    /** Port pengganti untuk tombol "Use port N instead", atau 0. */
    static int fixPort(TunnelStatus status) {
        return status instanceof TunnelStatus.Failed f && (f.reason() == TunnelStatus.Reason.PORT_IN_USE
                || f.reason() == TunnelStatus.Reason.PORT_DENIED
                || f.reason() == TunnelStatus.Reason.REMOTE_PORT_IN_USE) ? f.suggestedPort() : 0;
    }

    /** Nama profil, atau "host dihapus" kalau profilnya sudah tidak ada. */
    static String hostName(Optional<HostProfile> host) {
        return host.map(HostProfile::name).orElse(I18n.t("tunnel.host.missing"));
    }

    /** {@code PROD}, {@code STAGING}, {@code DEV}; kosong untuk tanpa tag / host tidak ada. */
    static String env(Optional<HostProfile> host) {
        return host.map(HostProfile::environment).filter(e -> e != EnvironmentTag.NONE).map(Enum::name).orElse("");
    }

    /** Pilihan "Through host": {@code nama · PROD}. */
    static String hostOption(HostProfile p) {
        String env = env(Optional.of(p));
        return env.isEmpty() ? p.name() : p.name() + " · " + env;
    }

    /**
     * Alur tunnel (detail dan Preview form). Isian yang belum diisi tampil sebagai {@code …}.
     *
     * @param bind alamat yang didengarkan di PC ini
     */
    static List<Node> nodes(TunnelType type, String bind, String source, String destinationHost,
                            String destinationPort, String hostName) {
        String src = blank(source) ? "…" : source.strip();
        String dst = hostPort(blank(destinationHost) ? "localhost" : destinationHost.strip(),
                blank(destinationPort) ? "…" : destinationPort.strip());
        String ssh = I18n.t("tunnel.node.sshTunnel");
        return switch (type) {
            case REMOTE -> List.of(
                    new Node(I18n.t("tunnel.node.onServer", hostName), "localhost:" + src, ssh, true),
                    new Node(I18n.t("tunnel.node.thisPc"), "TermUL", I18n.t("tunnel.node.onThisPc"), false),
                    new Node(I18n.t("tunnel.node.yourService"), dst, "", false));
            case DYNAMIC -> List.of(
                    new Node(I18n.t("tunnel.node.appsOnThisPc"), "SOCKS5 " + hostPort(bind, src), ssh, true),
                    new Node(I18n.t("tunnel.node.through"), hostName, I18n.t("tunnel.node.fromServerNetwork"), false),
                    new Node(I18n.t("tunnel.node.destination"), I18n.t("tunnel.node.anyAddress"), "", false));
            case LOCAL -> List.of(
                    new Node(I18n.t("tunnel.node.thisPc"), hostPort(bind, src), ssh, true),
                    new Node(I18n.t("tunnel.node.through"), hostName, I18n.t("tunnel.node.fromServer"), false),
                    new Node(I18n.t("tunnel.node.destination"), dst, "", false));
        };
    }

    static List<Node> nodes(Tunnel t, String hostName) {
        return nodes(t.type(), t.bindAddress(), String.valueOf(t.sourcePort()), t.destinationHost(),
                String.valueOf(t.destinationPort()), hostName);
    }

    /**
     * Perintah OpenSSH setara untuk host ini, termasuk jump host ({@code -J}).
     *
     * @param profiles mencari profil (jump host)
     */
    static String command(TunnelType type, String bind, String source, String destinationHost, String destinationPort,
                          Optional<HostProfile> host, Function<UUID, Optional<HostProfile>> profiles) {
        String target = host.map(HostTarget::userAtHost).orElse("user@host");
        int port = host.map(HostProfile::port).orElse(22);
        String jump = host.map(HostProfile::jumpHostId).flatMap(profiles).map(HostTarget::jump).orElse(null);
        return TunnelChecks.command(type, bind, source, destinationHost, destinationPort, target, port, jump);
    }

    static String command(Tunnel t, Optional<HostProfile> host, Function<UUID, Optional<HostProfile>> profiles) {
        return command(t.type(), t.bindAddress(), String.valueOf(t.sourcePort()), t.destinationHost(),
                String.valueOf(t.destinationPort()), host, profiles);
    }

    /**
     * Isi konfirmasi hapus: koneksi yang sedang terbuka (dengan alamat asalnya), tunnel jalan tanpa koneksi, atau
     * tunnel berhenti.
     */
    static String deleteText(Tunnel t, TunnelState state) {
        if (!state.status().isRunning()) {
            return I18n.t("tunnel.delete.stopped");
        }
        if (state.clients().isEmpty()) {
            return I18n.t("tunnel.delete.running", String.valueOf(t.sourcePort()));
        }
        return I18n.t("tunnel.delete.connected", state.clients().size(), clients(state.clients()));
    }

    /**
     * Alamat asal koneksi, dikelompokkan: {@code 127.0.0.1 (this PC) ×2, 192.168.1.20}. Koneksi tunnel remote (alamat
     * kosong) datang dari server.
     */
    static String clients(List<String> addresses) {
        var counts = new java.util.LinkedHashMap<String, Integer>();
        for (String a : addresses) {
            counts.merge(a, 1, Integer::sum);
        }
        var parts = new java.util.ArrayList<String>();
        counts.forEach((address, n) -> {
            String label = address.isEmpty() ? I18n.t("tunnel.client.server")
                    : isLoopback(address) ? I18n.t("tunnel.client.thisPc", address) : address;
            parts.add(n > 1 ? label + " ×" + n : label);
        });
        return String.join(", ", parts);
    }

    private static boolean isLoopback(String address) {
        return address.startsWith("127.") || address.equals("::1") || address.equals("0:0:0:0:0:0:0:1");
    }

    /**
     * Salinan tunnel untuk form Duplicate: id baru, nama "… (copy)" yang belum dipakai, dan port sumber terdekat yang
     * tidak bentrok dengan tunnel lain. Pengaturan lain sama.
     */
    static Tunnel duplicate(Tunnel t, List<Tunnel> all) {
        var names = all.stream().map(x -> x.name().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        String name = copyName(t.name(), 1);
        for (int n = 2; names.contains(name.toLowerCase(Locale.ROOT)); n++) {
            name = copyName(t.name(), n);
        }
        int port = TunnelChecks.nextFreePort(all, t.type(), t.hostId(), t.sourcePort());
        return new Tunnel(UUID.randomUUID(), name, t.type(), t.hostId(), port, t.destinationHost(),
                t.destinationPort(), t.shared(), t.autoStart());
    }

    /** {@code PostgreSQL (copy)}, {@code PostgreSQL (copy 2)}; nama asal dipotong supaya muat {@link Tunnel#MAX_NAME}. */
    static String copyName(String name, int n) {
        String full = format(name, n);
        int over = full.length() - Tunnel.MAX_NAME;
        return over <= 0 ? full : format(name.substring(0, Math.max(1, name.length() - over)).strip(), n);
    }

    private static String format(String name, int n) {
        return n == 1 ? I18n.t("tunnel.copyName", name) : I18n.t("tunnel.copyNameN", name, String.valueOf(n));
    }

    /** Satu baris hasil Test connection untuk isian {@code t}. */
    static String testLine(TunnelManager.TestStep s, Tunnel t, String hostName) {
        boolean ok = s.check() == TunnelManager.Check.OK;
        String port = t == null ? "" : String.valueOf(t.sourcePort());
        String target = t == null ? "" : hostPort(t.destinationHost(), String.valueOf(t.destinationPort()));
        String suggest = s.suggestedPort() > 0 ? " " + I18n.t("tunnel.test.suggest", String.valueOf(s.suggestedPort()))
                : "";
        return switch (s.step()) {
            case LOCAL_PORT -> ok ? I18n.t("tunnel.test.localPort.ok", port)
                    : I18n.t("tunnel.test.localPort.busy", port) + suggest;
            case SERVICE -> ok ? I18n.t("tunnel.test.service.ok", target)
                    : I18n.t("tunnel.test.service.fail", target, s.detail());
            case CONNECT -> ok ? I18n.t("tunnel.test.connect.ok", hostName, s.detail())
                    : s.detail().isEmpty() ? I18n.t("tunnel.error.hostMissing")
                    : I18n.t("tunnel.test.connect.fail", hostName, s.detail());
            case DESTINATION -> ok ? I18n.t("tunnel.test.destination.ok", hostName, target)
                    : I18n.t("tunnel.test.destination.fail", hostName, target, s.detail());
            case SERVER_PORT -> switch (s.check()) {
                case OK -> I18n.t("tunnel.test.serverPort.ok", port, hostName);
                case PROBLEM -> I18n.t("tunnel.test.serverPort.busy", port, hostName) + suggest;
                case UNKNOWN -> I18n.t("tunnel.test.serverPort.unknown", port, hostName);
            };
        };
    }

    static String bytes(long n) {
        return Formats.size(n);
    }

    /** Alamat yang dipakai aplikasi di PC ini (Copy): selalu loopback, juga saat port dibagi ke jaringan. */
    static String localAddress(Tunnel t) {
        return Tunnel.LOOPBACK + ":" + t.sourcePort();
    }

    private static String hostPort(String host, String port) {
        return (host.contains(":") ? "[" + host + "]" : host) + ":" + port;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** {@code user@host} dan {@code user@host:port} untuk {@code -J}. */
    private static final class HostTarget {
        static String userAtHost(HostProfile p) {
            return p.username() + "@" + p.host();
        }

        static String jump(HostProfile p) {
            return userAtHost(p) + (p.port() == 22 ? "" : ":" + p.port());
        }
    }
}

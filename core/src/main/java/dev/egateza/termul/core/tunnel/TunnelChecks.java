package dev.egateza.termul.core.tunnel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Validasi isian tunnel dan perintah {@code ssh} yang setara, dipakai form tunnel dan {@link Tunnel}. */
public final class TunnelChecks {

    /** Port di bawah ini biasanya butuh hak administrator / root. */
    public static final int PRIVILEGED_BELOW = 1024;
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9._\\-:%]+");
    private static final String PORT_PLACEHOLDER = "<port>";

    private TunnelChecks() {
    }

    public static boolean isPort(int port) {
        return port >= 1 && port <= 65535;
    }

    /** Hostname, IPv4, atau IPv6 (tanpa kurung siku). */
    public static boolean isHost(String host) {
        return host != null && !host.isEmpty() && host.length() <= Tunnel.MAX_HOST && HOST.matcher(host).matches();
    }

    /** @return port 1..65535 dari teks isian, atau kosong kalau bukan port */
    public static Optional<Integer> parsePort(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String t = text.strip();
        if (t.isEmpty() || t.length() > 5 || !t.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        int port = Integer.parseInt(t);
        return isPort(port) ? Optional.of(port) : Optional.empty();
    }

    /**
     * Tunnel lain yang sudah memakai port sumber yang sama: sesama port di PC ini (local/dynamic), atau port yang sama
     * di server yang sama (remote).
     *
     * @param selfId tunnel yang sedang diedit (tidak bentrok dengan dirinya sendiri), null untuk tunnel baru
     */
    public static Optional<Tunnel> clash(List<Tunnel> all, UUID selfId, TunnelType type, UUID hostId, int port) {
        return all.stream()
                .filter(t -> !t.id().equals(selfId) && t.sourcePort() == port)
                .filter(t -> type.listensOnThisPc() ? t.type().listensOnThisPc()
                        : !t.type().listensOnThisPc() && t.hostId().equals(hostId))
                .findFirst();
    }

    /**
     * Port sumber terdekat setelah {@code port} yang tidak bentrok dengan tunnel lain (untuk tunnel hasil duplikat).
     * Hanya memeriksa daftar tunnel, bukan program lain (itu dicek form / saat tunnel dinyalakan).
     *
     * @return port, atau {@code port} sendiri kalau tidak ada yang kosong sampai 65535
     */
    public static int nextFreePort(List<Tunnel> all, TunnelType type, UUID hostId, int port) {
        for (int p = port + 1; p <= 65535; p++) {
            if (clash(all, null, type, hostId, p).isEmpty()) {
                return p;
            }
        }
        return port;
    }

    /**
     * Perintah OpenSSH yang setara (kotak "Same as"). Isian yang belum diisi tampil sebagai {@code <port>}.
     *
     * @param bind       alamat yang didengarkan di PC ini (local/dynamic)
     * @param userAtHost {@code user@host} profil
     * @param sshPort    port SSH profil; {@code -p} hanya ditulis kalau bukan 22
     * @param jump       {@code user@host[:port]} jump host, atau null
     */
    public static String command(TunnelType type, String bind, String sourcePort, String destinationHost,
                                 String destinationPort, String userAtHost, int sshPort, String jump) {
        String src = orPlaceholder(sourcePort);
        String dst = bracket(destinationHost == null || destinationHost.isBlank() ? "localhost" : destinationHost.strip())
                + ":" + orPlaceholder(destinationPort);
        var cmd = new StringBuilder("ssh -N ");
        switch (type) {
            case LOCAL -> cmd.append("-L ").append(bracket(bind)).append(':').append(src).append(':').append(dst);
            case REMOTE -> cmd.append("-R ").append(src).append(':').append(dst);
            case DYNAMIC -> cmd.append("-D ").append(bracket(bind)).append(':').append(src);
        }
        if (jump != null && !jump.isBlank()) {
            cmd.append(" -J ").append(jump);
        }
        if (sshPort != 22) {
            cmd.append(" -p ").append(sshPort);
        }
        return cmd.append(' ').append(userAtHost).toString();
    }

    /** {@link #command} untuk tunnel tersimpan. */
    public static String command(Tunnel t, String userAtHost, int sshPort, String jump) {
        return command(t.type(), t.bindAddress(), String.valueOf(t.sourcePort()), t.destinationHost(),
                String.valueOf(t.destinationPort()), userAtHost, sshPort, jump);
    }

    private static String orPlaceholder(String port) {
        return port == null || port.isBlank() ? PORT_PLACEHOLDER : port.strip();
    }

    private static String bracket(String host) {
        return host.contains(":") ? "[" + host + "]" : host;
    }
}

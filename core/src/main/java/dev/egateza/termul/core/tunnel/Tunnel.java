package dev.egateza.termul.core.tunnel;

import java.util.Objects;
import java.util.UUID;

/**
 * Satu port forwarding tersimpan, lewat koneksi SSH ke profil {@link #hostId()}. Tidak berisi secret.
 *
 * @param sourcePort      local/dynamic: port di PC ini; remote: port yang dibuka di server (loopback server)
 * @param destinationHost local: tujuan dilihat dari server; remote: layanan dilihat dari PC ini; dynamic: {@code ""}
 * @param destinationPort 0 untuk dynamic
 * @param shared          local/dynamic: port didengarkan di semua interface ({@code 0.0.0.0}), bukan hanya loopback;
 *                        selalu false untuk remote
 * @param autoStart       jalan sendiri setiap kali koneksi ke host tersambung
 */
public record Tunnel(UUID id, String name, TunnelType type, UUID hostId, int sourcePort, String destinationHost,
                     int destinationPort, boolean shared, boolean autoStart) {

    public static final int MAX_NAME = 60;
    /** Panjang maksimum hostname (RFC 1035). */
    public static final int MAX_HOST = 253;
    public static final String LOOPBACK = "127.0.0.1";
    public static final String ANY_ADDRESS = "0.0.0.0";

    public Tunnel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(hostId, "hostId");
        name = name == null ? "" : name.strip();
        destinationHost = destinationHost == null ? "" : destinationHost.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Nama tunnel wajib diisi.");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Nama tunnel maksimal " + MAX_NAME + " karakter.");
        }
        if (hasControlCharacter(name)) {
            throw new IllegalArgumentException("Nama tunnel tidak boleh berisi karakter kontrol.");
        }
        if (!TunnelChecks.isPort(sourcePort)) {
            throw new IllegalArgumentException("Port sumber harus 1 sampai 65535.");
        }
        if (type.hasDestination()) {
            if (!TunnelChecks.isHost(destinationHost)) {
                throw new IllegalArgumentException("Host tujuan tidak valid.");
            }
            if (!TunnelChecks.isPort(destinationPort)) {
                throw new IllegalArgumentException("Port tujuan harus 1 sampai 65535.");
            }
        } else {
            destinationHost = "";
            destinationPort = 0;
        }
        if (!type.listensOnThisPc()) {
            shared = false;
        }
    }

    /** Tunnel baru dengan id acak. */
    public static Tunnel create(String name, TunnelType type, UUID hostId, int sourcePort, String destinationHost,
                                int destinationPort, boolean shared, boolean autoStart) {
        return new Tunnel(UUID.randomUUID(), name, type, hostId, sourcePort, destinationHost, destinationPort, shared,
                autoStart);
    }

    /** Alamat yang didengarkan di PC ini (local/dynamic). */
    public String bindAddress() {
        return shared ? ANY_ADDRESS : LOOPBACK;
    }

    public Tunnel withSourcePort(int port) {
        return new Tunnel(id, name, type, hostId, port, destinationHost, destinationPort, shared, autoStart);
    }

    public Tunnel withAutoStart(boolean on) {
        return new Tunnel(id, name, type, hostId, sourcePort, destinationHost, destinationPort, shared, on);
    }

    static boolean hasControlCharacter(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c == 0x7F || (c >= 0x80 && c < 0xA0)) {
                return true;
            }
        }
        return false;
    }
}

package dev.egateza.termul.core.wsl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Kondisi sshd di dalam distro WSL, hasil {@link WslManager#checkSsh}. Hanya dibaca, tidak ada yang diubah.
 *
 * @param installed    binary sshd ada
 * @param systemd      distro berjalan dengan systemd
 * @param socketActive sshd dijalankan lewat {@code ssh.socket} (socket activation); port-nya dari unit socket, bukan
 *                     dari {@code sshd_config}
 * @param configValid  {@code sshd -t} lolos
 * @param configPorts  port efektif dari {@code sshd -T}; kosong kalau config tidak valid
 * @param listening    port TCP yang sedang listen di distro; null kalau tidak bisa dibaca ({@code ss}/{@code netstat}
 *                     tidak ada)
 */
public record SshCheck(boolean installed, boolean systemd, boolean socketActive, boolean configValid,
                       Set<Integer> configPorts, Set<Integer> listening) {

    /** Masalah yang membuat SSH ke port profil gagal, urut dari yang harus dibereskan dulu. */
    public enum Problem {
        /** {@code openssh-server} belum terpasang. */
        NOT_INSTALLED,
        /** {@code sshd_config} tidak valid, sshd tidak mau jalan. */
        CONFIG_INVALID,
        /** Port profil tidak ada di {@code sshd_config}. */
        PORT_NOT_CONFIGURED,
        /** sshd lewat {@code ssh.socket}: port di {@code sshd_config} diabaikan. */
        SOCKET_ACTIVATION,
        /** Tidak ada yang listen di port profil (sshd mati atau belum di-restart setelah config diubah). */
        NOT_LISTENING
    }

    public SshCheck {
        configPorts = Set.copyOf(configPorts);
        listening = listening == null ? null : Set.copyOf(listening);
    }

    /** Masalah untuk SSH ke {@code port}; kosong = sshd siap di port itu. */
    public List<Problem> problems(int port) {
        var result = new ArrayList<Problem>();
        if (!installed) {
            result.add(Problem.NOT_INSTALLED);
            return result;
        }
        if (!configValid) {
            result.add(Problem.CONFIG_INVALID);
            return result;
        }
        if (!configPorts.contains(port)) {
            result.add(Problem.PORT_NOT_CONFIGURED);
        }
        if (listening != null && !listening.contains(port)) {
            result.add(socketActive ? Problem.SOCKET_ACTIVATION : Problem.NOT_LISTENING);
        }
        return result;
    }

    /** true kalau ada yang listen di port ini (null kalau tidak diketahui). */
    public Boolean isListening(int port) {
        return listening == null ? null : listening.contains(port);
    }

    /** Parse output {@link WslManager#CHECK_SSHD} (baris {@code key=value}). */
    static SshCheck parse(String output) {
        boolean installed = false;
        boolean systemd = false;
        boolean socket = false;
        boolean valid = false;
        boolean listenKnown = false;
        Set<Integer> config = new LinkedHashSet<>();
        Set<Integer> listen = new LinkedHashSet<>();
        for (String line : WslOutput.lines(output)) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq);
            String value = line.substring(eq + 1).strip();
            switch (key) {
                case "installed" -> installed = value.equals("yes");
                case "systemd" -> systemd = value.equals("yes");
                case "socket" -> socket = value.equals("active");
                case "configtest" -> valid = value.equals("ok");
                case "config_port" -> port(value, config);
                case "listen_tool" -> listenKnown = !value.equals("none");
                case "listen" -> port(value, listen);
                default -> { }
            }
        }
        return new SshCheck(installed, systemd, socket, valid, config, listenKnown ? listen : null);
    }

    private static void port(String value, Set<Integer> into) {
        try {
            int port = Integer.parseInt(value);
            if (port >= 1 && port <= 65_535) {
                into.add(port);
            }
        } catch (NumberFormatException e) {
            // baris yang bukan angka port (mis. socket unix) dilewati
        }
    }
}

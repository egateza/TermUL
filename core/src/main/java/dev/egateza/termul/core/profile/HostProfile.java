package dev.egateza.termul.core.profile;

import java.util.Objects;
import java.util.UUID;

/**
 * Profil koneksi SSH ke satu server. Immutable dan <b>tidak pernah</b> berisi secret:
 * password/passphrase disimpan di vault dengan kunci {@link #id()}.
 *
 * @param id               identitas stabil profil (dipakai sebagai kunci vault)
 * @param name             nama tampilan
 * @param group            path grup dipisah '/', mis. {@code "Produksi/Backend"}; kosong = root
 * @param host             hostname atau IP
 * @param port             port SSH (1..65535)
 * @param username         user login
 * @param authMethod       metode autentikasi
 * @param privateKeyPath   path private key (wajib kalau {@link AuthMethod#KEY}), boleh null
 * @param jumpHostId       id profil jump host (ProxyJump), boleh null
 * @param environment      tag environment
 * @param initialDirectory working directory awal, boleh null
 * @param notes            catatan bebas, boleh null
 * @param autoSudo         auto-trigger inject password sudo (default OFF, apalagi untuk prod)
 * @param os               OS server yang terdeteksi otomatis saat connect (untuk ikon), boleh null
 * @param terminalTheme    warna terminal khusus host ini (id tema custom atau preset bawaan, diterjemahkan di modul
 *                         app), null = ikut pengaturan
 */
public record HostProfile(
        UUID id,
        String name,
        String group,
        String host,
        int port,
        String username,
        AuthMethod authMethod,
        String privateKeyPath,
        UUID jumpHostId,
        EnvironmentTag environment,
        String initialDirectory,
        String notes,
        boolean autoSudo,
        OsInfo os,
        String terminalTheme) {

    public static final int DEFAULT_PORT = 22;

    /** Constructor tanpa info OS (belum pernah terdeteksi). */
    public HostProfile(UUID id, String name, String group, String host, int port, String username,
                       AuthMethod authMethod, String privateKeyPath, UUID jumpHostId, EnvironmentTag environment,
                       String initialDirectory, String notes, boolean autoSudo) {
        this(id, name, group, host, port, username, authMethod, privateKeyPath, jumpHostId, environment,
                initialDirectory, notes, autoSudo, null, null);
    }

    /** Constructor tanpa warna terminal khusus. */
    public HostProfile(UUID id, String name, String group, String host, int port, String username,
                       AuthMethod authMethod, String privateKeyPath, UUID jumpHostId, EnvironmentTag environment,
                       String initialDirectory, String notes, boolean autoSudo, OsInfo os) {
        this(id, name, group, host, port, username, authMethod, privateKeyPath, jumpHostId, environment,
                initialDirectory, notes, autoSudo, os, null);
    }

    public HostProfile withTerminalTheme(String themeId) {
        return new HostProfile(id, name, group, host, port, username, authMethod, privateKeyPath, jumpHostId,
                environment, initialDirectory, notes, autoSudo, os, themeId);
    }

    public HostProfile {
        Objects.requireNonNull(id, "id");
        name = requireText(name, "Nama profil");
        host = requireText(host, "Host");
        username = requireText(username, "Username");
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Port harus di antara 1 dan 65535: " + port);
        }
        authMethod = Objects.requireNonNullElse(authMethod, AuthMethod.AGENT);
        environment = Objects.requireNonNullElse(environment, EnvironmentTag.NONE);
        group = normalizeGroup(group);
        privateKeyPath = blankToNull(privateKeyPath);
        initialDirectory = blankToNull(initialDirectory);
        notes = blankToNull(notes);
        terminalTheme = blankToNull(terminalTheme);
        if (authMethod == AuthMethod.KEY && privateKeyPath == null) {
            throw new IllegalArgumentException("Path private key wajib diisi untuk auth KEY");
        }
        if (id.equals(jumpHostId)) {
            throw new IllegalArgumentException("Jump host tidak boleh profil itu sendiri");
        }
    }

    /** Profil baru dengan id acak dan nilai default. */
    public static HostProfile create(String name, String host, String username) {
        return new HostProfile(UUID.randomUUID(), name, "", host, DEFAULT_PORT, username,
                AuthMethod.AGENT, null, null, EnvironmentTag.NONE, null, null, false);
    }

    /** Salinan dengan id baru dan nama "(salinan)". */
    public HostProfile duplicate() {
        return new HostProfile(UUID.randomUUID(), name + " (salinan)", group, host, port, username,
                authMethod, privateKeyPath, jumpHostId, environment, initialDirectory, notes, autoSudo, os, terminalTheme);
    }

    public HostProfile withGroup(String newGroup) {
        return new HostProfile(id, name, newGroup, host, port, username, authMethod, privateKeyPath,
                jumpHostId, environment, initialDirectory, notes, autoSudo, os, terminalTheme);
    }

    public HostProfile withJumpHostId(UUID newJumpHostId) {
        return new HostProfile(id, name, group, host, port, username, authMethod, privateKeyPath,
                newJumpHostId, environment, initialDirectory, notes, autoSudo, os, terminalTheme);
    }

    public HostProfile withOs(OsInfo newOs) {
        return new HostProfile(id, name, group, host, port, username, authMethod, privateKeyPath,
                jumpHostId, environment, initialDirectory, notes, autoSudo, newOs, terminalTheme);
    }

    /** Label singkat {@code user@host[:port]} untuk judul tab/log. */
    public String address() {
        return port == DEFAULT_PORT ? username + "@" + host : username + "@" + host + ":" + port;
    }

    /** Menormalkan path grup: trim tiap segmen, buang segmen kosong, pakai '/'. */
    public static String normalizeGroup(String group) {
        if (group == null || group.isBlank()) {
            return "";
        }
        var sb = new StringBuilder();
        for (String part : group.replace('\\', '/').split("/")) {
            String p = part.strip();
            if (!p.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append('/');
                }
                sb.append(p);
            }
        }
        return sb.toString();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " wajib diisi");
        }
        return value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

package dev.egateza.termul.app.ui.profiles;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.OsInfo;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.profile.Protocol;
import dev.egateza.termul.vault.SecretType;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Isi form edit/baru di Profile manager, tanpa secret (password diambil langsung dari field saat Save/Test, lalu
 * di-zero). Validasi dan teks error mengikuti prototipe. EDT.
 */
final class ProfileDraft {

    /** Pilihan "Sudo auto-fill". */
    enum Sudo {
        OFF,
        /** password login dipakai juga untuk sudo (hanya untuk sign-in password) */
        SAME,
        /** password sudo sendiri di vault */
        SEPARATE
    }

    /** Field yang bisa salah (prototipe: name, host, port, user, key, sudo). */
    enum Field { NAME, HOST, PORT, USER, KEY, SUDO }

    private static final Pattern PASTE = Pattern.compile("^(?:([^@\\s]+)@)?([^:@\\s]+)(?::(\\d+))?$");

    UUID id; // null = profil baru
    String name = "";
    String group = "";
    Protocol protocol = Protocol.SSH;
    String host = "";
    String port = "22";
    String user = "";
    UUID jump;
    AuthMethod auth = AuthMethod.PASSWORD;
    boolean savePassword = true;
    String keyPath = "";
    Sudo sudo = Sudo.OFF;
    String startDir = "";
    boolean favorite;
    EnvironmentTag environment = EnvironmentTag.NONE;
    Boolean sensitive;
    String terminalTheme;
    String notes = "";
    OsInfo os;
    /** secret yang sudah ada di vault untuk profil ini */
    Set<SecretType> stored = Set.of();

    static ProfileDraft blank(String group) {
        var d = new ProfileDraft();
        d.group = HostProfile.normalizeGroup(group);
        return d;
    }

    static ProfileDraft of(HostProfile p, Set<SecretType> stored, boolean favorite) {
        var d = new ProfileDraft();
        d.id = p.id();
        d.name = p.name();
        d.group = p.group();
        d.protocol = p.protocol();
        d.host = p.host();
        d.port = String.valueOf(p.port());
        d.user = p.username();
        d.jump = p.jumpHostId();
        d.auth = p.authMethod();
        d.stored = Set.copyOf(stored);
        d.savePassword = p.authMethod() != AuthMethod.PASSWORD || stored.contains(SecretType.LOGIN_PASSWORD);
        d.keyPath = Objects.toString(p.privateKeyPath(), "");
        d.sudo = sudoOf(p.autoSudo(), p.authMethod(), stored);
        d.startDir = Objects.toString(p.initialDirectory(), "");
        d.favorite = favorite;
        d.environment = p.environment();
        d.sensitive = p.sensitive();
        d.terminalTheme = p.terminalTheme();
        d.notes = Objects.toString(p.notes(), "");
        d.os = p.os();
        return d;
    }

    /**
     * Pilihan sudo dari data yang tersimpan. "Pakai password login" tidak bisa dibedakan dari password sudo sendiri
     * setelah disimpan (keduanya disimpan sebagai password sudo di vault), jadi profil dengan password sudo tampil
     * sebagai "terpisah"; auto-sudo tanpa password sudo tampil sebagai "pakai password login" kalau sign-in password.
     */
    static Sudo sudoOf(boolean autoSudo, AuthMethod auth, Set<SecretType> stored) {
        if (!autoSudo) {
            return Sudo.OFF;
        }
        if (stored.contains(SecretType.SUDO_PASSWORD) || auth != AuthMethod.PASSWORD) {
            return Sudo.SEPARATE;
        }
        return Sudo.SAME;
    }

    boolean isNew() {
        return id == null;
    }

    /** Ganti cara login; "pakai password login" tidak berlaku lagi tanpa sign-in password (prototipe). */
    void setAuth(AuthMethod a) {
        auth = a;
        if (a != AuthMethod.PASSWORD && sudo == Sudo.SAME) {
            sudo = Sudo.OFF;
        }
    }

    /**
     * Teks yang ditempel di Host: {@code user@host:port} mengisi ketiga field.
     *
     * @return true kalau teks berisi user dan/atau port dan sudah dipecah
     */
    boolean setHost(String text) {
        String v = text == null ? "" : text.strip();
        var m = PASTE.matcher(v);
        if (m.matches() && (v.indexOf('@') >= 0 || m.group(3) != null)) {
            host = m.group(2);
            if (m.group(1) != null) {
                user = m.group(1);
            }
            if (m.group(3) != null) {
                port = m.group(3);
            }
            return true;
        }
        host = text == null ? "" : text;
        return false;
    }

    /**
     * @param sudoTyped ada password sudo yang diketik di form
     * @return kunci i18n pesan error per field; kosong = valid
     */
    Map<Field, String> validate(ProfileSnapshot snapshot, boolean sudoTyped) {
        var errs = new EnumMap<Field, String>(Field.class);
        String n = name.strip();
        if (n.isEmpty()) {
            errs.put(Field.NAME, "pm.err.name");
        } else if (snapshot.profiles().stream().anyMatch(p -> !p.id().equals(id)
                && p.name().toLowerCase(Locale.ROOT).equals(n.toLowerCase(Locale.ROOT)))) {
            errs.put(Field.NAME, "pm.err.nameUsed");
        }
        String h = host.strip();
        if (h.isEmpty()) {
            errs.put(Field.HOST, "pm.err.host");
        } else if (h.chars().anyMatch(Character::isWhitespace)) {
            errs.put(Field.HOST, "pm.err.hostSpace");
        }
        if (portNumber() < 0) {
            errs.put(Field.PORT, "pm.err.port");
        }
        if (user.strip().isEmpty()) {
            errs.put(Field.USER, "pm.err.user");
        }
        if (!protocol.isFtp() && auth == AuthMethod.KEY && keyPath.strip().isEmpty()) {
            errs.put(Field.KEY, "pm.err.key");
        }
        if (!protocol.isFtp() && sudo == Sudo.SEPARATE && !sudoTyped && !stored.contains(SecretType.SUDO_PASSWORD)) {
            errs.put(Field.SUDO, "pm.err.sudo");
        }
        return errs;
    }

    /** @return port 1–65535, atau -1 kalau tidak valid */
    int portNumber() {
        String p = port.strip();
        if (!p.matches("\\d{1,5}")) {
            return -1;
        }
        int v = Integer.parseInt(p);
        return v >= 1 && v <= 65_535 ? v : -1;
    }

    /** Profil dari isi form (sudah divalidasi). {@link #id} kosong diisi id baru. */
    HostProfile toProfile() {
        UUID pid = id != null ? id : UUID.randomUUID();
        boolean ftp = protocol.isFtp();
        return new HostProfile(pid, name.strip(), group, host.strip(), portNumber(), user.strip(),
                ftp ? AuthMethod.PASSWORD : auth, auth == AuthMethod.KEY && !ftp ? keyPath.strip() : null,
                ftp || pid.equals(jump) ? null : jump, environment, startDir, notes, !ftp && sudo != Sudo.OFF, os,
                terminalTheme, protocol, sensitive);
    }

    /**
     * Nilai flag sensitif yang disimpan: null (ikut environment) kalau sama dengan default environment, supaya profil
     * yang kelak diganti ke/dari PROD ikut berubah; selain itu pilihan eksplisit user.
     *
     * @param chosen user pernah mengubah centang "sensitif" di form
     */
    static Boolean sensitiveValue(boolean chosen, boolean selected, EnvironmentTag env) {
        return chosen && selected != env.isProduction() ? selected : null;
    }

    /** Isi form tanpa secret, untuk mendeteksi perubahan yang belum disimpan. */
    String fingerprint() {
        return String.join("\u0000", String.valueOf(id), name, group, protocol.name(), host, port, user,
                String.valueOf(jump), auth.name(), String.valueOf(savePassword), keyPath, sudo.name(), startDir,
                String.valueOf(favorite), environment.name(), String.valueOf(sensitive), String.valueOf(terminalTheme),
                notes);
    }
}

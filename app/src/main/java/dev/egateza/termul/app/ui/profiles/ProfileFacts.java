package dev.egateza.termul.app.ui.profiles;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.profile.Protocol;
import dev.egateza.termul.vault.SecretType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Teks panel detail Profile manager (perintah ssh, cara login, fakta). Murni, bisa dites. */
final class ProfileFacts {

    /** Satu baris fakta; {@code mono} = font kode, {@code muted} = abu-abu (nilai default). */
    record Fact(String key, String value, boolean mono, boolean muted) {
    }

    private ProfileFacts() {
    }

    /** Perintah OpenSSH yang setara: {@code ssh -p 2211 -i "key" -J jump user@host}. FTP: alamat URL. */
    static String command(HostProfile p, ProfileSnapshot snapshot) {
        if (p.isFtp()) {
            return p.address();
        }
        var parts = new ArrayList<String>();
        parts.add("ssh");
        if (p.port() != HostProfile.DEFAULT_PORT) {
            parts.add("-p " + p.port());
        }
        if (p.authMethod() == AuthMethod.KEY && p.privateKeyPath() != null) {
            parts.add("-i \"" + p.privateKeyPath() + "\"");
        }
        jumpOf(p, snapshot).ifPresent(j -> parts.add("-J " + j.address()));
        parts.add(p.username() + "@" + p.host());
        return String.join(" ", parts);
    }

    static java.util.Optional<HostProfile> jumpOf(HostProfile p, ProfileSnapshot snapshot) {
        return p.jumpHostId() == null ? java.util.Optional.empty() : snapshot.find(p.jumpHostId());
    }

    static String authText(HostProfile p, Set<SecretType> stored) {
        return switch (p.isFtp() ? AuthMethod.PASSWORD : p.authMethod()) {
            case KEY -> I18n.t("pm.auth.keyFile", fileName(p.privateKeyPath()));
            case AGENT -> I18n.t("pm.auth.agent.text");
            case PASSWORD -> I18n.t(stored.contains(SecretType.LOGIN_PASSWORD)
                    ? "pm.auth.passwordSaved" : "pm.auth.passwordAsked");
        };
    }

    static String sudoText(HostProfile p, Set<SecretType> stored) {
        if (!p.autoSudo()) {
            return I18n.t("pm.sudo.off");
        }
        return I18n.t(stored.contains(SecretType.SUDO_PASSWORD) ? "pm.sudo.text.saved" : "pm.sudo.text.asked");
    }

    /** Badge sign-in di daftar: key / agent / password / ftp. */
    static String authBadge(HostProfile p) {
        if (p.isFtp()) {
            return p.protocol() == Protocol.FTP ? "ftp" : "ftps";
        }
        return switch (p.authMethod()) {
            case KEY -> "key";
            case AGENT -> "agent";
            case PASSWORD -> "password";
        };
    }

    static List<Fact> facts(HostProfile p, ProfileSnapshot snapshot, Set<SecretType> stored) {
        var facts = new ArrayList<Fact>();
        if (p.isFtp()) {
            facts.add(new Fact(I18n.t("pm.fact.protocol"), protocolLabel(p.protocol()), false, false));
        }
        facts.add(new Fact(I18n.t("pm.fact.signIn"), authText(p, stored), false, false));
        if (!p.isFtp()) {
            facts.add(new Fact(I18n.t("pm.fact.sudo"), sudoText(p, stored), false, false));
            var jump = jumpOf(p, snapshot);
            facts.add(new Fact(I18n.t("pm.fact.jump"), jump.map(HostProfile::name).orElse(I18n.t("pm.jump.none")),
                    false, jump.isEmpty()));
        }
        var dependents = snapshot.profiles().stream().filter(x -> p.id().equals(x.jumpHostId()))
                .map(HostProfile::name).toList();
        if (!dependents.isEmpty()) {
            facts.add(new Fact(I18n.t("pm.fact.usedBy"), String.join(", ", dependents), false, false));
        }
        String dir = p.initialDirectory();
        facts.add(new Fact(I18n.t("pm.fact.startDir"), dir == null ? I18n.t("pm.startDir.home") : dir, dir != null,
                dir == null));
        if (p.environment() != EnvironmentTag.NONE) {
            facts.add(new Fact(I18n.t("pm.fact.environment"), p.environment().name(), false, false));
        }
        if (p.needsGuard()) {
            facts.add(new Fact(I18n.t("pm.fact.sensitive"), I18n.t("pm.sensitive.on"), false, false));
        }
        if (p.notes() != null) {
            facts.add(new Fact(I18n.t("pm.fact.notes"), p.notes(), false, false));
        }
        return facts;
    }

    static String protocolLabel(Protocol protocol) {
        return switch (protocol) {
            case SSH -> I18n.t("profile.protocol.ssh");
            case FTPS -> I18n.t("profile.protocol.ftps");
            case FTPS_IMPLICIT -> I18n.t("profile.protocol.ftpsImplicit");
            case FTP -> I18n.t("profile.protocol.ftp");
        };
    }

    /** Nama folder untuk ditampilkan; {@code ""} = Ungrouped. */
    static String folderName(String group) {
        return group == null || group.isEmpty() ? I18n.t("pm.folder.ungrouped") : group;
    }

    /** Teks "Last used" dari daftar Recent (aplikasi tidak menyimpan waktu, hanya urutan 5 terakhir). */
    static String lastUsed(UUID id, ProfileSnapshot snapshot) {
        int rank = snapshot.recent().indexOf(id);
        return rank < 0 ? I18n.t("pm.lastUsed.notRecent") : rank == 0 ? I18n.t("pm.lastUsed.latest")
                : I18n.t("pm.lastUsed.rank", rank + 1);
    }

    static String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        try {
            var name = Path.of(path).getFileName();
            return name == null ? path : name.toString();
        } catch (java.nio.file.InvalidPathException e) {
            int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            return path.substring(i + 1);
        }
    }

    /** "N profile(s)" sesuai bahasa. */
    static String profiles(int n) {
        return I18n.t(n == 1 ? "pm.count.one" : "pm.count.many", n);
    }
}

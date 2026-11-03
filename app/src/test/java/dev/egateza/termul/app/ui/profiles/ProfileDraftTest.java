package dev.egateza.termul.app.ui.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.profile.Protocol;
import dev.egateza.termul.vault.SecretType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileDraftTest {

    private static final HostProfile GATEWAY = new HostProfile(UUID.randomUUID(), "Gateway", "Production",
            "10.0.55.50", 22, "admin", AuthMethod.PASSWORD, null, null, EnvironmentTag.PROD, "/var/www/app", null,
            true);
    private static final ProfileSnapshot SNAPSHOT = new ProfileSnapshot(1, List.of(), List.of(GATEWAY));

    private static ProfileDraft valid() {
        var d = ProfileDraft.blank("Development");
        d.name = "api-server-01";
        d.host = "10.0.55.21";
        d.user = "admin";
        return d;
    }

    @Test
    void validasiSesuaiPrototipe() {
        var d = ProfileDraft.blank("");
        d.port = "70000";
        d.auth = AuthMethod.KEY;
        d.sudo = ProfileDraft.Sudo.SEPARATE;

        var errs = d.validate(SNAPSHOT, false);

        assertThat(errs).containsEntry(ProfileDraft.Field.NAME, "pm.err.name")
                .containsEntry(ProfileDraft.Field.HOST, "pm.err.host")
                .containsEntry(ProfileDraft.Field.PORT, "pm.err.port")
                .containsEntry(ProfileDraft.Field.USER, "pm.err.user")
                .containsEntry(ProfileDraft.Field.KEY, "pm.err.key")
                .containsEntry(ProfileDraft.Field.SUDO, "pm.err.sudo");
        assertThat(valid().validate(SNAPSHOT, false)).isEmpty();
    }

    @Test
    void namaTidakBolehSamaDenganProfilLainTanpaBedaHuruf() {
        var d = valid();
        d.name = "gateway";

        assertThat(d.validate(SNAPSHOT, false)).containsEntry(ProfileDraft.Field.NAME, "pm.err.nameUsed");
        d.id = GATEWAY.id(); // profil itu sendiri boleh
        assertThat(d.validate(SNAPSHOT, false)).doesNotContainKey(ProfileDraft.Field.NAME);
    }

    @Test
    void hostDenganSpasiDitolak() {
        var d = valid();
        d.host = "10.0.0.1 x";

        assertThat(d.validate(SNAPSHOT, false)).containsEntry(ProfileDraft.Field.HOST, "pm.err.hostSpace");
    }

    @Test
    void tempelUserHostPortMengisiKetigaField() {
        var d = valid();

        assertThat(d.setHost("ops@203.0.113.20:2200")).isTrue();
        assertThat(d.host).isEqualTo("203.0.113.20");
        assertThat(d.user).isEqualTo("ops");
        assertThat(d.port).isEqualTo("2200");
        assertThat(d.setHost("db.example.com")).isFalse();
        assertThat(d.host).isEqualTo("db.example.com");
    }

    @Test
    void passwordSudoTersimpanTidakWajibDiketikUlang() {
        var d = valid();
        d.sudo = ProfileDraft.Sudo.SEPARATE;
        assertThat(d.validate(SNAPSHOT, true)).isEmpty();
        d.stored = Set.of(SecretType.SUDO_PASSWORD);
        assertThat(d.validate(SNAPSHOT, false)).isEmpty();
    }

    @Test
    void gantiCaraLoginMematikanSudoPakaiPasswordLogin() {
        var d = valid();
        d.sudo = ProfileDraft.Sudo.SAME;

        d.setAuth(AuthMethod.KEY);

        assertThat(d.sudo).isEqualTo(ProfileDraft.Sudo.OFF);
    }

    @Test
    void sudoDariDataTersimpan() {
        assertThat(ProfileDraft.sudoOf(false, AuthMethod.PASSWORD, Set.of())).isEqualTo(ProfileDraft.Sudo.OFF);
        assertThat(ProfileDraft.sudoOf(true, AuthMethod.PASSWORD, Set.of())).isEqualTo(ProfileDraft.Sudo.SAME);
        assertThat(ProfileDraft.sudoOf(true, AuthMethod.PASSWORD, Set.of(SecretType.SUDO_PASSWORD)))
                .isEqualTo(ProfileDraft.Sudo.SEPARATE);
        assertThat(ProfileDraft.sudoOf(true, AuthMethod.KEY, Set.of())).isEqualTo(ProfileDraft.Sudo.SEPARATE);
    }

    @Test
    void bolakBalikProfilTanpaKehilanganFieldLama() {
        var original = new HostProfile(UUID.randomUUID(), "db", "Prod/DB", "10.1.1.1", 2222, "dev", AuthMethod.KEY,
                "C:/keys/id", null, EnvironmentTag.STAGING, "/srv", "catatan", true, null, "nord", Protocol.SSH, true);

        var back = ProfileDraft.of(original, Set.of(SecretType.SUDO_PASSWORD), true).toProfile();

        assertThat(back).isEqualTo(original);
    }

    @Test
    void ftpTidakMenyimpanFieldKhususSsh() {
        var d = valid();
        d.protocol = Protocol.FTPS;
        d.auth = AuthMethod.KEY;
        d.keyPath = "C:/k";
        d.sudo = ProfileDraft.Sudo.SEPARATE;

        assertThat(d.validate(SNAPSHOT, false)).isEmpty();
        var p = d.toProfile();
        assertThat(p.authMethod()).isEqualTo(AuthMethod.PASSWORD);
        assertThat(p.privateKeyPath()).isNull();
        assertThat(p.autoSudo()).isFalse();
    }

    @Test
    void sensitifSamaDenganDefaultEnvironmentDisimpanNull() {
        assertThat(ProfileDraft.sensitiveValue(false, true, EnvironmentTag.PROD)).isNull();
        assertThat(ProfileDraft.sensitiveValue(true, true, EnvironmentTag.PROD)).isNull();
        assertThat(ProfileDraft.sensitiveValue(true, true, EnvironmentTag.STAGING)).isTrue();
        assertThat(ProfileDraft.sensitiveValue(true, false, EnvironmentTag.PROD)).isFalse();
    }

    @Test
    void sidikJariBerubahSaatIsiFormBerubah() {
        var d = valid();
        String before = d.fingerprint();
        d.startDir = "/srv";

        assertThat(d.fingerprint()).isNotEqualTo(before);
    }
}

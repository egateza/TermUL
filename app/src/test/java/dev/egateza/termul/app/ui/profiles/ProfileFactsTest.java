package dev.egateza.termul.app.ui.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.profile.Protocol;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileFactsTest {

    private static final HostProfile GW = new HostProfile(UUID.randomUUID(), "public-gw", "", "203.0.113.58", 2211,
            "admin", AuthMethod.KEY, "C:/Users/me/.ssh/id_ed25519", null, EnvironmentTag.NONE, null, null, false);

    @Test
    void perintahSshSetaraDenganPortKeyDanJumpHost() {
        var inner = new HostProfile(UUID.randomUUID(), "stg", "", "10.0.54.11", 22, "admin", AuthMethod.AGENT, null,
                GW.id(), EnvironmentTag.NONE, null, null, false);
        var snapshot = new ProfileSnapshot(1, List.of(), List.of(GW, inner));

        assertThat(ProfileFacts.command(GW, snapshot))
                .isEqualTo("ssh -p 2211 -i \"C:/Users/me/.ssh/id_ed25519\" admin@203.0.113.58");
        assertThat(ProfileFacts.command(inner, snapshot)).isEqualTo("ssh -J admin@203.0.113.58:2211 admin@10.0.54.11");
    }

    @Test
    void badgeSignIn() {
        assertThat(ProfileFacts.authBadge(GW)).isEqualTo("key");
        var ftp = new HostProfile(UUID.randomUUID(), "f", "", "h", 21, "u", AuthMethod.PASSWORD, null, null,
                EnvironmentTag.NONE, null, null, false, null, null, Protocol.FTP, null);
        assertThat(ProfileFacts.authBadge(ftp)).isEqualTo("ftp");
    }

    @Test
    void namaFileKeyTanpaFolder() {
        assertThat(ProfileFacts.fileName("C:\\Users\\me\\.ssh\\clients_ed25519")).endsWith("clients_ed25519");
        assertThat(ProfileFacts.fileName(null)).isEmpty();
    }

    @Test
    void kolomDaftarMengikutiRasioPrototipe() {
        int[] cols = ProfileManagerDialog.columns(36, 600);

        assertThat(cols[0]).isEqualTo(36);
        assertThat(cols[2]).isEqualTo(600 - 112);
        assertThat(cols[1] - cols[0]).isGreaterThan(cols[2] - cols[1]); // 1.1fr : 1fr
    }

    @Test
    void namaFolderDivalidasi() {
        assertThat(ProfileFoldersDialog.folderError(" ", List.of())).isNotNull();
        assertThat(ProfileFoldersDialog.folderError("x".repeat(25), List.of())).isNotNull();
        assertThat(ProfileFoldersDialog.folderError("a/b", List.of())).isNotNull();
        assertThat(ProfileFoldersDialog.folderError("staging", List.of("Staging"))).isNotNull();
        assertThat(ProfileFoldersDialog.folderError("Ungrouped", List.of())).isNotNull();
        assertThat(ProfileFoldersDialog.folderError("Staging", List.of("Production"))).isNull();
    }
}

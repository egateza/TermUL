package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrayControllerTest {

    @Test
    void hostCepatFavoritDuluLaluTerakhirTanpaDuplikat() {
        var a = HostProfile.create("a", "a.example", "u");
        var b = HostProfile.create("b", "b.example", "u");
        var c = HostProfile.create("c", "c.example", "u");
        var snapshot = new ProfileSnapshot(ProfileSnapshot.CURRENT_VERSION, List.of(), List.of(a, b, c),
                List.of(b.id(), a.id()), List.of(c.id(), a.id()));

        assertThat(TrayController.quickHosts(snapshot)).extracting(HostProfile::name).containsExactly("b", "a", "c");
        assertThat(TrayController.quickHosts(ProfileSnapshot.empty())).isEmpty();
    }

    @Test
    void hostCepatDibatasi() {
        var profiles = new ArrayList<HostProfile>();
        var favorites = new ArrayList<UUID>();
        for (int i = 0; i < TrayController.MAX_HOSTS + 5; i++) {
            var p = HostProfile.create("h" + i, "h" + i + ".example", "u");
            profiles.add(p);
            favorites.add(p.id());
        }
        var snapshot = new ProfileSnapshot(ProfileSnapshot.CURRENT_VERSION, List.of(), profiles, favorites, List.of());

        assertThat(TrayController.quickHosts(snapshot)).hasSize(TrayController.MAX_HOSTS);
    }
}

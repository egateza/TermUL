package dev.egateza.termul.core.highlight;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HighlightSettingsTest {

    private final HighlightProfile global = HighlightProfile.create("Global", List.of());
    private final HighlightProfile prod = HighlightProfile.create("Produksi", List.of());
    private final HighlightProfile nginx = HighlightProfile.create("Nginx", List.of());
    private final HighlightProfile shell = HighlightProfile.create("Shell", List.of());
    private final UUID web1 = UUID.randomUUID();
    private final UUID web2 = UUID.randomUUID();
    private final UUID zsh = UUID.randomUUID();
    private final UUID bash = UUID.randomUUID();

    private HighlightSettings settings() {
        return new HighlightSettings(true, false, global.id(), List.of(global, prod, nginx, shell),
                Map.of(web1, nginx.id()), Map.of("Prod", prod.id()), Map.of(zsh, shell.id()), null);
    }

    @Test
    void hostLaluGrupPalingSpesifikLaluGlobal() {
        var s = settings();

        assertThat(s.forHost(web1, "Prod/Web")).contains(nginx);
        assertThat(s.forHost(web2, "Prod/Web")).contains(prod); // grup induk
        assertThat(s.forHost(web2, "prod/web")).contains(global); // grup case sensitive, sama seperti snippet
        assertThat(s.forHost(web2, "")).contains(global);
        assertThat(s.withAssignments(nginx.id(), Set.of(), Set.of("Prod/Web"), Set.of(), false)
                .forHost(web2, "Prod/Web/Edge")).contains(nginx);
    }

    @Test
    void terminalLokalLaluSemuaLokalLaluGlobal() {
        var s = settings();

        assertThat(s.forLocal(zsh)).contains(shell);
        assertThat(s.forLocal(bash)).contains(global);
        var all = s.withAssignments(prod.id(), Set.of(), Set.of("Prod"), Set.of(), true);
        assertThat(all.forLocal(bash)).contains(prod);
        assertThat(all.forLocal(zsh)).contains(shell);
    }

    @Test
    void matiAtauTanpaGlobalBerartiTanpaHighlight() {
        assertThat(settings().withEnabled(false).forHost(web1, "Prod")).isEmpty();
        assertThat(settings().withGlobalProfile(null).forHost(web2, "")).isEmpty();
        assertThat(HighlightSettings.defaults().forHost(web2, "")).isEmpty(); // bawaan: belum aktif
        assertThat(HighlightSettings.defaults().withEnabled(true).forHost(web2, "")).contains(HighlightProfile.defaults());
    }

    @Test
    void penugasanPindahKeProfilYangMencentang() {
        var s = settings().withAssignments(prod.id(), Set.of(web1), Set.of("Prod", "Dev"), Set.of(zsh), false);

        assertThat(s.hosts()).containsEntry(web1, prod.id());
        assertThat(s.groups()).containsOnly(Map.entry("Prod", prod.id()), Map.entry("Dev", prod.id()));
        assertThat(s.locals()).containsEntry(zsh, prod.id());
        // hilangkan centang: penugasan profil ini kosong, milik profil lain tetap
        var cleared = s.withAssignments(prod.id(), Set.of(), Set.of(), Set.of(), false);
        assertThat(cleared.hosts()).isEmpty();
        assertThat(cleared.groups()).isEmpty();
    }

    @Test
    void hapusProfilIkutMenghapusPenugasannya() {
        var s = settings().withAssignments(nginx.id(), Set.of(web1), Set.of(), Set.of(), true).withoutProfile(nginx.id());

        assertThat(s.profiles()).doesNotContain(nginx);
        assertThat(s.hosts()).isEmpty();
        assertThat(s.allLocalsProfileId()).isNull();
        assertThat(s.withoutProfile(global.id()).globalProfileId()).isNull();
    }

    @Test
    void withProfileMenggantiTanpaMengubahUrutan() {
        var renamed = prod.withName("Prod baru");

        assertThat(settings().withProfile(renamed).profiles()).extracting(HighlightProfile::name)
                .containsExactly("Global", "Prod baru", "Nginx", "Shell");
    }
}

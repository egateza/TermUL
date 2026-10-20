package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.terminal.LocalProfiles;
import dev.egateza.termul.app.ui.QuickOpenItems.Host;
import dev.egateza.termul.app.ui.QuickOpenItems.Item;
import dev.egateza.termul.app.ui.QuickOpenItems.Local;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuickOpenItemsTest {

    private final HostProfile web = HostProfile.create("web-1", "10.0.0.1", "dev").withGroup("Produksi/Backend");
    private final HostProfile db = HostProfile.create("db-1", "10.0.0.2", "dev").withGroup("Produksi");
    private final HostProfile lab = HostProfile.create("lab", "192.168.1.5", "root");
    private final HostProfile ubuntu = HostProfile.create("Ubuntu", "localhost", "ega").withWslDistro("Ubuntu-22.04");

    private static final List<LocalProfiles.Choice> LOCALS = List.of(
            new LocalProfiles.Choice(UUID.randomUUID(), "zsh", List.of("/bin/zsh", "-l"), "", Map.of(), null, true, true),
            new LocalProfiles.Choice(UUID.randomUUID(), "Git Bash", List.of("bash.exe", "--login"), "", Map.of(), null,
                    false, false));

    private static List<String> labels(List<Item> items) {
        return items.stream().map(i -> switch (i) {
            case Local l -> "L:" + l.choice().name();
            case Host(HostProfile p, boolean pinned) -> (pinned ? "*" : "") + p.name();
        }).toList();
    }

    @Test
    void terminalLokalLaluFavoritesLastUsedLaluSisanyaUrutGrupDanNama() {
        var s = new ProfileSnapshot(1, List.of(), List.of(web, db, lab), List.of(db.id()), List.of(lab.id(), db.id()));

        assertThat(labels(QuickOpenItems.build(s, "", false, LOCALS)))
                .containsExactly("L:zsh", "L:Git Bash", "*db-1", "*lab", "web-1");
    }

    @Test
    void tanpaFavoritesHostUrutGrupLaluNama() {
        var s = new ProfileSnapshot(1, List.of(), List.of(web, db, lab));

        // grup "" < "Produksi" < "Produksi/Backend"
        assertThat(labels(QuickOpenItems.build(s, null, false, LOCALS))).containsExactly("L:zsh", "L:Git Bash", "lab", "db-1", "web-1");
    }

    @Test
    void pencarianSamaSepertiDaftarHost() {
        var s = new ProfileSnapshot(1, List.of(), List.of(web, db, lab));

        assertThat(labels(QuickOpenItems.build(s, "  BACKEND ", false, LOCALS))).containsExactly("web-1");
        assertThat(labels(QuickOpenItems.build(s, "root", false, LOCALS))).containsExactly("lab");
        assertThat(labels(QuickOpenItems.build(s, "shell", false, LOCALS))).containsExactly("L:zsh", "L:Git Bash");
        assertThat(QuickOpenItems.build(s, "tidak-ada-apa-apa", false, LOCALS)).isEmpty();
        // nama/perintah profil lokal ikut dicari
        assertThat(labels(QuickOpenItems.build(s, "git", false, LOCALS))).containsExactly("L:Git Bash");
        assertThat(labels(QuickOpenItems.build(s, "--login", false, LOCALS))).containsExactly("L:Git Bash");
    }

    @Test
    void profilWslHanyaTampilKalauManajemenWslAktif() {
        var s = new ProfileSnapshot(1, List.of(), List.of(lab, ubuntu), List.of(ubuntu.id()), List.of());

        assertThat(labels(QuickOpenItems.build(s, "", false, LOCALS))).containsExactly("L:zsh", "L:Git Bash", "lab");
        assertThat(labels(QuickOpenItems.build(s, "", true, LOCALS))).containsExactly("L:zsh", "L:Git Bash", "*Ubuntu", "lab");
    }
}

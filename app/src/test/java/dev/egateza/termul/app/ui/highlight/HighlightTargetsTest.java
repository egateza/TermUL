package dev.egateza.termul.app.ui.highlight;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.terminal.LocalProfiles;
import dev.egateza.termul.app.ui.highlight.HighlightTargets.AllLocals;
import dev.egateza.termul.app.ui.highlight.HighlightTargets.Group;
import dev.egateza.termul.app.ui.highlight.HighlightTargets.Host;
import dev.egateza.termul.app.ui.highlight.HighlightTargets.Local;
import dev.egateza.termul.core.highlight.HighlightProfile;
import dev.egateza.termul.core.highlight.HighlightSettings;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class HighlightTargetsTest {

    private final HostProfile web = HostProfile.create("web-1", "10.0.0.1", "dev").withGroup("Prod/Web");
    private final HostProfile db = HostProfile.create("db-1", "10.0.0.2", "dev").withGroup("Prod");
    private final HostProfile lab = HostProfile.create("lab", "192.168.1.5", "root");
    private final LocalProfiles.Choice zsh = new LocalProfiles.Choice(UUID.randomUUID(), "zsh",
            List.of("/bin/zsh", "-l"), "", Map.of(), null, true, true);
    private final HighlightProfile a = HighlightProfile.create("A", List.of());
    private final HighlightProfile b = HighlightProfile.create("B", List.of());
    private final HighlightSettings base = new HighlightSettings(true, false, a.id(), List.of(a, b), Map.of(),
            Map.of(), Map.of(), null);

    private static List<String> outline(DefaultMutableTreeNode node, String indent) {
        var out = new ArrayList<String>();
        for (var child : Collections.list(node.children())) {
            var n = (DefaultMutableTreeNode) child;
            out.add(indent + switch ((HighlightTargets.Target) n.getUserObject()) {
                case Group g -> "G:" + g.name();
                case Host h -> "H:" + h.profile().name();
                case AllLocals _ -> "LOKAL";
                case Local l -> "L:" + l.choice().name();
            });
            out.addAll(outline(n, indent + "  "));
        }
        return out;
    }

    @Test
    void treeGrupBersarangHostLaluTerminalLokal() {
        var snapshot = new ProfileSnapshot(1, List.of("Dev"), List.of(web, db, lab));

        assertThat(outline(HighlightTargets.tree(snapshot, List.of(zsh)), "")).containsExactly(
                "G:Dev",
                "G:Prod",
                "  G:Web",
                "    H:web-1",
                "  H:db-1",
                "H:lab",
                "LOKAL",
                "  L:zsh");
    }

    @Test
    void centangMemindahkanTargetDariProfilLain() {
        var host = new Host(web);
        var s = HighlightTargets.toggle(host, base, a.id());

        assertThat(HighlightTargets.checked(host, s, a.id())).isTrue();
        s = HighlightTargets.toggle(host, s, b.id());
        assertThat(HighlightTargets.checked(host, s, b.id())).isTrue();
        assertThat(HighlightTargets.checked(host, s, a.id())).isFalse();
        s = HighlightTargets.toggle(host, s, b.id());
        assertThat(s.hosts()).isEmpty();
    }

    @Test
    void centangGrupDanLokalTidakMenggangguPenugasanLain() {
        var s = HighlightTargets.toggle(new Host(lab), base, a.id());
        s = HighlightTargets.toggle(new Group("Prod", "Prod"), s, a.id());
        s = HighlightTargets.toggle(AllLocals.INSTANCE, s, b.id());
        s = HighlightTargets.toggle(new Local(zsh), s, a.id());

        assertThat(s.hosts()).containsEntry(lab.id(), a.id());
        assertThat(s.groups()).containsEntry("Prod", a.id());
        assertThat(s.allLocalsProfileId()).isEqualTo(b.id());
        assertThat(s.locals()).containsEntry(zsh.id(), a.id());
        assertThat(s.forHost(web.id(), web.group())).contains(a); // lewat grup induk
        assertThat(HighlightTargets.count(s, a.id())).isEqualTo(3);
    }

    @Test
    void keteranganPemilik() {
        var s = HighlightTargets.toggle(new Group("Prod", "Prod"), base, b.id());
        s = HighlightTargets.toggle(AllLocals.INSTANCE, s, b.id());
        var names = HighlightTargets.names(s.profiles());

        assertThat(HighlightTargets.note(new Group("Prod", "Prod"), s, a.id(), names))
                .isEqualTo(I18n.t("highlight.target.owner", "B"));
        assertThat(HighlightTargets.note(new Group("Prod", "Prod"), s, b.id(), names)).isEmpty();
        assertThat(HighlightTargets.note(new Host(web), s, b.id(), names))
                .isEqualTo(I18n.t("highlight.target.followsGroup"));
        assertThat(HighlightTargets.note(new Host(web), s, a.id(), names))
                .isEqualTo(I18n.t("highlight.target.viaGroup", "B"));
        assertThat(HighlightTargets.note(new Group("Prod/Web", "Web"), s, a.id(), names))
                .isEqualTo(I18n.t("highlight.target.viaGroup", "B"));
        assertThat(HighlightTargets.note(new Local(zsh), s, a.id(), names))
                .isEqualTo(I18n.t("highlight.target.viaLocals", "B"));
        assertThat(HighlightTargets.note(new Host(lab), s, a.id(), names)).isEmpty();
    }

    @Test
    void anakIkutCentangIndukProfilYangSama() {
        var s = HighlightTargets.toggle(AllLocals.INSTANCE, base, a.id());
        s = HighlightTargets.toggle(new Group("Prod", "Prod"), s, a.id());

        assertThat(HighlightTargets.inheritedChecked(new Local(zsh), s, a.id())).isTrue();
        assertThat(HighlightTargets.inheritedChecked(new Host(db), s, a.id())).isTrue();
        assertThat(HighlightTargets.inheritedChecked(new Group("Prod/Web", "Web"), s, a.id())).isTrue();
        assertThat(HighlightTargets.inheritedChecked(new Host(web), s, a.id())).isTrue();
        assertThat(HighlightTargets.inheritedChecked(new Local(zsh), s, b.id())).isFalse();
        assertThat(HighlightTargets.inheritedChecked(new Host(lab), s, a.id())).isFalse();
        // anak yang dicentang langsung ke profil lain tidak ikut induk
        s = HighlightTargets.toggle(new Host(db), s, b.id());
        assertThat(HighlightTargets.inheritedChecked(new Host(db), s, a.id())).isFalse();
    }

    @Test
    void konfirmasiHanyaKalauTargetDiambilDariProfilLain() {
        var s = HighlightTargets.toggle(AllLocals.INSTANCE, base, a.id());

        assertThat(HighlightTargets.takenFrom(AllLocals.INSTANCE, s, b.id())).contains(a.id());
        assertThat(HighlightTargets.takenFrom(AllLocals.INSTANCE, s, a.id())).isEmpty();
        assertThat(HighlightTargets.takenFrom(new Local(zsh), s, b.id())).isEmpty(); // hanya warisan, tidak dipindah
        assertThat(HighlightTargets.takenFrom(new Host(lab), s, b.id())).isEmpty();
    }

    @Test
    void urutanKeyword() {
        assertThat(HighlightDialog.reorder(List.of("a", "b", "c", "d"), 0, 2)).containsExactly("b", "c", "a", "d");
        assertThat(HighlightDialog.reorder(List.of("a", "b", "c", "d"), 3, 0)).containsExactly("d", "a", "b", "c");
        assertThat(HighlightDialog.reorder(List.of("a", "b"), 0, 9)).containsExactly("b", "a");
    }
}

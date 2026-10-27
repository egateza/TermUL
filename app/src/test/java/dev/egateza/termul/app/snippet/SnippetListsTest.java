package dev.egateza.termul.app.snippet;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.snippet.Snippet;
import dev.egateza.termul.core.snippet.SnippetScope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SnippetListsTest {

    private static Snippet snippet(String title, String command, String category, boolean favorite) {
        return new Snippet(UUID.randomUUID(), title, command, false, category, favorite, false, SnippetScope.ALL,
                Map.of());
    }

    private final Snippet reload = snippet("Reload Nginx", "sudo systemctl reload nginx", "nginx", true);
    private final Snippet status = snippet("Service status", "systemctl status ${service}", "sistem", false);
    private final Snippet disk = snippet("Disk", "df -h", "sistem", false);
    private final Snippet loose = snippet("Uptime", "uptime", "", false);
    private final Snippet orphan = snippet("Lama", "ls", "kategori-terhapus", false);
    private final List<Snippet> all = List.of(status, reload, disk, loose, orphan);
    private final List<String> categories = List.of("sistem", "nginx");

    @Test
    void menuCepatDikelompokkanFavoritKategoriLaluTanpaKategori() {
        var groups = SnippetQuickPick.groups(all, categories, id -> id.toUpperCase(), "");

        assertThat(groups.keySet()).containsExactly(I18n.t("snippet.nav.favorites"), "SISTEM",
                I18n.t("snippet.nav.uncategorized"));
        assertThat(groups.get(I18n.t("snippet.nav.favorites"))).containsExactly(reload);
        assertThat(groups.get("SISTEM")).containsExactly(status, disk);
        assertThat(groups.get(I18n.t("snippet.nav.uncategorized"))).as("kategori yang sudah dihapus ikut di sini")
                .containsExactly(loose, orphan);
    }

    @Test
    void filterMencocokkanJudulDanPerintah() {
        var groups = SnippetQuickPick.groups(all, categories, id -> id, "SYSTEMCTL");
        assertThat(groups.values().stream().flatMap(List::stream)).containsExactlyInAnyOrder(reload, status);
        assertThat(SnippetQuickPick.groups(all, categories, id -> id, "tidak ada")).isEmpty();
    }

    @Test
    void daftarManagerFavoritDiAtasDanPerKategori() {
        var allNav = new SnippetManagerDialog.Nav(SnippetManagerDialog.Nav.Kind.ALL, "", "", 0);
        assertThat(SnippetManagerDialog.shown(all, allNav, categories, ""))
                .containsExactly(reload, status, disk, loose, orphan);

        var sistem = new SnippetManagerDialog.Nav(SnippetManagerDialog.Nav.Kind.CATEGORY, "sistem", "", 0);
        assertThat(SnippetManagerDialog.shown(all, sistem, categories, "")).containsExactly(status, disk);

        var none = new SnippetManagerDialog.Nav(SnippetManagerDialog.Nav.Kind.NONE, "", "", 0);
        assertThat(SnippetManagerDialog.shown(all, none, categories, "")).containsExactly(loose, orphan);

        var favs = new SnippetManagerDialog.Nav(SnippetManagerDialog.Nav.Kind.FAVORITES, "", "", 0);
        assertThat(SnippetManagerDialog.shown(all, favs, categories, "nginx")).containsExactly(reload);
    }

    @Test
    void pratinjauBerisiPromptPerBaris() {
        assertThat(SnippetQuickPick.prefixed("a\nb")).isEqualTo("$ a\n$ b");
    }
}

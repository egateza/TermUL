package dev.egateza.termul.core.snippet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Kelola kategori, urutan manual, dan ekspor (Snippet manager). */
class SnippetStoreManageTest {

    @TempDir
    Path tmp;

    private SnippetStore loaded() {
        var store = new SnippetStore(tmp.resolve("snippets.json"));
        store.load();
        return store;
    }

    private SnippetStore reloaded() {
        return loaded();
    }

    private static Snippet snippet(String title, String command, String category) {
        return new Snippet(UUID.randomUUID(), title, command, false, category, false, false, SnippetScope.ALL, Map.of());
    }

    @Test
    void namaKategoriDivalidasi() {
        var cats = List.of(new SnippetCategory("docker", "Docker"), new SnippetCategory("nginx", "Nginx"));
        assertThat(SnippetStore.categoryProblem(cats, "  ", null)).isEqualTo(SnippetStore.CategoryProblem.EMPTY);
        assertThat(SnippetStore.categoryProblem(cats, "x".repeat(25), null))
                .isEqualTo(SnippetStore.CategoryProblem.TOO_LONG);
        assertThat(SnippetStore.categoryProblem(cats, "nginx", null)).isEqualTo(SnippetStore.CategoryProblem.DUPLICATE);
        assertThat(SnippetStore.categoryProblem(cats, "NGINX", "nginx")).as("nama sendiri boleh").isNull();
        assertThat(SnippetStore.categoryProblem(cats, "Nginx", "docker"))
                .isEqualTo(SnippetStore.CategoryProblem.DUPLICATE);
        assertThat(SnippetStore.categoryProblem(cats, "tanpa kategori", null, "Tanpa kategori"))
                .isEqualTo(SnippetStore.CategoryProblem.DUPLICATE);
        assertThat(SnippetStore.categoryProblem(cats, "Redis", null)).isNull();
    }

    @Test
    void tambahGantiNamaGeserHapusKategori() {
        var store = loaded();
        var docker = store.addCategory("Docker");
        var nginx = store.addCategory("Nginx");
        var db = store.addCategory("Database");
        var ps = snippet("ps", "docker ps", docker.id());
        var other = snippet("logs", "docker logs x", docker.id());
        store.save(ps);
        store.save(other);

        store.renameCategory(docker.id(), "Kontainer");
        store.moveCategory(db.id(), -1);
        store.moveCategory(db.id(), -5); // di luar batas: diam
        assertThat(reloaded().categories()).containsExactly(new SnippetCategory("docker", "Kontainer"),
                new SnippetCategory(db.id(), "Database"), nginx);

        assertThat(store.deleteCategory(docker.id())).isEqualTo(2);
        var after = reloaded();
        assertThat(after.categories()).extracting(SnippetCategory::id).containsExactly(db.id(), nginx.id());
        assertThat(after.snapshot()).as("snippet tidak terhapus, pindah ke tanpa kategori")
                .extracting(Snippet::category).containsOnly("");
        assertThat(after.snapshot()).hasSize(2);
    }

    @Test
    void namaTidakBolehDitolakStore() {
        var store = loaded();
        store.addCategory("Docker");
        assertThatThrownBy(() -> store.addCategory("docker")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.addCategory("")).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.categories()).hasSize(1);
    }

    @Test
    void urutanManualTersimpan() {
        var store = loaded();
        var a = snippet("a", "echo a", "");
        var b = snippet("b", "echo b", "");
        var c = snippet("c", "echo c", "");
        store.save(a);
        store.save(b);
        store.save(c); // urutan: c, b, a

        store.moveSnippet(a.id(), 0);
        assertThat(reloaded().snapshot()).extracting(Snippet::title).containsExactly("a", "c", "b");
        store.moveSnippet(a.id(), 99);
        assertThat(reloaded().snapshot()).extracting(Snippet::title).containsExactly("c", "b", "a");
    }

    @Test
    void eksporDenganAtauTanpaExtras() {
        var store = loaded();
        var docker = store.addCategory("Docker");
        store.addCategory("Nginx");
        var logs = new Snippet(UUID.randomUUID(), "Logs", "docker logs ${c}", false, docker.id(), true, true,
                new SnippetScope.Group("Production"), Map.of("c", "web"));
        store.save(logs);
        store.remember("host-1", logs.id(), Map.of("c", "rahasia-host"));

        String full = new String(store.export(List.of(logs), true, true), StandardCharsets.UTF_8);
        assertThat(full).contains("\"version\" : 2", "\"name\" : \"Nginx\"", "\"appliesTo\"", "\"dangerous\" : true",
                "\"variables\"", "\"favorite\" : true").doesNotContain("remembered", "rahasia-host");

        String plain = new String(store.export(List.of(logs), false, false), StandardCharsets.UTF_8);
        assertThat(plain).contains("\"category\" : \"docker\"", "\"name\" : \"Docker\"")
                .doesNotContain("Nginx", "appliesTo", "dangerous", "variables");
    }
}

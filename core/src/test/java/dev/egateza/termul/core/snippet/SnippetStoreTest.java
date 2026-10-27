package dev.egateza.termul.core.snippet;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnippetStoreTest {

    @TempDir
    Path tmp;

    private Path file() {
        return tmp.resolve("snippets.json");
    }

    private SnippetStore loaded() {
        var store = new SnippetStore(file());
        store.load();
        return store;
    }

    @Test
    void fileBelumAdaBerartiKosong() {
        assertThat(new SnippetStore(file()).load()).isEmpty();
    }

    @Test
    void snippetBaruDiDepanDanDiubahDiTempatnya() {
        var store = loaded();
        var disk = Snippet.create("disk", "df -h");
        var logs = Snippet.create("Log nginx", "tail -f /var/log/nginx/error.log");
        store.save(disk);
        store.save(logs);
        store.save(new Snippet(disk.id(), "Disk", "df -hT\n", false, "", true, false, SnippetScope.ALL, Map.of()));

        var reloaded = new SnippetStore(file()).load();
        assertThat(reloaded).extracting(Snippet::title).containsExactly("Log nginx", "Disk");
        assertThat(reloaded.get(1).command()).isEqualTo("df -hT");
        assertThat(reloaded.get(1).enter()).isTrue();
        assertThat(reloaded.get(1).favorite()).isTrue();

        store.delete(logs.id());
        assertThat(new SnippetStore(file()).load()).extracting(Snippet::title).containsExactly("Disk");
    }

    @Test
    void semuaFieldTersimpanDalamFormatVersion2() throws Exception {
        var store = loaded();
        var sistem = store.ensureCategory("Sistem");
        assertThat(store.ensureCategory("sistem")).as("nama sama, beda huruf: kategori yang sama").isEqualTo(sistem);
        var logs = new Snippet(UUID.randomUUID(), "Service logs", "journalctl -u ${service} -n ${lines}", false,
                sistem.id(), false, true, new SnippetScope.Group("Prod/DB"), Map.of("service", "app.service"));
        var gw = new Snippet(UUID.randomUUID(), "Koneksi gateway", "ss -tan | wc -l\n", false, "", false, false,
                new SnippetScope.Host("gateway"), Map.of());
        store.save(logs);
        store.save(gw);

        String json = Files.readString(file());
        assertThat(json).contains("\"version\" : 2", "\"appliesTo\"", "\"folders\" : [ \"Prod/DB\" ]",
                "\"hosts\" : [ \"gateway\" ]", "\"command\" : \"ss -tan | wc -l\\n\"", "\"dangerous\" : true");

        var reloaded = new SnippetStore(file());
        assertThat(reloaded.load()).containsExactly(gw, logs);
        assertThat(reloaded.categories()).containsExactly(new SnippetCategory("sistem", "Sistem"));
        assertThat(reloaded.categoryName("sistem")).isEqualTo("Sistem");
        assertThat(reloaded.forHost("db1", "Prod/DB/Replica")).containsExactly(logs);
        assertThat(reloaded.forHost("gateway", "")).containsExactly(gw);
    }

    @Test
    void fileEksporDariMobileTerbaca() throws Exception {
        Files.writeString(file(), """
                {"version":2,
                 "categories":[{"id":"nginx","name":"Nginx"}],
                 "snippets":[
                   {"id":"5b1e0c3a-2f7d-4c61-9a8e-0d4b6f2a91c7","title":"Reload Nginx",
                    "command":"sudo nginx -t && sudo systemctl reload nginx","category":"nginx","favorite":true},
                   {"id":"88c2f847-f6d3-4406-8d44-1f12d735b700","title":"Clear screen","command":"clear\\n",
                    "unknownField":1}
                 ]}
                """);

        var store = loaded();
        assertThat(store.snapshot()).extracting(Snippet::title).containsExactly("Reload Nginx", "Clear screen");
        assertThat(store.snapshot().get(0).favorite()).isTrue();
        assertThat(store.snapshot().get(0).category()).isEqualTo("nginx");
        assertThat(store.snapshot().get(1).enter()).isTrue();
    }

    @Test
    void formatLamaDimigrasikan() throws Exception {
        Files.writeString(file(), """
                {"format":1,"snippets":[
                  {"id":"6f1c9a52-3c8e-4a7e-9d43-0d2b1c3f4a11","name":"Vacuum","command":"vacuumdb --all","group":"Prod/DB"},
                  {"id":"7a2d0b63-4d9f-4b8f-8e54-1e3c2d4f5b22","name":"disk","command":"df -h","group":""}
                ]}
                """);

        var store = loaded();
        assertThat(store.snapshot()).extracting(Snippet::title).containsExactly("disk", "Vacuum");
        assertThat(store.snapshot().get(1).scope()).isEqualTo(new SnippetScope.Group("Prod/DB"));
        assertThat(store.snapshot().get(0).scope()).isEqualTo(SnippetScope.ALL);
        assertThat(store.snapshot()).noneMatch(Snippet::enter);

        store.setFavorite(store.snapshot().get(0).id(), true);
        assertThat(Files.readString(file())).contains("\"version\" : 2").doesNotContain("\"format\"");
        assertThat(new SnippetStore(file()).load().get(0).favorite()).isTrue();
    }

    @Test
    void nilaiVariabelDiingatPerHost() {
        var store = loaded();
        var s = Snippet.create("Logs", "journalctl -u ${service}");
        store.save(s);
        store.remember("host-a", s.id(), Map.of("service", "nginx", "bad", "a\nb"));

        var reloaded = new SnippetStore(file());
        reloaded.load();
        assertThat(reloaded.remembered("host-a", s.id())).containsExactly(Map.entry("service", "nginx"));
        assertThat(reloaded.remembered("host-b", s.id())).isEmpty();

        reloaded.delete(s.id());
        assertThat(reloaded.remembered("host-a", s.id())).as("ikut terhapus bersama snippet").isEmpty();
    }

    @Test
    void idKategoriUnik() {
        var store = loaded();
        var a = store.ensureCategory("Docker / K8s");
        var b = store.ensureCategory("docker k8s");
        assertThat(a.id()).isEqualTo("docker-k8s");
        assertThat(b.id()).isEqualTo("docker-k8s-2");
        assertThat(store.ensureCategory("  ")).isNull();
    }

    @Test
    void fileRusakDisisihkanTidakHilang() throws Exception {
        Files.writeString(file(), "{ ini bukan json");

        var store = new SnippetStore(file());

        assertThat(store.load()).isEmpty();
        try (var files = Files.list(tmp)) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .anyMatch(n -> n.startsWith("snippets.json.corrupt-"));
        }
        store.save(Snippet.create("Disk", "df -h"));
        assertThat(new SnippetStore(file()).load()).hasSize(1);
    }

    @Test
    void snippetTidakValidDiFileMembuatFileDisisihkan() throws Exception {
        Files.writeString(file(), """
                {"version":2,"snippets":[{"id":"6f1c9a52-3c8e-4a7e-9d43-0d2b1c3f4a11","title":"x","command":"printf '\\u001b[2J'"}]}
                """);

        assertThat(new SnippetStore(file()).load()).isEmpty();
    }
}

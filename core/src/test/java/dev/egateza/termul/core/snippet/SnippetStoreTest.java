package dev.egateza.termul.core.snippet;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnippetStoreTest {

    @TempDir
    Path tmp;

    private Path file() {
        return tmp.resolve("snippets.json");
    }

    @Test
    void fileBelumAdaBerartiKosong() {
        assertThat(new SnippetStore(file()).load()).isEmpty();
    }

    @Test
    void tersimpanUrutNamaDanBisaDiubahDanDihapus() {
        var store = new SnippetStore(file());
        store.load();
        var disk = Snippet.create("disk", "df -h", "");
        var logs = Snippet.create("Log nginx", "tail -f /var/log/nginx/error.log", "Prod");
        store.save(logs);
        store.save(disk);
        store.save(new Snippet(disk.id(), "Disk", "df -hT", ""));

        var reloaded = new SnippetStore(file()).load();
        assertThat(reloaded).extracting(Snippet::name).containsExactly("Disk", "Log nginx");
        assertThat(reloaded.getFirst().command()).isEqualTo("df -hT");

        store.delete(logs.id());
        assertThat(new SnippetStore(file()).load()).extracting(Snippet::name).containsExactly("Disk");
    }

    @Test
    void forGroupMenggabungkanGlobalDanGrupInduk() {
        var store = new SnippetStore(file());
        store.load();
        store.save(Snippet.create("Disk", "df -h", ""));
        store.save(Snippet.create("Vacuum", "vacuumdb --all", "Prod/DB"));
        store.save(Snippet.create("Deploy", "./deploy.sh", "Staging"));

        assertThat(store.forGroup("Prod/DB/Replica")).extracting(Snippet::name).containsExactly("Disk", "Vacuum");
        assertThat(store.forGroup("")).extracting(Snippet::name).containsExactly("Disk");
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
        store.save(Snippet.create("Disk", "df -h", ""));
        assertThat(new SnippetStore(file()).load()).hasSize(1);
    }

    @Test
    void snippetTidakValidDiFileMembuatFileDisisihkan() throws Exception {
        Files.writeString(file(), """
                {"format":1,"snippets":[{"id":"6f1c9a52-3c8e-4a7e-9d43-0d2b1c3f4a11","name":"x","command":"ls\\nrm -rf /","group":""}]}
                """);

        assertThat(new SnippetStore(file()).load()).isEmpty();
    }
}

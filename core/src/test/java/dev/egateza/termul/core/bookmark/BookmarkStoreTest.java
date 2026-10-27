package dev.egateza.termul.core.bookmark;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.bookmark.BookmarkStore.Side;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BookmarkStoreTest {

    @TempDir
    Path tmp;

    private final UUID web = UUID.randomUUID();
    private final UUID db = UUID.randomUUID();

    private Path file() {
        return tmp.resolve("bookmarks.json");
    }

    private BookmarkStore loaded() {
        var store = new BookmarkStore(file());
        store.load();
        return store;
    }

    @Test
    void fileBelumAdaBerartiKosong() {
        var store = loaded();

        assertThat(store.list(Side.REMOTE, null)).isEmpty();
        assertThat(store.list(Side.LOCAL, web)).isEmpty();
    }

    @Test
    void perSisiDanPerHostTerpisahDanTersimpan() {
        var store = loaded();
        store.set(Side.REMOTE, web, List.of("/var/www", "/etc/nginx"));
        store.set(Side.REMOTE, null, List.of("/home/ega/docker"));
        store.set(Side.LOCAL, null, List.of("D:\\IdeaProjects"));

        var reloaded = loaded();
        assertThat(reloaded.list(Side.REMOTE, web)).containsExactly("/var/www", "/etc/nginx");
        assertThat(reloaded.list(Side.REMOTE, db)).isEmpty();
        assertThat(reloaded.list(Side.REMOTE, null)).containsExactly("/home/ega/docker");
        assertThat(reloaded.list(Side.LOCAL, null)).containsExactly("D:\\IdeaProjects");
        assertThat(reloaded.list(Side.LOCAL, web)).isEmpty();
    }

    @Test
    void urutanDipertahankanTanpaDuplikatDanPathKosong() {
        var store = loaded();
        store.set(Side.REMOTE, web, List.of("/b", " /a ", "", "/b", "  "));

        assertThat(loaded().list(Side.REMOTE, web)).containsExactly("/b", "/a");
    }

    @Test
    void addMenambahDiAkhirDanMenolakDuplikat() {
        var store = loaded();
        store.set(Side.REMOTE, null, List.of("/etc"));

        assertThat(store.add(Side.REMOTE, null, "/var/log")).isTrue();
        assertThat(store.add(Side.REMOTE, null, " /etc ")).isFalse();
        assertThat(store.add(Side.REMOTE, null, "  ")).isFalse();

        assertThat(loaded().list(Side.REMOTE, null)).containsExactly("/etc", "/var/log");
    }

    @Test
    void bookmarkHostYangProfilnyaDihapusIkutDibuang() {
        var store = loaded();
        store.set(Side.REMOTE, web, List.of("/var/www"));
        store.set(Side.LOCAL, db, List.of("C:\\dump"));
        store.set(Side.REMOTE, null, List.of("/tmp"));

        store.retainHosts(Set.of(web));

        var reloaded = loaded();
        assertThat(reloaded.list(Side.REMOTE, web)).containsExactly("/var/www");
        assertThat(reloaded.list(Side.LOCAL, db)).isEmpty();
        assertThat(reloaded.list(Side.REMOTE, null)).as("bookmark bersama tidak tersentuh").containsExactly("/tmp");
    }

    @Test
    void fileRusakDisisihkanTidakHilang() throws Exception {
        Files.writeString(file(), "{ ini bukan json");

        var store = loaded();

        assertThat(store.list(Side.REMOTE, null)).isEmpty();
        try (var files = Files.list(tmp)) {
            assertThat(files.map(p -> p.getFileName().toString())).anyMatch(n -> n.startsWith("bookmarks.json.corrupt-"));
        }
    }
}

package dev.egateza.termul.core.highlight;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HighlightStoreTest {

    @TempDir
    Path dir;

    @Test
    void tanpaFileBerisiBawaanYangBelumAktif() {
        var store = new HighlightStore(dir.resolve("highlight.json"));

        HighlightSettings s = store.load();

        assertThat(s.enabled()).isFalse();
        assertThat(s.profiles()).containsExactly(HighlightProfile.defaults());
        assertThat(s.globalProfileId()).isEqualTo(HighlightProfile.defaults().id());
    }

    @Test
    void simpanDanMuatUlang() {
        Path file = dir.resolve("highlight.json");
        var store = new HighlightStore(file);
        store.load();
        UUID host = UUID.randomUUID();
        var p = HighlightProfile.create("Log", List.of(HighlightKeyword.of("FATAL", "#ff0000")));
        var seen = new ArrayList<HighlightSettings>();
        store.addListener(seen::add);

        store.update(s -> s.withEnabled(true).withProfile(p)
                .withAssignments(p.id(), Set.of(host), Set.of("Prod/DB"), Set.of(), true));

        HighlightSettings loaded = new HighlightStore(file).load();
        assertThat(loaded).isEqualTo(store.snapshot());
        assertThat(loaded.forHost(host, "")).contains(p);
        assertThat(loaded.groups()).containsEntry("Prod/DB", p.id());
        assertThat(loaded.allLocalsProfileId()).isEqualTo(p.id());
        assertThat(seen).hasSize(1);
    }

    @Test
    void fileRusakDisisihkan() throws IOException {
        Path file = dir.resolve("highlight.json");
        Files.writeString(file, "{rusak");

        HighlightSettings s = new HighlightStore(file).load();

        assertThat(s).isEqualTo(HighlightSettings.defaults());
        try (var files = Files.list(dir)) {
            assertThat(files.map(f -> f.getFileName().toString())).anyMatch(n -> n.startsWith("highlight.json.corrupt-"));
        }
    }
}

package dev.egateza.termul.core.shortcut;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShortcutStoreTest {

    @TempDir
    Path dir;

    @Test
    void tanpaFileSemuaDefault() {
        assertThat(new ShortcutStore(dir.resolve("shortcuts.json")).load()).isEmpty();
    }

    @Test
    void simpanDanMuatUlang() {
        var file = dir.resolve("shortcuts.json");
        var store = new ShortcutStore(file);
        var seen = new ArrayList<Map<String, String>>();
        store.addListener(seen::add);
        var overrides = new HashMap<String, String>();
        overrides.put("tab.new", " mod+shift+T ");
        overrides.put("inject.root", "");
        overrides.put(" ", "mod+X");
        store.save(overrides);

        assertThat(store.snapshot()).containsExactlyInAnyOrderEntriesOf(Map.of("tab.new", "mod+shift+T", "inject.root", ""));
        assertThat(seen).hasSize(1);
        assertThat(new ShortcutStore(file).load()).isEqualTo(store.snapshot());
    }

    @Test
    void fileRusakDisisihkan() throws Exception {
        var file = dir.resolve("shortcuts.json");
        Files.writeString(file, "{bukan json");

        assertThat(new ShortcutStore(file).load()).isEmpty();
        assertThat(file).doesNotExist();
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).anyMatch(n -> n.startsWith("shortcuts.json.corrupt-"));
        }
    }
}

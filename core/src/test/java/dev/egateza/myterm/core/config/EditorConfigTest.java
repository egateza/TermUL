package dev.egateza.myterm.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorConfigTest {

    @TempDir
    Path dir;

    @Test
    void defaultVsCodeWait() {
        Path f = dir.resolve("config.yml");
        assertThat(EditorConfig.defaults().commandFor(f))
                .containsExactly("code", "--wait", f.toAbsolutePath().toString());
    }

    @Test
    void mappingPerEkstensiCaseInsensitive() {
        var cfg = new EditorConfig(null, Map.of(".SQL", "\"C:\\Program Files\\Notepad++\\notepad++.exe\" -multiInst"));
        Path f = dir.resolve("query.sql");

        assertThat(cfg.commandFor(f)).containsExactly("C:\\Program Files\\Notepad++\\notepad++.exe", "-multiInst",
                f.toAbsolutePath().toString());
        assertThat(cfg.commandFor(dir.resolve("a.conf")).getFirst()).isEqualTo("code");
        assertThat(cfg.commandFor(dir.resolve("Makefile")).getFirst()).isEqualTo("code");
    }

    @Test
    void placeholderDiTengahArgumen() {
        var cfg = new EditorConfig("subl --wait --file={file}", Map.of());
        Path f = dir.resolve("x.txt");
        assertThat(cfg.commandFor(f)).containsExactly("subl", "--wait", "--file=" + f.toAbsolutePath());
    }

    @Test
    void parseMappingText() {
        var cfg = EditorConfig.parse("notepad", "# komentar\n.yml = code --wait\n\nsql=notepad++ {file}\n");
        assertThat(cfg.byExtension()).containsEntry("yml", "code --wait").containsEntry("sql", "notepad++ {file}");
        assertThat(EditorConfig.parse(cfg.defaultCommand(), cfg.mappingText())).isEqualTo(cfg);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> EditorConfig.parse("x", "ok = a\nsalah"))
                .hasMessageContaining("Baris 2");
    }

    @Test
    void configStoreRoundTripDanFileRusak() throws Exception {
        Path file = dir.resolve("config.json");
        var store = new ConfigStore(file);
        assertThat(store.load()).isEqualTo(AppConfig.defaults());

        var cfg = AppConfig.defaults().withEditors(new EditorConfig("notepad", Map.of("py", "code {file}")));
        store.save(cfg);
        assertThat(new ConfigStore(file).load()).isEqualTo(cfg);

        Files.writeString(file, "{rusak");
        assertThat(new ConfigStore(file).load()).isEqualTo(AppConfig.defaults());
        assertThat(file).hasContent("{rusak");
    }
}

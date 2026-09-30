package dev.egateza.termul.core.config;

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

    @Test
    void firstMatchingMaskWinsAndFallsBackToLegacySettings() {
        var cfg = new EditorConfig("fallback {file}", Map.of("md", "legacy {file}"), java.util.List.of(
                new EditorConfig.NamedEditor("Notepad++", "*.sql;*.TXT", "notepad++ {file}"),
                new EditorConfig.NamedEditor("Code", "*.*", "code --wait {file}")));

        assertThat(cfg.templateFor(Path.of("a.SQL"))).isEqualTo("notepad++ {file}");
        assertThat(cfg.templateFor(Path.of("x", "b.txt"))).isEqualTo("notepad++ {file}");
        assertThat(cfg.templateFor(Path.of("c.java"))).isEqualTo("code --wait {file}");
        assertThat(cfg.templateFor(Path.of("Makefile"))).isEqualTo("code --wait {file}");
        assertThat(new EditorConfig("fallback {file}", Map.of("md", "legacy {file}")).templateFor(Path.of("r.md")))
                .isEqualTo("legacy {file}");
    }

    @Test
    void maskSupportsWildcardsAndSeparators() {
        var editor = new EditorConfig.NamedEditor("Log", "*.log, app-?.out", "less {file}");

        assertThat(editor.matches("server.LOG")).isTrue();
        assertThat(editor.matches("app-1.out")).isTrue();
        assertThat(editor.matches("app-12.out")).isFalse();
        assertThat(editor.matches("server.log.gz")).isFalse();
        assertThat(new EditorConfig.NamedEditor("Semua", " ", "x").mask()).isEqualTo("*.*");
    }

    @Test
    void invalidEditorIsRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new EditorConfig.NamedEditor("", "*.*", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new EditorConfig.NamedEditor("A", ";,", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateEditorNamesKeepFirst() {
        var cfg = new EditorConfig(null, Map.of(), java.util.List.of(
                new EditorConfig.NamedEditor("Code", "code {file}"),
                new EditorConfig.NamedEditor("code", "other")));

        assertThat(cfg.editors()).hasSize(1);
        assertThat(cfg.editors().getFirst().command()).isEqualTo("code {file}");
    }

    @Test
    void explicitTemplateOverridesExtensionMapping() {
        var file = java.nio.file.Path.of("tmp", "a.sql");

        assertThat(EditorConfig.commandFor(file, "subl --wait {file}"))
                .containsExactly("subl", "--wait", file.toAbsolutePath().toString());
    }
}

package dev.egateza.termul.app.edit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.WINDOWS)
class EditorLauncherTest {

    @TempDir
    Path dir;

    @Test
    void scriptCmdDijalankanLewatCmdExe() throws Exception {
        Path script = Files.writeString(dir.resolve("editor.cmd"), "@echo off\r\n");

        assertThat(EditorLauncher.resolve(List.of(script.toString(), "--wait", "C:\\x\\a.txt")))
                .containsExactly("cmd.exe", "/c", script.toString(), "--wait", "C:\\x\\a.txt");
    }

    @Test
    void exeDariPathTanpaWrapper() {
        var resolved = EditorLauncher.resolve(List.of("cmd", "/c", "echo"));
        assertThat(resolved.getFirst().toLowerCase()).endsWith("cmd.exe");
        assertThat(resolved).hasSize(3);
    }

    @Test
    void commandTidakDitemukanDibiarkan() {
        assertThat(EditorLauncher.resolve(List.of("tidak-ada-editor-xyz", "f"))).containsExactly("tidak-ada-editor-xyz", "f");
    }
}

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
    void fileExecutableTidakDibukaLewatAssociation() {
        assertThat(EditorLauncher.isExecutable("deploy.bat")).isTrue();
        assertThat(EditorLauncher.isExecutable("Setup.EXE")).isTrue();
        assertThat(EditorLauncher.isExecutable("x.ps1")).isTrue();
        assertThat(EditorLauncher.isExecutable("app.jar")).isTrue();
        assertThat(EditorLauncher.isExecutable("a.hta")).isTrue();
        assertThat(EditorLauncher.isExecutable("evil.vbs. . ")).isTrue(); // titik/spasi akhir dibuang Windows
        assertThat(EditorLauncher.isExecutable("index.php")).isFalse();
        assertThat(EditorLauncher.isExecutable("log4j2.xml")).isFalse();
        assertThat(EditorLauncher.isExecutable("Makefile")).isFalse();
    }

    @Test
    void ekstensiFile() {
        assertThat(EditorLauncher.extension("BWN.PHP")).isEqualTo("php");
        assertThat(EditorLauncher.extension("a.tar.gz")).isEqualTo("gz");
        assertThat(EditorLauncher.extension(".bashrc")).isEmpty();
        assertThat(EditorLauncher.extension("monitor_db.")).isEmpty();
        assertThat(EditorLauncher.extension("README")).isEmpty();
    }

    @Test
    void commandTidakDitemukanDibiarkan() {
        assertThat(EditorLauncher.resolve(List.of("tidak-ada-editor-xyz", "f"))).containsExactly("tidak-ada-editor-xyz", "f");
    }
}

package dev.egateza.termul.app.edit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class EditorLauncherTest {

    @TempDir
    Path dir;

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void scriptCmdDijalankanLewatCmdExe() throws Exception {
        Path script = Files.writeString(dir.resolve("editor.cmd"), "@echo off\r\n");

        assertThat(EditorLauncher.resolve(List.of(script.toString(), "--wait", "C:\\x\\a.txt")))
                .containsExactly("cmd.exe", "/c", script.toString(), "--wait", "C:\\x\\a.txt");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
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
    void fileYangDijalankanMacOsTidakDibukaLewatAssociation() {
        assertThat(EditorLauncher.isExecutable("deploy.command")).isTrue(); // dibuka Terminal = dijalankan
        assertThat(EditorLauncher.isExecutable("Evil.app")).isTrue();
        assertThat(EditorLauncher.isExecutable("install.sh")).isTrue();
        assertThat(EditorLauncher.isExecutable("x.scpt")).isTrue();
        assertThat(EditorLauncher.isExecutable("setup.pkg")).isTrue();
        assertThat(EditorLauncher.isExecutable("nginx.conf")).isFalse();
    }

    @Test
    void unixCommandDicariDiPathLaluFolderCliUmum() throws Exception {
        Path bin = Files.createDirectories(dir.resolve("bin"));
        Path code = executable(bin.resolve("code"));

        // PATH minimal aplikasi Finder: code tetap ketemu lewat ~/bin
        assertThat(EditorLauncher.resolveUnix(List.of("code", "--wait", "/tmp/a.txt"), "/usr/bin:/bin", dir))
                .containsExactly(code.toString(), "--wait", "/tmp/a.txt");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // PATH Unix dipisah ':', bentrok dengan drive letter path Windows
    void unixCommandDicariDiPath() throws Exception {
        Path bin = Files.createDirectories(dir.resolve("tools"));
        Path code = executable(bin.resolve("code"));

        assertThat(EditorLauncher.resolveUnix(List.of("code", "x"), "/usr/bin:" + bin, dir))
                .containsExactly(code.toString(), "x");
    }

    @Test
    void unixCodeJatuhKeBundleVsCode() throws Exception {
        Path cli = EditorLauncher.vsCodeCli(dir).getLast(); // ~/Applications/Visual Studio Code.app/...
        Files.createDirectories(cli.getParent());
        executable(cli);

        assertThat(EditorLauncher.resolveUnix(List.of("code", "--wait", "f"), null, dir))
                .containsExactly(cli.toString(), "--wait", "f");
    }

    @Test
    void unixCommandDenganPathAtauTidakDitemukanDibiarkan() {
        assertThat(EditorLauncher.resolveUnix(List.of("/usr/bin/vim", "f"), "", dir)).containsExactly("/usr/bin/vim", "f");
        assertThat(EditorLauncher.resolveUnix(List.of("open", "-e", "f"), "", dir)).containsExactly("open", "-e", "f");
    }

    @Test
    void programDariChooser() {
        assertThat(EditorSettingsDialog.programCommand("/Applications/Sublime Text.app"))
                .isEqualTo("open -a \"/Applications/Sublime Text.app\" {file}");
        assertThat(EditorSettingsDialog.programCommand("C:\\Tools\\npp.exe")).isEqualTo("\"C:\\Tools\\npp.exe\" {file}");
    }

    private static Path executable(Path p) throws Exception {
        Files.writeString(p, "#!/bin/sh\n");
        if (p.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(p, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        return p;
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

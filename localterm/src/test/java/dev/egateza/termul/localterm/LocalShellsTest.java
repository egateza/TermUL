package dev.egateza.termul.localterm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.core.Os;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class LocalShellsTest {

    private static final Path HOME = Path.of("/Users/me");

    private static Predicate<Path> existing(String... paths) {
        var set = Set.of(paths);
        return p -> set.contains(p.toString());
    }

    @Test
    void macMemakaiShellLoginUser() {
        var shell = LocalShells.defaultFor(Os.MAC, Map.of("SHELL", "/opt/homebrew/bin/fish"),
                existing("/opt/homebrew/bin/fish"), HOME);

        assertThat(shell.command()).containsExactly("/opt/homebrew/bin/fish", "-l");
        assertThat(shell.directory()).isEqualTo(HOME);
        assertThat(shell.label()).isEqualTo("fish");
    }

    @Test
    void shellTidakAdaAtauRelatifJatuhKeCadanganPerOs() {
        assertThat(LocalShells.defaultFor(Os.MAC, Map.of(), existing(), HOME).command())
                .containsExactly("/bin/zsh", "-l");
        assertThat(LocalShells.defaultFor(Os.MAC, Map.of("SHELL", "/usr/local/bin/gone"), existing(), HOME).command())
                .containsExactly("/bin/zsh", "-l");
        assertThat(LocalShells.defaultFor(Os.OTHER, Map.of("SHELL", "zsh"), existing("zsh"), HOME).command())
                .containsExactly("/bin/bash", "-l");
    }

    @Test
    void windowsMemilihPwshDariPathLaluPowershellBawaan() {
        var env = Map.of("Path", "C:\\Windows\\System32;\"C:\\Program Files\\PowerShell\\7\"", "SystemRoot", "C:\\Windows");
        var pwsh = Path.of("C:\\Program Files\\PowerShell\\7", "pwsh.exe").toString();
        assertThat(LocalShells.defaultFor(Os.WINDOWS, env, existing(pwsh), HOME).command())
                .containsExactly(pwsh, "-NoLogo");

        var builtin = Path.of("C:\\Windows", "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString();
        assertThat(LocalShells.defaultFor(Os.WINDOWS, env, existing(builtin), HOME).command())
                .containsExactly(builtin, "-NoLogo");
        assertThat(LocalShells.defaultFor(Os.WINDOWS, Map.of(), existing(), HOME).command())
                .containsExactly("powershell.exe", "-NoLogo");
    }

    @Test
    void environmentUntukJediTermDanUtf8() {
        var env = LocalShells.environment(Os.MAC, Map.of("TERM", "dumb", "HOME", "/Users/me"));

        assertThat(env).containsEntry("TERM", "xterm-256color").containsEntry("COLORTERM", "truecolor")
                .containsEntry("TERM_PROGRAM", "TermUL").containsEntry("LANG", "en_US.UTF-8")
                .containsEntry("HOME", "/Users/me");
    }

    @Test
    void localeYangSudahAdaTidakDitimpa() {
        assertThat(LocalShells.environment(Os.MAC, Map.of("LANG", "id_ID.UTF-8"))).containsEntry("LANG", "id_ID.UTF-8");
        assertThat(LocalShells.environment(Os.OTHER, Map.of("LC_ALL", "C.UTF-8"))).doesNotContainKey("LANG");
        assertThat(LocalShells.environment(Os.WINDOWS, Map.of())).doesNotContainKey("LANG");
    }

    @Test
    void labelTanpaFolderDanExe() {
        assertThat(new LocalShell(List.of("C:\\Program Files\\PowerShell\\7\\pwsh.exe"), HOME, Map.of()).label())
                .isEqualTo("pwsh");
        assertThat(new LocalShell(List.of("/bin/bash", "-l"), HOME, Map.of()).label()).isEqualTo("bash");
        assertThatThrownBy(() -> new LocalShell(List.of(), HOME, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deteksiUnixDariShellLoginDanEtcShellsSatuPerNama() {
        var etc = List.of("# /etc/shells", "/bin/bash", "/bin/zsh", "/usr/bin/bash", "/opt/homebrew/bin/fish", "/bin/gone", "");
        var shells = LocalShells.detect(Os.MAC, Map.of("SHELL", "/bin/zsh"),
                existing("/bin/bash", "/bin/zsh", "/usr/bin/bash", "/opt/homebrew/bin/fish"), etc, List.of("Ubuntu"));

        assertThat(shells).extracting(DetectedShell::name).containsExactly("zsh", "bash", "fish");
        assertThat(shells.getFirst().command()).containsExactly("/bin/zsh", "-l");
        assertThat(shells.get(2).command()).containsExactly("/opt/homebrew/bin/fish", "-l");
        assertThat(shells.getFirst().id()).isEqualTo(new DetectedShell("/bin/zsh", "x", List.of("y")).id()); // stabil
    }

    @Test
    void deteksiWindowsPowershellCmdGitBashDanWsl() {
        var env = Map.of("Path", "C:\\Program Files\\Git\\cmd", "SystemRoot", "C:\\Windows",
                "ProgramFiles", "C:\\Program Files", "ComSpec", "C:\\Windows\\system32\\cmd.exe");
        var pwsh = Path.of("C:\\Program Files", "PowerShell", "7", "pwsh.exe").toString();
        var bash = Path.of("C:\\Program Files", "Git", "bin", "bash.exe").toString(); // lokasi instalasi standar
        var cmd = Path.of("C:\\Windows\\system32\\cmd.exe").toString();

        var shells = LocalShells.detect(Os.WINDOWS, env, existing(pwsh, bash, cmd), List.of(), List.of("Ubuntu"));

        assertThat(shells).extracting(DetectedShell::key)
                .containsExactly("pwsh", "powershell", "cmd", "git-bash", "wsl:Ubuntu");
        assertThat(shells.get(0).command()).containsExactly(pwsh, "-NoLogo");
        assertThat(shells.get(2).command()).containsExactly(cmd);
        assertThat(shells.get(3).command()).containsExactly(bash, "--login", "-i");
        assertThat(shells.get(4).command()).containsExactly(
                Path.of("C:\\Windows", "System32", "wsl.exe").toString(), "-d", "Ubuntu");
        assertThat(LocalShells.detect(Os.WINDOWS, Map.of(), existing(), List.of(), List.of()))
                .extracting(DetectedShell::key).containsExactly("powershell", "cmd");
    }

    @Test
    void buildMenerapkanEnvironmentProfilDanDirektori() {
        var shell = LocalShells.build(List.of("/bin/zsh", "-l"), "~/proyek",
                new java.util.LinkedHashMap<>(Map.of("JAVA_HOME", "/opt/jdk", "PAGER", "")), Os.MAC,
                Map.of("PAGER", "less", "HOME", "/Users/me"), HOME);

        assertThat(shell.directory()).isEqualTo(HOME.resolve("proyek"));
        assertThat(shell.environment()).containsEntry("JAVA_HOME", "/opt/jdk").doesNotContainKey("PAGER")
                .containsEntry("TERM", "xterm-256color");
        assertThat(LocalShells.directory("", HOME)).isEqualTo(HOME);
        assertThat(LocalShells.directory("~", HOME)).isEqualTo(HOME);
        assertThat(LocalShells.directory("/tmp", HOME)).isEqualTo(Path.of("/tmp"));
    }

    @Test
    void buildWindowsMenggantiVariabelTanpaPekaHuruf() {
        var shell = LocalShells.build(List.of("cmd.exe"), "", Map.of("PATH", "C:\\bin"), Os.WINDOWS,
                Map.of("Path", "C:\\Windows"), HOME);

        assertThat(shell.environment()).containsEntry("PATH", "C:\\bin").doesNotContainKey("Path");
    }
}

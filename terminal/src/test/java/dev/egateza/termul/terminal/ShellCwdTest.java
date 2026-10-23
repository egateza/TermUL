package dev.egateza.termul.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.ssh.RemoteExec;
import org.junit.jupiter.api.Test;

class ShellCwdTest {

    @Test
    void tokenAcak32Hex() {
        String a = ShellCwd.newToken();

        assertThat(a).matches("[0-9a-f]{32}");
        assertThat(ShellCwd.newToken()).isNotEqualTo(a);
    }

    @Test
    void commandMemakaiShDanToken() {
        String token = ShellCwd.newToken();

        String cmd = ShellCwd.probeCommand(token);

        assertThat(cmd).startsWith("sh -c '").contains(ShellCwd.ENV_NAME + "=" + token).contains("/proc/$6/cwd");
    }

    @Test
    void tokenTidakValidDitolak() {
        assertThatThrownBy(() -> ShellCwd.probeCommand("abc'; rm -rf ~"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasilDitemukanMempertahankanSpasi() {
        var r = ShellCwd.parse(new RemoteExec.Result(0, "/home/dev/folder baru \n", ""));

        assertThat(r).isEqualTo(new ShellCwd.Result.Found("/home/dev/folder baru "));
    }

    @Test
    void exitCodeMenentukanJenisHasil() {
        assertThat(ShellCwd.parse(new RemoteExec.Result(ShellCwd.EXIT_NO_MARKER, "", "")))
                .isInstanceOf(ShellCwd.Result.Unsupported.class);
        assertThat(ShellCwd.parse(new RemoteExec.Result(ShellCwd.EXIT_UNREADABLE, "", "")))
                .isInstanceOf(ShellCwd.Result.Unreadable.class);
        assertThat(ShellCwd.parse(new RemoteExec.Result(127, "", "sh: not found")))
                .isInstanceOf(ShellCwd.Result.Unsupported.class);
    }

    @Test
    void keluaranAnehTidakDipakai() {
        assertThat(ShellCwd.parse(new RemoteExec.Result(0, "relatif\n", "")))
                .isInstanceOf(ShellCwd.Result.Unreadable.class);
        assertThat(ShellCwd.parse(new RemoteExec.Result(0, "/a\n/b\n", "")))
                .isInstanceOf(ShellCwd.Result.Unreadable.class);
    }
}

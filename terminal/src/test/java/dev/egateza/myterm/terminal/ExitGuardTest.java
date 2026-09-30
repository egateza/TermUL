package dev.egateza.myterm.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExitGuardTest {

    @Test
    void promptKosong() {
        assertThat(ExitGuard.isEmptyPrompt("ega@server01:~$ ")).isTrue();
        assertThat(ExitGuard.isEmptyPrompt("ega@server01:~$")).isTrue();
        assertThat(ExitGuard.isEmptyPrompt("root@server01:/home/ega# ")).isTrue();
        assertThat(ExitGuard.isEmptyPrompt("[root@centos ~]# ")).isTrue();
        assertThat(ExitGuard.isEmptyPrompt("$ ")).isTrue();
    }

    @Test
    void bukanPromptKosong() {
        assertThat(ExitGuard.isEmptyPrompt("ega@server01:~$ ls")).isFalse();
        assertThat(ExitGuard.isEmptyPrompt(">>> ")).isFalse();            // python REPL
        assertThat(ExitGuard.isEmptyPrompt("[sudo] password for ega: ")).isFalse();
        assertThat(ExitGuard.isEmptyPrompt("")).isFalse();
        assertThat(ExitGuard.isEmptyPrompt(null)).isFalse();
    }

    @Test
    void perintahExitDanLogout() {
        assertThat(ExitGuard.isExitCommand("ega@server01:~$ logout")).isTrue();
        assertThat(ExitGuard.isExitCommand("ega@server01:~$ exit   ")).isTrue();
        assertThat(ExitGuard.isExitCommand("root@server01:~# exit 0")).isTrue();
        assertThat(ExitGuard.isExitCommand("ega@server01:~$ exitcode")).isFalse();
        assertThat(ExitGuard.isExitCommand("ega@server01:~$ echo exit")).isFalse();
        assertThat(ExitGuard.isExitCommand("exit")).isFalse(); // tidak ada prompt (mis. di dalam program)
    }
}

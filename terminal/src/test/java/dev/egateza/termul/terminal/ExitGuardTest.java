package dev.egateza.termul.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExitGuardTest {

    @Test
    void promptKosong() {
        assertThat(ExitGuard.isEmptyPrompt("user@server01:~$ ")).isTrue();
        assertThat(ExitGuard.isEmptyPrompt("user@server01:~$")).isTrue();
        assertThat(ExitGuard.isEmptyPrompt("root@server01:/home/user# ")).isTrue();
        assertThat(ExitGuard.isEmptyPrompt("[root@centos ~]# ")).isTrue();
        assertThat(ExitGuard.isEmptyPrompt("$ ")).isTrue();
        assertThat(ExitGuard.isEmptyPrompt("~$ ")).isTrue();
        assertThat(ExitGuard.isEmptyPrompt("bash-5.1$ ")).isTrue();  // bash tanpa PS1 (mis. di container)
        assertThat(ExitGuard.isEmptyPrompt("sh-4.2# ")).isTrue();
        assertThat(ExitGuard.isEmptyPrompt("server01$ ")).isTrue();  // PS1='\h$ '
        assertThat(ExitGuard.isEmptyPrompt("$$ ")).isTrue();
    }

    @Test
    void promptPsqlBukanShell() {
        assertThat(ExitGuard.isEmptyPrompt("postgres=# ")).isFalse();
        assertThat(ExitGuard.isEmptyPrompt("mydb=> ")).isFalse();
        assertThat(ExitGuard.isEmptyPrompt("mydb-# ")).isFalse();     // baris lanjutan
        assertThat(ExitGuard.isEmptyPrompt("mydb=*# ")).isFalse();    // di dalam transaksi
        assertThat(ExitGuard.isEmptyPrompt("mydb=!# ")).isFalse();    // transaksi gagal
        assertThat(ExitGuard.isEmptyPrompt("mydb(# ")).isFalse();     // kurung belum ditutup
        assertThat(ExitGuard.isEmptyPrompt("postgres@db:5432/app=# ")).isFalse(); // PROMPT1 kustom
        assertThat(ExitGuard.isExitCommand("postgres=# exit")).isFalse();
        assertThat(ExitGuard.isExitCommand("postgres=# \\q")).isFalse();
    }

    @Test
    void promptProgramLainBukanShell() {
        assertThat(ExitGuard.isEmptyPrompt("mysql> ")).isFalse();
        assertThat(ExitGuard.isEmptyPrompt("sqlite> ")).isFalse();
        assertThat(ExitGuard.isEmptyPrompt("127.0.0.1:6379> ")).isFalse(); // redis-cli
    }

    @Test
    void bukanPromptKosong() {
        assertThat(ExitGuard.isEmptyPrompt("user@server01:~$ ls")).isFalse();
        assertThat(ExitGuard.isEmptyPrompt(">>> ")).isFalse();            // python REPL
        assertThat(ExitGuard.isEmptyPrompt("[sudo] password for user: ")).isFalse();
        assertThat(ExitGuard.isEmptyPrompt("")).isFalse();
        assertThat(ExitGuard.isEmptyPrompt(null)).isFalse();
    }

    @Test
    void perintahExitDanLogout() {
        assertThat(ExitGuard.isExitCommand("user@server01:~$ logout")).isTrue();
        assertThat(ExitGuard.isExitCommand("user@server01:~$ exit   ")).isTrue();
        assertThat(ExitGuard.isExitCommand("root@server01:~# exit 0")).isTrue();
        assertThat(ExitGuard.isExitCommand("user@server01:~$ exitcode")).isFalse();
        assertThat(ExitGuard.isExitCommand("user@server01:~$ echo exit")).isFalse();
        assertThat(ExitGuard.isExitCommand("exit")).isFalse(); // tidak ada prompt (mis. di dalam program)
    }
}

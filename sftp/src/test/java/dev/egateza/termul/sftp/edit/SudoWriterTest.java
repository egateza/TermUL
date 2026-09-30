package dev.egateza.termul.sftp.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.sftp.RemoteFileException;
import dev.egateza.termul.ssh.RemoteExec.Result;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SudoWriterTest {

    @Test
    void argumenDiQuoteSehinggaPathAnehTidakBisaMenyisipkanCommand() {
        var cmd = SudoWriter.command("/etc/app/x; rm -rf /", "/tmp/termul-ab/content", "nginx -t");

        assertThat(cmd.subList(0, 2)).containsExactly("sh", "-c");
        assertThat(cmd.get(3)).isEqualTo("termul");
        assertThat(cmd.get(4)).isEqualTo("'/etc/app/x; rm -rf /'");
        assertThat(cmd.get(6)).isEqualTo("'nginx -t'");
        assertThat(cmd.get(7)).isEqualTo(SudoWriter.BACKUP_DIR);
        assertThat(cmd.get(8)).isEqualTo(String.valueOf(SudoWriter.KEEP_BACKUPS));
        assertThat(String.join(" ", cmd)).doesNotContain("\n'nginx"); // script satu argumen ter-quote
    }

    @Test
    void hookKosongTetapMenjadiSatuArgumen() {
        assertThat(SudoWriter.command("/etc/x", "/tmp/termul-ab/content", "").get(6)).isEqualTo("''");
    }

    @Test
    void berhasilMengembalikanPathBackup() throws Exception {
        var installed = SudoWriter.interpret(
                new Result(0, "/var/backups/termul/%etc%x.20261001-101010.42\n", ""), "/etc/x", "");

        assertThat(installed.backupPath()).isEqualTo("/var/backups/termul/%etc%x.20261001-101010.42");
    }

    @Test
    void validasiGagalDanDirollbackMenyertakanOutputValidator() {
        assertThatThrownBy(() -> SudoWriter.interpret(
                new Result(10, "", "nginx: [emerg] unexpected \"}\"\nnginx: configuration file test failed"),
                "/etc/nginx/sites-available/x", "nginx -t"))
                .isInstanceOfSatisfying(RemoteValidationException.class, e -> {
                    assertThat(e.rolledBack()).isTrue();
                    assertThat(e.command()).isEqualTo("nginx -t");
                    assertThat(e.output()).contains("test failed");
                    assertThat(e.getMessage()).contains("dikembalikan");
                });
    }

    @Test
    void rollbackGagalDilaporkanKeras() {
        assertThatThrownBy(() -> SudoWriter.interpret(new Result(11, "", "x"), "/etc/x", "check"))
                .isInstanceOfSatisfying(RemoteValidationException.class,
                        e -> assertThat(e.rolledBack()).isFalse())
                .hasMessageContaining("rollback");
    }

    @Test
    void errorScriptDanSudoDiterjemahkan() {
        assertThatThrownBy(() -> SudoWriter.interpret(new Result(20, "", "Bukan file biasa: /etc/x"), "/etc/x", ""))
                .isExactlyInstanceOf(RemoteFileException.class)
                .hasMessageContaining("Bukan file biasa");
        assertThatThrownBy(() -> SudoWriter.interpret(
                new Result(1, "", "dev is not in the sudoers file."), "/etc/x", ""))
                .isInstanceOf(SudoAuthException.class)
                .hasMessageContaining("hak sudo");
        assertThatThrownBy(() -> SudoWriter.interpret(new Result(127, "", "sh: not found"), "/etc/x", ""))
                .hasMessageContaining("exit 127");
    }

    @Test
    void mengenaliPasswordSalahDanPasswordDibutuhkan() {
        assertThat(SudoWriter.wrongPassword(new Result(1, "", "Sorry, try again.\nsudo: 1 incorrect password attempt")))
                .isTrue();
        assertThat(SudoWriter.wrongPassword(new Result(1, "", "sudo: no password was provided"))).isTrue();
        assertThat(SudoWriter.wrongPassword(new Result(10, "", "Sorry, try again."))) // output validator, bukan sudo
                .isFalse();
        assertThat(SudoWriter.needsPassword(new Result(1, "", "sudo: a password is required"))).isTrue();
        assertThat(SudoWriter.needsPassword(new Result(0, "", ""))).isFalse();
    }

    @Test
    void passwordDiencodeUtf8DenganNewline() throws Exception {
        char[] pw = "päss".toCharArray();

        byte[] line = SudoWriter.utf8Line(pw);

        assertThat(line).isEqualTo("päss\n".getBytes(StandardCharsets.UTF_8));
    }
}

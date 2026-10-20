package dev.egateza.termul.core.localshell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandLineTest {

    @Test
    void pisahSpasiDanKutip() {
        assertThat(CommandLine.split("  --login   -i ")).containsExactly("--login", "-i");
        assertThat(CommandLine.split("-c \"echo halo dunia\" x")).containsExactly("-c", "echo halo dunia", "x");
        assertThat(CommandLine.split("a\"b c\"d")).containsExactly("ab cd");
        assertThat(CommandLine.split("\"\" x")).containsExactly("", "x");
        assertThat(CommandLine.split("say \\\"hi\\\"")).containsExactly("say", "\"hi\"");
        assertThat(CommandLine.split("")).isEmpty();
        assertThat(CommandLine.split(null)).isEmpty();
    }

    @Test
    void backslashPathWindowsTidakPerluEscape() {
        assertThat(CommandLine.split("\"C:\\Program Files\\Git\\bin\\bash.exe\" --login"))
                .containsExactly("C:\\Program Files\\Git\\bin\\bash.exe", "--login");
    }

    @Test
    void kutipTidakDitutupDitolak() {
        assertThatThrownBy(() -> CommandLine.split("-c \"echo")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kutip");
    }

    @Test
    void joinKebalikanSplit() {
        var args = List.of("-c", "echo halo", "", "say \"hi\"", "C:\\x\\y");
        String line = CommandLine.join(args);

        assertThat(line).isEqualTo("-c \"echo halo\" \"\" \"say \\\"hi\\\"\" C:\\x\\y");
        assertThat(CommandLine.split(line)).isEqualTo(args);
    }
}

package dev.egateza.termul.core.snippet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SnippetVariablesTest {

    @Test
    void namaUrutKemunculanTanpaDuplikat() {
        assertThat(SnippetVariables.names("journalctl -u ${service} -n ${lines} # ${service} $HOME ${9x}"))
                .containsExactly("service", "lines");
    }

    @Test
    void resolveMengisiYangValidSaja() {
        String cmd = "journalctl -u ${service} -n ${lines}";
        assertThat(SnippetVariables.resolve(cmd, Map.of("service", "nginx", "lines", "50")))
                .isEqualTo("journalctl -u nginx -n 50");
        assertThat(SnippetVariables.resolve(cmd, Map.of("service", "nginx", "lines", " ")))
                .isEqualTo("journalctl -u nginx -n ${lines}");
        assertThat(SnippetVariables.resolve("echo ${a}", Map.of("a", "$1 \\ ok"))).isEqualTo("echo $1 \\ ok");
    }

    @Test
    void nilaiDenganBarisBaruTidakBisaMenyelipkanEnter() {
        String cmd = "systemctl status ${service}";
        var values = Map.of("service", "nginx\nrm -rf /");
        assertThat(SnippetVariables.missing(cmd, values)).containsExactly("service");
        assertThat(SnippetVariables.resolve(cmd, values)).isEqualTo(cmd);
    }
}

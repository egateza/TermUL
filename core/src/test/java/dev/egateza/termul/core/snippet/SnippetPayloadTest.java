package dev.egateza.termul.core.snippet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SnippetPayloadTest {

    @Test
    void satuBarisTanpaEnterHanyaTeksnya() {
        assertThat(SnippetPayload.build("df -h", false, true)).isEqualTo("df -h");
        assertThat(SnippetPayload.build("df -h", true, false)).isEqualTo("df -h\r");
    }

    @Test
    void beberapaBarisDibungkusBracketedPasteKalauShellMemintanya() {
        assertThat(SnippetPayload.build("a\nb", false, true)).isEqualTo("\u001b[200~a\rb\u001b[201~");
        assertThat(SnippetPayload.build("a\nb", true, true)).isEqualTo("\u001b[200~a\rb\u001b[201~\r");
        assertThat(SnippetPayload.build("a\nb", false, false)).isEqualTo("a\rb");
    }

    @Test
    void tanpaBracketedPasteBarisAwalBerjalanLebihDulu() {
        assertThat(SnippetPayload.runsEarly("a\nb", false, false)).isTrue();
        assertThat(SnippetPayload.runsEarly("a\nb", false, true)).isFalse();
        assertThat(SnippetPayload.runsEarly("a\nb", true, false)).as("memang diminta berjalan").isFalse();
        assertThat(SnippetPayload.runsEarly("a", false, false)).isFalse();
    }

    @Test
    void karakterKontrolDitolak() {
        assertThatThrownBy(() -> SnippetPayload.build("echo \u001b[201~; rm -rf /", false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package dev.egateza.termul.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShellQuoteTest {

    @Test
    void pathAmanTidakDiQuote() {
        assertThat(ShellQuote.quote("/srv/app-1/config.yml")).isEqualTo("/srv/app-1/config.yml");
    }

    @Test
    void karakterBerbahayaDiQuote() {
        assertThat(ShellQuote.quote("/tmp/a b")).isEqualTo("'/tmp/a b'");
        assertThat(ShellQuote.quote("x; rm -rf /")).isEqualTo("'x; rm -rf /'");
        assertThat(ShellQuote.quote("it's")).isEqualTo("'it'\\''s'");
        assertThat(ShellQuote.quote("$(id)")).isEqualTo("'$(id)'");
        assertThat(ShellQuote.quote("")).isEqualTo("''");
    }
}

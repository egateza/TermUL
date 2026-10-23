package dev.egateza.termul.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Osc7ParserTest {

    private final List<String> paths = new ArrayList<>();
    private final Osc7Parser parser = new Osc7Parser(paths::add);

    private void feed(String s) {
        parser.onOutput(s.toCharArray(), 0, s.length());
    }

    @Test
    void osc7DenganBelDanSt() {
        feed("prompt\u001b]7;file://server01/home/dev\u0007$ ");
        feed("\u001b]7;file:///var/log\u001b\\");

        assertThat(paths).containsExactly("/home/dev", "/var/log");
    }

    @Test
    void sequenceTerpotongAntarChunk() {
        feed("x\u001b]7;file://h/et");
        feed("c/ngi");
        feed("nx\u001b");
        feed("\\");

        assertThat(paths).containsExactly("/etc/nginx");
    }

    @Test
    void percentEncodingUtf8() {
        feed("\u001b]7;file://h/home/dev/folder%20baru/caf%C3%A9+x\u0007");

        assertThat(paths).containsExactly("/home/dev/folder baru/café+x");
    }

    @Test
    void oscLainDiabaikan() {
        feed("\u001b]0;judul window\u0007\u001b]2;file://h/x\u0007\u001b]8;;http://x\u0007");

        assertThat(paths).isEmpty();
    }

    @Test
    void urlBukanFileAtauEncodingRusakDiabaikan() {
        feed("\u001b]7;http://h/x\u0007");
        feed("\u001b]7;file://hostsaja\u0007");
        feed("\u001b]7;file://h/a%2\u0007");
        feed("\u001b]7;file://h/a%00b\u0007");

        assertThat(paths).isEmpty();
    }

    @Test
    void oscTerpotongEscapeLainLaluOsc7Berikutnya() {
        feed("\u001b]7;file://h/rusak\u001b[0m\u001b]7;file://h/ok\u0007");

        assertThat(paths).containsExactly("/ok");
    }

    @Test
    void oscTerlaluPanjangDibuang() {
        feed("\u001b]7;file://h/" + "a".repeat(Osc7Parser.MAX_LENGTH) + "\u0007");
        feed("\u001b]7;file://h/b\u0007");

        assertThat(paths).containsExactly("/b");
    }
}

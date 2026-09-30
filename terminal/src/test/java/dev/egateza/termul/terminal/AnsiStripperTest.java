package dev.egateza.termul.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnsiStripperTest {

    private final AnsiStripper stripper = new AnsiStripper();
    private final StringBuilder out = new StringBuilder();

    private void feed(String s) {
        stripper.strip(s.toCharArray(), 0, s.length(), out);
    }

    @Test
    void membuangCsiOscDanKontrol() {
        feed("\u001b[1;32mhijau\u001b[0m \u001b]0;judul\u0007teks\u0007\b\r\n");

        assertThat(out).hasToString("hijau teks\r\n");
    }

    @Test
    void sequenceTerpotongAntarChunkTetapTerbuang() {
        feed("a\u001b[3");
        feed("1mb\u001b]2;x\u001b");
        feed("\\c");

        assertThat(out).hasToString("abc");
    }

    @Test
    void escSatuKarakterDanCsi8Bit() {
        feed("\u001b=x\u001b7y\u009b2Jz");

        assertThat(out).hasToString("xyz");
    }
}

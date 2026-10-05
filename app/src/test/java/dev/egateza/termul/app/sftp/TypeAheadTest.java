package dev.egateza.termul.app.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TypeAheadTest {

    private static final List<String> NAMES = List.of("etc", "test.sh", "Tmp", "tools", "var");

    @Test
    void hurufPertamaLompatKeNamaBerawalanItuTanpaBedaHuruf() {
        assertThat(TypeAhead.find(NAMES, -1, "t")).isEqualTo(1);
        assertThat(TypeAhead.find(NAMES, 0, "T")).isEqualTo(1);
    }

    @Test
    void hurufSamaBerulangBerpindahKeBerikutnyaDanBerputar() {
        assertThat(TypeAhead.find(NAMES, 1, "tt")).isEqualTo(2);
        assertThat(TypeAhead.find(NAMES, 2, "ttt")).isEqualTo(3);
        assertThat(TypeAhead.find(NAMES, 3, "tttt")).isEqualTo(1);
    }

    @Test
    void awalanPanjangBolehTetapDiBarisSaatIni() {
        assertThat(TypeAhead.find(NAMES, 3, "to")).isEqualTo(3);
        assertThat(TypeAhead.find(NAMES, 1, "tm")).isEqualTo(2);
    }

    @Test
    void tidakAdaYangCocok() {
        assertThat(TypeAhead.find(NAMES, 0, "x")).isEqualTo(-1);
        assertThat(TypeAhead.find(List.of(), -1, "t")).isEqualTo(-1);
    }

    @Test
    void ketikanCepatDigabungDanJedaMemulaiUlang() {
        var ta = new TypeAhead();
        assertThat(ta.type('t', 10_000)).isEqualTo("t");
        assertThat(ta.type('o', 10_300)).isEqualTo("to");
        assertThat(ta.type('v', 10_300 + TypeAhead.RESET_MILLIS + 1)).isEqualTo("v");
    }

    @Test
    void karakterKontrolDanSpasiAwalDiabaikan() {
        var ta = new TypeAhead();
        assertThat(ta.type('\b', 1_000)).isNull();
        assertThat(ta.type('\n', 1_000)).isNull();
        assertThat(ta.type(' ', 1_000)).isNull();
        assertThat(ta.type('a', 1_100)).isEqualTo("a");
        assertThat(ta.type(' ', 1_200)).isEqualTo("a ");
    }
}

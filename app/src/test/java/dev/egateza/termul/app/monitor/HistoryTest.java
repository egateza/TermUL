package dev.egateza.termul.app.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HistoryTest {

    @Test
    void menyimpanUrutanDariYangTerlama() {
        var h = new History(3);
        h.add(0.1);
        h.add(0.2);

        assertThat(h.size()).isEqualTo(2);
        assertThat(h.get(0)).isEqualTo(0.1);
        assertThat(h.get(1)).isEqualTo(0.2);
    }

    @Test
    void penuhMembuangYangTerlamaDanUkuranTetap() {
        var h = new History(3);
        for (int i = 1; i <= 1000; i++) {
            h.add(i);
        }

        assertThat(h.size()).isEqualTo(3);
        assertThat(h.added()).isEqualTo(1000);
        assertThat(new double[] {h.get(0), h.get(1), h.get(2)}).containsExactly(998, 999, 1000);
    }

    @Test
    void nanDisimpanApaAdanya() {
        var h = new History(2);
        h.add(Double.NaN);

        assertThat(h.get(0)).isNaN();
    }

    @Test
    void indeksDiLuarIsiDitolak() {
        var h = new History(2);
        h.add(1);

        assertThatThrownBy(() -> h.get(1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> new History(0)).isInstanceOf(IllegalArgumentException.class);
    }
}

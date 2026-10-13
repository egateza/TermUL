package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NewLinesCounterTest {

    private final NewLinesCounter counter = new NewLinesCounter();

    @Test
    void tidakMembacaHistoryBerartiNol() {
        assertThat(counter.count(500)).isZero();
    }

    @Test
    void menghitungBarisYangMasukHistorySejakMulaiMembaca() {
        counter.start(100);

        assertThat(counter.count(112)).isEqualTo(12);
    }

    @Test
    void historyPenuhTetapDihitungLewatBarisYangDibuang() {
        counter.start(1000);
        counter.discarded(30); // history penuh: jumlahnya tetap 1000, 30 baris lama dibuang

        assertThat(counter.count(1000)).isEqualTo(30);
    }

    @Test
    void kembaliKeBawahMengulangHitungan() {
        counter.start(100);
        counter.stop();
        counter.discarded(5);

        assertThat(counter.count(140)).isZero();
        counter.start(140);
        assertThat(counter.count(141)).isEqualTo(1);
    }

    @Test
    void historyDikosongkanMulaiDariNol() {
        counter.start(800);
        counter.reset(0);

        assertThat(counter.count(3)).isEqualTo(3);
    }
}

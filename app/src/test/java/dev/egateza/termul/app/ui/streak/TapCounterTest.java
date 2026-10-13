package dev.egateza.termul.app.ui.streak;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TapCounterTest {

    @Test
    void tigaKlikCepatMenggenapi() {
        var taps = new TapCounter(3);
        assertThat(taps.tap(1000)).isFalse();
        assertThat(taps.tap(1300)).isFalse();
        assertThat(taps.tap(1700)).isTrue();
    }

    @Test
    void jedaTerlaluLamaMulaiDariAwal() {
        var taps = new TapCounter(3);
        taps.tap(1000);
        taps.tap(1400);
        assertThat(taps.tap(1400 + TapCounter.WINDOW_MS)).as("jeda tepat 500 ms terlalu lama").isFalse();
        assertThat(taps.tap(2100)).isFalse();
        assertThat(taps.tap(2200)).isTrue();
    }

    @Test
    void setelahGenapHitunganMulaiLagi() {
        var taps = new TapCounter(3);
        taps.tap(0);
        taps.tap(100);
        assertThat(taps.tap(200)).isTrue();
        assertThat(taps.tap(300)).isFalse();
    }
}

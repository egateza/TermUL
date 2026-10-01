package dev.egateza.termul.app.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LevelTest {

    @Test
    void ambangBatas() {
        assertThat(Level.of(0.0)).isEqualTo(Level.NORMAL);
        assertThat(Level.of(0.749)).isEqualTo(Level.NORMAL);
        assertThat(Level.of(0.75)).isEqualTo(Level.WARN);
        assertThat(Level.of(0.899)).isEqualTo(Level.WARN);
        assertThat(Level.of(0.90)).isEqualTo(Level.CRITICAL);
        assertThat(Level.of(1.0)).isEqualTo(Level.CRITICAL);
    }

    @Test
    void tidakDiketahuiDianggapNormal() {
        assertThat(Level.of(Double.NaN)).isEqualTo(Level.NORMAL);
    }
}

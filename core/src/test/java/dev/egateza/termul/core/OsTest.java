package dev.egateza.termul.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OsTest {

    @Test
    void namaOsDikenali() {
        assertThat(Os.of("Windows 11")).isEqualTo(Os.WINDOWS);
        assertThat(Os.of("Mac OS X")).isEqualTo(Os.MAC);
        assertThat(Os.of("Darwin")).isEqualTo(Os.MAC);
        assertThat(Os.of("Linux")).isEqualTo(Os.OTHER);
        assertThat(Os.of(null)).isEqualTo(Os.OTHER);
    }
}

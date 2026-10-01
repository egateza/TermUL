package dev.egateza.termul.app.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JvmSamplerTest {

    @Test
    void cpuRatioDibagiJumlahCore() {
        // 1 detik CPU dalam 1 detik nyata di mesin 4 core = 25%
        assertThat(JvmSampler.cpuRatio(1_000_000_000L, 1_000_000_000L, 4)).isEqualTo(0.25);
    }

    @Test
    void cpuRatioDibatasiSatuDanTidakValidJadiNaN() {
        assertThat(JvmSampler.cpuRatio(9_000_000_000L, 1_000_000_000L, 4)).isEqualTo(1.0);
        assertThat(JvmSampler.cpuRatio(1, 0, 4)).isNaN();
        assertThat(JvmSampler.cpuRatio(-1, 1, 4)).isNaN();
        assertThat(JvmSampler.cpuRatio(1, 1, 0)).isNaN();
    }

    @Test
    void sampelPertamaCpuNaNBerikutnyaTerukur() {
        var sampler = new JvmSampler();
        var first = sampler.sample();
        long end = System.nanoTime() + 50_000_000L;
        long x = 0;
        while (System.nanoTime() < end) {
            x += System.nanoTime() % 7; // beban CPU sedikit
        }
        var second = sampler.sample();

        assertThat(first.processCpu()).isNaN();
        assertThat(second.processCpu()).as("x=%d", x).isBetween(0.0, 1.0);
        assertThat(second.heapUsed()).isPositive();
        assertThat(second.heapLimit()).isGreaterThanOrEqualTo(second.heapUsed());
        assertThat(second.heapRatio()).isBetween(0.0, 1.0);
        assertThat(second.threads()).isPositive();
    }

    @Test
    void heapLimitPakaiXmxAtauCommitted() {
        var withMax = new JvmSample(0, 0, 50, 100, 200, 0, 1, 0, 0, -1, -1);
        var noMax = new JvmSample(0, 0, 50, 100, -1, 0, 1, 0, 0, -1, -1);

        assertThat(withMax.heapLimit()).isEqualTo(200);
        assertThat(withMax.heapRatio()).isEqualTo(0.25);
        assertThat(noMax.heapLimit()).isEqualTo(100);
        assertThat(noMax.heapRatio()).isEqualTo(0.5);
    }
}

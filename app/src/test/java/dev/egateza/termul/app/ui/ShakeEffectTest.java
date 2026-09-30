package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ShakeEffectTest {

    @Test
    void mulaiDanBerakhirDiPosisiAsal() {
        assertThat(ShakeEffect.offset(0)).isZero();
        assertThat(ShakeEffect.offset(ShakeEffect.STEPS)).isZero();
    }

    @Test
    void offsetTidakMelebihiAmplitudo() {
        assertThat(IntStream.rangeClosed(0, ShakeEffect.STEPS).map(ShakeEffect::offset))
                .allMatch(o -> Math.abs(o) <= ShakeEffect.AMPLITUDE_PX);
    }

    @Test
    void benarBenarBergetarKeDuaArah() {
        var offsets = IntStream.rangeClosed(0, ShakeEffect.STEPS).map(ShakeEffect::offset).toArray();
        assertThat(IntStream.of(offsets)).anyMatch(o -> o > 0).anyMatch(o -> o < 0);
    }

    @Test
    void amplitudoMeredam() {
        int early = IntStream.rangeClosed(1, 6).map(i -> Math.abs(ShakeEffect.offset(i))).max().orElse(0);
        int late = IntStream.rangeClosed(ShakeEffect.STEPS - 6, ShakeEffect.STEPS - 1)
                .map(i -> Math.abs(ShakeEffect.offset(i))).max().orElse(0);
        assertThat(late).isLessThan(early);
    }

    @Test
    void getaranCukupBesarUntukTerlihat() {
        int max = IntStream.rangeClosed(0, ShakeEffect.STEPS).map(o -> Math.abs(ShakeEffect.offset(o))).max().orElse(0);
        assertThat(max).isGreaterThanOrEqualTo(ShakeEffect.AMPLITUDE_PX / 2);
    }

    @Test
    void tidakBerbalikArahDiSetiapLangkah() {
        // berganti tanda tiap langkah berarti flicker; siklus harus lebih dari 2 langkah
        int flips = 0;
        for (int i = 2; i < ShakeEffect.STEPS - 1; i++) {
            if (Integer.signum(ShakeEffect.offset(i)) * Integer.signum(ShakeEffect.offset(i - 1)) < 0) {
                flips++;
            }
        }
        assertThat(flips).isLessThan((ShakeEffect.STEPS - 3) / 2);
    }
}

package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultBoundedRangeModel;
import org.junit.jupiter.api.Test;

class SmoothWheelTest {

    /** Buffer 200 baris, layar 40 baris, posisi di tengah. */
    private final DefaultBoundedRangeModel model = new DefaultBoundedRangeModel(100, 40, 0, 200);
    private final SmoothWheel wheel = new SmoothWheel(model, false);

    /** Jalankan semua frame; kembalikan posisi setelah tiap frame. */
    private List<Integer> frames() {
        var positions = new ArrayList<Integer>();
        boolean more = true;
        while (more) {
            int before = model.getValue();
            more = wheel.tick();
            if (model.getValue() != before) {
                positions.add(model.getValue());
            }
        }
        return positions;
    }

    @Test
    void satuPutaranDigeserBarisPerBaris() {
        wheel.scroll(3);

        assertThat(frames()).containsExactly(101, 102, 103);
    }

    @Test
    void putaranCepatMelambatDiAkhir() {
        wheel.scroll(-30);

        var positions = frames();
        assertThat(positions.getLast()).isEqualTo(70);
        assertThat(100 - positions.getFirst()).isGreaterThan(positions.get(positions.size() - 2) - positions.getLast());
        assertThat(positions.size()).isLessThan(30); // tidak satu baris per frame untuk scroll jauh
    }

    @Test
    void deltaTouchpadKecilDikumpulkan() {
        wheel.scroll(0.4);
        assertThat(frames()).isEmpty();
        wheel.scroll(0.4);
        assertThat(frames()).isEmpty();
        wheel.scroll(0.4);

        assertThat(frames()).containsExactly(101);
        assertThat(wheel.pending()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void berbalikArahMembatalkanSisaScroll() {
        wheel.scroll(30);
        wheel.tick();
        int after = model.getValue();

        wheel.scroll(-3);

        assertThat(frames()).containsExactly(after - 1, after - 2, after - 3);
    }

    @Test
    void berhentiDiUjungBuffer() {
        model.setValue(1);
        wheel.scroll(-30);

        assertThat(frames()).containsExactly(0);
        assertThat(wheel.pending()).isZero();
    }
}

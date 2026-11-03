package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.Memory;
import org.junit.jupiter.api.Test;

class WindowBlurTest {

    /** Struct {@code WINDOWCOMPOSITIONATTRIBDATA}: Attrib (int), pvData (pointer, rata ke ukuran pointer), cbData. */
    @Test
    void dataStructLayout64Bit() {
        try (var data = new Memory(WindowBlur.dataSize(8)); var policy = new Memory(16)) {
            WindowBlur.writeData(data, 8, policy);

            assertThat(data.size()).isEqualTo(24);
            assertThat(data.getInt(0)).isEqualTo(19); // WCA_ACCENT_POLICY
            assertThat(data.getPointer(8)).isEqualTo(policy);
            assertThat(data.getLong(16)).isEqualTo(16);
        }
    }

    @Test
    void dataStructLayout32Bit() {
        try (var data = new Memory(WindowBlur.dataSize(4)); var policy = new Memory(16)) {
            WindowBlur.writeData(data, 4, policy);

            assertThat(data.size()).isEqualTo(12);
            assertThat(data.getInt(0)).isEqualTo(19);
            assertThat(data.getInt(8)).isEqualTo(16);
        }
    }

    /** Tanpa window, blur tidak dianggap menyala: lapisan gelap tidak dipasang. */
    @Test
    void blurBelumMenyala() {
        assertThat(WindowBlur.isOn(null)).isFalse();
    }

    /** Blur hanya menyala selama window tembus: window solid tidak punya piksel untuk memperlihatkan blur. */
    @Test
    void blurHanyaSelamaTransparansiAktif() {
        assertThat(WindowOpacity.blurWanted(true, 100, 100)).isFalse();
        assertThat(WindowOpacity.blurWanted(true, 90, 100)).isTrue();
        assertThat(WindowOpacity.blurWanted(true, 100, 40)).isTrue();
        assertThat(WindowOpacity.blurWanted(false, 50, 40)).isFalse();
        assertThat(WindowOpacity.blurWanted(true, 5, 100)).isTrue(); // dibatasi ke minimum, tetap tembus
        assertThat(WindowOpacity.blurWanted(true, 250, 100)).isFalse();
    }
}

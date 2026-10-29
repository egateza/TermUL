package dev.egateza.termul.app.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class MeterTest {

    private static final int INNER = Meter.WIDTH - 2;

    @Test
    void lebarIsiMengikutiPersenDenganMinimalDuaPersen() {
        assertThat(Meter.fillWidth(Double.NaN, INNER)).isZero();
        assertThat(Meter.fillWidth(0, INNER)).isZero();
        assertThat(Meter.fillWidth(0.004, INNER)).as("dibulatkan ke 0%").isZero();
        assertThat(Meter.fillWidth(0.01, INNER)).as("1% tetap terlihat: minimal 2%").isEqualTo(2);
        assertThat(Meter.fillWidth(0.5, INNER)).isEqualTo(38);
        assertThat(Meter.fillWidth(1.0, INNER)).isEqualTo(INNER);
        assertThat(Meter.fillWidth(1.7, INNER)).as("di atas batas tetap penuh").isEqualTo(INNER);
    }

    @Test
    void warnaPerLevelSamaDenganPrototipe() {
        assertThat(Meter.Palette.of(Level.NORMAL).fill()).isEqualTo(new Color(0x3DD68C));
        assertThat(Meter.Palette.of(Level.WARN).fill()).isEqualTo(new Color(0xE0AF68));
        assertThat(Meter.Palette.of(Level.CRITICAL).fill()).isEqualTo(new Color(0xF7768E));
    }

    @Test
    void isiDigambarSelebarPersennya() {
        var meter = new Meter("Heap");
        meter.setSize(Meter.WIDTH, Meter.HEIGHT);
        meter.update(0.92, "92%", Level.of(0.92));
        var img = new BufferedImage(Meter.WIDTH, Meter.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        meter.paint(g);
        g.dispose();
        int red = Meter.Palette.of(Level.CRITICAL).fill().getRGB();
        assertThat(img.getRGB(Meter.WIDTH - 10, Meter.HEIGHT - 2)).as("dalam isi, di bawah teks").isEqualTo(red);
        assertThat(img.getRGB(Meter.WIDTH - 3, Meter.HEIGHT - 2)).as("di luar isi: track").isNotEqualTo(red);
        assertThat(meter.getAccessibleContext().getAccessibleName()).isEqualTo("Heap 92%");
    }
}

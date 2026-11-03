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
        int red = Meter.Palette.current(Level.CRITICAL).fill().getRGB();
        assertThat(img.getRGB(Meter.WIDTH - 10, Meter.HEIGHT - 2)).as("dalam isi, di bawah teks").isEqualTo(red);
        assertThat(img.getRGB(Meter.WIDTH - 3, Meter.HEIGHT - 2)).as("di luar isi: track").isNotEqualTo(red);
        assertThat(meter.getAccessibleContext().getAccessibleName()).isEqualTo("Heap 92%");
    }

    /**
     * Angka persen tepat di tengah tinggi meter untuk font apa pun: mis. Bahnschrift (leading 2 px, ascent rapat)
     * sebelumnya membuat angka menempel di tepi atas.
     */
    @Test
    void angkaPersenDiTengahVertikal() {
        var original = javax.swing.UIManager.getFont("Label.font");
        try {
            for (String family : new String[] {"Bahnschrift", java.awt.Font.DIALOG}) {
                var font = new java.awt.Font(family, java.awt.Font.PLAIN, 12);
                if (!font.getFamily().equals(family) && !family.equals(java.awt.Font.DIALOG)) {
                    continue; // font tidak terpasang di mesin ini
                }
                javax.swing.UIManager.put("Label.font", font);
                var meter = new Meter("CPU");
                meter.setSize(Meter.WIDTH, Meter.HEIGHT);
                meter.update(0, "3%", Level.NORMAL);
                var img = new BufferedImage(Meter.WIDTH, Meter.HEIGHT, BufferedImage.TYPE_INT_ARGB);
                var g = img.createGraphics();
                meter.paint(g);
                g.dispose();
                int green = Meter.Palette.current(Level.NORMAL).fill().getRGB();
                int first = -1;
                int last = -1;
                for (int y = 1; y < Meter.HEIGHT - 1; y++) {
                    for (int x = 2; x < 20; x++) {
                        if (close(img.getRGB(x, y), green)) {
                            first = first < 0 ? y : first;
                            last = y;
                            break;
                        }
                    }
                }
                int top = first - 1;
                int bottom = Meter.HEIGHT - 2 - last;
                assertThat(Math.abs(top - bottom)).as(family + ": ruang atas " + top + ", bawah " + bottom)
                        .isLessThanOrEqualTo(1);
            }
        } finally {
            javax.swing.UIManager.put("Label.font", original);
        }
    }

    private static boolean close(int argb, int rgb) {
        int dr = Math.abs((argb >> 16 & 0xFF) - (rgb >> 16 & 0xFF));
        int dg = Math.abs((argb >> 8 & 0xFF) - (rgb >> 8 & 0xFF));
        int db = Math.abs((argb & 0xFF) - (rgb & 0xFF));
        return (argb >>> 24) > 200 && dr + dg + db < 90;
    }

    /**
     * Tema terang: warna prototipe (untuk tema gelap) hanya berkontras 1,5–2,7:1 di title bar terang. Palet terang
     * harus terbaca: angka ≥ 4,5:1 terhadap latar putih sampai #E8E8E8, dan teks di dalam isi ≥ 4,5:1 terhadap isinya.
     */
    @Test
    void paletTerangTerbacaDiLatarTerang() {
        for (Level level : Level.values()) {
            var light = Meter.Palette.of(level, false);
            for (int background : new int[] {0xFFFFFF, 0xF2F2F2, 0xE8E8E8}) {
                assertThat(contrast(light.fill().getRGB(), background)).as(level + " di #" + Integer.toHexString(background))
                        .isGreaterThanOrEqualTo(4.5);
            }
            assertThat(contrast(light.textInFill().getRGB(), light.fill().getRGB())).as(level + " teks di isi")
                    .isGreaterThanOrEqualTo(4.5);
            assertThat(contrast(Meter.Palette.of(level).fill().getRGB(), 0xFFFFFF)).as(level + " palet gelap di putih")
                    .isLessThan(3.0); // alasan palet terang ada
        }
    }

    private static double contrast(int a, int b) {
        double x = luminance(a);
        double y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private static double luminance(int rgb) {
        double sum = 0;
        double[] weight = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) {
            double v = (rgb >> (16 - 8 * i) & 0xFF) / 255.0;
            sum += weight[i] * (v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
        }
        return sum;
    }
}

package dev.egateza.termul.app.ui.streak;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreakWidgetTest {

    private static final Dimension SIZE = new Dimension(150, 64);
    private static final Rectangle MAIN = new Rectangle(0, 0, 1920, 1080);
    private static final Rectangle LEFT = new Rectangle(-1280, 0, 1280, 1024);
    /** Area kerja layar utama (tanpa taskbar 40 px). */
    private static final Rectangle USABLE = new Rectangle(0, 0, 1920, 1040);

    @Test
    void belumPernahDigeserDiPojokKananBawahAreaKerja() {
        assertThat(StreakWidget.place(null, SIZE, List.of(MAIN), USABLE))
                .isEqualTo(new Point(1920 - 150 - StreakWidget.MARGIN, 1040 - 64 - StreakWidget.MARGIN));
    }

    @Test
    void posisiTersimpanDipakaiTermasukDiMonitorKedua() {
        assertThat(StreakWidget.place(new Point(300, 200), SIZE, List.of(MAIN, LEFT), USABLE))
                .isEqualTo(new Point(300, 200));
        assertThat(StreakWidget.place(new Point(-1000, 50), SIZE, List.of(MAIN, LEFT), USABLE))
                .isEqualTo(new Point(-1000, 50));
    }

    @Test
    void saatDiseretWidgetSelaluUtuhDiMonitorTempatPointer() {
        assertThat(StreakWidget.clampInto(new Point(500, 400), SIZE, MAIN)).as("di tengah: tidak berubah")
                .isEqualTo(new Point(500, 400));
        assertThat(StreakWidget.clampInto(new Point(-60, 400), SIZE, MAIN)).as("menggantung ke monitor kiri")
                .isEqualTo(new Point(0, 400));
        assertThat(StreakWidget.clampInto(new Point(-100, 1050), SIZE, LEFT)).as("menggantung ke monitor utama")
                .isEqualTo(new Point(-150, 1024 - 64));
    }

    @Test
    void perubahanLayarDikenaliDariIdentitasKonfigurasi() {
        var a = new Object();
        var b = new Object();
        assertThat(StreakWidget.sameElements(List.of(a, b), List.of(a, b))).isTrue();
        assertThat(StreakWidget.sameElements(List.of(a), List.of(a, b))).as("monitor bertambah").isFalse();
        assertThat(StreakWidget.sameElements(List.of(new Point(1, 1)), List.of(new Point(1, 1))))
                .as("objek baru walau equals = layar dikonfigurasi ulang").isFalse();
    }

    @Test
    void monitorDicabutKembaliKePojokLayarUtama() {
        var fallback = StreakWidget.place(null, SIZE, List.of(MAIN), USABLE);
        assertThat(StreakWidget.place(new Point(-1000, 50), SIZE, List.of(MAIN), USABLE)).isEqualTo(fallback);
        assertThat(StreakWidget.place(new Point(1910, 1075), SIZE, List.of(MAIN), USABLE))
                .as("hampir seluruhnya di luar layar").isEqualTo(fallback);
    }
}

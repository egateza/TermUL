package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import javax.accessibility.AccessibleRole;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class PacmanLoaderTest {

    @Test
    void pacmanMasukDariKiriDanBerulangSetelahSatuPutaran() {
        assertThat(PacmanLoader.pacX(0)).isEqualTo(-PacmanLoader.SIZE);
        assertThat(PacmanLoader.pacX(1)).isGreaterThan(PacmanLoader.pacX(0));
        int framesPerLoop = PacmanLoader.LOOP / PacmanLoader.SPEED;
        assertThat(PacmanLoader.pacX(framesPerLoop)).isEqualTo(PacmanLoader.pacX(0));
    }

    @Test
    void hantuIkutKeluarDariTrackSebelumPutaranBerulang() {
        int lastFrame = PacmanLoader.LOOP / PacmanLoader.SPEED - 1;
        int ghostX = PacmanLoader.pacX(lastFrame) - PacmanLoader.GHOST_LAG;
        assertThat(ghostX + PacmanLoader.SIZE).isGreaterThanOrEqualTo(PacmanLoader.TRACK - PacmanLoader.SPEED);
    }

    @Test
    void mulutMembukaDanMenutupDalamBatas() {
        var angles = IntStream.range(0, 40).map(PacmanLoader::mouthDeg).toArray();
        assertThat(IntStream.of(angles)).allMatch(a -> a >= 0 && a <= PacmanLoader.MAX_MOUTH_DEG);
        assertThat(IntStream.of(angles)).contains(0, PacmanLoader.MAX_MOUTH_DEG);
    }

    @Test
    void titikDimakanSaatDilewatiDanSemuaUtuhDiAwalPutaran() {
        int start = PacmanLoader.pacX(0);
        assertThat(IntStream.range(0, PacmanLoader.dotCount()).map(PacmanLoader::dotX))
                .noneMatch(x -> PacmanLoader.eaten(x, start));

        int first = PacmanLoader.dotX(0);
        int last = PacmanLoader.dotX(PacmanLoader.dotCount() - 1);
        int pac = first - PacmanLoader.SIZE / 2 + 1; // tengah Pac-Man sudah lewat titik pertama
        assertThat(PacmanLoader.eaten(first, pac)).isTrue();
        assertThat(PacmanLoader.eaten(last, pac)).isFalse();
    }

    @Test
    void withMessageMembuatPanelDenganNamaAksesibel() {
        var panel = PacmanLoader.withMessage("Menghubungkan ke host ...");

        var loader = (PacmanLoader) panel.getComponent(0);
        assertThat(loader.getAccessibleContext().getAccessibleName()).isEqualTo("Menghubungkan ke host ...");
        assertThat(loader.getAccessibleContext().getAccessibleRole()).isEqualTo(AccessibleRole.PROGRESS_BAR);
        assertThat(((JLabel) panel.getComponent(1)).getText()).isEqualTo("Menghubungkan ke host ...");
    }

    @Test
    void semuaTitikMuatDiTrack() {
        int last = PacmanLoader.dotX(PacmanLoader.dotCount() - 1);
        assertThat(last).isLessThan(PacmanLoader.TRACK);
    }
}

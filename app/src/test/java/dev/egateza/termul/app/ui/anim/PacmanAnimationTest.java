package dev.egateza.termul.app.ui.anim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.app.ui.anim.PacmanAnimation.Geometry;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

class PacmanAnimationTest {

    static final Geometry[] GEOMETRIES = {PacmanAnimation.LARGE, PacmanAnimation.COMPACT};

    @ParameterizedTest
    @FieldSource("GEOMETRIES")
    void pacmanMasukDariKiriDanBerulangSetelahSatuPutaran(Geometry g) {
        assertThat(g.pacX(0)).isEqualTo(-g.size());
        assertThat(g.pacX(1)).isGreaterThan(g.pacX(0));
        assertThat(g.loop() % g.speed()).isZero();
        assertThat(g.pacX(g.loop() / g.speed())).isEqualTo(g.pacX(0));
    }

    @Test
    void adaTigaHantuPengejar() {
        assertThat(PacmanAnimation.GHOSTS).hasSize(3).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @FieldSource("GEOMETRIES")
    void hantuBerurutanDiBelakangPacmanTanpaBertumpuk(Geometry g) {
        int pac = 100;
        int prevLeft = pac;
        for (int i = 0; i < PacmanAnimation.GHOSTS.size(); i++) {
            int x = g.ghostX(i, pac);
            assertThat(x + g.size()).isLessThanOrEqualTo(prevLeft);
            prevLeft = x;
        }
    }

    @ParameterizedTest
    @FieldSource("GEOMETRIES")
    void hantuTerakhirKeluarDariTrackSebelumPutaranBerulang(Geometry g) {
        int lastFrame = g.loop() / g.speed() - 1;
        int lastGhost = g.ghostX(PacmanAnimation.GHOSTS.size() - 1, g.pacX(lastFrame));
        assertThat(lastGhost + g.size()).isGreaterThanOrEqualTo(g.track() - g.speed());
    }

    @Test
    void geometriDenganHantuBertumpukDitolak() {
        assertThatThrownBy(() -> new Geometry(200, 14, 12, 2, 30, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void geometriPasDenganUkuranArea() {
        assertThat(PacmanAnimation.LARGE.track()).isEqualTo(Animation.Size.LARGE.width());
        assertThat(PacmanAnimation.LARGE.size()).isLessThanOrEqualTo(Animation.Size.LARGE.height());
        assertThat(PacmanAnimation.COMPACT.track()).isEqualTo(Animation.Size.COMPACT.width());
        assertThat(PacmanAnimation.COMPACT.size()).isLessThanOrEqualTo(Animation.Size.COMPACT.height());
    }

    @Test
    void mulutMembukaDanMenutupDalamBatas() {
        var angles = IntStream.range(0, 40).map(PacmanAnimation::mouthDeg).toArray();
        assertThat(IntStream.of(angles)).allMatch(a -> a >= 0 && a <= PacmanAnimation.MAX_MOUTH_DEG);
        assertThat(IntStream.of(angles)).contains(0, PacmanAnimation.MAX_MOUTH_DEG);
    }

    @ParameterizedTest
    @FieldSource("GEOMETRIES")
    void titikDimakanSaatDilewatiDanSemuaUtuhDiAwalPutaran(Geometry g) {
        int start = g.pacX(0);
        assertThat(IntStream.range(0, g.dotCount()).map(g::dotX)).noneMatch(x -> g.eaten(x, start));
        assertThat(g.dotX(g.dotCount() - 1)).isLessThan(g.track());

        int first = g.dotX(0);
        int pac = first - g.size() / 2 + 1; // tengah Pac-Man sudah lewat titik pertama
        assertThat(g.eaten(first, pac)).isTrue();
        assertThat(g.eaten(g.dotX(g.dotCount() - 1), pac)).isFalse();
    }
}

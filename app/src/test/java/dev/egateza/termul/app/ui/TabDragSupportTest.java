package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TabDragSupportTest {

    /** Tiga tab selebar 100 px berdampingan: 0-99, 100-199, 200-299. */
    private static final List<Rectangle> THREE = List.of(
            new Rectangle(0, 0, 100, 30), new Rectangle(100, 0, 100, 30), new Rectangle(200, 0, 100, 30));

    @Test
    void posisiSisipMengikutiTitikTengahTab() {
        assertThat(TabDragSupport.insertionIndex(THREE, -20)).isZero();
        assertThat(TabDragSupport.insertionIndex(THREE, 49)).isZero();
        assertThat(TabDragSupport.insertionIndex(THREE, 50)).isEqualTo(1);
        assertThat(TabDragSupport.insertionIndex(THREE, 160)).isEqualTo(2);
        assertThat(TabDragSupport.insertionIndex(THREE, 260)).isEqualTo(3);
        assertThat(TabDragSupport.insertionIndex(THREE, 900)).isEqualTo(3);
    }

    @Test
    void tabYangTerguliKeluarDilewati() {
        var bounds = Arrays.asList(null, new Rectangle(0, 0, 100, 30), new Rectangle(100, 0, 100, 30), null);
        assertThat(TabDragSupport.insertionIndex(bounds, 10)).isEqualTo(1);
        assertThat(TabDragSupport.insertionIndex(bounds, 180)).isEqualTo(3);
    }

    @Test
    void tujuanSetelahTabDilepasDariPosisiLama() {
        // tab 0 ke ujung kanan (sisip 3) → indeks 2 setelah dilepas
        assertThat(TabDragSupport.destination(0, 3)).isEqualTo(2);
        assertThat(TabDragSupport.destination(0, 2)).isEqualTo(1);
        // tab 2 ke depan
        assertThat(TabDragSupport.destination(2, 0)).isZero();
        assertThat(TabDragSupport.destination(2, 1)).isEqualTo(1);
    }

    @Test
    void dilepasDiSampingDirinyaSendiriTidakBerpindah() {
        assertThat(TabDragSupport.destination(1, 1)).isEqualTo(-1);
        assertThat(TabDragSupport.destination(1, 2)).isEqualTo(-1);
    }
}

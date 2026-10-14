package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.ui.fab.FloatingOverlay;
import dev.egateza.termul.core.config.AppConfig;
import org.junit.jupiter.api.Test;

class SnippetButtonTest {

    @Test
    void sudutConfigDanAnchorBolakBalik() {
        for (String corner : AppConfig.SNIPPET_CORNERS) {
            assertThat(SnippetButton.cornerOf(SnippetButton.anchorOf(corner))).isEqualTo(corner);
        }
    }

    @Test
    void geseranKecilSaatKlikBukanSeret() {
        // dulu: dx+dy >= 5 sudah dianggap seret, sehingga klik dengan sedikit geseran tidak membuka menu
        assertThat(SnippetButton.isDrag(3, 3, SnippetButton.DRAG_THRESHOLD)).isFalse();
        assertThat(SnippetButton.isDrag(-7, 7, SnippetButton.DRAG_THRESHOLD)).isFalse();
        assertThat(SnippetButton.isDrag(0, 8, SnippetButton.DRAG_THRESHOLD)).isTrue();
        assertThat(SnippetButton.isDrag(-40, 2, SnippetButton.DRAG_THRESHOLD)).isTrue();
    }

    @Test
    void sudutTidakDikenalKeKananAtas() {
        assertThat(SnippetButton.anchorOf(null)).isEqualTo(FloatingOverlay.Anchor.TOP_RIGHT);
        assertThat(SnippetButton.anchorOf("tengah")).isEqualTo(FloatingOverlay.Anchor.TOP_RIGHT);
    }
}

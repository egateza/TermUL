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
    void sudutTidakDikenalKeKananAtas() {
        assertThat(SnippetButton.anchorOf(null)).isEqualTo(FloatingOverlay.Anchor.TOP_RIGHT);
        assertThat(SnippetButton.anchorOf("tengah")).isEqualTo(FloatingOverlay.Anchor.TOP_RIGHT);
    }
}

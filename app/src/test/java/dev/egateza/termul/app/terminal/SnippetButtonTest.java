package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.fab.FloatingOverlay;
import dev.egateza.termul.core.config.AppConfig;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
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
    void menuKlikKananBisaMenyembunyikanTombol() throws Exception {
        var hidden = new AtomicInteger();
        var managed = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            var button = new SnippetButton(new FloatingOverlay(), AppConfig.SNIPPET_TOP_RIGHT, null, null,
                    managed::incrementAndGet, corner -> { }, hidden::incrementAndGet);
            var items = Arrays.stream(button.contextMenu().getComponents())
                    .filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast).toList();
            assertThat(items).extracting(JMenuItem::getText).containsExactly(
                    I18n.t("fab.snippet.open"), I18n.t("snippet.quick.manage"), I18n.t("fab.snippet.hide"));
            items.get(2).doClick();
            items.get(1).doClick();
        });
        assertThat(hidden).hasValue(1);
        assertThat(managed).hasValue(1);
    }

    @Test
    void sudutTidakDikenalKeKananAtas() {
        assertThat(SnippetButton.anchorOf(null)).isEqualTo(FloatingOverlay.Anchor.TOP_RIGHT);
        assertThat(SnippetButton.anchorOf("tengah")).isEqualTo(FloatingOverlay.Anchor.TOP_RIGHT);
    }
}

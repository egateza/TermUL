package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.config.AppConfig;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

class HostToggleStyleTest {

    private static final Rectangle AREA = new Rectangle(260, 30, 900, 600);

    @Test
    void idDikenaliDanSelainItuDefault() {
        for (var style : HostToggleStyle.values()) {
            assertThat(HostToggleStyle.fromId(style.id())).isSameAs(style);
        }
        assertThat(HostToggleStyle.fromId(AppConfig.DEFAULT_HOST_TOGGLE)).isEqualTo(HostToggleStyle.EDGE_CIRCLE);
        assertThat(HostToggleStyle.fromId("aneh")).isEqualTo(HostToggleStyle.EDGE_CIRCLE);
        assertThat(HostToggleStyle.fromId(null)).isEqualTo(HostToggleStyle.EDGE_CIRCLE);
    }

    @Test
    void lingkaranMenumpangDiGarisSaatPanelTerbukaDanDiTepiSaatTertutup() {
        var open = HostToggleStyle.EDGE_CIRCLE.bounds(AREA, 257, true);
        assertThat(open.getCenterX()).isEqualTo(257);
        assertThat(open.y).isEqualTo(AREA.y + HostToggleStyle.CIRCLE_TOP);

        var closed = HostToggleStyle.EDGE_CIRCLE.bounds(AREA, 257, false);
        assertThat(closed.x).isGreaterThanOrEqualTo(AREA.x);
    }

    @Test
    void kapsulDanGarisTipisDiTengahTinggi() {
        var tab = HostToggleStyle.EDGE_TAB.bounds(AREA, 0, true);
        assertThat(tab.x).isEqualTo(AREA.x);
        assertThat(tab.getCenterY()).isEqualTo(AREA.getCenterY());

        var grabber = HostToggleStyle.GRABBER.bounds(AREA, 0, true);
        assertThat(grabber.getCenterY()).isEqualTo(AREA.getCenterY());
    }

    @Test
    void pojokBawahDiDalamArea() {
        var b = HostToggleStyle.CORNER.bounds(AREA, 0, false);
        assertThat(AREA.contains(b)).isTrue();
        assertThat(b.getMaxY()).isEqualTo(AREA.getMaxY() - HostToggleStyle.CORNER_MARGIN);
    }

    @Test
    void tabBarMemakaiLingkaranSebagaiCadangan() {
        assertThat(HostToggleStyle.TAB_BAR.floating()).isEqualTo(HostToggleStyle.EDGE_CIRCLE);
        assertThat(HostToggleStyle.TAB_BAR.bounds(AREA, 257, true))
                .isEqualTo(HostToggleStyle.EDGE_CIRCLE.bounds(AREA, 257, true));
    }

    @Test
    void zonaMunculMencakupTombolnya() {
        var button = HostToggleStyle.HOVER_REVEAL.bounds(AREA, 0, true);
        assertThat(HostToggleStyle.revealZone(AREA).contains(button)).isTrue();
    }
}

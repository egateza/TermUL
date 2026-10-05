package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.ui.anim.AnimationChoice;
import dev.egateza.termul.app.ui.anim.AnimationKind;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class BottomBarTest {

    private static final ZonedDateTime TIME = ZonedDateTime.of(2026, 10, 1, 14, 23, 5, 0, ZoneId.of("Asia/Jakarta"));

    @Test
    void jamBahasaIndonesia() {
        assertThat(BottomBar.clock(TIME, Locale.forLanguageTag("id")))
                .startsWith("Kamis, 01 Okt 2026  14:23:05");
    }

    @Test
    void jamBahasaInggris() {
        assertThat(BottomBar.clock(TIME, Locale.ENGLISH)).startsWith("Thursday, 01 Oct 2026  14:23:05");
    }

    @Test
    void zonaDenganOffset() {
        assertThat(BottomBar.zone(TIME)).isEqualTo("Asia/Jakarta (UTC+07:00)");
        assertThat(BottomBar.zone(TIME.withZoneSameInstant(ZoneId.of("UTC")))).isEqualTo("UTC (UTC+00:00)");
    }

    @Test
    void defaultPacmanDanTidakAdaMenuSembunyikan() {
        var bar = new BottomBar();
        assertThat(bar.shown()).isEqualTo(AnimationKind.PACMAN);
        assertThat(bar.getComponentPopupMenu()).isNull();
    }

    @Test
    void gantiAnimasiDanTanpaAnimasi() {
        var bar = new BottomBar();

        bar.setAnimation(new AnimationChoice.Fixed(AnimationKind.INVADERS));
        assertThat(bar.shown()).isEqualTo(AnimationKind.INVADERS);

        bar.setAnimation(AnimationChoice.OFF);
        assertThat(bar.shown()).isNull();
        assertThat(bar.rotating()).isFalse();
    }

    @Test
    void acakMemilihAnimasiLain() {
        var bar = new BottomBar(); // default Pac-Man
        bar.setAnimation(AnimationChoice.RANDOM);
        assertThat(bar.shown()).isNotNull().isNotEqualTo(AnimationKind.PACMAN);
        assertThat(BottomBar.RANDOM_ROTATE_MS).isEqualTo(5 * 60 * 1000);
    }
}

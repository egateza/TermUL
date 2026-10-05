package dev.egateza.termul.app.ui.idle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.ui.anim.AnimationChoice;
import dev.egateza.termul.app.ui.anim.AnimationKind;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;

class IdleScreensTest {

    private static final ZonedDateTime TIME = ZonedDateTime.of(2026, 10, 5, 9, 7, 30, 0, ZoneId.of("Asia/Jakarta"));

    @Test
    void jamDanTanggal() {
        assertThat(ClockFace.time(TIME)).isEqualTo("09:07");
        assertThat(ClockFace.date(TIME, Locale.forLanguageTag("id"))).isEqualTo("Senin, 5 Oktober 2026");
        assertThat(ClockFace.date(TIME, Locale.ENGLISH)).isEqualTo("Monday, 5 October 2026");
    }

    @Test
    void berandaMenampilkanHostTerakhirDanFavorit() {
        var web = HostProfile.create("web-01", "10.0.0.1", "dev");
        var db = HostProfile.create("db-01", "10.0.0.2", "dev");
        var snapshot = new ProfileSnapshot(ProfileSnapshot.CURRENT_VERSION, List.of(), List.of(web, db),
                List.of(db.id()), List.of(web.id(), db.id()));
        var opened = new ArrayList<HostProfile>();
        var home = new HomeScreen(opened::add);

        home.setSnapshot(snapshot);

        assertThat(home.recentNames()).containsExactly("web-01", "db-01");
        assertThat(home.favoriteNames()).containsExactly("db-01");
        assertThat(clickButton(home, "web-01")).isTrue();
        assertThat(opened).containsExactly(web);
    }

    @Test
    void berandaTanpaFavoritMenyembunyikanBagiannyaDanFavoritDibatasi() {
        var home = new HomeScreen(p -> { });
        home.setSnapshot(ProfileSnapshot.empty());
        assertThat(home.favoritesVisible()).isFalse();

        var many = IntStream.range(0, 12).mapToObj(i -> HostProfile.create("h" + i, "10.0.0." + i, "dev")).toList();
        home.setSnapshot(new ProfileSnapshot(ProfileSnapshot.CURRENT_VERSION, List.of(), many,
                many.stream().map(HostProfile::id).toList(), List.of()));
        assertThat(home.favoritesVisible()).isTrue();
        assertThat(home.favoriteNames()).hasSize(HomeScreen.MAX_FAVORITES);
    }

    @Test
    void layarIdleMenampilkanJumlahTabDanAnimasiPilihan() {
        var overlay = new IdleOverlay(() -> { });
        overlay.setAnimation(new AnimationChoice.Fixed(AnimationKind.MATRIX));

        overlay.showIdle(3);
        assertThat(overlay.isVisible()).isTrue();
        assertThat(overlay.infoText()).contains("3");
        assertThat(overlay.animationSlot().shown()).isEqualTo(AnimationKind.MATRIX);

        overlay.showIdle(0);
        assertThat(overlay.infoText()).isEmpty();

        overlay.setAnimation(AnimationChoice.OFF);
        assertThat(overlay.animationSlot().shown()).isNull();
    }

    @Test
    void gerakanMouseKecilTidakMembangunkanTapiKlikMembangunkan() {
        int[] wakes = {0};
        var overlay = new IdleOverlay(() -> wakes[0]++);
        overlay.setSize(800, 600);
        overlay.showIdle(1);

        move(overlay, 100, 100);
        move(overlay, 100 + IdleOverlay.MOVE_THRESHOLD, 100);
        assertThat(wakes[0]).isZero();

        move(overlay, 140, 100);
        assertThat(wakes[0]).isEqualTo(1);

        var press = new MouseEvent(overlay, MouseEvent.MOUSE_PRESSED, 0, 0, 10, 10, 1, false, MouseEvent.BUTTON1);
        for (var l : overlay.getMouseListeners()) {
            l.mousePressed(press);
        }
        assertThat(wakes[0]).isEqualTo(2);
        assertThat(press.isConsumed()).isTrue();
    }

    private static void move(IdleOverlay overlay, int x, int y) {
        var e = new MouseEvent(overlay, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false);
        for (var l : overlay.getMouseMotionListeners()) {
            l.mouseMoved(e);
        }
    }

    private static boolean clickButton(java.awt.Container root, String text) {
        for (var c : root.getComponents()) {
            if (c instanceof JButton b && text.equals(b.getText())) {
                b.doClick(0);
                return true;
            }
            if (c instanceof java.awt.Container child && clickButton(child, text)) {
                return true;
            }
        }
        return false;
    }
}

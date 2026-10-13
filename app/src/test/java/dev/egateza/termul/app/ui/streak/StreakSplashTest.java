package dev.egateza.termul.app.ui.streak;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.geom.Point2D;
import org.junit.jupiter.api.Test;

class StreakSplashTest {

    private static final class FakeHost implements StreakSplash.Host {
        int clicks;
        boolean closed;

        @Override
        public Point2D anchor(StreakSplash splash) {
            return new Point2D.Double(10, 590);
        }

        @Override
        public void flameClicked() {
            clicks++;
        }

        @Override
        public void closed() {
            closed = true;
        }
    }

    private static StreakSplash splash(FakeHost host) {
        var splash = new StreakSplash(host);
        splash.setSize(900, 600);
        return splash;
    }

    @Test
    void naikStreakMenampilkanAngkaSebelumnya() {
        var s = splash(new FakeHost());
        s.playCounted(22, 23);
        assertThat(s.texts()).isEqualTo(new StreakSplash.Texts("22", "hari streak", "", "", "", false));
        s.close();
    }

    @Test
    void baraMenampilkanProgresTigaHari() {
        var s = splash(new FakeHost());
        s.playKindling(2);
        assertThat(s.texts().count()).isEqualTo("2/3");
        assertThat(s.texts().message()).contains("hari ke-3");
        s.close();
    }

    @Test
    void bekuMenampilkanJumlahKlikDanRetakan() {
        var s = splash(new FakeHost());
        s.playPending(new FlameLook(FlameLook.Mode.FROZEN, 47, 6, 0));
        assertThat(s.waiting()).isTrue();
        assertThat(s.texts().message()).isEqualTo("Kamu absen 6 hari, api membeku. Klik api 8 kali untuk memecahkan es.");
        assertThat(s.texts().progress()).isEqualTo("Retak 0/8");

        s.update(new FlameLook(FlameLook.Mode.FROZEN, 47, 6, 3), FlameActor.Effect.CRACK);
        assertThat(s.texts().progress()).isEqualTo("Retak 3/8");

        s.update(FlameLook.active(48), FlameActor.Effect.SHATTER);
        assertThat(s.waiting()).isFalse();
        assertThat(s.texts().count()).isEqualTo("48");
        assertThat(s.texts().message()).startsWith("Api menyala lagi");
        s.close();
    }

    @Test
    void hangusMenampilkanStreakYangHilang() {
        var s = splash(new FakeHost());
        s.playExpired(47, 12);
        assertThat(s.texts().count()).isEqualTo("47");
        assertThat(s.texts().message()).isEqualTo("Kamu absen 12 hari. Streak 47 hari hangus.");
        s.close();
    }

    @Test
    void klikApiSaatMenungguDiteruskanKeController() {
        var host = new FakeHost();
        var s = splash(host);
        s.playPending(new FlameLook(FlameLook.Mode.GRAY, 47, 1, 0));
        // posisi api diketahui setelah digambar sekali
        var image = new java.awt.image.BufferedImage(900, 600, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        s.paint(g);
        g.dispose();
        var press = new java.awt.event.MouseEvent(s, java.awt.event.MouseEvent.MOUSE_PRESSED, 0,
                java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 450, 260, 1, false, java.awt.event.MouseEvent.BUTTON1);
        s.dispatchEvent(press);
        assertThat(host.clicks).isEqualTo(1);

        s.close();
        assertThat(host.closed).isTrue();
    }
}

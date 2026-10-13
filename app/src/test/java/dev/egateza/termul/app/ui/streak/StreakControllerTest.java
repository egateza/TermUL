package dev.egateza.termul.app.ui.streak;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.streak.Streak;
import dev.egateza.termul.core.streak.StreakStore;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreakControllerTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 13);

    @TempDir
    Path tmp;

    private final AtomicReference<LocalDate> today = new AtomicReference<>(D);

    private StreakStore store() {
        return new StreakStore(tmp.resolve("streak.json"));
    }

    /** Controller dengan executor langsung; {@code start()} selesai setelah antrean EDT dikosongkan. */
    private StreakController started(Streak initial) throws Exception {
        store().save(initial);
        var ref = new AtomicReference<StreakController>();
        SwingUtilities.invokeAndWait(() -> {
            var c = new StreakController(store(), Runnable::run, new JLayeredPane(), true, today::get);
            c.start();
            ref.set(c);
        });
        SwingUtilities.invokeAndWait(() -> { }); // hasil load dikirim lewat invokeLater
        return ref.get();
    }

    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    @Test
    void pertamaKaliDibukaJadiBaraSatuPerTiga() throws Exception {
        var c = started(Streak.NONE);
        onEdt(() -> {
            assertThat(c.streak()).isEqualTo(new Streak(1, D));
            assertThat(c.chipComponent().countText()).isEqualTo("1/3");
            assertThat(c.chipComponent().isVisible()).isTrue();
        });
        assertThat(store().load()).isEqualTo(new Streak(1, D));
    }

    @Test
    void hariBerikutnyaNaikDanTersimpan() throws Exception {
        var c = started(new Streak(22, D.minusDays(1)));
        onEdt(() -> assertThat(c.chipComponent().countText()).isEqualTo("23x"));
        assertThat(store().load()).isEqualTo(new Streak(23, D));
    }

    @Test
    void padamDinyalakanDenganTripleKlik() throws Exception {
        var c = started(new Streak(47, D.minusDays(2)));
        onEdt(() -> {
            assertThat(c.chipComponent().look().mode()).isEqualTo(FlameLook.Mode.GRAY);
            assertThat(c.chipComponent().hintText()).isEqualTo("klik 3x");
            c.click(false);
            c.click(false);
            assertThat(c.streak().count()).isEqualTo(47);
            c.click(false);
            assertThat(c.streak()).isEqualTo(new Streak(48, D));
            assertThat(c.chipComponent().look()).isEqualTo(FlameLook.active(48));
        });
        assertThat(store().load()).isEqualTo(new Streak(48, D));
    }

    @Test
    void esDipecahkanSesuaiLamaAbsen() throws Exception {
        var c = started(new Streak(47, D.minusDays(7))); // absen 6 hari -> 8 klik
        onEdt(() -> {
            for (int i = 1; i < 8; i++) {
                c.click(false);
                assertThat(c.chipComponent().hintText()).isEqualTo("es " + i + "/8");
            }
            c.click(false);
            assertThat(c.streak()).isEqualTo(new Streak(48, D));
        });
    }

    @Test
    void absenLamaHangusJadiHariKeSatu() throws Exception {
        var c = started(new Streak(47, D.minusDays(12)));
        onEdt(() -> assertThat(c.streak()).isEqualTo(new Streak(1, D)));
    }

    @Test
    void tanggalBergantiSelagiTerbukaIkutDihitung() throws Exception {
        var c = started(new Streak(22, D.minusDays(1)));
        today.set(D.plusDays(1));
        onEdt(() -> {
            c.checkDay();
            assertThat(c.streak()).isEqualTo(new Streak(24, D.plusDays(1)));
        });
    }

    @Test
    void disembunyikanTapiTetapMenghitung() throws Exception {
        var c = started(new Streak(22, D.minusDays(1)));
        onEdt(() -> {
            c.setEnabled(false);
            assertThat(c.chipComponent().isVisible()).isFalse();
            today.set(D.plusDays(1));
            c.checkDay();
            assertThat(c.streak().count()).isEqualTo(24);
            assertThat(c.chipComponent().isVisible()).isFalse();
        });
    }
}

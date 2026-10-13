package dev.egateza.termul.core.streak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StreakTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 13);

    private static Streak streak(int count, int daysAgo) {
        return new Streak(count, D.minusDays(daysAgo));
    }

    @Test
    void pertamaKaliDibukaJadiHariKeSatu() {
        var opening = Streak.NONE.open(D);
        assertThat(opening.streak()).isEqualTo(new Streak(1, D));
        assertThat(opening.event()).isEqualTo(new StreakEvent.Counted(0, 1));
        assertThat(opening.streak().status(D)).isEqualTo(new StreakStatus.Kindling(1));
    }

    @Test
    void hariYangSamaTidakDihitungDua() {
        var s = streak(5, 0);
        assertThat(s.open(D)).isEqualTo(new Streak.Opening(s, new StreakEvent.SameDay()));
    }

    @Test
    void jamMundurKeHariSebelumnyaTidakMengubahApaPun() {
        var s = streak(5, 0);
        assertThat(s.open(D.minusDays(3)).event()).isEqualTo(new StreakEvent.SameDay());
        assertThat(s.open(D.minusDays(3)).streak()).isSameAs(s);
    }

    @Test
    void aktifDiHariKetiga() {
        var day2 = streak(2, 1);
        assertThat(day2.active()).isFalse();
        var opening = day2.open(D);
        assertThat(opening.event()).isEqualTo(new StreakEvent.Counted(2, 3));
        assertThat(opening.streak().active()).isTrue();
        assertThat(opening.streak().status(D)).isEqualTo(new StreakStatus.Burning(3));
    }

    @Test
    void baraYangBolongMulaiLagiDariHariKeSatu() {
        var opening = streak(2, 2).open(D);
        assertThat(opening.streak()).isEqualTo(new Streak(1, D));
        assertThat(opening.event()).isEqualTo(new StreakEvent.Counted(0, 1));
    }

    @Test
    void absenSatuDanDuaHariPadam() {
        for (int missed = 1; missed <= 2; missed++) {
            var s = streak(47, missed + 1);
            var opening = s.open(D);
            assertThat(opening.streak()).isSameAs(s);
            assertThat(opening.event()).isEqualTo(new StreakEvent.NeedsRevival(new StreakStatus.Gray(47, missed)));
        }
    }

    @Test
    void absenTigaSampaiSepuluhHariBekuDenganLimaSampaiSepuluhKlik() {
        int[] expected = {5, 6, 7, 8, 9, 10, 10, 10};
        for (int missed = 3; missed <= 10; missed++) {
            var status = streak(47, missed + 1).status(D);
            assertThat(status).isEqualTo(new StreakStatus.Frozen(47, missed, expected[missed - 3]));
        }
    }

    @Test
    void absenLebihDariSepuluhHariHangus() {
        var opening = streak(47, 12).open(D);
        assertThat(opening.event()).isEqualTo(new StreakEvent.Expired(47, 11));
        assertThat(opening.streak()).isEqualTo(new Streak(1, D));
    }

    @Test
    void memulihkanMelanjutkanAngkaDanMenghitungHariIni() {
        var gray = streak(47, 2);
        assertThat(gray.revive(D)).isEqualTo(new Streak(48, D));
        var frozen = streak(47, 6);
        assertThat(frozen.revive(D)).isEqualTo(new Streak(48, D));
        assertThat(frozen.revive(D).status(D)).isEqualTo(new StreakStatus.Burning(48));
    }

    @Test
    void ditundaTetapMenungguDanBisaMembeku() {
        var gray = streak(47, 3); // absen 2 hari
        assertThat(gray.status(D)).isInstanceOf(StreakStatus.Gray.class);
        // "Nanti saja" lalu besok dibuka lagi: absen 3 hari, jadi beku
        assertThat(gray.open(D.plusDays(1)).event())
                .isEqualTo(new StreakEvent.NeedsRevival(new StreakStatus.Frozen(47, 3, 5)));
    }

    @Test
    void tidakBisaMemulihkanStreakYangMenyala() {
        assertThatThrownBy(() -> streak(47, 0).revive(D)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> streak(47, 12).revive(D)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tanpaTanggalBerartiKosong() {
        assertThat(new Streak(5, null)).isEqualTo(Streak.NONE);
        assertThatThrownBy(() -> new Streak(-1, D)).isInstanceOf(IllegalArgumentException.class);
    }
}

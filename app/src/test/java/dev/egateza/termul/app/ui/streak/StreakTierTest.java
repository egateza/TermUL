package dev.egateza.termul.app.ui.streak;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StreakTierTest {

    @Test
    void batasLevelBerjenjang() {
        assertThat(StreakTier.of(0)).isEqualTo(StreakTier.BARA);
        assertThat(StreakTier.of(2)).isEqualTo(StreakTier.BARA);
        assertThat(StreakTier.of(3)).isEqualTo(StreakTier.JINGGA);
        assertThat(StreakTier.of(9)).isEqualTo(StreakTier.JINGGA);
        assertThat(StreakTier.of(10)).isEqualTo(StreakTier.MERAH);
        assertThat(StreakTier.of(29)).isEqualTo(StreakTier.MERAH);
        assertThat(StreakTier.of(30)).isEqualTo(StreakTier.MAGENTA);
        assertThat(StreakTier.of(60)).isEqualTo(StreakTier.UNGU);
        assertThat(StreakTier.of(100)).isEqualTo(StreakTier.BIRU);
        assertThat(StreakTier.of(180)).isEqualTo(StreakTier.ZAMRUD);
        assertThat(StreakTier.of(364)).isEqualTo(StreakTier.ZAMRUD);
        assertThat(StreakTier.of(365)).isEqualTo(StreakTier.EMAS);
        assertThat(StreakTier.of(730)).isEqualTo(StreakTier.PELANGI);
        assertThat(StreakTier.of(100_000)).isEqualTo(StreakTier.PELANGI);
    }

    @Test
    void levelAtasMakinRamai() {
        StreakTier previous = null;
        for (var tier : StreakTier.values()) {
            if (previous != null) {
                assertThat(tier.min).isGreaterThan(previous.min);
                assertThat(tier.tongues).isGreaterThanOrEqualTo(previous.tongues);
                assertThat(tier.sparks).isGreaterThanOrEqualTo(previous.sparks);
            }
            previous = tier;
        }
    }

    @Test
    void hanyaPelangiYangBerputarWarna() {
        assertThat(StreakTier.BIRU.palette(0)).isEqualTo(StreakTier.BIRU.palette(3));
        assertThat(StreakTier.PELANGI.palette(0).outer()).isNotEqualTo(StreakTier.PELANGI.palette(1).outer());
    }

    @Test
    void namaLevelDariI18n() {
        assertThat(StreakTier.MERAH.displayName()).isEqualTo("Api Merah");
    }
}

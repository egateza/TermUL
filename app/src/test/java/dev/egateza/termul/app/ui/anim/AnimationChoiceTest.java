package dev.egateza.termul.app.ui.anim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.config.AppConfig;
import java.util.EnumSet;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class AnimationChoiceTest {

    @Test
    void idBolakBalikUntukSemuaPilihan() {
        for (var choice : AnimationChoice.all()) {
            assertThat(AnimationChoice.fromId(choice.id())).isEqualTo(choice);
        }
        assertThat(AnimationChoice.fromId(AppConfig.ANIMATION_OFF)).isEqualTo(AnimationChoice.OFF);
        assertThat(AnimationChoice.fromId(AppConfig.ANIMATION_RANDOM)).isEqualTo(AnimationChoice.RANDOM);
        assertThat(AnimationChoice.fromId("tidak-ada")).isEqualTo(new AnimationChoice.Fixed(AnimationKind.PACMAN));
    }

    @Test
    void urutanMenuTanpaAnimasiAcakLaluSemuaAnimasi() {
        var all = AnimationChoice.all();
        assertThat(all).hasSize(AnimationKind.values().length + 2).doesNotHaveDuplicates();
        assertThat(all.get(0)).isEqualTo(AnimationChoice.OFF);
        assertThat(all.get(1)).isEqualTo(AnimationChoice.RANDOM);
    }

    @Test
    void labelAcakAdaDiSemuaBahasa() {
        for (var language : I18n.SUPPORTED) {
            assertThat(I18n.tIn(language.tag(), "anim.random")).isNotEqualTo("anim.random");
        }
    }

    @Test
    void offDanFixed() {
        var random = new SplittableRandom(1);
        assertThat(AnimationChoice.OFF.pick(random, null)).isEmpty();
        assertThat(new AnimationChoice.Fixed(AnimationKind.EKG).pick(random, AnimationKind.EKG)).contains(AnimationKind.EKG);
    }

    @Test
    void acakTidakMengulangYangBaruTampilDanMencakupSemua() {
        var random = new SplittableRandom(42);
        var seen = EnumSet.noneOf(AnimationKind.class);
        AnimationKind previous = null;
        for (int i = 0; i < 2000; i++) {
            var kind = AnimationChoice.RANDOM.pick(random, previous).orElseThrow();
            assertThat(kind).isNotEqualTo(previous);
            seen.add(kind);
            previous = kind;
        }
        assertThat(seen).containsExactlyInAnyOrder(AnimationKind.values());
    }
}

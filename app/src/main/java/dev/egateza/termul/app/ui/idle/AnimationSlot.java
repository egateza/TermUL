package dev.egateza.termul.app.ui.idle;

import dev.egateza.termul.app.ui.anim.Animation;
import dev.egateza.termul.app.ui.anim.AnimationChoice;
import dev.egateza.termul.app.ui.anim.AnimationKind;
import dev.egateza.termul.app.ui.anim.AnimationView;
import java.util.concurrent.ThreadLocalRandom;

/** Animasi besar (berskala) sesuai {@link AnimationChoice}; "acak" memilih animasi baru setiap {@link #reshuffle}. EDT. */
final class AnimationSlot {

    private final AnimationView view;
    private AnimationChoice choice = AnimationChoice.RANDOM;
    private AnimationKind shown;

    AnimationSlot(int scale) {
        view = new AnimationView(Animation.Size.LARGE, AnimationKind.PACMAN.create(Animation.Size.LARGE, null), scale);
        reshuffle();
    }

    AnimationView view() {
        return view;
    }

    void setChoice(AnimationChoice choice) {
        this.choice = choice;
        reshuffle();
    }

    /** Pilih ulang animasi (untuk "acak": hindari yang baru saja tampil). */
    void reshuffle() {
        var kind = choice.pick(ThreadLocalRandom.current(), shown);
        view.setVisible(kind.isPresent());
        kind.ifPresent(k -> {
            shown = k;
            view.setAnimation(k.create(Animation.Size.LARGE, null));
        });
    }

    AnimationKind shown() {
        return view.isVisible() ? shown : null;
    }
}

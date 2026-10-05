package dev.egateza.termul.app.ui.anim;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.config.AppConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Pilihan animasi di menu Pengaturan: tanpa animasi, acak, atau satu {@link AnimationKind}. Disimpan di
 * {@code config.json} sebagai {@link #id()}.
 */
public sealed interface AnimationChoice {

    record Off() implements AnimationChoice {
    }

    /** Animasi dipilih acak: tiap layar connect baru, dan berganti berkala di panel bawah. */
    record Randomized() implements AnimationChoice {
    }

    record Fixed(AnimationKind kind) implements AnimationChoice {
    }

    AnimationChoice OFF = new Off();
    AnimationChoice RANDOM = new Randomized();

    default String id() {
        return switch (this) {
            case Off _ -> AppConfig.ANIMATION_OFF;
            case Randomized _ -> AppConfig.ANIMATION_RANDOM;
            case Fixed(var kind) -> kind.id();
        };
    }

    default String label() {
        return switch (this) {
            case Off _ -> I18n.t("anim.off");
            case Randomized _ -> I18n.t("anim.random");
            case Fixed(var kind) -> kind.label();
        };
    }

    /**
     * Animasi yang dipakai sekarang.
     *
     * @param previous animasi sebelumnya, dihindari kalau acak supaya tidak tampil dua kali berturut-turut (boleh null)
     * @return kosong untuk {@link Off}
     */
    default Optional<AnimationKind> pick(RandomGenerator random, AnimationKind previous) {
        return switch (this) {
            case Off _ -> Optional.empty();
            case Randomized _ -> Optional.of(randomKind(random, previous));
            case Fixed(var kind) -> Optional.of(kind);
        };
    }

    private static AnimationKind randomKind(RandomGenerator random, AnimationKind previous) {
        var kinds = AnimationKind.values();
        if (previous == null) {
            return kinds[random.nextInt(kinds.length)];
        }
        int i = random.nextInt(kinds.length - 1);
        return kinds[i >= previous.ordinal() ? i + 1 : i];
    }

    /** @return id kosong/tidak dikenal = {@link AnimationKind#PACMAN} */
    static AnimationChoice fromId(String id) {
        if (AppConfig.ANIMATION_OFF.equals(id)) {
            return OFF;
        }
        if (AppConfig.ANIMATION_RANDOM.equals(id)) {
            return RANDOM;
        }
        return new Fixed(AnimationKind.fromId(id));
    }

    /** Urutan di menu: tanpa animasi, acak, lalu semua animasi. */
    static List<AnimationChoice> all() {
        var list = new ArrayList<AnimationChoice>(List.of(OFF, RANDOM));
        for (var k : AnimationKind.values()) {
            list.add(new Fixed(k));
        }
        return List.copyOf(list);
    }
}

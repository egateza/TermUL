package dev.egateza.termul.app.ui.anim;

import dev.egateza.termul.app.i18n.I18n;

/**
 * Animasi yang tersedia untuk layar "Menghubungkan…" dan panel bawah. Pilihan user (termasuk tanpa animasi / acak)
 * ada di {@link AnimationChoice}.
 */
public enum AnimationKind {
    PACMAN("pacman"),
    SSH("ssh"),
    TYPING("typing"),
    MATRIX("matrix"),
    RACK("rack"),
    INVADERS("invaders"),
    DINO("dino"),
    SNAKE("snake"),
    ROCKET("rocket"),
    EKG("ekg"),
    PULSE("pulse");

    private final String id;

    AnimationKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String label() {
        return I18n.t("anim." + id);
    }

    /**
     * @param who teks prompt untuk {@link #TYPING} (lihat {@link #promptFor}); null = prompt generik. Diabaikan
     *            animasi lain.
     */
    public Animation create(Animation.Size size, String who) {
        return switch (this) {
            case PACMAN -> new PacmanAnimation(size);
            case SSH -> new SshAnimation(size);
            case TYPING -> new TypingAnimation(size, who);
            case MATRIX -> new MatrixAnimation(size);
            case RACK -> new RackAnimation(size);
            case INVADERS -> new InvadersAnimation(size);
            case DINO -> new DinoAnimation(size);
            case SNAKE -> new SnakeAnimation(size);
            case ROCKET -> new RocketAnimation(size);
            case EKG -> new EkgAnimation(size);
            case PULSE -> new PulseAnimation(size);
        };
    }

    /** Teks prompt {@code user@host} pendek untuk animasi mengetik, dari user dan host profil. */
    public static String promptFor(String user, String host) {
        return TypingAnimation.prompt(user, host);
    }

    /** @return animasi dengan id tersebut, atau {@link #PACMAN} kalau kosong/tidak dikenal */
    static AnimationKind fromId(String id) {
        for (var k : values()) {
            if (k.id.equals(id)) {
                return k;
            }
        }
        return PACMAN;
    }
}

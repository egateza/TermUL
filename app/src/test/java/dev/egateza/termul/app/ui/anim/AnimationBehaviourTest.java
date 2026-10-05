package dev.egateza.termul.app.ui.anim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Perilaku khusus per animasi yang tidak tertangkap oleh smoke test di {@link AnimationKindTest}. */
class AnimationBehaviourTest {

    @Test
    void promptMemendekkanHostnameDanMempertahankanIp() {
        assertThat(TypingAnimation.prompt("termul", "prod-01")).isEqualTo("termul@prod-01");
        assertThat(TypingAnimation.prompt("termul", "db-backup.internal")).isEqualTo("termul@db-backup");
        assertThat(TypingAnimation.prompt("termul", "10.0.0.12")).isEqualTo("termul@10.0.0.12");
        assertThat(TypingAnimation.prompt("root", "fe80::1")).isEqualTo("root@fe80::1");
    }

    @Test
    void promptTerlaluPanjangDipotong() {
        String p = TypingAnimation.prompt("termul", "staging-api-gateway.example.co.id");
        assertThat(p).isEqualTo("termul@staging-ap…").hasSize(TypingAnimation.MAX_PROMPT);
    }

    @Test
    void promptTanpaUserAtauHost() {
        assertThat(TypingAnimation.prompt(null, "prod-01")).isEqualTo("prod-01");
        assertThat(TypingAnimation.prompt("termul", " ")).isEqualTo(TypingAnimation.GENERIC);
        assertThat(new TypingAnimation(Animation.Size.COMPACT, null).who()).isEqualTo(TypingAnimation.GENERIC);
    }

    @Test
    void perintahMengetikBukanPerintahBerbahaya() {
        assertThat(TypingAnimation.COMMANDS).noneMatch(c -> c.startsWith("sudo") || c.contains("rm ") || c.contains("restart"));
    }

    @ParameterizedTest
    @EnumSource(Animation.Size.class)
    void ularSelaluDiGridDanTidakMenabrakDirinya(Animation.Size size) {
        var snake = new SnakeAnimation(size);
        int maxLength = 0;
        for (int i = 0; i < 20_000; i++) {
            snake.step();
            var seen = new HashSet<SnakeAnimation.Cell>();
            for (var cell : snake.body()) {
                assertThat(snake.inGrid(cell)).isTrue();
                assertThat(seen.add(cell)).as("badan ular bertumpuk").isTrue();
            }
            maxLength = Math.max(maxLength, snake.length());
        }
        assertThat(maxLength).as("ular sempat memanjang").isGreaterThan(4);
    }

    @ParameterizedTest
    @EnumSource(Animation.Size.class)
    void semuaAlienTertembakLaluBarisanMunculLagi(Animation.Size size) {
        var invaders = new InvadersAnimation(size);
        boolean cleared = false;
        boolean restored = false;
        for (int i = 0; i < 20_000 && !restored; i++) {
            invaders.step();
            if (invaders.aliveCount() == 0) {
                cleared = true;
            } else if (cleared && invaders.aliveCount() == InvadersAnimation.COLUMNS) {
                restored = true;
            }
        }
        assertThat(cleared).isTrue();
        assertThat(restored).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Animation.Size.class)
    void dinoMelompat(Animation.Size size) {
        var dino = new DinoAnimation(size);
        int jumps = 0;
        boolean wasJumping = false;
        for (int i = 0; i < 3000; i++) {
            dino.step();
            if (dino.jumping() && !wasJumping) {
                jumps++;
            }
            wasJumping = dino.jumping();
        }
        assertThat(jumps).isGreaterThan(5);
    }

    @Test
    void detakEkgPuncakDiR() {
        assertThat(EkgAnimation.beat(0.32)).isGreaterThan(0.9);
        assertThat(EkgAnimation.beat(0.9)).isBetween(-0.05, 0.05); // garis dasar di antara detak
        assertThat(EkgAnimation.beat(0.35)).isNegative();          // lembah S
    }

    @Test
    void titikBerdenyutBergantian() {
        assertThat(PulseAnimation.pulse(8, 0)).isGreaterThan(PulseAnimation.pulse(8, 2));
        for (int f = 0; f < 100; f++) {
            for (int i = 0; i < 3; i++) {
                assertThat(PulseAnimation.pulse(f, i)).isBetween(0.0, 1.0);
            }
        }
    }
}

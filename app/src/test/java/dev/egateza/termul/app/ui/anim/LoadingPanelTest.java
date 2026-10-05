package dev.egateza.termul.app.ui.anim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;
import javax.accessibility.AccessibleRole;
import javax.swing.JLabel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LoadingPanelTest {

    private static final String MESSAGE = "Menghubungkan ke termul@prod-01 ...";

    @AfterEach
    void reset() {
        LoadingPanel.use(new AnimationChoice.Fixed(AnimationKind.PACMAN));
    }

    @Test
    void kosongSebelumPenundaanSelesai() {
        var panel = new LoadingPanel(MESSAGE, Optional.of(AnimationKind.PACMAN), "termul@prod-01");

        assertThat(panel.isRevealed()).isFalse();
        assertThat(panel.getComponentCount()).isZero();
        assertThat(panel.getAccessibleContext().getAccessibleName()).isEqualTo(MESSAGE);
        assertThat(LoadingPanel.DELAY_MS).isEqualTo(300);
    }

    @Test
    void setelahPenundaanTampilAnimasiDanPesan() {
        var panel = new LoadingPanel(MESSAGE, Optional.of(AnimationKind.TYPING), "termul@prod-01");

        panel.reveal();
        panel.reveal(); // kedua kali tidak menambah komponen

        assertThat(panel.getComponentCount()).isEqualTo(2);
        var view = panel.view();
        assertThat(view.animation()).isInstanceOf(TypingAnimation.class);
        assertThat(((TypingAnimation) view.animation()).who()).isEqualTo("termul@prod-01");
        assertThat(view.getAccessibleContext().getAccessibleRole()).isEqualTo(AccessibleRole.PROGRESS_BAR);
        assertThat(view.getPreferredSize().width).isEqualTo(Animation.Size.LARGE.width());
        assertThat(labels(panel)).containsExactly(MESSAGE);
    }

    @Test
    void tanpaAnimasiHanyaPesan() {
        var panel = new LoadingPanel(MESSAGE, Optional.empty(), null);

        panel.reveal();

        assertThat(panel.view()).isNull();
        assertThat(labels(panel)).containsExactly(MESSAGE);
    }

    @Test
    void panelBaruMemakaiPilihanMenu() {
        LoadingPanel.use(new AnimationChoice.Fixed(AnimationKind.DINO));
        var panel = new LoadingPanel(MESSAGE, null);

        panel.reveal();

        assertThat(panel.view().animation()).isInstanceOf(DinoAnimation.class);
    }

    @Test
    void acakBergantiAnimasiDiPanelBerikutnya() {
        LoadingPanel.use(AnimationChoice.RANDOM);
        Class<?> previous = null;
        for (int i = 0; i < 20; i++) {
            var panel = new LoadingPanel(MESSAGE, null);
            panel.reveal();
            Class<?> current = panel.view().animation().getClass();
            assertThat(current).isNotEqualTo(previous);
            previous = current;
        }
    }

    @Test
    void pilihanTanpaAnimasi() {
        LoadingPanel.use(AnimationChoice.OFF);
        var panel = new LoadingPanel(MESSAGE, null);

        panel.reveal();

        assertThat(panel.view()).isNull();
        assertThat(labels(panel)).containsExactly(MESSAGE);
    }

    private static String[] labels(LoadingPanel panel) {
        return Arrays.stream(panel.getComponents()).filter(JLabel.class::isInstance)
                .map(c -> ((JLabel) c).getText()).toArray(String[]::new);
    }
}

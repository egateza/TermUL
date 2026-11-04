package dev.egateza.termul.app.ui.anim;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class AnimationGateTest {

    /** Layar idle (glass pane pekat yang tampil) menutupi animasi di bawahnya, tetapi tidak animasi di dalamnya. */
    @Test
    void tertutupLayarIdleKecualiIsiLayarIdleSendiri() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root = new JRootPane();
            var chip = new JPanel();
            root.getContentPane().add(chip);
            var glass = new JPanel();
            glass.setOpaque(true);
            var idleAnimation = new JPanel();
            glass.add(idleAnimation);
            root.setGlassPane(glass);

            glass.setVisible(false);
            assertThat(AnimationGate.hidden(chip)).isFalse();

            glass.setVisible(true);
            assertThat(AnimationGate.hidden(chip)).isTrue();
            assertThat(AnimationGate.hidden(idleAnimation)).isFalse();
            assertThat(AnimationGate.hidden(glass)).isFalse();

            glass.setOpaque(false); // glass pane tembus (mis. efek sementara) tidak menutupi
            assertThat(AnimationGate.hidden(chip)).isFalse();
        });
    }

    @Test
    void tidakTampilTidakDianimasikan() throws Exception {
        SwingUtilities.invokeAndWait(() -> assertThat(AnimationGate.shouldAnimate(new JPanel())).isFalse());
    }
}

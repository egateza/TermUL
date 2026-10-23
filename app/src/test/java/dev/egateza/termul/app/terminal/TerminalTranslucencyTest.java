package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TerminalTranslucencyTest {

    @AfterEach
    void reset() {
        TerminalTranslucency.setPercent(100);
    }

    @Test
    void panelTanpaWindowBeralphaTetapSolid() {
        TerminalTranslucency.setPercent(60);

        assertThat(TerminalTranslucency.percent()).isEqualTo(60);
        assertThat(TerminalTranslucency.percentFor(new JPanel())).isEqualTo(100);
    }
}

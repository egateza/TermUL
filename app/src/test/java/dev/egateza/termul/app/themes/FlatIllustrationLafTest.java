package dev.egateza.termul.app.themes;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class FlatIllustrationLafTest {

    @Test
    void propertiesAreLoadedIntoDefaults() {
        var defaults = new FlatIllustrationLaf().getDefaults();

        assertThat(defaults.getColor("Button.default.background")).isEqualTo(new Color(0xFFC93C));
        assertThat(defaults.getColor("Component.borderColor")).isEqualTo(new Color(0x1E2430));
        assertThat(defaults.getInt("Button.arc")).isEqualTo(12);
    }
}

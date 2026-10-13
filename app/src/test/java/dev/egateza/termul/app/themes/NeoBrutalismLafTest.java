package dev.egateza.termul.app.themes;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class NeoBrutalismLafTest {

    @Test
    void lightPropertiesAreLoadedIntoDefaults() {
        var defaults = new NeoBrutalismLaf().getDefaults();

        assertThat(defaults.getColor("Button.default.background")).isEqualTo(new Color(0xFFDE00));
        assertThat(defaults.getColor("Component.borderColor")).isEqualTo(Color.BLACK);
        assertThat(defaults.getInt("Component.borderWidth")).isEqualTo(2);
        assertThat(defaults.getInt("Button.arc")).isEqualTo(14);
        assertThat(defaults.getInt("TextComponent.arc")).isEqualTo(999);
        assertThat(defaults.getBoolean("Popup.dropShadowPainted")).isFalse();
    }

    @Test
    void darkPropertiesAreLoadedIntoDefaults() {
        var defaults = new NeoBrutalismDarkLaf().getDefaults();

        assertThat(defaults.getColor("Component.borderColor")).isEqualTo(new Color(0xF5F0E1));
        assertThat(defaults.getColor("Button.default.foreground")).isEqualTo(Color.BLACK);
        assertThat(defaults.getInt("Component.borderWidth")).isEqualTo(2);
    }

    @Test
    void retroPropertiesAreLoadedIntoDefaults() {
        var defaults = new NeoBrutalismRetroLaf().getDefaults();

        assertThat(defaults.getColor("Button.default.background")).isEqualTo(new Color(0xF4BE45));
        assertThat(defaults.getColor("Panel.background")).isEqualTo(new Color(0xF2E3CE));
        assertThat(defaults.getInt("Button.arc")).isEqualTo(999);
        assertThat(defaults.getBoolean("Button.paintShadow")).isFalse();
    }
}

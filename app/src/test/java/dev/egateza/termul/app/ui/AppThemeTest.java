package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AppThemeTest {

    @Test
    void unknownIdFallsBackToDefault() {
        assertThat(AppTheme.fromId("tidak-ada")).isEqualTo(AppTheme.DEFAULT);
        assertThat(AppTheme.fromId(null)).isEqualTo(AppTheme.DEFAULT);
    }

    @Test
    void defaultThemeHasBothModes() {
        assertThat(AppTheme.DEFAULT.modes()).containsExactlyInAnyOrder(ThemeMode.LIGHT, ThemeMode.DARK);
        assertThat(AppTheme.DEFAULT.effectiveMode(ThemeMode.LIGHT)).isEqualTo(ThemeMode.LIGHT);
        assertThat(AppTheme.DEFAULT.effectiveMode(ThemeMode.DARK)).isEqualTo(ThemeMode.DARK);
    }

    @Test
    void lightOnlyThemeKeepsLightWhenDarkRequested() {
        assertThat(AppTheme.FLAT_ILLUSTRATION.modes()).containsExactly(ThemeMode.LIGHT);
        assertThat(AppTheme.FLAT_ILLUSTRATION.effectiveMode(ThemeMode.DARK)).isEqualTo(ThemeMode.LIGHT);
        assertThat(AppTheme.fromId("flat-illustration")).isEqualTo(AppTheme.FLAT_ILLUSTRATION);
    }

    @Test
    void everyThemeSupportsAtLeastOneModeAndAlwaysResolvesToASupportedOne() {
        for (AppTheme theme : AppTheme.values()) {
            assertThat(theme.modes()).isNotEmpty();
            Arrays.stream(ThemeMode.values())
                    .forEach(m -> assertThat(theme.supports(theme.effectiveMode(m))).isTrue());
        }
    }

    @Test
    void modeIdsRoundTrip() {
        for (ThemeMode m : ThemeMode.values()) {
            assertThat(ThemeMode.fromId(m.id())).isEqualTo(m);
        }
        assertThat(ThemeMode.fromId("aneh")).isEqualTo(ThemeMode.DARK);
    }
}

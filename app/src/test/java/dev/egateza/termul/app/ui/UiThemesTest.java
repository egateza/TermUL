package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.config.AppConfig;
import dev.egateza.termul.core.theme.CustomTheme;
import dev.egateza.termul.core.theme.CustomTheme.UiPalette;
import dev.egateza.termul.core.theme.ThemeTemplates;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiThemesTest {

    private final CustomTheme laut = ThemeTemplates.dark("laut", "Laut");
    private final CustomTheme pagi = ThemeTemplates.light("pagi", "Pagi");
    private final List<CustomTheme> custom = List.of(laut, pagi);

    @Test
    void resolvePrefersBuiltinThenCustomThenDefault() {
        assertThat(UiThemes.resolve("flat-illustration", custom)).isEqualTo(AppTheme.FLAT_ILLUSTRATION);
        assertThat(UiThemes.resolve("laut", custom)).isEqualTo(new CustomUiTheme(laut));
        assertThat(UiThemes.resolve("sudah-dihapus", custom)).isEqualTo(AppTheme.DEFAULT);
        assertThat(UiThemes.resolve(null, custom)).isEqualTo(AppTheme.DEFAULT);
    }

    @Test
    void allListsBuiltinFirstThenCustom() {
        assertThat(UiThemes.all(custom)).extracting(UiTheme::id)
                .containsExactly("default", "flat-illustration", "laut", "pagi");
    }

    @Test
    void customThemeHasOnlyItsBaseMode() {
        var dark = new CustomUiTheme(laut);
        var light = new CustomUiTheme(pagi);

        assertThat(dark.modes()).containsExactly(ThemeMode.DARK);
        assertThat(dark.effectiveMode(ThemeMode.LIGHT)).isEqualTo(ThemeMode.DARK);
        assertThat(light.modes()).containsExactly(ThemeMode.LIGHT);
        assertThat(light.effectiveMode(ThemeMode.DARK)).isEqualTo(ThemeMode.LIGHT);
        assertThat(dark.label()).isEqualTo("Laut");
    }

    @Test
    void overridesOnlyContainColorsTheUserSet() {
        assertThat(CustomUiTheme.overrides(new UiPalette(null, null, null, null, null))).isEmpty();

        var map = CustomUiTheme.overrides(new UiPalette("#111111", "#222222", null, "#444444", "#555555"));

        assertThat(map).containsEntry("@accentColor", "#111111")
                .containsEntry("@background", "#222222")
                .containsEntry("@selectionBackground", "#444444")
                .containsEntry("Component.borderColor", "#555555")
                .containsEntry("Separator.foreground", "#555555")
                .doesNotContainKey("@foreground");
    }

    @Test
    void builtinIdsAreReserved() {
        assertThat(UiThemes.builtinIds()).contains("default", "flat-illustration");
    }

    @Test
    void terminalThemeFollowsUiThemeWhenLinked() {
        var config = AppConfig.defaults().withTheme("laut").withTerminalTheme("pagi");

        assertThat(UiThemes.terminalTheme(config, custom)).contains(laut);
    }

    @Test
    void terminalThemeUsesOwnChoiceWhenUnlinked() {
        var config = AppConfig.defaults().withTheme("laut").withTerminalThemeLinked(false).withTerminalTheme("pagi");

        assertThat(UiThemes.terminalTheme(config, custom)).contains(pagi);
    }

    @Test
    void terminalThemeIsEmptyForBuiltinDeletedOrUnset() {
        var linkedBuiltin = AppConfig.defaults(); // tema bawaan tidak punya palet terminal
        var unlinkedNone = AppConfig.defaults().withTerminalThemeLinked(false);
        var unlinkedDeleted = unlinkedNone.withTerminalTheme("sudah-dihapus");

        assertThat(UiThemes.terminalTheme(linkedBuiltin, custom)).isEmpty();
        assertThat(UiThemes.terminalTheme(unlinkedNone, custom)).isEmpty();
        assertThat(UiThemes.terminalTheme(unlinkedDeleted, custom)).isEmpty();
    }

    @Test
    void builtinThemesGetTerminalPaletteMatchingUiMode() {
        var dark = UiThemes.terminalPalette(AppConfig.defaults(), custom, ThemeMode.DARK);
        var light = UiThemes.terminalPalette(AppConfig.defaults(), custom, ThemeMode.LIGHT);

        assertThat(dark).isEqualTo(ThemeTemplates.dark("x", "x").terminal());
        assertThat(light).isEqualTo(ThemeTemplates.light("x", "x").terminal());
        assertThat(dark.background()).isNotEqualTo(light.background());
    }

    @Test
    void customThemePaletteWinsOverModeDefaultAndUnlinkedNoneFollowsMode() {
        assertThat(UiThemes.terminalPalette(AppConfig.defaults().withTheme("pagi"), custom, ThemeMode.DARK))
                .isEqualTo(pagi.terminal());
        // tidak ditautkan dan memilih "Bawaan": palet standar sesuai mode UI, bukan palet tema UI custom
        var unlinked = AppConfig.defaults().withTheme("pagi").withTerminalThemeLinked(false);
        assertThat(UiThemes.terminalPalette(unlinked, custom, ThemeMode.DARK))
                .isEqualTo(ThemeTemplates.dark("x", "x").terminal());
    }
}

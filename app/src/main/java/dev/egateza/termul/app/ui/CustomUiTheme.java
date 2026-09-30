package dev.egateza.termul.app.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import dev.egateza.termul.core.theme.CustomTheme;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tema UI buatan user: LaF terang atau gelap bawaan ({@link CustomTheme#base()}) dengan warna yang ditimpa lewat
 * {@link FlatLaf#setGlobalExtraDefaults}. Hanya punya satu mode, sama dengan basenya.
 */
public record CustomUiTheme(CustomTheme theme) implements UiTheme {

    @Override
    public String id() {
        return theme.id();
    }

    @Override
    public String label() {
        return theme.name();
    }

    @Override
    public Set<ThemeMode> modes() {
        return Set.of(baseMode());
    }

    private ThemeMode baseMode() {
        return CustomTheme.BASE_LIGHT.equals(theme.base()) ? ThemeMode.LIGHT : ThemeMode.DARK;
    }

    @Override
    public boolean install(ThemeMode wanted) {
        FlatLaf.setGlobalExtraDefaults(overrides(theme.ui()));
        return FlatLaf.setup(baseMode() == ThemeMode.LIGHT ? new FlatLightLaf() : new FlatDarkLaf());
    }

    /** Kunci FlatLaf yang ditimpa; palet UI yang kosong tidak menimpa apa pun (ikut tema dasar). */
    static Map<String, String> overrides(CustomTheme.UiPalette ui) {
        var map = new HashMap<String, String>();
        put(map, ui.accent(), "@accentColor");
        put(map, ui.background(), "@background");
        put(map, ui.foreground(), "@foreground");
        put(map, ui.selection(), "@selectionBackground");
        put(map, ui.border(), "Component.borderColor", "Separator.foreground");
        return map;
    }

    private static void put(Map<String, String> map, String color, String... keys) {
        if (color != null) {
            for (String key : keys) {
                map.put(key, color);
            }
        }
    }
}

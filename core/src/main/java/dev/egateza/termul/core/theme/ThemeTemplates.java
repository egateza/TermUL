package dev.egateza.termul.core.theme;

import dev.egateza.termul.core.theme.CustomTheme.TerminalPalette;
import dev.egateza.termul.core.theme.CustomTheme.UiPalette;
import java.util.List;

/** Titik awal untuk tema baru di editor: palet yang sudah terbaca baik, tinggal diubah sesuai selera. */
public final class ThemeTemplates {

    private ThemeTemplates() {
    }

    public static CustomTheme dark(String id, String name) {
        return new CustomTheme(id, name, CustomTheme.BASE_DARK,
                new UiPalette("#4B9EFF", null, null, null, null),
                new TerminalPalette("#1E1E1E", "#D4D4D4", "#264F78", List.of(
                        "#000000", "#CD3131", "#0DBC79", "#E5E510", "#2472C8", "#BC3FBC", "#11A8CD", "#E5E5E5",
                        "#666666", "#F14C4C", "#23D18B", "#F5F543", "#3B8EEA", "#D670D6", "#29B8DB", "#FFFFFF")));
    }

    public static CustomTheme light(String id, String name) {
        return new CustomTheme(id, name, CustomTheme.BASE_LIGHT,
                new UiPalette("#2675BF", null, null, null, null),
                new TerminalPalette("#FFFFFF", "#333333", "#ADD6FF", List.of(
                        "#000000", "#CD3131", "#00883B", "#949800", "#0451A5", "#BC05BC", "#0598BC", "#555555",
                        "#666666", "#CD3131", "#14CE14", "#B5BA00", "#0451A5", "#BC05BC", "#0598BC", "#A5A5A5")));
    }

    /** @return salinan {@code source} dengan id dan nama baru */
    public static CustomTheme copyOf(CustomTheme source, String id, String name) {
        return new CustomTheme(id, name, source.base(), source.ui(), source.terminal());
    }
}

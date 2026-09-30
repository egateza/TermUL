package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.theme.CustomTheme;
import dev.egateza.termul.core.theme.CustomTheme.TerminalPalette;
import dev.egateza.termul.core.theme.ThemeTemplates;
import java.util.ArrayList;
import java.util.List;

/**
 * Warna terminal khusus per host ({@code HostProfile.terminalTheme}): preset bawaan atau tema custom. Tema yang sudah
 * dihapus dianggap "ikut pengaturan".
 */
public final class HostTerminalColors {

    /** Preset: latar merah gelap, penanda server produksi. */
    public static final String PROD_RED = "builtin:prod-red";

    /** @param id null = ikut pengaturan */
    public record Choice(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private HostTerminalColors() {
    }

    public static List<Choice> choices(List<CustomTheme> custom) {
        var result = new ArrayList<Choice>();
        result.add(new Choice(null, I18n.t("profile.terminalColors.inherit")));
        result.add(new Choice(PROD_RED, I18n.t("profile.terminalColors.prodRed")));
        custom.forEach(t -> result.add(new Choice(t.id(), t.name())));
        return result;
    }

    /** @return palet untuk id, atau null kalau ikut pengaturan / tema tidak ditemukan */
    public static TerminalPalette palette(String id, List<CustomTheme> custom) {
        if (id == null) {
            return null;
        }
        if (id.equals(PROD_RED)) {
            var dark = ThemeTemplates.dark("prod-red", "Produksi").terminal();
            return new TerminalPalette("#2B0B0E", dark.foreground(), "#6B2A30", dark.ansi(), null, null, null);
        }
        return custom.stream().filter(t -> t.id().equals(id)).findFirst().map(CustomTheme::terminal)
                .map(p -> new TerminalPalette(p.background(), p.foreground(), p.selection(), p.ansi(), null, null, null))
                .orElse(null);
    }
}

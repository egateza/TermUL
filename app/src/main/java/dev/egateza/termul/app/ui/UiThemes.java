package dev.egateza.termul.app.ui;

import dev.egateza.termul.core.config.AppConfig;
import dev.egateza.termul.core.theme.CustomTheme;
import dev.egateza.termul.core.theme.ThemeTemplates;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Menyatukan tema bawaan dan tema custom: pencarian berdasarkan id dan pemilihan warna terminal. */
public final class UiThemes {

    private UiThemes() {
    }

    /** @return semua tema yang bisa dipilih: bawaan dulu, lalu custom */
    public static List<UiTheme> all(List<CustomTheme> custom) {
        var all = new ArrayList<UiTheme>(List.of(AppTheme.values()));
        custom.forEach(t -> all.add(new CustomUiTheme(t)));
        return all;
    }

    /** @return tema dengan id tersebut; {@link AppTheme#DEFAULT} kalau kosong atau sudah tidak ada (mis. file dihapus) */
    public static UiTheme resolve(String id, List<CustomTheme> custom) {
        for (AppTheme t : AppTheme.values()) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return find(id, custom).<UiTheme>map(CustomUiTheme::new).orElse(AppTheme.DEFAULT);
    }

    public static Optional<CustomTheme> find(String id, List<CustomTheme> custom) {
        return custom.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    /** @return id yang tidak boleh dipakai tema custom karena sudah milik tema bawaan */
    public static Set<String> builtinIds() {
        var ids = new HashSet<String>();
        for (AppTheme t : AppTheme.values()) {
            ids.add(t.id());
        }
        return ids;
    }

    /**
     * @return tema custom yang warna terminalnya dipakai: tema UI sendiri kalau {@code terminalThemeLinked},
     *         kalau tidak pilihan terpisah; kosong = warna bawaan JediTerm
     */
    public static Optional<CustomTheme> terminalTheme(AppConfig config, List<CustomTheme> custom) {
        return find(config.terminalThemeLinked() ? config.theme() : config.terminalTheme(), custom);
    }

    /**
     * Palet terminal yang berlaku: milik tema custom yang dipilih ({@link #terminalTheme}), atau untuk tema bawaan
     * palet standar yang sesuai {@code mode} UI (gelap untuk mode gelap, terang untuk mode terang), supaya terminal
     * tidak putih di tengah UI gelap.
     *
     * @param mode mode UI yang benar-benar dipakai ({@link UiTheme#effectiveMode})
     */
    public static CustomTheme.TerminalPalette terminalPalette(AppConfig config, List<CustomTheme> custom,
                                                              ThemeMode mode) {
        return terminalTheme(config, custom).map(CustomTheme::terminal).orElseGet(() ->
                (mode == ThemeMode.LIGHT ? ThemeTemplates.light("bawaan", "Bawaan")
                        : ThemeTemplates.dark("bawaan", "Bawaan")).terminal());
    }
}

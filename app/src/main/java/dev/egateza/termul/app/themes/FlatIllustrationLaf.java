package dev.egateza.termul.app.themes;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * Tema "Flat Illustration": serba putih, border tegas berwarna gelap, sudut membulat, dan aksen warna kontras
 * (kuning, tosca, ungu). Nilainya ada di {@code FlatIllustrationLaf.properties} pada package ini.
 */
public final class FlatIllustrationLaf extends FlatLightLaf {

    public static final String NAME = "Flat Illustration";

    static {
        // properties dicari per nama class LaF di package ini (lihat FlatLaf.registerCustomDefaultsSource)
        registerCustomDefaultsSource(FlatIllustrationLaf.class.getPackageName());
    }

    @Override
    public String getName() {
        return NAME;
    }
}

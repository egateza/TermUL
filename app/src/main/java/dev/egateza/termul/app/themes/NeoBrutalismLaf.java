package dev.egateza.termul.app.themes;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * Tema "Neobrutalism" (terang): latar krem, border hitam tebal, sudut membulat, bayangan keras tanpa blur, dan aksen
 * warna mencolok (kuning, ungu, lime, pink, cyan). Nilainya ada di {@code NeoBrutalismLaf.properties}.
 */
public final class NeoBrutalismLaf extends FlatLightLaf {

    public static final String NAME = "Neobrutalism";

    static {
        // properties dicari per nama class LaF di package ini (lihat FlatLaf.registerCustomDefaultsSource)
        registerCustomDefaultsSource(NeoBrutalismLaf.class.getPackageName());
    }

    @Override
    public String getName() {
        return NAME;
    }
}

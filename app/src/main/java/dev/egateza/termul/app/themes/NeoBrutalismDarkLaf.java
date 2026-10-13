package dev.egateza.termul.app.themes;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * Tema "Neobrutalism" (gelap): permukaan arang, border krem tebal, sudut membulat, bayangan keras, dan aksen warna
 * mencolok yang sama dengan {@link NeoBrutalismLaf}. Nilainya ada di {@code NeoBrutalismDarkLaf.properties}.
 */
public final class NeoBrutalismDarkLaf extends FlatDarkLaf {

    public static final String NAME = "Neobrutalism Dark";

    static {
        registerCustomDefaultsSource(NeoBrutalismDarkLaf.class.getPackageName());
    }

    @Override
    public String getName() {
        return NAME;
    }
}

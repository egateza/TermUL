package dev.egateza.termul.app.themes;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * Tema "Neobrutalism Retro" (hanya terang): kertas krem kecokelatan, tinta hitam, sudut membulat, tombol pil, tanpa
 * bayangan, dan aksen retro-komik (oranye, pink, teal, biru, mustard). Nilainya ada di
 * {@code NeoBrutalismRetroLaf.properties}.
 */
public final class NeoBrutalismRetroLaf extends FlatLightLaf {

    public static final String NAME = "Neobrutalism Retro";

    static {
        registerCustomDefaultsSource(NeoBrutalismRetroLaf.class.getPackageName());
    }

    @Override
    public String getName() {
        return NAME;
    }
}

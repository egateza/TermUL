package dev.egateza.termul.app.ui;

import dev.egateza.termul.core.profile.EnvironmentTag;
import java.awt.Color;

/** Warna per environment (tab, tree). */
public final class EnvColors {

    private EnvColors() {
    }

    /** @return warna aksen, atau null untuk {@link EnvironmentTag#NONE} */
    public static Color of(EnvironmentTag tag) {
        return switch (tag) {
            case PROD -> new Color(0xE5484D);
            case STAGING -> new Color(0xF5A524);
            case DEV -> new Color(0x30A46C);
            case NONE -> null;
        };
    }

    public static String label(EnvironmentTag tag) {
        return switch (tag) {
            case PROD -> "Produksi";
            case STAGING -> "Staging";
            case DEV -> "Dev";
            case NONE -> "-";
        };
    }
}

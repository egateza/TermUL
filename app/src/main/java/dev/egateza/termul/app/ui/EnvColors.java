package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.profile.EnvironmentTag;
import java.awt.Color;

/** Warna per environment (tab, tree). */
public final class EnvColors {

    /** Bingkai terminal profil sensitif (sama dengan PROD). */
    public static final Color SENSITIVE = new Color(0xE5484D);

    private EnvColors() {
    }

    /** @return warna aksen, atau null untuk {@link EnvironmentTag#NONE} */
    public static Color of(EnvironmentTag tag) {
        return switch (tag) {
            case PROD -> SENSITIVE;
            case STAGING -> new Color(0xF5A524);
            case DEV -> new Color(0x30A46C);
            case NONE -> null;
        };
    }

    public static String label(EnvironmentTag tag) {
        return switch (tag) {
            case PROD -> I18n.t("common.env.prod");
            case STAGING -> I18n.t("common.env.staging");
            case DEV -> I18n.t("common.env.dev");
            case NONE -> "-";
        };
    }
}

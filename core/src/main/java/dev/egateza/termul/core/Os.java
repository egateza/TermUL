package dev.egateza.termul.core;

import java.util.Locale;

/** OS tempat aplikasi berjalan, untuk perilaku yang berbeda per platform (lokasi data, shortcut, editor). */
public enum Os {
    WINDOWS, MAC, OTHER;

    private static final Os CURRENT = of(System.getProperty("os.name", ""));

    public static Os current() {
        return CURRENT;
    }

    /** @param osName nilai {@code os.name}, mis. "Windows 11" atau "Mac OS X" */
    public static Os of(String osName) {
        String name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return WINDOWS;
        }
        return name.startsWith("mac") || name.startsWith("darwin") ? MAC : OTHER;
    }

    public boolean isWindows() {
        return this == WINDOWS;
    }

    public boolean isMac() {
        return this == MAC;
    }
}

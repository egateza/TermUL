package dev.egateza.termul.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Versi build dari {@code build.properties} yang di-filter Maven: {@code version} = major.minor.build
 * (build = jumlah commit git), {@code commit} = hash singkat dengan akhiran {@code -dirty} kalau working tree
 * belum di-commit. Dijalankan dari IDE tanpa filtering Maven → {@link #DEV}.
 */
public record BuildInfo(String version, String commit) {

    static final String RESOURCE = "build.properties";
    public static final BuildInfo DEV = new BuildInfo("dev", "");

    /** Teks lengkap untuk dialog Tentang, mis. {@code 0.1.105 (1b5275d-dirty)}. */
    public String display() {
        return commit.isEmpty() ? version : version + " (" + commit + ")";
    }

    public static BuildInfo load() {
        try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return DEV;
            }
            var props = new Properties();
            props.load(in);
            return from(props);
        } catch (IOException e) {
            return DEV;
        }
    }

    static BuildInfo from(Properties props) {
        var version = resolved(props.getProperty("version"));
        if (version == null) {
            return DEV;
        }
        var commit = resolved(props.getProperty("commit"));
        return new BuildInfo(version, commit == null ? "" : commit);
    }

    /** null kalau kosong atau placeholder Maven yang tidak ter-resolve ({@code ${...}}). */
    private static String resolved(String value) {
        return value == null || value.isBlank() || value.contains("${") ? null : value.strip();
    }
}

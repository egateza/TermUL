package dev.egateza.termul.app.bugreport;

import dev.egateza.termul.app.BuildInfo;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.update.UpdateProtocol;
import java.util.List;

/**
 * Info sistem untuk laporan bug. Sengaja hanya versi dan platform: tidak ada nama host, IP, username, path, atau isi
 * profil, karena issue di repo publik bisa dibaca siapa saja.
 */
public record SystemInfo(String termul, String os, String java, String language, int updateGeneration) {

    public static SystemInfo current() {
        return new SystemInfo(BuildInfo.load().display(),
                System.getProperty("os.name", "?") + " " + System.getProperty("os.version", "")
                        + " (" + System.getProperty("os.arch", "?") + ")",
                Runtime.version().toString(), I18n.current(), UpdateProtocol.GENERATION);
    }

    /** Baris Markdown, satu item per baris. */
    List<String> lines() {
        return List.of("- TermUL: " + termul, "- OS: " + os, "- Java: " + java, "- Language: " + language,
                "- Update generation: " + updateGeneration);
    }
}

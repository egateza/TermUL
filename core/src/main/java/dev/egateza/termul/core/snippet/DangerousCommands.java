package dev.egateza.termul.core.snippet;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Pola perintah yang bisa menghapus data atau menghentikan layanan. Snippet yang cocok selalu minta konfirmasi
 * sebelum ditempel atau disalin, walaupun tidak ditandai berbahaya oleh user. Ini pengaman tambahan, bukan daftar
 * lengkap: user tetap bisa menandai snippet lain sebagai berbahaya.
 */
public final class DangerousCommands {

    private record Rule(Pattern pattern, String label) {
    }

    private static final List<Rule> RULES = List.of(
            rule("\\brm\\s+-[a-zA-Z]*r[a-zA-Z]*f|\\brm\\s+-[a-zA-Z]*f[a-zA-Z]*r", "rm -rf"),
            rule("\\bdd\\s+if=", "dd"),
            rule("\\bmkfs", "mkfs"),
            rule("\\b(shutdown|reboot|poweroff|halt)\\b", "shutdown / reboot"),
            rule("chmod\\s+-R\\s+777", "chmod -R 777"),
            rule("(?i)\\bdrop\\s+(database|table|schema)\\b", "DROP"),
            rule("(?i)\\btruncate\\s+table\\b", "TRUNCATE"));

    private DangerousCommands() {
    }

    private static Rule rule(String regex, String label) {
        return new Rule(Pattern.compile(regex), label);
    }

    /** Label pola pertama yang cocok (mis. {@code rm -rf}), atau kosong kalau tidak ada. */
    public static Optional<String> detect(String command) {
        if (command == null) {
            return Optional.empty();
        }
        return RULES.stream().filter(r -> r.pattern().matcher(command).find()).map(Rule::label).findFirst();
    }
}

package dev.egateza.termul.terminal;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Heuristik untuk konfirmasi keluar dari shell: mengenali prompt shell di baris kursor
 * (mis. {@code user@host:~$ }, {@code [root@host ~]# }, {@code $ }) dan perintah setelahnya.
 * Tidak sempurna (prompt bisa dikustom), jadi hanya dipakai untuk konfirmasi, bukan keamanan.
 */
public final class ExitGuard {

    /** Prompt: satu token berakhiran $/#, atau bentuk [..]$/#, lalu satu spasi. */
    private static final Pattern PROMPT = Pattern.compile("^(\\S*[$#]|\\[[^\\]]*\\][$#])(?: (.*))?$");
    private static final Set<String> EXIT_COMMANDS = Set.of("exit", "logout");

    private ExitGuard() {
    }

    /** Teks perintah setelah prompt di baris kursor, atau kosong kalau baris bukan prompt shell. */
    public static Optional<String> commandAfterPrompt(String cursorLine) {
        if (cursorLine == null) {
            return Optional.empty();
        }
        String line = cursorLine.stripTrailing();
        var m = PROMPT.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(m.group(2) == null ? "" : m.group(2).strip());
    }

    /** Ctrl+D di prompt kosong = logout. */
    public static boolean isEmptyPrompt(String cursorLine) {
        return commandAfterPrompt(cursorLine).map(String::isEmpty).orElse(false);
    }

    /** Baris kursor berisi {@code exit}/{@code logout} (opsional dengan kode keluar) setelah prompt. */
    public static boolean isExitCommand(String cursorLine) {
        return commandAfterPrompt(cursorLine)
                .map(cmd -> cmd.split("\\s+")[0])
                .filter(EXIT_COMMANDS::contains)
                .isPresent();
    }
}

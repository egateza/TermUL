package dev.egateza.termul.core.localshell;

import java.util.ArrayList;
import java.util.List;

/**
 * Argumen perintah sebagai satu baris teks di dialog: dipisah spasi, kutip ganda untuk argumen berspasi, dan
 * {@code \"} untuk kutip di dalamnya. Backslash lain dibiarkan apa adanya supaya path Windows
 * ({@code C:\Program Files\...}) tidak perlu di-escape.
 */
public final class CommandLine {

    private CommandLine() {
    }

    /** @throws IllegalArgumentException kalau ada kutip yang tidak ditutup */
    public static List<String> split(String line) {
        var args = new ArrayList<String>();
        if (line == null) {
            return args;
        }
        var current = new StringBuilder();
        boolean quoted = false;
        boolean inArg = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                current.append('"');
                inArg = true;
                i++;
            } else if (c == '"') {
                quoted = !quoted;
                inArg = true; // "" = argumen kosong
            } else if (Character.isWhitespace(c) && !quoted) {
                if (inArg) {
                    args.add(current.toString());
                    current.setLength(0);
                    inArg = false;
                }
            } else {
                current.append(c);
                inArg = true;
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Tanda kutip (\") tidak ditutup.");
        }
        if (inArg) {
            args.add(current.toString());
        }
        return args;
    }

    /** Kebalikan {@link #split}: argumen berspasi/kosong/berkutip dibungkus kutip. */
    public static String join(List<String> args) {
        var sb = new StringBuilder();
        for (String arg : args) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            boolean needsQuotes = arg.isEmpty() || arg.chars().anyMatch(ch -> Character.isWhitespace(ch) || ch == '"');
            String escaped = arg.replace("\"", "\\\"");
            sb.append(needsQuotes ? "\"" + escaped + "\"" : escaped);
        }
        return sb.toString();
    }
}

package dev.egateza.termul.core.snippet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Variabel snippet ditulis {@code ${nama}} (huruf, angka, {@code _}; tidak diawali angka) dan diisi setiap kali
 * snippet dipakai. Nilai variabel satu baris tanpa karakter kontrol, jadi mengisinya tidak bisa menambah Enter.
 */
public final class SnippetVariables {

    public static final int MAX_VALUE = 500;
    private static final Pattern VAR = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private SnippetVariables() {
    }

    /** Nama variabel di {@code command}, urut kemunculan pertama, tanpa duplikat. */
    public static List<String> names(String command) {
        var out = new ArrayList<String>();
        Matcher m = VAR.matcher(command == null ? "" : command);
        while (m.find()) {
            if (!out.contains(m.group(1))) {
                out.add(m.group(1));
            }
        }
        return List.copyOf(out);
    }

    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    /** Nilai yang boleh dipakai: tidak kosong, maksimal {@link #MAX_VALUE} karakter, tanpa karakter kontrol. */
    public static boolean isValidValue(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_VALUE && !Snippet.hasControlCharacter(value);
    }

    /** Variabel yang nilainya belum diisi (atau tidak valid), urut kemunculan. */
    public static List<String> missing(String command, Map<String, String> values) {
        return names(command).stream().filter(n -> !isValidValue(values.get(n))).toList();
    }

    /** Ganti setiap {@code ${nama}} dengan nilainya; yang belum diisi / tidak valid dibiarkan apa adanya. */
    public static String resolve(String command, Map<String, String> values) {
        Matcher m = VAR.matcher(command == null ? "" : command);
        var sb = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(isValidValue(v) ? v : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}

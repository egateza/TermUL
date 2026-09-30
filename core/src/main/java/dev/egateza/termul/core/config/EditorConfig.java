package dev.egateza.termul.core.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Editor lokal per ekstensi file. Command berupa template dengan placeholder {@value #FILE_PLACEHOLDER};
 * kalau placeholder tidak ada, path file ditambahkan di akhir.
 *
 * @param defaultCommand command untuk ekstensi yang tidak ada di mapping, mis. {@code code --wait {file}}
 * @param byExtension    ekstensi tanpa titik (huruf kecil) → command, mis. {@code "sql" → "notepad++ {file}"}
 */
public record EditorConfig(String defaultCommand, Map<String, String> byExtension) {

    public static final String FILE_PLACEHOLDER = "{file}";
    public static final String DEFAULT_COMMAND = "code --wait {file}";

    public EditorConfig {
        if (defaultCommand == null || defaultCommand.isBlank()) {
            defaultCommand = DEFAULT_COMMAND;
        }
        var normalized = new TreeMap<String, String>();
        if (byExtension != null) {
            byExtension.forEach((ext, cmd) -> {
                if (ext != null && cmd != null && !ext.isBlank() && !cmd.isBlank()) {
                    normalized.put(normalizeExt(ext), cmd.strip());
                }
            });
        }
        byExtension = Map.copyOf(normalized);
    }

    public static EditorConfig defaults() {
        return new EditorConfig(DEFAULT_COMMAND, Map.of());
    }

    /** Command template untuk file ini. */
    public String templateFor(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            String cmd = byExtension.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
            if (cmd != null) {
                return cmd;
            }
        }
        return defaultCommand;
    }

    /** Argumen proses (sudah di-tokenize, placeholder diganti path absolut). */
    public List<String> commandFor(Path file) {
        List<String> tokens = tokenize(templateFor(file));
        String path = file.toAbsolutePath().toString();
        var result = new ArrayList<String>(tokens.size() + 1);
        boolean replaced = false;
        for (String t : tokens) {
            if (t.contains(FILE_PLACEHOLDER)) {
                result.add(t.replace(FILE_PLACEHOLDER, path));
                replaced = true;
            } else {
                result.add(t);
            }
        }
        if (!replaced) {
            result.add(path);
        }
        return result;
    }

    /** Mapping dalam format teks: satu baris {@code ext = command}; baris kosong/{@code #} diabaikan. */
    public String mappingText() {
        var sb = new StringBuilder();
        byExtension.forEach((ext, cmd) -> sb.append(ext).append(" = ").append(cmd).append('\n'));
        return sb.toString();
    }

    /** Kebalikan {@link #mappingText()}. @throws IllegalArgumentException dengan nomor baris kalau format salah */
    public static EditorConfig parse(String defaultCommand, String mappingText) {
        var map = new TreeMap<String, String>();
        String[] lines = mappingText == null ? new String[0] : mappingText.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0 || eq == line.length() - 1) {
                throw new IllegalArgumentException("Baris " + (i + 1) + ": format harus 'ext = command'");
            }
            map.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
        }
        tokenize(defaultCommand == null || defaultCommand.isBlank() ? DEFAULT_COMMAND : defaultCommand);
        return new EditorConfig(defaultCommand, map);
    }

    /** Pisah berdasarkan spasi; tanda kutip ganda mengelompokkan (untuk path dengan spasi). */
    static List<String> tokenize(String command) {
        var tokens = new ArrayList<String>();
        var cur = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        for (char c : command.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
                any = true;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (any) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                    any = false;
                }
            } else {
                cur.append(c);
                any = true;
            }
        }
        if (any) {
            tokens.add(cur.toString());
        }
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Command editor kosong");
        }
        return tokens;
    }

    private static String normalizeExt(String ext) {
        String e = ext.strip().toLowerCase(Locale.ROOT);
        return e.startsWith(".") ? e.substring(1) : e;
    }
}

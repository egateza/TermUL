package dev.egateza.termul.core.sshconfig;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parser {@code ~/.ssh/config} (subset OpenSSH) untuk impor profil. Semantik OpenSSH: untuk tiap alias, blok
 * {@code Host} dibaca berurutan dan <b>nilai pertama</b> yang ditemukan untuk sebuah key yang dipakai; baris sebelum
 * {@code Host} pertama berlaku untuk semua host. Pola mendukung {@code *}, {@code ?}, dan negasi {@code !}. Blok
 * {@code Match} dilewati. {@code Include} (dengan glob pada nama file) di-inline.
 *
 * <p>Key yang dibaca: HostName, User, Port, IdentityFile, ProxyJump, ProxyCommand (hanya untuk peringatan).
 */
public final class SshConfigParser {

    private static final int MAX_INCLUDE_DEPTH = 5;
    private static final Pattern SPLIT = Pattern.compile("^\\s*([A-Za-z]+)\\s*(?:=\\s*|\\s+)(.*)$");

    /** Satu blok: pola host (null = global/Match yang dilewati) dan opsi (key huruf kecil → nilai pertama). */
    private record Block(List<String> patterns, boolean match, Map<String, String> options) {
    }

    private SshConfigParser() {
    }

    /** Membaca file config (mis. {@code ~/.ssh/config}); {@code Include} relatif terhadap {@code sshDir}. */
    public static List<SshConfigHost> parse(Path file, Path sshDir, Path home) throws IOException {
        var lines = new ArrayList<String>();
        readWithIncludes(file, sshDir, lines, 0);
        return parse(lines, home);
    }

    /** Parse teks config yang sudah di-inline. */
    public static List<SshConfigHost> parse(List<String> lines, Path home) {
        var blocks = new ArrayList<Block>();
        var current = new Block(null, false, new HashMap<>());
        blocks.add(current);
        var aliases = new LinkedHashSet<String>();
        for (String raw : lines) {
            var kv = keyValue(raw);
            if (kv == null) {
                continue;
            }
            String key = kv[0];
            String value = kv[1];
            if (key.equals("host")) {
                var patterns = List.of(value.trim().split("\\s+"));
                current = new Block(patterns, false, new HashMap<>());
                blocks.add(current);
                patterns.stream().filter(p -> !p.startsWith("!") && p.indexOf('*') < 0 && p.indexOf('?') < 0)
                        .forEach(aliases::add);
            } else if (key.equals("match")) {
                current = new Block(null, true, new HashMap<>());
                blocks.add(current);
            } else {
                current.options().putIfAbsent(key, value);
            }
        }
        var result = new ArrayList<SshConfigHost>();
        for (String alias : aliases) {
            result.add(resolve(alias, blocks, home));
        }
        return result;
    }

    private static SshConfigHost resolve(String alias, List<Block> blocks, Path home) {
        var options = new HashMap<String, String>();
        for (Block b : blocks) {
            if (b.match() || (b.patterns() != null && !matches(alias, b.patterns()))) {
                continue;
            }
            b.options().forEach(options::putIfAbsent);
        }
        String user = unquote(options.get("user"));
        String hostName = expand(unquote(options.get("hostname")), alias, user, home);
        Integer port = null;
        String portText = options.get("port");
        if (portText != null) {
            try {
                port = Integer.valueOf(portText.trim());
            } catch (NumberFormatException e) {
                port = null;
            }
        }
        String identity = expand(unquote(options.get("identityfile")), alias, user, home);
        var jumps = new ArrayList<String>();
        String proxyJump = unquote(options.get("proxyjump"));
        if (proxyJump != null && !proxyJump.equalsIgnoreCase("none")) {
            for (String hop : proxyJump.split(",")) {
                if (!hop.isBlank()) {
                    jumps.add(hop.strip());
                }
            }
        }
        String proxyCommand = unquote(options.get("proxycommand"));
        boolean hasProxyCommand = proxyCommand != null && !proxyCommand.equalsIgnoreCase("none");
        return new SshConfigHost(alias, hostName, user, port, identity, List.copyOf(jumps), hasProxyCommand);
    }

    static boolean matches(String alias, List<String> patterns) {
        boolean positive = false;
        for (String p : patterns) {
            boolean negated = p.startsWith("!");
            String glob = negated ? p.substring(1) : p;
            if (glob(glob, alias)) {
                if (negated) {
                    return false;
                }
                positive = true;
            }
        }
        return positive;
    }

    private static boolean glob(String glob, String text) {
        var sb = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                default -> sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE).matcher(text).matches();
    }

    /** @return {key huruf kecil, nilai} atau null untuk baris kosong/komentar */
    private static String[] keyValue(String raw) {
        String line = raw.strip();
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        var m = SPLIT.matcher(line);
        if (!m.matches()) {
            return null;
        }
        String value = m.group(2).strip();
        int hash = value.indexOf(" #");
        if (hash >= 0 && !value.startsWith("\"")) {
            value = value.substring(0, hash).strip();
        }
        return new String[] {m.group(1).toLowerCase(Locale.ROOT), value};
    }

    private static String unquote(String v) {
        if (v == null) {
            return null;
        }
        String s = v.strip();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.isEmpty() ? null : s;
    }

    /** Token OpenSSH yang umum: {@code ~}, {@code %d} (home), {@code %h}/{@code %n} (alias), {@code %r} (user), {@code %%}. */
    private static String expand(String v, String alias, String user, Path home) {
        if (v == null) {
            return null;
        }
        String s = v;
        if (s.startsWith("~/") || s.equals("~")) {
            s = home.toString() + s.substring(1);
        }
        var sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 1 < s.length()) {
                char t = s.charAt(++i);
                switch (t) {
                    case 'd' -> sb.append(home);
                    case 'h', 'n' -> sb.append(alias);
                    case 'r' -> sb.append(user == null ? "" : user);
                    case '%' -> sb.append('%');
                    default -> sb.append('%').append(t);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void readWithIncludes(Path file, Path sshDir, List<String> out, int depth) throws IOException {
        for (String line : Files.readAllLines(file)) {
            var kv = keyValue(line);
            if (kv != null && kv[0].equals("include")) {
                if (depth >= MAX_INCLUDE_DEPTH) {
                    continue;
                }
                for (String spec : kv[1].split("\\s+")) {
                    for (Path included : includeTargets(unquote(spec), sshDir)) {
                        readWithIncludes(included, sshDir, out, depth + 1);
                    }
                }
            } else {
                out.add(line);
            }
        }
    }

    private static List<Path> includeTargets(String spec, Path sshDir) throws IOException {
        if (spec == null) {
            return List.of();
        }
        String s = (spec.startsWith("~/") ? System.getProperty("user.home") + spec.substring(1) : spec).replace('\\', '/');
        int slash = s.lastIndexOf('/');
        String name = s.substring(slash + 1); // glob hanya di nama file (Windows menolak * di Path)
        Path dir = slash < 0 ? sshDir : Path.of(slash == 0 ? "/" : s.substring(0, slash));
        if (!dir.isAbsolute()) {
            dir = sshDir.resolve(dir);
        }
        if (name.indexOf('*') < 0 && name.indexOf('?') < 0) {
            Path path = dir.resolve(name);
            return Files.isRegularFile(path) ? List.of(path) : List.of();
        }
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        var result = new ArrayList<Path>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, name)) {
            ds.forEach(p -> {
                if (Files.isRegularFile(p)) {
                    result.add(p);
                }
            });
        }
        result.sort(null); // OpenSSH: urutan leksikal
        return result;
    }
}

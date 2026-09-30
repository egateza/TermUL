package dev.egateza.termul.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Hook validasi untuk edit file root (sudo): setelah file baru dipasang, command hook dijalankan sebagai root.
 * Kalau command gagal (exit ≠ 0), file dikembalikan dari backup. Hook pertama yang pola path-nya cocok dipakai.
 *
 * @param hooks daftar berurutan; null (config lama) = {@link #defaults()}, list kosong = tanpa validasi
 */
public record ValidationHooks(List<Hook> hooks) {

    /**
     * @param pattern pola path absolut remote: {@code **} = apa saja (termasuk {@code /}), {@code *} = apa saja dalam
     *                satu segmen, {@code ?} = satu karakter; membedakan huruf besar/kecil (Linux)
     * @param command command shell yang dijalankan sebagai root; path file tersedia di {@code $TERMUL_FILE}
     */
    public record Hook(String pattern, String command) {

        public Hook {
            if (pattern == null || !pattern.strip().startsWith("/")) {
                throw new IllegalArgumentException("Pola harus path absolut (diawali /): " + pattern);
            }
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("Command validasi wajib diisi untuk " + pattern);
            }
            pattern = pattern.strip();
            command = command.strip();
            if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 || command.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Command validasi harus satu baris: " + pattern);
            }
        }

        public boolean matches(String remotePath) {
            return Pattern.matches(toRegex(pattern), remotePath);
        }
    }

    public ValidationHooks {
        hooks = hooks == null ? defaults().hooks() : List.copyOf(hooks);
    }

    /** Validator bawaan untuk config umum di Ubuntu. */
    public static ValidationHooks defaults() {
        return new ValidationHooks(List.of(
                new Hook("/etc/nginx/**", "nginx -t"),
                new Hook("/etc/ssh/sshd_config", "sshd -t"),
                new Hook("/etc/ssh/sshd_config.d/**", "sshd -t"),
                new Hook("/etc/sudoers", "visudo -c"),
                new Hook("/etc/sudoers.d/**", "visudo -c"),
                new Hook("/etc/apache2/**", "apache2ctl configtest")));
    }

    /** Hook pertama yang cocok dengan path absolut file yang diedit. */
    public Optional<Hook> find(String remotePath) {
        return hooks.stream().filter(h -> h.matches(remotePath)).findFirst();
    }

    /** Format teks: satu baris {@code pola = command}. */
    public String text() {
        var sb = new StringBuilder();
        hooks.forEach(h -> sb.append(h.pattern()).append(" = ").append(h.command()).append('\n'));
        return sb.toString();
    }

    /** Kebalikan {@link #text()}; baris kosong/{@code #} diabaikan. @throws IllegalArgumentException dengan nomor baris */
    public static ValidationHooks parse(String text) {
        var result = new ArrayList<Hook>();
        String[] lines = text == null ? new String[0] : text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf(" = ");
            if (eq <= 0) {
                throw new IllegalArgumentException("Baris " + (i + 1) + ": format harus 'pola = command'");
            }
            try {
                result.add(new Hook(line.substring(0, eq), line.substring(eq + 3)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Baris " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return new ValidationHooks(result);
    }

    private static String toRegex(String glob) {
        var sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                sb.append(".*");
                i++;
            } else if (c == '*') {
                sb.append("[^/]*");
            } else if (c == '?') {
                sb.append("[^/]");
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return sb.toString();
    }
}

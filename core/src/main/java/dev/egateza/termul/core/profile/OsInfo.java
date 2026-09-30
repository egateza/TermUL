package dev.egateza.termul.core.profile;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * OS server yang terdeteksi dari {@code /etc/os-release}. Isi file berasal dari server (tidak tepercaya),
 * jadi divalidasi: id hanya {@code [a-z0-9._-]}, teks dipotong, karakter kontrol dibuang.
 *
 * @param id         ID distro huruf kecil, mis. {@code ubuntu}, {@code debian}, {@code alpine}
 * @param versionId  VERSION_ID, mis. {@code 22.04}; boleh null
 * @param prettyName PRETTY_NAME, mis. {@code Ubuntu 22.04.4 LTS}; boleh null
 */
public record OsInfo(String id, String versionId, String prettyName) {

    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9._-]{1,32}");
    private static final int MAX_TEXT = 80;

    public OsInfo {
        id = id == null ? null : id.strip().toLowerCase(Locale.ROOT);
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("ID OS tidak valid");
        }
        versionId = clean(versionId);
        prettyName = clean(prettyName);
    }

    /** Label untuk tooltip: PRETTY_NAME, atau id + versi. */
    public String label() {
        if (prettyName != null) {
            return prettyName;
        }
        return versionId == null ? id : id + " " + versionId;
    }

    /**
     * Parse format {@code os-release} (KEY=value, value boleh dikutip).
     *
     * @return kosong kalau tidak ada ID yang valid
     */
    public static Optional<OsInfo> parseOsRelease(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> values = new HashMap<>();
        for (String raw : content.split("\\R", 200)) {
            String line = raw.strip();
            int eq = line.indexOf('=');
            if (line.startsWith("#") || eq <= 0) {
                continue;
            }
            values.putIfAbsent(line.substring(0, eq).strip(), unquote(line.substring(eq + 1).strip()));
        }
        try {
            return Optional.of(new OsInfo(values.get("ID"), values.get("VERSION_ID"), values.get("PRETTY_NAME")));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = s.codePoints()
                .filter(c -> !Character.isISOControl(c))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString().strip();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
    }
}

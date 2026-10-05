package dev.egateza.termul.update;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Versi rilis numerik {@code major.minor.build} (build = jumlah commit git), mis. {@code 0.1.130}. */
public record ReleaseVersion(List<Integer> parts) implements Comparable<ReleaseVersion> {

    private static final Pattern FORMAT = Pattern.compile("\\d{1,9}(\\.\\d{1,9}){1,3}");

    public ReleaseVersion {
        parts = List.copyOf(parts);
        if (parts.size() < 2 || parts.size() > 4 || parts.stream().anyMatch(p -> p < 0)) {
            throw new IllegalArgumentException("Versi tidak valid: " + parts);
        }
    }

    /** @throws IllegalArgumentException kalau bukan angka bertitik (mis. {@code dev} atau placeholder Maven) */
    public static ReleaseVersion parse(String text) {
        if (text == null || !FORMAT.matcher(text.strip()).matches()) {
            throw new IllegalArgumentException("Versi tidak valid: " + text);
        }
        var parts = new ArrayList<Integer>();
        for (String p : text.strip().split("\\.")) {
            parts.add(Integer.parseInt(p));
        }
        return new ReleaseVersion(parts);
    }

    /** @return null kalau {@code text} bukan versi rilis (build dev dari IDE) */
    public static ReleaseVersion parseOrNull(String text) {
        try {
            return parse(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Nama tag git/GitHub untuk versi ini, mis. {@code v0.1.130}. */
    public String tag() {
        return "v" + this;
    }

    public boolean isNewerThan(ReleaseVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(ReleaseVersion o) {
        int n = Math.max(parts.size(), o.parts.size());
        for (int i = 0; i < n; i++) {
            int a = i < parts.size() ? parts.get(i) : 0;
            int b = i < o.parts.size() ? o.parts.get(i) : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return parts.stream().map(String::valueOf).collect(Collectors.joining("."));
    }
}

package dev.egateza.termul.core.theme;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Membuat id tema (nama file) dari nama yang diketik user. */
public final class ThemeIds {

    private ThemeIds() {
    }

    /**
     * @param name  nama tampilan, mis. "Laut Malam"
     * @param taken id yang sudah dipakai (tema bawaan dan tema custom lain)
     * @return id unik bentuk {@code laut-malam}, atau {@code laut-malam-2} kalau sudah dipakai
     */
    public static String fromName(String name, Set<String> taken) {
        String base = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "tema";
        }
        if (base.length() > 34) { // sisakan ruang untuk akhiran angka (batas id 40 karakter)
            base = base.substring(0, 34).replaceAll("-+$", "");
        }
        String candidate = base;
        for (int n = 2; taken.contains(candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }
}

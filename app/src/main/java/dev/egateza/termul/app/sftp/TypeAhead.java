package dev.egateza.termul.app.sftp;

import java.util.List;
import java.util.Locale;

/**
 * Type-ahead ala Explorer untuk tabel SFTP: huruf yang diketik cepat berurutan membentuk awalan nama yang dicari;
 * mengetik huruf yang sama berulang (mis. {@code t}, {@code t}) berpindah ke nama berikutnya dengan awalan itu.
 * Hanya diakses di EDT.
 */
final class TypeAhead {

    /** Jeda ketik lebih dari ini memulai pencarian baru. */
    static final long RESET_MILLIS = 1000;

    private final StringBuilder typed = new StringBuilder();
    private long lastTyped;

    /**
     * Mencatat satu karakter yang diketik.
     *
     * @return teks pencarian saat ini, atau null kalau karakter ini diabaikan (kontrol, atau spasi di awal)
     */
    String type(char c, long whenMillis) {
        if (Character.isISOControl(c)) {
            return null;
        }
        if (whenMillis - lastTyped > RESET_MILLIS) {
            typed.setLength(0);
        }
        if (c == ' ' && typed.isEmpty()) {
            return null;
        }
        lastTyped = whenMillis;
        typed.append(c);
        return typed.toString();
    }

    /**
     * Mencari baris untuk teks yang diketik, berputar ke awal kalau sampai di akhir.
     *
     * @param names   nama per baris sesuai urutan tampilan
     * @param current baris terpilih saat ini (-1 = tidak ada)
     * @return indeks baris, atau -1 kalau tidak ada yang cocok
     */
    static int find(List<String> names, int current, String text) {
        if (text == null || text.isEmpty() || names.isEmpty()) {
            return -1;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean cycle = lower.chars().allMatch(ch -> ch == lower.charAt(0));
        String prefix = cycle ? lower.substring(0, 1) : lower;
        // Huruf berulang/pertama: mulai setelah baris saat ini. Awalan lebih panjang: baris saat ini boleh tetap.
        int start = cycle ? current + 1 : Math.max(current, 0);
        int n = names.size();
        for (int i = 0; i < n; i++) {
            int idx = Math.floorMod(start + i, n);
            if (names.get(idx).toLowerCase(Locale.ROOT).startsWith(prefix)) {
                return idx;
            }
        }
        return -1;
    }
}

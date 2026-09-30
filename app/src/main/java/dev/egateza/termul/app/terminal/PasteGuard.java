package dev.egateza.termul.app.terminal;

import dev.egateza.termul.core.profile.EnvironmentTag;
import java.util.List;

/**
 * Aturan konfirmasi paste: di host produksi, teks yang berisi baris baru langsung dijalankan shell begitu di-paste
 * (tiap baris baru = Enter), jadi perlu konfirmasi dulu.
 */
final class PasteGuard {

    static final int PREVIEW_LINES = 8;
    static final int PREVIEW_COLUMNS = 120;

    private PasteGuard() {
    }

    static boolean needsConfirmation(String text, EnvironmentTag environment) {
        return environment.isProduction() && text != null && (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0);
    }

    /** Baris yang akan dikirim (CRLF/CR dianggap satu pemisah; baris kosong di akhir tidak dihitung). */
    static List<String> lines(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = List.of(normalized.split("\n", -1));
        int end = lines.size();
        while (end > 0 && lines.get(end - 1).isEmpty()) {
            end--;
        }
        return lines.subList(0, end);
    }

    /** Cuplikan untuk dialog: paling banyak {@value #PREVIEW_LINES} baris, baris panjang dipotong. */
    static String preview(List<String> lines) {
        var sb = new StringBuilder();
        for (int i = 0; i < Math.min(PREVIEW_LINES, lines.size()); i++) {
            String line = lines.get(i);
            sb.append(line.length() > PREVIEW_COLUMNS ? line.substring(0, PREVIEW_COLUMNS) + "…" : line).append('\n');
        }
        if (lines.size() > PREVIEW_LINES) {
            sb.append("… (+").append(lines.size() - PREVIEW_LINES).append(")\n");
        }
        return sb.toString();
    }
}

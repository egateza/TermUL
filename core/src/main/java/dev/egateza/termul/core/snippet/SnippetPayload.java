package dev.egateza.termul.core.snippet;

/**
 * Teks yang dikirim ke shell saat snippet ditempel. Baris baru dikirim sebagai CR (seperti paste biasa). Snippet
 * multi-baris dibungkus <em>bracketed paste</em> kalau shell memintanya (bash ≥ 5.1, zsh, fish), sehingga semua baris
 * menunggu di prompt dan tidak ada yang berjalan sebelum Enter. Tanpa bracketed paste, setiap baris kecuali yang
 * terakhir langsung berjalan; pemanggil wajib minta konfirmasi dulu ({@link #runsEarly}).
 */
public final class SnippetPayload {

    static final String PASTE_START = "\u001b[200~";
    static final String PASTE_END = "\u001b[201~";

    private SnippetPayload() {
    }

    /**
     * @param resolved       perintah yang variabelnya sudah diisi (baris dipisah {@code \n})
     * @param enter          kirim Enter di akhir (snippet langsung berjalan)
     * @param bracketedPaste shell sedang dalam mode bracketed paste (DECSET 2004)
     */
    public static String build(String resolved, boolean enter, boolean bracketedPaste) {
        if (Snippet.hasControlCharacter(resolved.replace("\n", ""))) {
            throw new IllegalArgumentException("Perintah snippet berisi karakter kontrol.");
        }
        String text = resolved.replace("\n", "\r");
        if (bracketedPaste && resolved.contains("\n")) {
            text = PASTE_START + text + PASTE_END;
        }
        return enter ? text + "\r" : text;
    }

    /** true kalau ada baris yang langsung berjalan tanpa Enter dari user, padahal snippet tidak meminta Enter. */
    public static boolean runsEarly(String resolved, boolean enter, boolean bracketedPaste) {
        return !enter && !bracketedPaste && resolved.contains("\n");
    }
}

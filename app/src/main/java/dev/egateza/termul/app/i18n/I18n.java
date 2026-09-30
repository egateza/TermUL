package dev.egateza.termul.app.i18n;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Terjemahan teks UI lewat {@link ResourceBundle} ({@code messages_<tag>.properties}). Bahasa dipilih sekali saat
 * startup ({@link #use}); ganti bahasa berlaku setelah restart.
 *
 * <p>Menambah bahasa: buat {@code messages_<tag>.properties} baru lalu daftarkan di {@link #SUPPORTED}.
 * Key yang belum ada di bahasa terpilih jatuh ke {@link #FALLBACK}, dan kalau tidak ada juga, key itu sendiri
 * yang tampil (kelihatan jelas di UI). Pesan yang punya argumen ({@code {0}}) diproses {@link MessageFormat},
 * jadi apostrof di dalamnya harus ditulis dobel ({@code ''}); pesan tanpa argumen dipakai apa adanya.
 */
public final class I18n {

    public record Language(String tag, String label) {
    }

    public static final String FALLBACK = "id";

    /** Label ditulis dalam bahasanya sendiri supaya tetap terbaca kalau user salah pilih bahasa. */
    public static final List<Language> SUPPORTED = List.of(
            new Language("id", "Bahasa Indonesia"),
            new Language("en", "English"));

    private static final String BASE = "dev.egateza.termul.app.i18n.messages";
    // tanpa fallback ke Locale.getDefault(): bahasa yang dipilih tidak boleh tertimpa bahasa sistem
    private static final ResourceBundle.Control CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private static volatile String current = FALLBACK;

    private I18n() {
    }

    /** Pilih bahasa aktif; tag yang tidak dikenal jatuh ke {@link #FALLBACK}. */
    public static void use(String tag) {
        current = isSupported(tag) ? tag : FALLBACK;
    }

    public static String current() {
        return current;
    }

    public static boolean isSupported(String tag) {
        return SUPPORTED.stream().anyMatch(l -> l.tag().equals(tag));
    }

    public static String t(String key, Object... args) {
        return tIn(current, key, args);
    }

    /** Terjemahan di bahasa tertentu, mis. untuk memberi tahu hasil ganti bahasa sebelum restart. */
    public static String tIn(String tag, String key, Object... args) {
        String pattern = lookup(tag, key);
        if (pattern == null && !FALLBACK.equals(tag)) {
            pattern = lookup(FALLBACK, key);
        }
        if (pattern == null) {
            return key;
        }
        return args.length == 0 ? pattern : new MessageFormat(pattern, Locale.forLanguageTag(tag)).format(args);
    }

    /** Semua key sebuah bahasa; dipakai test untuk memastikan bundle sinkron. */
    public static java.util.Set<String> keys(String tag) {
        return bundle(tag).keySet();
    }

    private static String lookup(String tag, String key) {
        try {
            return bundle(tag).getString(key);
        } catch (MissingResourceException e) {
            return null;
        }
    }

    private static ResourceBundle bundle(String tag) {
        return ResourceBundle.getBundle(BASE, Locale.forLanguageTag(tag), CONTROL);
    }
}

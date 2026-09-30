package dev.egateza.termul.core.theme;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Tema buatan user ({@code themes/<id>.json}). Warna disimpan sebagai hex {@code #RRGGBB} supaya {@code core}
 * tidak bergantung pada Swing. Warna UI yang null mengikuti tema dasar ({@link #base()}).
 *
 * @param id       pengenal (juga nama file): huruf kecil, angka, dan tanda hubung
 * @param name     nama yang ditampilkan di menu
 * @param base     {@value #BASE_LIGHT} atau {@value #BASE_DARK}: tema bawaan yang ditimpa palet UI
 * @param ui       palet UI
 * @param terminal palet terminal
 */
public record CustomTheme(String id, String name, String base, UiPalette ui, TerminalPalette terminal) {

    public static final String BASE_LIGHT = "light";
    public static final String BASE_DARK = "dark";

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    public CustomTheme {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Id tema tidak valid (huruf kecil, angka, tanda hubung; maks 40 karakter)");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nama tema tidak boleh kosong");
        }
        name = name.strip();
        if (!BASE_LIGHT.equals(base)) {
            base = BASE_DARK;
        }
        if (ui == null) {
            ui = new UiPalette(null, null, null, null, null);
        }
        if (terminal == null) {
            throw new IllegalArgumentException("Palet terminal wajib ada");
        }
    }

    /**
     * Palet UI; field null = ikut tema dasar.
     *
     * @param selection warna latar item terpilih (menu, tabel, tree)
     */
    public record UiPalette(String accent, String background, String foreground, String selection, String border) {
        public UiPalette {
            accent = hexOrNull(accent);
            background = hexOrNull(background);
            foreground = hexOrNull(foreground);
            selection = hexOrNull(selection);
            border = hexOrNull(border);
        }
    }

    /**
     * Palet terminal.
     *
     * @param ansi            16 warna: 0-7 normal (hitam, merah, hijau, kuning, biru, magenta, cyan, putih), 8-15 bright
     * @param backgroundImage path absolut file gambar latar terminal, null = tanpa gambar. Hanya path yang disimpan,
     *                        bukan isi gambarnya, jadi tema yang diekspor tidak membawa gambar
     * @param imageVisibility seberapa jelas gambar terlihat di atas warna latar, persen {@value #MIN_VISIBILITY}..100
     *                        (100 = gambar penuh); null di tema lama = {@value #DEFAULT_VISIBILITY}
     * @param desktopWallpaper true = gambar latar adalah wallpaper desktop saat ini (diikuti setiap tema diterapkan)
     *                        dan {@code backgroundImage} diabaikan; null di tema lama = false
     */
    public record TerminalPalette(String background, String foreground, String selection, List<String> ansi,
                                  String backgroundImage, Integer imageVisibility, Boolean desktopWallpaper) {
        public static final int ANSI_COUNT = 16;
        public static final int MIN_VISIBILITY = 0;
        public static final int DEFAULT_VISIBILITY = 40;
        private static final int MAX_PATH = 1024;

        public TerminalPalette {
            background = requireHex(background, "background terminal");
            foreground = requireHex(foreground, "foreground terminal");
            selection = requireHex(selection, "selection terminal");
            if (ansi == null || ansi.size() != ANSI_COUNT) {
                throw new IllegalArgumentException("Palet ANSI harus berisi tepat " + ANSI_COUNT + " warna");
            }
            ansi = ansi.stream().map(c -> requireHex(c, "warna ANSI")).toList();
            backgroundImage = backgroundImage == null || backgroundImage.isBlank() ? null : backgroundImage.strip();
            if (backgroundImage != null && backgroundImage.length() > MAX_PATH) {
                throw new IllegalArgumentException("Path gambar latar terlalu panjang");
            }
            imageVisibility = imageVisibility == null ? DEFAULT_VISIBILITY
                    : Math.max(MIN_VISIBILITY, Math.min(100, imageVisibility));
            desktopWallpaper = desktopWallpaper != null && desktopWallpaper;
        }

        /** Palet tanpa gambar latar. */
        public TerminalPalette(String background, String foreground, String selection, List<String> ansi) {
            this(background, foreground, selection, ansi, null, null, null);
        }

        /** Salinan dengan gambar latar dari file {@code path} (null = tanpa gambar); flag wallpaper tidak berubah. */
        public TerminalPalette withBackgroundImage(String path, int visibility) {
            return new TerminalPalette(background, foreground, selection, ansi, path, visibility, desktopWallpaper);
        }

        public TerminalPalette withDesktopWallpaper(boolean useWallpaper) {
            return new TerminalPalette(background, foreground, selection, ansi, backgroundImage, imageVisibility,
                    useWallpaper);
        }

        /** @return true kalau palet ini meminta gambar latar, dari file maupun wallpaper desktop */
        public boolean hasBackdrop() {
            return desktopWallpaper || backgroundImage != null;
        }
    }

    static String hexOrNull(String value) {
        return value == null || value.isBlank() ? null : requireHex(value, "warna");
    }

    static String requireHex(String value, String what) {
        if (value == null || !HEX.matcher(value.strip()).matches()) {
            throw new IllegalArgumentException("Format " + what + " tidak valid, gunakan #RRGGBB");
        }
        return value.strip().toUpperCase(java.util.Locale.ROOT);
    }
}

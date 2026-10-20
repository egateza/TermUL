package dev.egateza.termul.core.highlight;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Warna highlight dalam format plugin Tabby: indeks {@code 0–15} (ANSI, ikut tema terminal), {@code 16–255}
 * (xterm-256), {@code #RRGGBB}/{@code #RGB}, atau nama warna CSS dasar.
 */
public sealed interface HighlightColor {

    /** @param index 0–255; 0–15 diterjemahkan lewat palet tema terminal yang aktif */
    record Indexed(int index) implements HighlightColor {
        public Indexed {
            if (index < 0 || index > 255) {
                throw new IllegalArgumentException("Indeks warna di luar 0–255: " + index);
            }
        }
    }

    /** @param rgb 0xRRGGBB */
    record Rgb(int rgb) implements HighlightColor {
        public Rgb {
            rgb &= 0xFFFFFF;
        }

        public int red() {
            return (rgb >> 16) & 0xFF;
        }

        public int green() {
            return (rgb >> 8) & 0xFF;
        }

        public int blue() {
            return rgb & 0xFF;
        }
    }

    /** Nama warna CSS yang dikenali (sebagian yang umum; selebihnya pakai {@code #RRGGBB}). */
    Map<String, Integer> CSS_NAMES = Map.ofEntries(
            Map.entry("black", 0x000000), Map.entry("white", 0xFFFFFF), Map.entry("red", 0xFF0000),
            Map.entry("green", 0x008000), Map.entry("lime", 0x00FF00), Map.entry("blue", 0x0000FF),
            Map.entry("yellow", 0xFFFF00), Map.entry("cyan", 0x00FFFF), Map.entry("aqua", 0x00FFFF),
            Map.entry("magenta", 0xFF00FF), Map.entry("fuchsia", 0xFF00FF), Map.entry("gray", 0x808080),
            Map.entry("grey", 0x808080), Map.entry("silver", 0xC0C0C0), Map.entry("maroon", 0x800000),
            Map.entry("olive", 0x808000), Map.entry("navy", 0x000080), Map.entry("teal", 0x008080),
            Map.entry("purple", 0x800080), Map.entry("orange", 0xFFA500), Map.entry("gold", 0xFFD700),
            Map.entry("pink", 0xFFC0CB), Map.entry("brown", 0xA52A2A), Map.entry("violet", 0xEE82EE),
            Map.entry("indigo", 0x4B0082), Map.entry("crimson", 0xDC143C), Map.entry("coral", 0xFF7F50),
            Map.entry("salmon", 0xFA8072), Map.entry("tomato", 0xFF6347), Map.entry("khaki", 0xF0E68C),
            Map.entry("turquoise", 0x40E0D0), Map.entry("skyblue", 0x87CEEB), Map.entry("orchid", 0xDA70D6),
            Map.entry("plum", 0xDDA0DD), Map.entry("chocolate", 0xD2691E), Map.entry("darkred", 0x8B0000),
            Map.entry("darkgreen", 0x006400), Map.entry("darkblue", 0x00008B), Map.entry("darkorange", 0xFF8C00),
            Map.entry("lightgreen", 0x90EE90), Map.entry("lightblue", 0xADD8E6), Map.entry("lightgray", 0xD3D3D3),
            Map.entry("lightgrey", 0xD3D3D3), Map.entry("darkgray", 0xA9A9A9), Map.entry("darkgrey", 0xA9A9A9),
            Map.entry("dodgerblue", 0x1E90FF), Map.entry("deepskyblue", 0x00BFFF), Map.entry("hotpink", 0xFF69B4),
            Map.entry("deeppink", 0xFF1493), Map.entry("limegreen", 0x32CD32), Map.entry("seagreen", 0x2E8B57),
            Map.entry("royalblue", 0x4169E1), Map.entry("steelblue", 0x4682B4), Map.entry("slategray", 0x708090),
            Map.entry("goldenrod", 0xDAA520), Map.entry("firebrick", 0xB22222), Map.entry("tan", 0xD2B48C),
            Map.entry("beige", 0xF5F5DC), Map.entry("lavender", 0xE6E6FA), Map.entry("mintcream", 0xF5FFFA));

    /** @return warna, atau kosong kalau teks tidak dikenali */
    static Optional<HighlightColor> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String s = text.strip().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return Optional.empty();
        }
        if (s.chars().allMatch(Character::isDigit)) {
            if (s.length() > 3) {
                return Optional.empty();
            }
            int index = Integer.parseInt(s);
            return index <= 255 ? Optional.of(new Indexed(index)) : Optional.empty();
        }
        if (s.startsWith("#")) {
            String hex = s.substring(1);
            if (!hex.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
                return Optional.empty();
            }
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
            }
            return hex.length() == 6 ? Optional.of(new Rgb(Integer.parseInt(hex, 16))) : Optional.empty();
        }
        Integer named = CSS_NAMES.get(s);
        return named == null ? Optional.empty() : Optional.of(new Rgb(named));
    }

    /** Teks yang disimpan/ditampilkan: indeks sebagai angka, RGB sebagai {@code #rrggbb}. */
    static String format(HighlightColor color) {
        return switch (color) {
            case Indexed(int index) -> Integer.toString(index);
            case Rgb rgb -> String.format(Locale.ROOT, "#%06x", rgb.rgb());
        };
    }
}

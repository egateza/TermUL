package dev.egateza.termul.app.terminal;

import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import dev.egateza.termul.core.theme.CustomTheme.TerminalPalette;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pengaturan JediTerm: font monospace yang tersedia di Windows/macOS, bell bawaan sistem (BEL dari server).
 * Ukuran font bisa diubah (zoom); satu instance per tab supaya zoom tidak saling memengaruhi.
 * Family font, warna (tema), dan pengaturan bell dipakai bersama semua tab.
 */
public final class TerminalSettings extends DefaultSettingsProvider {

    public static final float MIN_SIZE = 8f;
    public static final float MAX_SIZE = 40f;

    private static final List<String> PREFERRED_FONTS =
            List.of("JetBrains Mono", "Cascadia Mono", "Cascadia Code", "Consolas", "SF Mono", "Menlo", "Monaco",
                    "DejaVu Sans Mono");

    private final AtomicReference<String> chosenFamily; // null = otomatis; dipakai bersama semua tab
    private final float defaultSize;
    private volatile float fontSize;
    private volatile Font font;
    private final BellSettings bell;
    private final AtomicReference<Colors> colors; // null = warna bawaan JediTerm; dipakai bersama semua tab
    private final AtomicReference<Backdrop> backdrop; // null = tanpa gambar latar; dipakai bersama semua tab
    private final AtomicBoolean rightClickCopyPaste; // true = klik kanan copy/paste gaya Windows; dipakai bersama semua tab
    private volatile Colors override; // null = ikut warna bersama; hanya untuk tab ini (warna khusus host)
    /**
     * Warna default yang dibaca saat digambar ({@link TerminalColor} berbasis supplier), bukan disalin ke sel. JediTerm
     * menyimpan style di setiap sel yang ditulis, jadi warna tetap yang dipakai sekarang akan tertinggal di sel-sel
     * lama begitu tema diganti (teks lama tetap berlatar tema sebelumnya). Dengan supplier, semua sel ikut tema baru.
     */
    private final TextStyle defaultStyle;

    private static final Color FALLBACK_FOREGROUND = new Color(0, 0, 0);
    private static final Color FALLBACK_BACKGROUND = new Color(255, 255, 255);

    /**
     * Gambar latar yang digambar di bawah teks.
     *
     * @param image      gambar asli (belum disesuaikan dengan ukuran panel)
     * @param visibility persen 0..100: seberapa jelas gambar terlihat di atas {@code base}
     * @param base       warna latar tema; juga warna yang isiannya dibuang agar gambar terlihat di antara teks
     */
    public record BackgroundLayer(BufferedImage image, int visibility, java.awt.Color base) {
    }

    private record Backdrop(BufferedImage image, int visibility) {
    }

    public TerminalSettings(float fontSize) {
        this(null, fontSize);
    }

    /** @param family nama font pilihan user; null atau tidak terpasang = pilihan otomatis */
    public TerminalSettings(String family, float fontSize) {
        this(new AtomicReference<>(family), fontSize, new BellSettings(), new AtomicReference<>(),
                new AtomicReference<>(), new AtomicBoolean());
    }

    private TerminalSettings(AtomicReference<String> chosenFamily, float fontSize, BellSettings bell,
                             AtomicReference<Colors> colors, AtomicReference<Backdrop> backdrop,
                             AtomicBoolean rightClickCopyPaste) {
        this.chosenFamily = chosenFamily;
        this.rightClickCopyPaste = rightClickCopyPaste;
        this.bell = bell;
        this.colors = colors;
        this.backdrop = backdrop;
        this.defaultStyle = new TextStyle(
                new TerminalColor(() -> {
                    var c = effectiveColors();
                    return c == null ? FALLBACK_FOREGROUND : c.foregroundColor();
                }),
                new TerminalColor(() -> {
                    var c = effectiveColors();
                    return c == null ? FALLBACK_BACKGROUND : c.backgroundColor();
                }));
        this.defaultSize = clamp(fontSize);
        setFontSize(fontSize);
    }

    /** Salinan dengan ukuran default yang sama (untuk tab baru); family font, warna, dan pengaturan bell dipakai bersama. */
    public TerminalSettings copy() {
        return new TerminalSettings(chosenFamily, defaultSize, bell, colors, backdrop, rightClickCopyPaste);
    }

    /**
     * Ganti warna terminal untuk semua tab (instance ini dan semua salinannya). Tab yang sudah terbuka perlu
     * {@code ZoomableTermWidget.refreshColors()} untuk menerapkannya.
     *
     * @param palette palet tema, atau null untuk warna bawaan JediTerm
     */
    public void setPalette(TerminalPalette palette) {
        colors.set(palette == null ? null : Colors.of(palette));
    }

    /**
     * Pasang gambar latar untuk semua tab. Hanya berlaku bersama palet tema ({@link #setPalette}), karena warna
     * dasarnya diambil dari sana.
     *
     * @param image      gambar yang sudah dimuat, atau null untuk melepas gambar latar
     * @param visibility persen 0..100
     */
    public void setBackgroundImage(BufferedImage image, int visibility) {
        backdrop.set(image == null ? null : new Backdrop(image, Math.max(0, Math.min(100, visibility))));
    }

    /**
     * Warna khusus untuk tab ini saja (mis. host produksi), di atas warna bersama. Salinan lain tidak terpengaruh.
     *
     * @param palette palet, atau null untuk kembali ke warna bersama
     */
    public void setPaletteOverride(TerminalPalette palette) {
        override = palette == null ? null : Colors.of(palette);
    }

    private Colors effectiveColors() {
        var o = override;
        return o != null ? o : colors.get();
    }

    /** @return lapisan gambar latar yang berlaku sekarang, atau null kalau tidak ada gambar atau palet tema */
    public BackgroundLayer backgroundLayer() {
        if (override != null) {
            return null; // warna khusus host: tanpa gambar latar supaya warnanya jelas terlihat
        }
        var b = backdrop.get();
        var c = colors.get();
        return b == null || c == null ? null : new BackgroundLayer(b.image(), b.visibility(), c.awtBackground());
    }

    public BellSettings bell() {
        return bell;
    }

    /**
     * @return true = klik kanan di terminal gaya Windows (copy selection, atau paste kalau tidak ada selection);
     *         false = menu konteks bawaan JediTerm
     */
    public boolean rightClickCopyPaste() {
        return rightClickCopyPaste.get();
    }

    /** Berlaku langsung untuk semua tab (instance ini dan semua salinannya). */
    public void setRightClickCopyPaste(boolean on) {
        rightClickCopyPaste.set(on);
    }

    /** @return family yang dipilih user (null = otomatis); berlaku untuk semua tab */
    public String family() {
        return chosenFamily.get();
    }

    /**
     * Ganti family font untuk semua tab (instance ini dan semua salinannya). Tiap tab baru memakainya setelah
     * {@link #reloadFont()}.
     */
    public void setFamily(String family) {
        chosenFamily.set(family == null || family.isBlank() ? null : family);
        reloadFont();
    }

    /** Bangun ulang {@link Font} dari family bersama dan ukuran saat ini (family berubah dari instance lain). */
    public void reloadFont() {
        setFontSize(fontSize);
    }

    /** @return ukuran baru setelah dibatasi {@link #MIN_SIZE}..{@link #MAX_SIZE} */
    public float setFontSize(float size) {
        float s = clamp(size);
        this.fontSize = s;
        this.font = new Font(resolveFamily(chosenFamily.get()), Font.PLAIN, Math.round(s));
        return s;
    }

    public float defaultSize() {
        return defaultSize;
    }

    static float clamp(float size) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
    }

    /** @return {@code wanted} kalau terpasang, kalau tidak font monospace pilihan otomatis */
    static String resolveFamily(String wanted) {
        Set<String> available = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        if (wanted != null && available.contains(wanted)) {
            return wanted;
        }
        return PREFERRED_FONTS.stream().filter(available::contains).findFirst().orElse(Font.MONOSPACED);
    }

    /** @return nama font yang dipakai kalau user tidak memilih (untuk ditampilkan di dialog) */
    public static String automaticFamily() {
        return resolveFamily(null);
    }

    @Override
    public Font getTerminalFont() {
        return font;
    }

    @Override
    public float getTerminalFontSize() {
        return fontSize;
    }

    @Override
    public ColorPalette getTerminalColorPalette() {
        var c = effectiveColors();
        return c == null ? super.getTerminalColorPalette() : c.palette();
    }

    /**
     * Sumber warna default JediTerm: {@code getDefaultForeground/Background} hanya membaca dari sini, dan
     * {@code StyleState} untuk sel-sel terminal juga. Implementasi bawaannya memakai indeks palet 0/15, bukan RGB,
     * jadi menimpa dua fungsi turunannya saja membuat kanvas dan sel berbeda warna. Warnanya dinamis, lihat
     * {@link #defaultStyle}.
     */
    @Override
    public TextStyle getDefaultStyle() {
        return defaultStyle;
    }

    @Override
    public TextStyle getSelectionColor() {
        var c = effectiveColors();
        return c == null ? super.getSelectionColor() : c.selection();
    }

    @Override
    public boolean audibleBell() {
        return bell.sound();
    }

    @Override
    public boolean copyOnSelect() {
        return false;
    }

    /** Warna tema yang sudah diterjemahkan ke tipe JediTerm, dibuat sekali per penggantian tema. */
    private record Colors(ColorPalette palette, Color foregroundColor, Color backgroundColor, TextStyle selection,
                          java.awt.Color awtBackground) {

        static Colors of(TerminalPalette p) {
            var ansi = p.ansi().stream().map(Colors::color).toArray(Color[]::new);
            var fg = color(p.foreground());
            return new Colors(new ThemedPalette(ansi), fg, color(p.background()),
                    new TextStyle(TerminalColor.fromColor(fg), TerminalColor.fromColor(color(p.selection()))),
                    java.awt.Color.decode(p.background()));
        }

        static Color color(String hex) {
            int rgb = Integer.parseInt(hex.substring(1), 16);
            return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        }
    }

    /** 16 warna ANSI dari tema. Indeks 16..255 tidak sampai ke palet: emulator JediTerm sudah mengubahnya ke RGB. */
    private static final class ThemedPalette extends ColorPalette {
        private final Color[] ansi;

        ThemedPalette(Color[] ansi) {
            this.ansi = ansi;
        }

        @Override
        protected Color getForegroundByColorIndex(int index) {
            return ansi[index];
        }

        @Override
        protected Color getBackgroundByColorIndex(int index) {
            return ansi[index];
        }
    }
}

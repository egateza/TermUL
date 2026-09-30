package dev.egateza.termul.app.terminal;

import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pengaturan JediTerm: font monospace yang tersedia di Windows, bell bawaan sistem (BEL dari server).
 * Ukuran font bisa diubah (zoom); satu instance per tab supaya zoom tidak saling memengaruhi.
 * Family font dan pengaturan bell dipakai bersama semua tab.
 */
public final class TerminalSettings extends DefaultSettingsProvider {

    public static final float MIN_SIZE = 8f;
    public static final float MAX_SIZE = 40f;

    private static final List<String> PREFERRED_FONTS =
            List.of("JetBrains Mono", "Cascadia Mono", "Cascadia Code", "Consolas", "DejaVu Sans Mono");

    private final AtomicReference<String> chosenFamily; // null = otomatis; dipakai bersama semua tab
    private final float defaultSize;
    private volatile float fontSize;
    private volatile Font font;
    private final BellSettings bell;

    public TerminalSettings(float fontSize) {
        this(null, fontSize);
    }

    /** @param family nama font pilihan user; null atau tidak terpasang = pilihan otomatis */
    public TerminalSettings(String family, float fontSize) {
        this(new AtomicReference<>(family), fontSize, new BellSettings());
    }

    private TerminalSettings(AtomicReference<String> chosenFamily, float fontSize, BellSettings bell) {
        this.chosenFamily = chosenFamily;
        this.bell = bell;
        this.defaultSize = clamp(fontSize);
        setFontSize(fontSize);
    }

    /** Salinan dengan ukuran default yang sama (untuk tab baru); family font dan pengaturan bell dipakai bersama. */
    public TerminalSettings copy() {
        return new TerminalSettings(chosenFamily, defaultSize, bell);
    }

    public BellSettings bell() {
        return bell;
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
    public boolean audibleBell() {
        return bell.sound();
    }

    @Override
    public boolean copyOnSelect() {
        return false;
    }
}

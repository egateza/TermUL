package dev.egateza.termul.app.terminal;

import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Set;

/**
 * Pengaturan JediTerm: font monospace yang tersedia di Windows, bell bawaan sistem (BEL dari server).
 * Ukuran font bisa diubah (zoom); satu instance per tab supaya zoom tidak saling memengaruhi.
 */
public final class TerminalSettings extends DefaultSettingsProvider {

    public static final float MIN_SIZE = 8f;
    public static final float MAX_SIZE = 40f;

    private static final List<String> PREFERRED_FONTS =
            List.of("JetBrains Mono", "Cascadia Mono", "Cascadia Code", "Consolas", "DejaVu Sans Mono");

    private final String family;
    private final float defaultSize;
    private volatile float fontSize;
    private volatile Font font;

    public TerminalSettings(float fontSize) {
        this(pickFont(), fontSize);
    }

    private TerminalSettings(String family, float fontSize) {
        this.family = family;
        this.defaultSize = clamp(fontSize);
        setFontSize(fontSize);
    }

    /** Salinan dengan ukuran default yang sama (untuk tab baru). */
    public TerminalSettings copy() {
        return new TerminalSettings(family, defaultSize);
    }

    /** @return ukuran baru setelah dibatasi {@link #MIN_SIZE}..{@link #MAX_SIZE} */
    public float setFontSize(float size) {
        float s = clamp(size);
        this.fontSize = s;
        this.font = new Font(family, Font.PLAIN, Math.round(s));
        return s;
    }

    public float defaultSize() {
        return defaultSize;
    }

    static float clamp(float size) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
    }

    private static String pickFont() {
        Set<String> available = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        return PREFERRED_FONTS.stream().filter(available::contains).findFirst().orElse(Font.MONOSPACED);
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
        return true;
    }

    @Override
    public boolean copyOnSelect() {
        return false;
    }
}

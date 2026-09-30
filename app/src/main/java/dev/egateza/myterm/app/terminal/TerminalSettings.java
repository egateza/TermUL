package dev.egateza.myterm.app.terminal;

import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Set;

/** Pengaturan JediTerm: font monospace yang tersedia di Windows, tanpa bell. */
public final class TerminalSettings extends DefaultSettingsProvider {

    private static final List<String> PREFERRED_FONTS =
            List.of("JetBrains Mono", "Cascadia Mono", "Cascadia Code", "Consolas", "DejaVu Sans Mono");

    private final Font font;
    private final float fontSize;

    public TerminalSettings(float fontSize) {
        this.fontSize = fontSize;
        this.font = new Font(pickFont(), Font.PLAIN, Math.round(fontSize));
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
        return false;
    }

    @Override
    public boolean copyOnSelect() {
        return false;
    }
}

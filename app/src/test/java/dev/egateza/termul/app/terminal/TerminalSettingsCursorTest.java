package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jediterm.terminal.CursorShape;
import dev.egateza.termul.core.config.AppConfig;
import org.junit.jupiter.api.Test;

class TerminalSettingsCursorTest {

    @Test
    void bentukDanKedipDariConfig() {
        assertThat(TerminalSettings.cursorShapeOf(AppConfig.CURSOR_BLOCK, true)).isEqualTo(CursorShape.BLINK_BLOCK);
        assertThat(TerminalSettings.cursorShapeOf(AppConfig.CURSOR_BLOCK, false)).isEqualTo(CursorShape.STEADY_BLOCK);
        assertThat(TerminalSettings.cursorShapeOf(AppConfig.CURSOR_UNDERLINE, true)).isEqualTo(CursorShape.BLINK_UNDERLINE);
        assertThat(TerminalSettings.cursorShapeOf(AppConfig.CURSOR_UNDERLINE, false)).isEqualTo(CursorShape.STEADY_UNDERLINE);
        assertThat(TerminalSettings.cursorShapeOf(AppConfig.CURSOR_BAR, true)).isEqualTo(CursorShape.BLINK_VERTICAL_BAR);
        assertThat(TerminalSettings.cursorShapeOf(AppConfig.CURSOR_BAR, false)).isEqualTo(CursorShape.STEADY_VERTICAL_BAR);
        assertThat(TerminalSettings.cursorShapeOf(null, true)).isEqualTo(CursorShape.BLINK_BLOCK);
        assertThat(TerminalSettings.cursorShapeOf("bintang", false)).isEqualTo(CursorShape.STEADY_BLOCK);
    }

    @Test
    void defaultSamaDenganBawaanJediTermDanDipakaiBersamaSemuaTab() {
        var shared = new TerminalSettings(14f);
        var tab = shared.copy();
        assertThat(tab.cursorShape()).isEqualTo(CursorShape.BLINK_BLOCK);

        shared.setCursorShape(CursorShape.STEADY_VERTICAL_BAR);

        assertThat(tab.cursorShape()).as("salinan per tab ikut pengaturan bersama").isEqualTo(CursorShape.STEADY_VERTICAL_BAR);
    }
}

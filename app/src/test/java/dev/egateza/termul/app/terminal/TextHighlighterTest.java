package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.util.CharUtils;
import dev.egateza.termul.core.highlight.HighlightKeyword;
import dev.egateza.termul.core.highlight.HighlightMatcher;
import dev.egateza.termul.core.highlight.HighlightProfile;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TextHighlighterTest {

    private final TerminalTextBuffer buffer = new TerminalTextBuffer(20, 4, new StyleState(), 100);
    private final AtomicInteger repaints = new AtomicInteger();
    private final TextHighlighter highlighter = new TextHighlighter(buffer, repaints::incrementAndGet);

    @AfterEach
    void close() {
        highlighter.close();
    }

    private static TextHighlighter.Rules rules(boolean alternateDisable, HighlightKeyword... keywords) {
        return TextHighlighter.Rules.of(HighlightMatcher.compile(HighlightProfile.create("t", List.of(keywords))),
                alternateDisable);
    }

    private void write(int row, String text) {
        buffer.writeString(0, row + 1, new CharBuffer(text));
    }

    private TextStyle style(int row, int col) {
        return buffer.getLine(row).getStyleAt(col);
    }

    private static TerminalColor bg(TextStyle s) {
        return s == null ? null : s.getBackground();
    }

    @Test
    void keywordDiwarnaiTanpaMengubahTeks() {
        write(0, "a ERROR b");
        TextStyle before = style(0, 0);
        highlighter.setRules(rules(false, HighlightKeyword.of("error", "1")));

        assertThat(highlighter.pass()).isTrue();

        assertThat(buffer.getLine(0).getText()).isEqualTo("a ERROR b");
        assertThat(bg(style(0, 2))).isEqualTo(TerminalColor.index(1));
        assertThat(bg(style(0, 6))).isEqualTo(TerminalColor.index(1));
        assertThat(style(0, 1)).isSameAs(before);
        assertThat(style(0, 7)).isSameAs(before);
        assertThat(highlighter.pass()).isFalse(); // sudah diproses, tidak ditulis ulang
    }

    @Test
    void dimatikanMengembalikanStyleAsli() {
        write(0, "ERROR");
        TextStyle before = style(0, 0);
        highlighter.setRules(rules(false, HighlightKeyword.of("ERROR", "1")));
        highlighter.pass();

        highlighter.setRules(null);
        assertThat(highlighter.pass()).isTrue();

        assertThat(style(0, 0)).isSameAs(before);
        assertThat(buffer.getLine(0).getEntries()).hasSize(1);
    }

    @Test
    void gantiProfilMenggantiWarna() {
        write(0, "WARN x");
        highlighter.setRules(rules(false, HighlightKeyword.of("WARN", "1")));
        highlighter.pass();

        highlighter.setRules(rules(false, HighlightKeyword.of("WARN", "#00ff00"), HighlightKeyword.of("x", "4")));
        highlighter.pass();

        assertThat(bg(style(0, 0))).isEqualTo(TerminalColor.rgb(0, 255, 0));
        assertThat(bg(style(0, 5))).isEqualTo(TerminalColor.index(4));
    }

    @Test
    void keywordTerpotongSoftWrapTetapKena() {
        write(0, "x".repeat(17) + "ERR");
        buffer.setLineWrapped(0, true);
        write(1, "OR y");
        highlighter.setRules(rules(false, HighlightKeyword.of("ERROR", "1")));

        highlighter.pass();

        assertThat(bg(style(0, 17))).isEqualTo(TerminalColor.index(1));
        assertThat(bg(style(0, 19))).isEqualTo(TerminalColor.index(1));
        assertThat(bg(style(1, 1))).isEqualTo(TerminalColor.index(1));
        assertThat(bg(style(1, 2))).isNull();
    }

    @Test
    void karakterLebarDanPenandanyaIkutDiwarnai() {
        write(0, "a 警" + CharUtils.DWC + "告" + CharUtils.DWC + " b");
        highlighter.setRules(rules(false, HighlightKeyword.of("警告", "3")));

        highlighter.pass();

        for (int col = 2; col <= 5; col++) {
            assertThat(bg(style(0, col))).as("kolom %d", col).isEqualTo(TerminalColor.index(3));
        }
        assertThat(bg(style(0, 6))).isNull();
    }

    @Test
    void barisDitulisUlangDenganTeksSamaDiwarnaiLagi() {
        write(0, "ERROR");
        highlighter.setRules(rules(false, HighlightKeyword.of("ERROR", "1")));
        highlighter.pass();

        write(0, "ERROR"); // mis. prompt digambar ulang: style kembali normal

        assertThat(bg(style(0, 0))).isNull();
        assertThat(highlighter.pass()).isTrue();
        assertThat(bg(style(0, 0))).isEqualTo(TerminalColor.index(1));
    }

    @Test
    void alternateScreenBisaDikecualikan() {
        buffer.useAlternateBuffer(true);
        write(0, "ERROR");
        highlighter.setRules(rules(true, HighlightKeyword.of("ERROR", "1")));

        highlighter.pass();

        assertThat(bg(style(0, 0))).isNull();

        highlighter.setRules(rules(false, HighlightKeyword.of("ERROR", "1")));
        highlighter.pass();
        assertThat(bg(style(0, 0))).isEqualTo(TerminalColor.index(1));
    }

    @Test
    void gayaTambahanDanWarnaTeks() {
        var k = new HighlightKeyword("x", true, false, false, false, true, "2", false, "1", true, true, true, null,
                false, false, false, false, null, null);
        write(0, "x");
        highlighter.setRules(rules(false, k));

        highlighter.pass();

        TextStyle s = style(0, 0);
        assertThat(s.getForeground()).isEqualTo(TerminalColor.index(2));
        assertThat(s.getBackground()).isNull(); // background mati
        assertThat(s.hasOption(TextStyle.Option.BOLD)).isTrue();
        assertThat(s.hasOption(TextStyle.Option.ITALIC)).isTrue();
        assertThat(s.hasOption(TextStyle.Option.UNDERLINED)).isTrue();
    }

    @Test
    void tanpaAturanTidakAdaPekerjaan() {
        write(0, "ERROR");

        assertThat(highlighter.pass()).isFalse();
        assertThat(repaints).hasValue(0);
    }

    @Test
    void warnaTabby() {
        assertThat(TextHighlighter.Patch.color("1")).isEqualTo(TerminalColor.index(1));
        assertThat(TextHighlighter.Patch.color("112")).isNotNull();
        assertThat(TextHighlighter.Patch.color("#102030")).isEqualTo(TerminalColor.rgb(0x10, 0x20, 0x30));
        assertThat(TextHighlighter.Patch.color("bukan")).isNull();
    }
}

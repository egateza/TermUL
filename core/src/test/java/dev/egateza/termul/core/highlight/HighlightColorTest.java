package dev.egateza.termul.core.highlight;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.highlight.HighlightColor.Indexed;
import dev.egateza.termul.core.highlight.HighlightColor.Rgb;
import org.junit.jupiter.api.Test;

class HighlightColorTest {

    @Test
    void indeksAnsiDanXterm256() {
        assertThat(HighlightColor.parse("3")).contains(new Indexed(3));
        assertThat(HighlightColor.parse(" 112 ")).contains(new Indexed(112));
        assertThat(HighlightColor.parse("255")).contains(new Indexed(255));
        assertThat(HighlightColor.parse("256")).isEmpty();
        assertThat(HighlightColor.parse("0003")).isEmpty();
    }

    @Test
    void hexDanNamaCss() {
        assertThat(HighlightColor.parse("#FF8800")).contains(new Rgb(0xFF8800));
        assertThat(HighlightColor.parse("#f80")).contains(new Rgb(0xFF8800));
        assertThat(HighlightColor.parse("Red")).contains(new Rgb(0xFF0000));
        assertThat(HighlightColor.parse("#12345")).isEmpty();
        assertThat(HighlightColor.parse("#zzzzzz")).isEmpty();
        assertThat(HighlightColor.parse("merah")).isEmpty();
        assertThat(HighlightColor.parse("")).isEmpty();
        assertThat(HighlightColor.parse(null)).isEmpty();
    }

    @Test
    void formatBolakBalik() {
        assertThat(HighlightColor.format(new Indexed(6))).isEqualTo("6");
        assertThat(HighlightColor.format(new Rgb(0xABCDEF))).isEqualTo("#abcdef");
        assertThat(HighlightColor.parse(HighlightColor.format(new Rgb(0x0000FF)))).contains(new Rgb(0x0000FF));
    }
}

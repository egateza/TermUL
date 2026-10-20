package dev.egateza.termul.core.highlight;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.highlight.HighlightMatcher.Span;
import java.util.List;
import org.junit.jupiter.api.Test;

class HighlightMatcherTest {

    private static HighlightMatcher compile(HighlightKeyword... keywords) {
        return HighlightMatcher.compile(HighlightProfile.create("t", List.of(keywords)));
    }

    private static HighlightKeyword regex(String text) {
        return HighlightKeyword.of(text, "1").withRegExp(true);
    }

    @Test
    void teksBiasaTidakCaseSensitiveSecaraDefault() {
        var m = compile(HighlightKeyword.of("error", "1"));

        assertThat(m.match("ERROR: x error Error")).containsExactly(
                new Span(0, 5, 0), new Span(9, 14, 0), new Span(15, 20, 0));
    }

    @Test
    void caseSensitiveDanKarakterRegexDiTeksBiasa() {
        var m = compile(HighlightKeyword.of("Error", "1").withCaseSensitive(true), HighlightKeyword.of("a.b", "2"));

        assertThat(m.match("ERROR Error axb a.b")).containsExactly(new Span(6, 11, 0), new Span(16, 19, 1));
    }

    @Test
    void keywordDiAtasMenangSaatTumpangTindih() {
        var m = compile(HighlightKeyword.of("WARN", "3"), regex("\\w+ING"));

        // WARNING: "WARN" milik keyword 0, sisanya "ING" milik keyword 1
        assertThat(m.match("WARNING")).containsExactly(new Span(0, 4, 0), new Span(4, 7, 1));
    }

    @Test
    void grupRegexNomorDanNama() {
        var byNumber = new HighlightKeyword("user=(\\w+)", true, false, true, false, false, null, true, "1", false,
                false, false, null, false, false, false, true, "1", null);
        var byName = new HighlightKeyword("id=(?<id>\\d+)", true, false, true, false, false, null, true, "1", false,
                false, false, null, false, false, false, true, "id", null);

        assertThat(compile(byNumber).match("login user=ega ok")).containsExactly(new Span(11, 14, 0));
        assertThat(compile(byName).match("id=42")).containsExactly(new Span(3, 5, 0));
    }

    @Test
    void keywordMatiKosongJsDanRegexRusakDilewati() {
        var js = new HighlightKeyword("return 'x'", true, true, false, false, false, null, true, "1", false, false,
                false, null, false, false, false, false, null, null);
        var m = compile(HighlightKeyword.of("off", "1").withEnabled(false), HighlightKeyword.of("", "1"), js,
                regex("(rusak"), HighlightKeyword.of("ok", "2"));

        assertThat(m.match("off rusak return 'x' ok")).containsExactly(new Span(21, 23, 4));
    }

    @Test
    void validasiRegexDanGrup() {
        assertThat(HighlightMatcher.validate(regex("(rusak"))).isPresent();
        assertThat(HighlightMatcher.validate(regex("ok(\\d)"))).isEmpty();
        var missingGroup = new HighlightKeyword("a(b)", true, false, true, false, false, null, true, "1", false,
                false, false, null, false, false, false, true, "2", null);
        assertThat(HighlightMatcher.validate(missingGroup)).hasValueSatisfying(e -> assertThat(e).contains("2"));
        assertThat(HighlightMatcher.validate(HighlightKeyword.of("(teks biasa", "1"))).isEmpty();
    }

    @Test
    void regexLambatDihentikanTanpaMenggantung() {
        var m = compile(regex("(a+)+$"), HighlightKeyword.of("tail", "2"));
        String evil = "a".repeat(40) + "!";

        long start = System.nanoTime();
        List<Span> spans = m.match(evil, 20_000_000L);

        assertThat(System.nanoTime() - start).isLessThan(2_000_000_000L);
        assertThat(spans).isEmpty();
    }

    @Test
    void barisPanjangDipotong() {
        var m = compile(HighlightKeyword.of("x", "1"));
        String line = "-".repeat(HighlightMatcher.MAX_LINE) + "x";

        assertThat(m.match(line)).isEmpty();
    }
}

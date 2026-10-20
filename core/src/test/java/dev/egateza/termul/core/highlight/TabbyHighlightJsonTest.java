package dev.egateza.termul.core.highlight;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TabbyHighlightJsonTest {

    /** Hasil Export dari Tabby (tabby-highlight). */
    private static final String TABBY = """
            {"id":"60606be0-c0ff-42bc-bf77-de8a2435447f","name":"Default","keywords":[\
            {"text":"ADVICE","enabled":true,"background":true,"backgroundColor":"3","foregroundColor":"0"},\
            {"text":"PAYMENT","enabled":true,"background":true,"backgroundColor":"112","foregroundColor":"0"},\
            {"text":"ERROR","enabled":true,"background":true,"backgroundColor":"1"},\
            {"text":"警告","enabled":false,"background":true,"backgroundColor":"3"}]}""";

    @Test
    void importProfilTunggalDariTabby() throws IOException {
        List<HighlightProfile> profiles = TabbyHighlightJson.parse(TABBY.getBytes(UTF_8));

        assertThat(profiles).hasSize(1);
        HighlightProfile p = profiles.getFirst();
        assertThat(p.id()).isEqualTo("60606be0-c0ff-42bc-bf77-de8a2435447f");
        assertThat(p.name()).isEqualTo("Default");
        assertThat(p.keywords()).extracting(HighlightKeyword::text).containsExactly("ADVICE", "PAYMENT", "ERROR", "警告");
        HighlightKeyword advice = p.keywords().getFirst();
        assertThat(advice.background()).isTrue();
        assertThat(advice.backgroundColor()).isEqualTo("3");
        assertThat(advice.foreground()).isFalse(); // foregroundColor ada, tapi saklarnya mati (sama seperti Tabby)
        assertThat(advice.foregroundColor()).isEqualTo("0");
        assertThat(advice.regExp()).isFalse();
        assertThat(p.keywords().get(3).enabled()).isFalse();
    }

    @Test
    void importArrayDanConfigPlugin() throws IOException {
        String array = "[" + TABBY + ",{\"name\":\"Lain\",\"keywords\":[]}]";
        String config = "{\"highlightPlugin\":{\"highlightEnabled\":true,\"highlightProfiles\":" + array + "}}";

        assertThat(TabbyHighlightJson.parse(array.getBytes(UTF_8))).extracting(HighlightProfile::name)
                .containsExactly("Default", "Lain");
        assertThat(TabbyHighlightJson.parse(config.getBytes(UTF_8))).hasSize(2);
        assertThat(TabbyHighlightJson.parse(array.getBytes(UTF_8)).get(1).id()).isNotBlank(); // id baru
    }

    @Test
    void fieldTambahanTabbyIkutTerbaca() throws IOException {
        String json = """
                {"name":"x","keywords":[{"text":"id=(\\\\d+)","enabled":true,"isRegExp":true,"isCaseSensitive":true,\
                "foreground":true,"foregroundColor":"#ff0000","background":false,"backgroundColor":"1","bold":true,\
                "underline":true,"underlineStyle":"3","isMatchGroup":true,"matchGroup":"1","remark":"catatan",\
                "isJS":false,"unknownField":42}]}""";

        HighlightKeyword k = TabbyHighlightJson.parse(json.getBytes(UTF_8)).getFirst().keywords().getFirst();

        assertThat(k.text()).isEqualTo("id=(\\d+)");
        assertThat(k.regExp()).isTrue();
        assertThat(k.caseSensitive()).isTrue();
        assertThat(k.foreground()).isTrue();
        assertThat(k.foregroundColor()).isEqualTo("#ff0000");
        assertThat(k.bold()).isTrue();
        assertThat(k.underlineStyle()).isEqualTo("3");
        assertThat(k.matchGroup()).isTrue();
        assertThat(k.matchGroupName()).isEqualTo("1");
        assertThat(k.remark()).isEqualTo("catatan");
    }

    @Test
    void exportBisaDiimporUlangDenganNamaFieldTabby() throws IOException {
        HighlightProfile original = TabbyHighlightJson.parse(TABBY.getBytes(UTF_8)).getFirst();

        String json = new String(TabbyHighlightJson.export(original), UTF_8);

        assertThat(json).contains("\"isRegExp\"", "\"isCaseSensitive\"", "\"backgroundColor\" : \"112\"")
                .doesNotContain("\"usable\"", "\"regExp\"", "\"remark\"");
        assertThat(TabbyHighlightJson.parse(json.getBytes(UTF_8))).containsExactly(original);
    }

    @Test
    void fileBukanProfilDitolak() {
        assertThatThrownBy(() -> TabbyHighlightJson.parse("{\"name\":\"x\"}".getBytes(UTF_8)))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> TabbyHighlightJson.parse("bukan json".getBytes(UTF_8)))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> TabbyHighlightJson.parse("[]".getBytes(UTF_8))).isInstanceOf(IOException.class);
    }
}

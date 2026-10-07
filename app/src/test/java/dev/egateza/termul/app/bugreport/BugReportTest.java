package dev.egateza.termul.app.bugreport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.i18n.I18n;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BugReportTest {

    private static final SystemInfo SYSTEM = new SystemInfo("0.1.163 (6ef8cee)", "Windows 11 10.0 (amd64)",
            "25+36-LTS", "id", 1);

    @BeforeEach
    void bahasaIndonesia() {
        I18n.use("id");
    }

    @Test
    void bodyBerisiBagianYangDiisiDanInfoSistem() {
        var report = new BugReport("  Beranda kosong ", "Daftar host tidak muncul", "", SYSTEM);

        assertThat(report.title()).isEqualTo("Beranda kosong");
        assertThat(report.body()).isEqualTo("""
                ### Deskripsi

                Daftar host tidak muncul

                ### Info sistem

                - TermUL: 0.1.163 (6ef8cee)
                - OS: Windows 11 10.0 (amd64)
                - Java: 25+36-LTS
                - Language: id
                - Update generation: 1""");
    }

    @Test
    void infoSistemTidakIkutKalauTidakDipilih() {
        var report = new BugReport("Judul", "Deskripsi", "1. Buka host", null);

        assertThat(report.body()).doesNotContain("Info sistem").contains("### Langkah reproduksi\n\n1. Buka host");
    }

    @Test
    void judulKosongTidakBisaDikirim() {
        assertThat(new BugReport("  ", "isi", "", SYSTEM).isSubmittable()).isFalse();
        assertThat(new BugReport(null, null, null, null).isSubmittable()).isFalse();
        assertThat(new BugReport("x", "", "", null).isSubmittable()).isTrue();
    }

    @Test
    void linkMengarahKeFormIssueRepoDenganParameterTerenkode() {
        var link = new BugReport("Error & crash #1", "a+b = c?", "", SYSTEM).link();

        assertThat(link.truncated()).isFalse();
        assertThat(link.uri().toString()).startsWith("https://github.com/egateza/TermUL/issues/new?")
                .doesNotContain("+").doesNotContain(" ");
        var query = query(link.uri().getRawQuery());
        assertThat(query).containsEntry("labels", "bug").containsEntry("title", "Error & crash #1");
        assertThat(query.get("body")).contains("a+b = c?").contains("- TermUL: 0.1.163");
    }

    @Test
    void isiPanjangDipotongTapiInfoSistemTetapUtuh() {
        var longText = "baris log yang panjang sekali\n".repeat(1000);
        var link = new BugReport("Judul", longText, "", SYSTEM).link();

        assertThat(link.truncated()).isTrue();
        assertThat(link.uri().toString().length()).isLessThanOrEqualTo(BugReport.MAX_URI_LENGTH);
        assertThat(link.body()).contains(BugReport.TRUNCATED_MARK).endsWith("- Update generation: 1");
        assertThat(query(link.uri().getRawQuery()).get("body")).isEqualTo(link.body());
    }

    @Test
    void judulPanjangDipotongTanpaMemecahEmoji() {
        var title = "a".repeat(BugReport.MAX_TITLE - 1) + "😀😀";
        var decoded = query(new BugReport(title, "", "", null).link().uri().getRawQuery()).get("title");

        assertThat(decoded).isEqualTo("a".repeat(BugReport.MAX_TITLE - 1));
    }

    private static Map<String, String> query(String raw) {
        var map = new LinkedHashMap<String, String>();
        for (var pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            map.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return map;
    }
}

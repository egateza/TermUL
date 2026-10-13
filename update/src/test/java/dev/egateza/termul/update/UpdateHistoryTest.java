package dev.egateza.termul.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdateHistoryTest {

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    @TempDir
    Path tmp;

    private UpdateHistory history() {
        return new UpdateHistory(tmp.resolve("config").resolve("update-history.json"));
    }

    private static UpdateHistory.Entry entry(String version, int day) {
        return new UpdateHistory.Entry(ReleaseVersion.parse(version), T0.plusSeconds(day * 86_400L), "- rilis " + version);
    }

    @Test
    void fileBelumAdaBerartiKosong() {
        assertThat(history().load()).isEmpty();
    }

    @Test
    void terbaruDiDepanDanTersimpanKeDisk() throws Exception {
        history().record(entry("0.1.140", 0));
        history().record(entry("0.1.145", 3));

        assertThat(history().load()).extracting(e -> e.version().toString()).containsExactly("0.1.145", "0.1.140");
        assertThat(history().load().getFirst().notes()).isEqualTo("- rilis 0.1.145");
    }

    @Test
    void versiYangSamaHanyaDicatatSekali() throws Exception {
        assertThat(history().record(entry("0.1.140", 0))).isTrue();
        assertThat(history().record(entry("0.1.140", 5))).isFalse();

        assertThat(history().load()).hasSize(1);
        assertThat(history().contains(ReleaseVersion.parse("0.1.140"))).isTrue();
    }

    @Test
    void hanyaLimaRilisTerakhirDisimpan() throws Exception {
        for (int i = 0; i < 7; i++) {
            history().record(entry("0.1." + (140 + i), i));
        }

        assertThat(history().load()).hasSize(UpdateHistory.MAX_ENTRIES)
                .extracting(e -> e.version().toString())
                .containsExactly("0.1.146", "0.1.145", "0.1.144", "0.1.143", "0.1.142");
    }

    @Test
    void fileRusakDianggapKosongLaluDitimpa() throws Exception {
        Path file = tmp.resolve("config").resolve("update-history.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{bukan json");

        assertThat(history().load()).isEmpty();
        history().record(entry("0.1.140", 0));
        assertThat(history().load()).hasSize(1);
    }

    @Test
    void entriTidakValidDilewati() throws Exception {
        Path file = tmp.resolve("config").resolve("update-history.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {"format":1,"entries":[
                  {"version":"dev","appliedAt":"2026-10-01T08:00:00Z","notes":"x"},
                  {"version":"0.1.141","appliedAt":"kemarin","notes":"x"},
                  {"version":"0.1.142","appliedAt":"2026-10-02T08:00:00Z"}
                ]}""");

        assertThat(history().load()).singleElement().satisfies(e -> {
            assertThat(e.version()).isEqualTo(ReleaseVersion.parse("0.1.142"));
            assertThat(e.notes()).isEmpty();
        });
    }
}

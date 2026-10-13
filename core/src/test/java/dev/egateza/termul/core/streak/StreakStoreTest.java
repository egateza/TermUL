package dev.egateza.termul.core.streak;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreakStoreTest {

    @TempDir
    Path tmp;

    private Path file() {
        return tmp.resolve("streak.json");
    }

    @Test
    void fileBelumAdaBerartiKosong() {
        assertThat(new StreakStore(file()).load()).isEqualTo(Streak.NONE);
    }

    @Test
    void tersimpanDanTerbacaLagi() throws Exception {
        var streak = new Streak(23, LocalDate.of(2026, 10, 13));
        new StreakStore(file()).save(streak);

        assertThat(new StreakStore(file()).load()).isEqualTo(streak);
        assertThat(Files.readString(file())).contains("\"lastDay\" : \"2026-10-13\"");
    }

    @Test
    void fileRusakDisisihkan() throws Exception {
        Files.writeString(file(), "{\"count\": 5, \"lastDay\": \"kemarin\"}");

        assertThat(new StreakStore(file()).load()).isEqualTo(Streak.NONE);
        assertThat(file()).doesNotExist();
        try (var files = Files.list(tmp)) {
            assertThat(files).anyMatch(p -> p.getFileName().toString().startsWith("streak.json.corrupt-"));
        }
    }
}

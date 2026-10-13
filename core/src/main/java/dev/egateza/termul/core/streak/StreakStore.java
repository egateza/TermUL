package dev.egateza.termul.core.streak;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.egateza.termul.core.io.AtomicFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streak tersimpan di {@code streak.json} (config dir), ditulis atomic. File rusak disisihkan dengan nama
 * {@code streak.json.corrupt-<waktu>} lalu mulai dari streak kosong. Disk I/O: panggil di luar EDT.
 */
public final class StreakStore {

    private static final Logger log = LoggerFactory.getLogger(StreakStore.class);

    private final Path file;
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** Isi file di disk; tanggal ISO ({@code 2026-10-13}) supaya tidak butuh modul Jackson java.time. */
    record FileContent(int format, int count, String lastDay) {
    }

    public StreakStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public synchronized Streak load() {
        try {
            var content = mapper.readValue(Files.readAllBytes(file), FileContent.class);
            var streak = new Streak(Math.max(0, content.count()),
                    content.lastDay() == null ? null : LocalDate.parse(content.lastDay()));
            log.info("Streak {} hari (terakhir {})", streak.count(), streak.lastDay());
            return streak;
        } catch (NoSuchFileException e) {
            return Streak.NONE;
        } catch (IOException | DateTimeParseException | IllegalArgumentException e) {
            quarantine(e);
            return Streak.NONE;
        }
    }

    public synchronized void save(Streak streak) {
        var lastDay = streak.lastDay() == null ? null : streak.lastDay().toString();
        try {
            AtomicFiles.write(file, mapper.writeValueAsBytes(new FileContent(1, streak.count(), lastDay)));
        } catch (IOException e) {
            throw new UncheckedIOException("Streak tidak bisa disimpan ke " + file, e);
        }
    }

    private void quarantine(Exception cause) {
        Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + Instant.now().toEpochMilli());
        try {
            Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            log.warn("File streak rusak ({}); disisihkan ke {} dan mulai dari awal", cause.getMessage(), aside);
        } catch (IOException e) {
            log.warn("File streak rusak ({}) dan tidak bisa disisihkan: {}", cause.getMessage(), e.toString());
        }
    }
}

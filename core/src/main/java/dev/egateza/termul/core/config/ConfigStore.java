package dev.egateza.termul.core.config;

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
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Membaca/menulis {@code config.json}. File rusak = pakai default (tanpa menimpa file lama). */
public final class ConfigStore {

    private static final Logger log = LoggerFactory.getLogger(ConfigStore.class);

    private final Path file;
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private volatile AppConfig current = AppConfig.defaults();

    public ConfigStore(Path file) {
        this.file = Objects.requireNonNull(file);
    }

    public synchronized AppConfig load() {
        try {
            current = mapper.readValue(Files.readAllBytes(file), AppConfig.class);
        } catch (NoSuchFileException e) {
            current = AppConfig.defaults();
        } catch (IOException e) {
            log.warn("config.json tidak bisa dibaca, memakai default: {}", e.getMessage());
            current = AppConfig.defaults();
        }
        return current;
    }

    public AppConfig current() {
        return current;
    }

    public synchronized void save(AppConfig config) {
        try {
            AtomicFiles.write(file, mapper.writeValueAsBytes(config));
            current = config;
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal menyimpan " + file, e);
        }
    }
}

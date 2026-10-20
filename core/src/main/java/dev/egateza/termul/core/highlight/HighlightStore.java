package dev.egateza.termul.core.highlight;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pengaturan highlight teks di {@code highlight.json} (config dir), ditulis atomic. Tidak berisi secret. File rusak
 * disisihkan dengan nama {@code highlight.json.corrupt-<waktu>} dan pengaturan kembali ke bawaan. Thread-safe; tulis/baca
 * disk di luar EDT, {@link #snapshot()} boleh dari mana saja.
 */
public final class HighlightStore {

    private static final Logger log = LoggerFactory.getLogger(HighlightStore.class);

    /** Isi file di disk. */
    record FileContent(int format, boolean enabled, boolean alternateDisable, String globalProfileId,
                       List<HighlightProfile> profiles, Map<UUID, String> hosts, Map<String, String> groups,
                       Map<UUID, String> locals, String allLocalsProfileId) {

        static FileContent of(HighlightSettings s) {
            return new FileContent(1, s.enabled(), s.alternateDisable(), s.globalProfileId(), s.profiles(), s.hosts(),
                    s.groups(), s.locals(), s.allLocalsProfileId());
        }

        HighlightSettings toSettings() {
            return new HighlightSettings(enabled, alternateDisable, globalProfileId,
                    profiles == null ? List.of() : profiles, hosts == null ? Map.of() : hosts,
                    groups == null ? Map.of() : groups, locals == null ? Map.of() : locals, allLocalsProfileId);
        }
    }

    private final Path file;
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final Object lock = new Object();
    private final List<Consumer<HighlightSettings>> listeners = new CopyOnWriteArrayList<>();
    private volatile HighlightSettings state = HighlightSettings.defaults(); // ditulis hanya saat memegang lock

    public HighlightStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public HighlightSettings load() {
        synchronized (lock) {
            try {
                state = mapper.readValue(Files.readAllBytes(file), FileContent.class).toSettings();
                log.info("Memuat {} profil highlight dari {}", state.profiles().size(), file);
            } catch (NoSuchFileException e) {
                state = HighlightSettings.defaults();
            } catch (IOException | IllegalArgumentException | NullPointerException e) {
                state = HighlightSettings.defaults();
                quarantine(e);
            }
            return state;
        }
    }

    public HighlightSettings snapshot() {
        return state;
    }

    /** Ganti seluruh pengaturan (dialog Highlight). */
    public void save(HighlightSettings settings) {
        update(s -> settings);
    }

    public void update(UnaryOperator<HighlightSettings> change) {
        HighlightSettings next;
        synchronized (lock) {
            next = change.apply(state);
            try {
                AtomicFiles.write(file, mapper.writeValueAsBytes(FileContent.of(next)));
            } catch (IOException e) {
                throw new UncheckedIOException("Pengaturan highlight tidak bisa disimpan ke " + file, e);
            }
            state = next;
        }
        listeners.forEach(l -> l.accept(next));
    }

    /** @param listener dipanggil (di thread penulis) setelah isi berubah dan tersimpan */
    public void addListener(Consumer<HighlightSettings> listener) {
        listeners.add(listener);
    }

    private void quarantine(Exception cause) {
        Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + Instant.now().toEpochMilli());
        try {
            Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            log.warn("File highlight rusak ({}); disisihkan ke {}", cause.getMessage(), aside);
        } catch (IOException e) {
            log.warn("File highlight rusak ({}) dan tidak bisa disisihkan: {}", cause.getMessage(), e.toString());
        }
    }
}

package dev.egateza.termul.core.shortcut;

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
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shortcut yang diganti user di {@code shortcuts.json} (config dir), ditulis atomic. Hanya override yang disimpan
 * (id aksi → kombinasi, mis. {@code "tab.new": "mod+shift+T"}; string kosong = tanpa shortcut); aksi lain mengikuti
 * default aplikasi, jadi default baru di versi berikutnya ikut terpakai. File rusak disisihkan ke
 * {@code shortcuts.json.corrupt-<waktu>} dan semua shortcut kembali ke default. Thread-safe; baca/tulis disk di luar
 * EDT, {@link #snapshot()} boleh dari mana saja.
 */
public final class ShortcutStore {

    private static final Logger log = LoggerFactory.getLogger(ShortcutStore.class);

    /** Isi file di disk. */
    record FileContent(int format, Map<String, String> overrides) {
    }

    private final Path file;
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final Object lock = new Object();
    private final List<Consumer<Map<String, String>>> listeners = new CopyOnWriteArrayList<>();
    private volatile Map<String, String> state = Map.of(); // immutable; ditulis hanya saat memegang lock

    public ShortcutStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public Map<String, String> load() {
        synchronized (lock) {
            try {
                var content = mapper.readValue(Files.readAllBytes(file), FileContent.class);
                state = clean(content.overrides());
                log.info("Memuat {} shortcut custom dari {}", state.size(), file);
            } catch (NoSuchFileException e) {
                state = Map.of();
            } catch (IOException | RuntimeException e) {
                state = Map.of();
                quarantine(e);
            }
            return state;
        }
    }

    /** Override saat ini (immutable). */
    public Map<String, String> snapshot() {
        return state;
    }

    /** Ganti semua override (dialog Shortcut). Map kosong = semua kembali ke default. */
    public void save(Map<String, String> overrides) {
        Map<String, String> next = clean(overrides);
        synchronized (lock) {
            try {
                AtomicFiles.write(file, mapper.writeValueAsBytes(new FileContent(1, next)));
            } catch (IOException e) {
                throw new UncheckedIOException("Pengaturan shortcut tidak bisa disimpan ke " + file, e);
            }
            state = next;
        }
        listeners.forEach(l -> l.accept(next));
    }

    /** @param listener dipanggil (di thread penulis) setelah isi berubah dan tersimpan */
    public void addListener(Consumer<Map<String, String>> listener) {
        listeners.add(listener);
    }

    private static Map<String, String> clean(Map<String, String> in) {
        var out = new TreeMap<String, String>();
        if (in != null) {
            in.forEach((k, v) -> {
                if (k != null && !k.isBlank()) {
                    out.put(k.strip(), v == null ? "" : v.strip());
                }
            });
        }
        return Map.copyOf(out);
    }

    private void quarantine(Exception cause) {
        Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + Instant.now().toEpochMilli());
        try {
            Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            log.warn("File shortcut rusak ({}); disisihkan ke {}", cause.getMessage(), aside);
        } catch (IOException e) {
            log.warn("File shortcut rusak ({}) dan tidak bisa disisihkan: {}", cause.getMessage(), e.toString());
        }
    }
}

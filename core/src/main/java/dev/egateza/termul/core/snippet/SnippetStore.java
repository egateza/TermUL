package dev.egateza.termul.core.snippet;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Snippet tersimpan di {@code snippets.json} (config dir), ditulis atomic. File rusak disisihkan dengan nama
 * {@code snippets.json.corrupt-<waktu>} lalu mulai dari daftar kosong, supaya isi lamanya tidak hilang dan aplikasi tetap
 * jalan. Thread-safe; tulis/baca disk di luar EDT, {@link #snapshot()} boleh dari mana saja.
 */
public final class SnippetStore {

    private static final Logger log = LoggerFactory.getLogger(SnippetStore.class);
    private static final Comparator<Snippet> ORDER = Comparator
            .comparing((Snippet s) -> s.name().toLowerCase(java.util.Locale.ROOT))
            .thenComparing(Snippet::group);

    private final Path file;
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final Object lock = new Object();
    private final List<Consumer<List<Snippet>>> listeners = new CopyOnWriteArrayList<>();
    private volatile List<Snippet> snippets = List.of(); // ditulis hanya saat memegang lock; urut nama

    /** Isi file di disk. */
    record FileContent(int format, List<Snippet> snippets) {
    }

    public SnippetStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public List<Snippet> load() {
        synchronized (lock) {
            try {
                FileContent content = mapper.readValue(Files.readAllBytes(file), new TypeReference<FileContent>() { });
                snippets = sorted(content.snippets() == null ? List.of() : content.snippets());
                log.info("Memuat {} snippet dari {}", snippets.size(), file);
            } catch (NoSuchFileException e) {
                snippets = List.of();
            } catch (IOException | IllegalArgumentException | NullPointerException e) {
                snippets = List.of();
                quarantine(e);
            }
            return snippets;
        }
    }

    public List<Snippet> snapshot() {
        return snippets;
    }

    /** Snippet untuk host di grup {@code hostGroup}: global + grup itu + grup induknya, urut nama. */
    public List<Snippet> forGroup(String hostGroup) {
        return snippets.stream().filter(s -> s.appliesTo(hostGroup)).toList();
    }

    /** Tambah atau ganti (berdasarkan id). */
    public void save(Snippet snippet) {
        mutate(list -> {
            list.removeIf(s -> s.id().equals(snippet.id()));
            list.add(snippet);
        });
    }

    public void delete(UUID id) {
        mutate(list -> list.removeIf(s -> s.id().equals(id)));
    }

    /** @param listener dipanggil (di thread penulis) setelah daftar berubah dan tersimpan */
    public void addListener(Consumer<List<Snippet>> listener) {
        listeners.add(listener);
    }

    private void mutate(Consumer<List<Snippet>> change) {
        List<Snippet> next;
        synchronized (lock) {
            var list = new ArrayList<>(snippets);
            change.accept(list);
            next = sorted(list);
            try {
                AtomicFiles.write(file, mapper.writeValueAsBytes(new FileContent(1, next)));
            } catch (IOException e) {
                throw new UncheckedIOException("Snippet tidak bisa disimpan ke " + file, e);
            }
            snippets = next;
        }
        listeners.forEach(l -> l.accept(next));
    }

    private void quarantine(Exception cause) {
        Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + Instant.now().toEpochMilli());
        try {
            Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            log.warn("File snippet rusak ({}); disisihkan ke {} dan mulai dari daftar kosong", cause.getMessage(), aside);
        } catch (IOException e) {
            log.warn("File snippet rusak ({}) dan tidak bisa disisihkan: {}", cause.getMessage(), e.toString());
        }
    }

    private static List<Snippet> sorted(List<Snippet> list) {
        return list.stream().sorted(ORDER).toList();
    }
}

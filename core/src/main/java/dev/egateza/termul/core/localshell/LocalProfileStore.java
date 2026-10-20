package dev.egateza.termul.core.localshell;

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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Profil terminal lokal buatan user dan pilihan default di {@code local-profiles.json} (config dir), ditulis atomic.
 * Shell yang terdeteksi otomatis tidak disimpan di sini (dideteksi ulang setiap start). File rusak disisihkan dengan
 * nama {@code local-profiles.json.corrupt-<waktu>}. Thread-safe; tulis/baca disk di luar EDT, {@link #snapshot()} boleh
 * dari mana saja.
 */
public final class LocalProfileStore {

    private static final Logger log = LoggerFactory.getLogger(LocalProfileStore.class);
    private static final Comparator<LocalProfile> ORDER =
            Comparator.comparing((LocalProfile p) -> p.name().toLowerCase(Locale.ROOT)).thenComparing(LocalProfile::id);

    /**
     * Isi store.
     *
     * @param defaultId profil yang dibuka Ctrl+Shift+O / ⇧⌘O (profil buatan user atau shell terdeteksi); null = otomatis
     */
    public record State(List<LocalProfile> profiles, UUID defaultId) {
        public State {
            profiles = List.copyOf(profiles);
        }

        static final State EMPTY = new State(List.of(), null);
    }

    /** Isi file di disk. */
    record FileContent(int format, UUID defaultId, List<LocalProfile> profiles) {
    }

    private final Path file;
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final Object lock = new Object();
    private final List<Consumer<State>> listeners = new CopyOnWriteArrayList<>();
    private volatile State state = State.EMPTY; // ditulis hanya saat memegang lock

    public LocalProfileStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public State load() {
        synchronized (lock) {
            try {
                FileContent content = mapper.readValue(Files.readAllBytes(file), new TypeReference<FileContent>() { });
                state = new State(sorted(content.profiles() == null ? List.of() : content.profiles()),
                        content.defaultId());
                log.info("Memuat {} profil terminal lokal dari {}", state.profiles().size(), file);
            } catch (NoSuchFileException e) {
                state = State.EMPTY;
            } catch (IOException | IllegalArgumentException | NullPointerException e) {
                state = State.EMPTY;
                quarantine(e);
            }
            return state;
        }
    }

    public State snapshot() {
        return state;
    }

    /** Tambah atau ganti (berdasarkan id). */
    public void save(LocalProfile profile) {
        mutate(s -> {
            var list = new ArrayList<>(s.profiles());
            list.removeIf(p -> p.id().equals(profile.id()));
            list.add(profile);
            return new State(list, s.defaultId());
        });
    }

    /** Hapus profil; kalau profil itu default, default kembali otomatis. */
    public void delete(UUID id) {
        mutate(s -> new State(s.profiles().stream().filter(p -> !p.id().equals(id)).toList(),
                id.equals(s.defaultId()) ? null : s.defaultId()));
    }

    /** @param id profil buatan user atau id shell terdeteksi; null = otomatis */
    public void setDefault(UUID id) {
        mutate(s -> new State(s.profiles(), id));
    }

    /** @param listener dipanggil (di thread penulis) setelah isi berubah dan tersimpan */
    public void addListener(Consumer<State> listener) {
        listeners.add(listener);
    }

    private void mutate(java.util.function.UnaryOperator<State> change) {
        State next;
        synchronized (lock) {
            State changed = change.apply(state);
            next = new State(sorted(changed.profiles()), changed.defaultId());
            try {
                AtomicFiles.write(file, mapper.writeValueAsBytes(new FileContent(1, next.defaultId(), next.profiles())));
            } catch (IOException e) {
                throw new UncheckedIOException("Profil terminal lokal tidak bisa disimpan ke " + file, e);
            }
            state = next;
        }
        listeners.forEach(l -> l.accept(next));
    }

    private void quarantine(Exception cause) {
        Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + Instant.now().toEpochMilli());
        try {
            Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            log.warn("File profil terminal lokal rusak ({}); disisihkan ke {}", cause.getMessage(), aside);
        } catch (IOException e) {
            log.warn("File profil terminal lokal rusak ({}) dan tidak bisa disisihkan: {}", cause.getMessage(),
                    e.toString());
        }
    }

    private static List<LocalProfile> sorted(List<LocalProfile> list) {
        return list.stream().sorted(ORDER).toList();
    }
}

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
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
     * @param disabled  id profil/shell terdeteksi yang dinonaktifkan: tidak ditawarkan di tree, Tab baru, dan menu
     *                  Explorer, tetapi tetap ada di Pengaturan → Profil terminal lokal
     */
    public record State(List<LocalProfile> profiles, UUID defaultId, Set<UUID> disabled) {
        public State {
            profiles = List.copyOf(profiles);
            disabled = disabled == null ? Set.of() : Set.copyOf(disabled);
        }

        public State(List<LocalProfile> profiles, UUID defaultId) {
            this(profiles, defaultId, Set.of());
        }

        public boolean isDisabled(UUID id) {
            return disabled.contains(id);
        }

        static final State EMPTY = new State(List.of(), null);
    }

    /** Isi file di disk; {@code disabled} terurut supaya file stabil. */
    record FileContent(int format, UUID defaultId, List<LocalProfile> profiles, List<UUID> disabled) {
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
                        content.defaultId(),
                        content.disabled() == null ? Set.of() : Set.copyOf(content.disabled()));
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
            return new State(list, s.defaultId(), s.disabled());
        });
    }

    /** Hapus profil; kalau profil itu default, default kembali otomatis. */
    public void delete(UUID id) {
        mutate(s -> new State(s.profiles().stream().filter(p -> !p.id().equals(id)).toList(),
                id.equals(s.defaultId()) ? null : s.defaultId(), without(s.disabled(), id)));
    }

    /** @param id profil buatan user atau id shell terdeteksi; null = otomatis. Profil nonaktif diaktifkan lagi. */
    public void setDefault(UUID id) {
        mutate(s -> new State(s.profiles(), id, without(s.disabled(), id)));
    }

    /**
     * Nonaktifkan atau aktifkan lagi profil/shell terdeteksi. Profil default yang dinonaktifkan membuat default kembali
     * otomatis. Tidak ada yang dihapus, termasuk shell di sistem.
     */
    public void setDisabled(UUID id, boolean disabled) {
        Objects.requireNonNull(id, "id");
        mutate(s -> {
            var set = new HashSet<>(s.disabled());
            if (disabled) {
                set.add(id);
            } else {
                set.remove(id);
            }
            return new State(s.profiles(), disabled && id.equals(s.defaultId()) ? null : s.defaultId(), set);
        });
    }

    private static Set<UUID> without(Set<UUID> set, UUID id) {
        if (id == null || !set.contains(id)) {
            return set;
        }
        var copy = new HashSet<>(set);
        copy.remove(id);
        return copy;
    }

    /** @param listener dipanggil (di thread penulis) setelah isi berubah dan tersimpan */
    public void addListener(Consumer<State> listener) {
        listeners.add(listener);
    }

    private void mutate(java.util.function.UnaryOperator<State> change) {
        State next;
        synchronized (lock) {
            State changed = change.apply(state);
            next = new State(sorted(changed.profiles()), changed.defaultId(), changed.disabled());
            try {
                AtomicFiles.write(file, mapper.writeValueAsBytes(new FileContent(1, next.defaultId(), next.profiles(),
                        next.disabled().stream().sorted().toList())));
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

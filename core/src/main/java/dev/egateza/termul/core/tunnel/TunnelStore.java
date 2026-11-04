package dev.egateza.termul.core.tunnel;

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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tunnel tersimpan di {@code tunnels.json} (config dir), ditulis atomic; urutan daftar = urutan dibuat. Hanya pengaturan,
 * bukan tempat secret, dan status jalan/berhenti tidak disimpan (tunnel hanya jalan selama TermUL terbuka). Entri yang
 * tidak valid dilewati; file rusak disisihkan ({@code tunnels.json.corrupt-<waktu>}) lalu mulai kosong. Thread-safe;
 * tulis disk di luar EDT, baca boleh dari mana saja.
 */
public final class TunnelStore {

    private static final Logger log = LoggerFactory.getLogger(TunnelStore.class);
    static final int VERSION = 1;

    record TunnelJson(String id, String name, String type, String host, int sourcePort, String destinationHost,
                      int destinationPort, boolean shared, boolean autoStart) {
    }

    record FileJson(int version, List<TunnelJson> tunnels) {
    }

    private final Path file;
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final Object lock = new Object();
    private final List<Consumer<List<Tunnel>>> listeners = new CopyOnWriteArrayList<>();
    private volatile List<Tunnel> tunnels = List.of(); // ditulis hanya saat memegang lock

    public TunnelStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public List<Tunnel> load() {
        synchronized (lock) {
            try {
                tunnels = parse(mapper.readValue(Files.readAllBytes(file), FileJson.class));
                log.info("Memuat {} tunnel dari {}", tunnels.size(), file);
            } catch (NoSuchFileException e) {
                tunnels = List.of();
            } catch (IOException | RuntimeException e) {
                tunnels = List.of();
                quarantine(e);
            }
            return tunnels;
        }
    }

    public List<Tunnel> snapshot() {
        return tunnels;
    }

    public Optional<Tunnel> find(UUID id) {
        return tunnels.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    /** Tambah di akhir, atau ganti di tempatnya (berdasarkan id). */
    public void save(Tunnel tunnel) {
        Objects.requireNonNull(tunnel, "tunnel");
        mutate(list -> {
            var next = new ArrayList<>(list);
            int at = indexOf(next, tunnel.id());
            if (at >= 0) {
                next.set(at, tunnel);
            } else {
                next.add(tunnel);
            }
            return List.copyOf(next);
        });
    }

    public void delete(UUID id) {
        mutate(list -> indexOf(list, id) < 0 ? list : list.stream().filter(t -> !t.id().equals(id)).toList());
    }

    /** @param listener dipanggil (di thread penulis) setelah isi store berubah dan tersimpan */
    public void addListener(Consumer<List<Tunnel>> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<List<Tunnel>> listener) {
        listeners.remove(listener);
    }

    private void mutate(UnaryOperator<List<Tunnel>> change) {
        List<Tunnel> next;
        synchronized (lock) {
            next = change.apply(tunnels);
            if (next == tunnels) {
                return;
            }
            try {
                AtomicFiles.write(file, mapper.writeValueAsBytes(toJson(next)));
            } catch (IOException e) {
                throw new UncheckedIOException("Tunnel tidak bisa disimpan ke " + file, e);
            }
            tunnels = next;
        }
        listeners.forEach(l -> l.accept(next));
    }

    static List<Tunnel> parse(FileJson json) {
        var out = new ArrayList<Tunnel>();
        if (json.tunnels() != null) {
            for (var j : json.tunnels()) {
                try {
                    out.add(new Tunnel(UUID.fromString(j.id()), j.name(),
                            TunnelType.valueOf(j.type().toUpperCase(Locale.ROOT)), UUID.fromString(j.host()),
                            j.sourcePort(), j.destinationHost(), j.destinationPort(), j.shared(), j.autoStart()));
                } catch (RuntimeException e) {
                    log.warn("Tunnel {} dilewati: {}", j.name(), e.getMessage());
                }
            }
        }
        return List.copyOf(out);
    }

    static FileJson toJson(List<Tunnel> list) {
        return new FileJson(VERSION, list.stream().map(t -> new TunnelJson(t.id().toString(), t.name(),
                t.type().name().toLowerCase(Locale.ROOT), t.hostId().toString(), t.sourcePort(), t.destinationHost(),
                t.destinationPort(), t.shared(), t.autoStart())).toList());
    }

    private static int indexOf(List<Tunnel> list, UUID id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void quarantine(Exception cause) {
        Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + Instant.now().toEpochMilli());
        try {
            Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            log.warn("File tunnel rusak ({}); disisihkan ke {} dan mulai dari daftar kosong", cause.getMessage(), aside);
        } catch (IOException e) {
            log.warn("File tunnel rusak ({}) dan tidak bisa disisihkan: {}", cause.getMessage(), e.toString());
        }
    }
}

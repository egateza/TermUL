package dev.egateza.termul.core.bookmark;

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bookmark folder panel file ({@code bookmarks.json}, ditulis atomic), seperti "Open directory" WinSCP: per sisi
 * (remote / lokal), dan per host atau bersama untuk semua host. Hanya path, bukan tempat secret. File rusak disisihkan
 * ({@code bookmarks.json.corrupt-<waktu>}) lalu mulai kosong. Thread-safe; tulis disk di luar EDT, baca boleh dari mana saja.
 */
public final class BookmarkStore {

    private static final Logger log = LoggerFactory.getLogger(BookmarkStore.class);

    /** Panel tempat bookmark dipakai: path remote dan lokal tidak dicampur. */
    public enum Side {
        REMOTE, LOCAL
    }

    /** Bookmark satu sisi: bersama untuk semua host, dan per id profil. Urutan daftar = urutan pilihan user. */
    record Lists(List<String> shared, Map<UUID, List<String>> hosts) {
        static final Lists EMPTY = new Lists(List.of(), Map.of());

        Lists {
            shared = shared == null ? List.of() : clean(shared);
            var copy = new LinkedHashMap<UUID, List<String>>();
            if (hosts != null) {
                hosts.forEach((id, paths) -> {
                    var cleaned = clean(paths);
                    if (id != null && !cleaned.isEmpty()) {
                        copy.put(id, cleaned);
                    }
                });
            }
            hosts = java.util.Collections.unmodifiableMap(copy);
        }

        List<String> get(UUID host) {
            return host == null ? shared : hosts.getOrDefault(host, List.of());
        }

        Lists with(UUID host, List<String> paths) {
            if (host == null) {
                return new Lists(paths, hosts);
            }
            var copy = new LinkedHashMap<>(hosts);
            copy.put(host, paths);
            return new Lists(shared, copy);
        }

        Lists retain(Set<UUID> ids) {
            var copy = new LinkedHashMap<>(hosts);
            copy.keySet().retainAll(ids);
            return new Lists(shared, copy);
        }
    }

    /** Isi file di disk. */
    record FileContent(int format, Lists remote, Lists local) {
        FileContent {
            remote = remote == null ? Lists.EMPTY : remote;
            local = local == null ? Lists.EMPTY : local;
        }

        Lists side(Side side) {
            return side == Side.REMOTE ? remote : local;
        }

        FileContent with(Side side, Lists lists) {
            return side == Side.REMOTE ? new FileContent(format, lists, local) : new FileContent(format, remote, lists);
        }
    }

    private final Path file;
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final Object lock = new Object();
    private volatile FileContent content = new FileContent(1, null, null); // ditulis hanya saat memegang lock

    public BookmarkStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public void load() {
        synchronized (lock) {
            try {
                content = mapper.readValue(Files.readAllBytes(file), FileContent.class);
            } catch (NoSuchFileException e) {
                content = new FileContent(1, null, null);
            } catch (IOException | IllegalArgumentException | NullPointerException e) {
                content = new FileContent(1, null, null);
                quarantine(e);
            }
        }
    }

    /** @param host id profil, atau null untuk bookmark bersama */
    public List<String> list(Side side, UUID host) {
        return content.side(side).get(host);
    }

    /**
     * Ganti seluruh daftar (urutan dipertahankan; path kosong dan duplikat dibuang), lalu simpan.
     *
     * @param host id profil, atau null untuk bookmark bersama
     */
    public void set(Side side, UUID host, List<String> paths) {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(paths, "paths");
        synchronized (lock) {
            write(content.with(side, content.side(side).with(host, paths)));
        }
    }

    /**
     * Tambah satu path di akhir daftar (atomic terhadap perubahan lain), lalu simpan.
     *
     * @param host id profil, atau null untuk bookmark bersama
     * @return false kalau path kosong atau sudah ada di daftar itu
     */
    public boolean add(Side side, UUID host, String path) {
        Objects.requireNonNull(side, "side");
        if (path == null || path.isBlank()) {
            return false;
        }
        synchronized (lock) {
            var current = content.side(side).get(host);
            if (current.contains(path.strip())) {
                return false;
            }
            var next = new java.util.ArrayList<>(current);
            next.add(path);
            write(content.with(side, content.side(side).with(host, next)));
            return true;
        }
    }

    /** Buang bookmark host yang profilnya sudah tidak ada (dipanggil setelah profil/grup dihapus). */
    public void retainHosts(Set<UUID> profileIds) {
        synchronized (lock) {
            var next = content.with(Side.REMOTE, content.remote().retain(profileIds))
                    .with(Side.LOCAL, content.local().retain(profileIds));
            if (!next.equals(content)) {
                write(next);
            }
        }
    }

    private void write(FileContent next) { // guarded by lock
        try {
            AtomicFiles.write(file, mapper.writeValueAsBytes(next));
        } catch (IOException e) {
            throw new UncheckedIOException("Bookmark tidak bisa disimpan ke " + file, e);
        }
        content = next;
    }

    private static List<String> clean(List<String> paths) {
        var unique = new LinkedHashSet<String>();
        for (String p : paths) {
            if (p != null && !p.isBlank()) {
                unique.add(p.strip());
            }
        }
        return List.copyOf(unique);
    }

    private void quarantine(Exception cause) {
        Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + Instant.now().toEpochMilli());
        try {
            Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            log.warn("File bookmark rusak ({}); disisihkan ke {} dan mulai dari daftar kosong", cause.getMessage(), aside);
        } catch (IOException e) {
            log.warn("File bookmark rusak ({}) dan tidak bisa disisihkan: {}", cause.getMessage(), e.toString());
        }
    }
}

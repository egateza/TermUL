package dev.egateza.myterm.core.profile;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.egateza.myterm.core.io.AtomicFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Penyimpanan profil di file JSON. Setiap mutasi langsung ditulis ke disk secara atomic.
 *
 * <p>Thread-safe. Mutasi melakukan disk I/O, jadi panggil dari luar EDT. Listener dipanggil
 * di thread yang melakukan mutasi (UI harus {@code invokeLater} sendiri).
 */
public final class ProfileStore {

    private static final Logger log = LoggerFactory.getLogger(ProfileStore.class);

    private final Path file;
    private final ObjectMapper mapper;
    private final List<Consumer<ProfileSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private volatile ProfileSnapshot snapshot = ProfileSnapshot.empty(); // ditulis hanya saat memegang lock

    public ProfileStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.mapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /** Membaca file dari disk. File belum ada = snapshot kosong. File rusak = exception (file tidak ditimpa). */
    public ProfileSnapshot load() {
        synchronized (lock) {
            try {
                byte[] bytes = Files.readAllBytes(file);
                snapshot = mapper.readValue(bytes, ProfileSnapshot.class);
                log.info("Memuat {} profil dari {}", snapshot.profiles().size(), file);
            } catch (NoSuchFileException e) {
                snapshot = ProfileSnapshot.empty();
            } catch (IOException | IllegalArgumentException e) {
                throw new ProfileStoreException("File profil tidak bisa dibaca: " + file, e);
            }
            return snapshot;
        }
    }

    public ProfileSnapshot snapshot() {
        return snapshot;
    }

    public void addListener(Consumer<ProfileSnapshot> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(Consumer<ProfileSnapshot> listener) {
        listeners.remove(listener);
    }

    /** Tambah profil baru atau ganti profil dengan id yang sama. */
    public ProfileSnapshot save(HostProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return mutate(s -> s.withProfile(profile));
    }

    public ProfileSnapshot delete(UUID id) {
        return mutate(s -> s.withoutProfile(id));
    }

    public ProfileSnapshot addGroup(String group) {
        return mutate(s -> {
            var groups = new ArrayList<>(s.groups());
            groups.add(group);
            return s.withGroups(groups);
        });
    }

    /** Menghapus grup (dan subgrup) kosong. Gagal kalau masih ada profil di dalamnya. */
    public ProfileSnapshot deleteGroup(String group) {
        String g = HostProfile.normalizeGroup(group);
        return mutate(s -> {
            boolean used = s.profiles().stream().anyMatch(p -> isSameOrChild(p.group(), g));
            if (used) {
                throw new IllegalStateException("Grup '" + g + "' masih berisi profil");
            }
            return s.withGroups(s.groups().stream().filter(x -> !isSameOrChild(x, g)).toList());
        });
    }

    /** Mengganti nama/memindahkan grup beserta subgrup dan profil di dalamnya. */
    public ProfileSnapshot renameGroup(String from, String to) {
        String src = HostProfile.normalizeGroup(from);
        String dst = HostProfile.normalizeGroup(to);
        if (src.isEmpty()) {
            throw new IllegalArgumentException("Grup root tidak bisa di-rename");
        }
        return mutate(s -> {
            var groups = s.groups().stream().map(g -> rebase(g, src, dst)).toList();
            var result = s.withGroups(groups);
            for (HostProfile p : s.profiles()) {
                if (isSameOrChild(p.group(), src)) {
                    result = result.withProfile(p.withGroup(rebase(p.group(), src, dst)));
                }
            }
            return result;
        });
    }

    private ProfileSnapshot mutate(java.util.function.UnaryOperator<ProfileSnapshot> change) {
        ProfileSnapshot updated;
        synchronized (lock) {
            updated = change.apply(snapshot);
            write(updated);
            snapshot = updated;
        }
        for (var l : listeners) {
            try {
                l.accept(updated);
            } catch (RuntimeException e) {
                log.warn("Listener ProfileStore gagal", e);
            }
        }
        return updated;
    }

    private void write(ProfileSnapshot s) {
        try {
            AtomicFiles.write(file, mapper.writeValueAsBytes(s));
        } catch (IOException e) {
            throw new ProfileStoreException("Gagal menyimpan file profil: " + file, e);
        }
    }

    static boolean isSameOrChild(String group, String parent) {
        return group.equals(parent) || group.startsWith(parent + "/");
    }

    private static String rebase(String group, String src, String dst) {
        if (!isSameOrChild(group, src)) {
            return group;
        }
        String rest = group.substring(src.length());
        return HostProfile.normalizeGroup(dst + rest);
    }
}

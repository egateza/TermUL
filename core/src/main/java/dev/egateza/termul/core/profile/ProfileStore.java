package dev.egateza.termul.core.profile;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.egateza.termul.core.io.AtomicFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

    /**
     * Menghapus profil. Profil lain yang memakainya sebagai jump host beralih ke koneksi langsung (bukan menunjuk ke
     * profil yang sudah tidak ada).
     */
    public ProfileSnapshot delete(UUID id) {
        return deleteAll(List.of(id));
    }

    /** Menghapus beberapa profil sekaligus; jump host yang menunjuk ke profil itu dikosongkan. */
    public ProfileSnapshot deleteAll(Collection<UUID> ids) {
        var gone = Set.copyOf(ids);
        return mutate(s -> {
            if (s.profiles().stream().noneMatch(p -> gone.contains(p.id()))) {
                return s;
            }
            return s.withProfiles(s.profiles().stream().filter(p -> !gone.contains(p.id()))
                    .map(p -> p.jumpHostId() != null && gone.contains(p.jumpHostId()) ? p.withJumpHostId(null) : p)
                    .toList());
        });
    }

    /** Tambah atau ganti beberapa profil sekaligus (satu kali tulis), mis. hasil import. */
    public ProfileSnapshot saveAll(Collection<HostProfile> profiles) {
        return mutate(s -> {
            var result = s;
            for (HostProfile p : profiles) {
                result = result.withProfile(Objects.requireNonNull(p, "profile"));
            }
            return result;
        });
    }

    /**
     * Menyimpan hasil import sekaligus (satu tulis file): folder baru, profil, favorit, dan urutan profil.
     *
     * @param order urutan profil yang ditambahkan; profil yang sudah ada di folder yang sama tetap di atas dengan
     *              urutan tampilnya sekarang, profil yang diimpor menyusul. Kosong = urut nama seperti biasa
     */
    public ProfileSnapshot importProfiles(Collection<String> folders, Collection<HostProfile> profiles,
                                          Collection<UUID> favorites, List<UUID> order) {
        return mutate(s -> {
            var groups = new ArrayList<>(s.groups());
            groups.addAll(folders);
            var result = s.withGroups(groups);
            for (HostProfile p : profiles) {
                result = result.withProfile(Objects.requireNonNull(p, "profile"));
            }
            for (UUID id : favorites) {
                if (result.find(id).isPresent() && !result.isFavorite(id)) {
                    result = result.withFavorite(id, true);
                }
            }
            if (!order.isEmpty()) {
                var touched = new java.util.LinkedHashSet<String>();
                for (UUID id : order) {
                    result.find(id).ifPresent(p -> touched.add(p.group()));
                }
                var newOrder = new ArrayList<>(result.profileOrder());
                for (String g : touched) { // urutan tampil sekarang dikunci dulu, supaya yang lama tidak bergeser
                    var shown = s.profilesIn(g).stream().map(HostProfile::id).toList();
                    newOrder.removeAll(shown);
                    newOrder.addAll(shown);
                }
                newOrder.removeAll(order);
                newOrder.addAll(order);
                result = result.withProfileOrder(newOrder);
            }
            return result;
        });
    }

    /** Memindahkan beberapa profil ke grup {@code group} ({@code ""} = tanpa grup). */
    public ProfileSnapshot moveProfiles(Collection<UUID> ids, String group) {
        String g = HostProfile.normalizeGroup(group);
        var moving = Set.copyOf(ids);
        return mutate(s -> s.withProfiles(s.profiles().stream()
                .map(p -> moving.contains(p.id()) && !p.group().equals(g) ? p.withGroup(g) : p).toList()));
    }

    /**
     * Urutan baru profil di satu folder (mode Reorder). Hanya urutan relatif antarprofil di folder yang sama yang
     * berpengaruh, jadi {@code ids} cukup berisi profil folder itu.
     */
    public ProfileSnapshot reorderProfiles(List<UUID> ids) {
        return mutate(s -> {
            var order = new ArrayList<>(s.profileOrder());
            order.removeAll(ids);
            order.addAll(ids);
            return s.withProfileOrder(order);
        });
    }

    /** Menggeser folder satu langkah ({@code delta} -1 / +1) di antara folder lain yang seinduk. */
    public ProfileSnapshot moveGroup(String group, int delta) {
        String g = HostProfile.normalizeGroup(group);
        return mutate(s -> {
            var siblings = new ArrayList<>(s.childGroups(ProfileSnapshot.parentOf(g)));
            int i = siblings.indexOf(g);
            int j = i + delta;
            if (i < 0 || j < 0 || j >= siblings.size()) {
                return s;
            }
            java.util.Collections.swap(siblings, i, j);
            var order = new ArrayList<>(s.groupOrder());
            order.removeAll(siblings);
            order.addAll(siblings);
            return s.withGroupOrder(order);
        });
    }

    /**
     * Menghapus folder beserta subfoldernya; profil di dalamnya pindah ke tanpa grup (Ungrouped), tidak ikut
     * terhapus.
     */
    public ProfileSnapshot deleteGroupKeepProfiles(String group) {
        String g = HostProfile.normalizeGroup(group);
        if (g.isEmpty()) {
            throw new IllegalArgumentException("Grup root tidak bisa dihapus");
        }
        return mutate(s -> s.withProfiles(s.profiles().stream()
                        .map(p -> isSameOrChild(p.group(), g) ? p.withGroup("") : p).toList())
                .withGroups(s.groups().stream().filter(x -> !isSameOrChild(x, g)).toList())
                .withGroupOrder(s.groupOrder().stream().filter(x -> !isSameOrChild(x, g)).toList()));
    }

    /** Menambah/menghapus profil dari grup bawaan Favorites. */
    public ProfileSnapshot setFavorite(UUID id, boolean favorite) {
        Objects.requireNonNull(id, "id");
        return mutate(s -> {
            if (s.find(id).isEmpty() || s.isFavorite(id) == favorite) {
                return s;
            }
            return s.withFavorite(id, favorite);
        });
    }

    /** Mencatat profil baru saja dipakai (grup bawaan Last used, maksimal {@link ProfileSnapshot#RECENT_LIMIT}). */
    public ProfileSnapshot markUsed(UUID id) {
        Objects.requireNonNull(id, "id");
        return mutate(s -> s.find(id).isEmpty() || (!s.recent().isEmpty() && s.recent().getFirst().equals(id))
                ? s : s.withUsed(id));
    }

    public ProfileSnapshot clearRecent() {
        return mutate(s -> s.recent().isEmpty() ? s : s.withoutRecent());
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

    /**
     * Mengubah flag sensitif semua profil di grup {@code group} beserta subgrupnya sekaligus.
     *
     * @param sensitive true/false = pilihan eksplisit, null = ikut environment (lihat {@link HostProfile#needsGuard()})
     */
    public ProfileSnapshot setGroupSensitive(String group, Boolean sensitive) {
        return mutate(s -> withSensitive(s, s.profilesUnder(group), sensitive));
    }

    /**
     * Mengubah flag sensitif profil dengan id {@code ids} sekaligus; id yang tidak ada diabaikan.
     *
     * @param sensitive true/false = pilihan eksplisit, null = ikut environment
     */
    public ProfileSnapshot setSensitive(Collection<UUID> ids, Boolean sensitive) {
        var targets = Set.copyOf(ids);
        return mutate(s -> withSensitive(s,
                s.profiles().stream().filter(p -> targets.contains(p.id())).toList(), sensitive));
    }

    private static ProfileSnapshot withSensitive(ProfileSnapshot s, List<HostProfile> targets, Boolean sensitive) {
        var result = s;
        for (HostProfile p : targets) {
            if (!Objects.equals(p.sensitive(), sensitive)) {
                result = result.withProfile(p.withSensitive(sensitive));
            }
        }
        return result;
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
            var result = s.withGroups(groups)
                    .withGroupOrder(s.groupOrder().stream().map(g -> rebase(g, src, dst)).toList());
            for (HostProfile p : s.profiles()) {
                if (isSameOrChild(p.group(), src)) {
                    result = result.withProfile(p.withGroup(rebase(p.group(), src, dst)));
                }
            }
            return result;
        });
    }

    /**
     * Menulis salinan seluruh profil (format sama dengan {@code profiles.json}) ke {@code target} secara atomic.
     * Tidak berisi secret karena {@link HostProfile} memang tidak menyimpannya. Melakukan disk I/O.
     *
     * @return jumlah profil yang diekspor
     */
    public int exportTo(Path target) {
        Objects.requireNonNull(target, "target");
        ProfileSnapshot s = snapshot;
        try {
            AtomicFiles.write(target, mapper.writeValueAsBytes(s));
        } catch (IOException e) {
            throw new ProfileStoreException("Gagal mengekspor profil ke: " + target, e);
        }
        log.info("Mengekspor {} profil ke {}", s.profiles().size(), target);
        return s.profiles().size();
    }

    private ProfileSnapshot mutate(java.util.function.UnaryOperator<ProfileSnapshot> change) {
        ProfileSnapshot updated;
        synchronized (lock) {
            updated = change.apply(snapshot);
            if (updated == snapshot) {
                return updated;
            }
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

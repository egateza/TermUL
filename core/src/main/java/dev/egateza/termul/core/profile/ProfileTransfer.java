package dev.egateza.termul.core.profile;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Export dan import profil dari Profile manager. File export berisi folder (urut seperti daftar host), profil, dan
 * opsional snippet; tidak pernah berisi secret karena {@link HostProfile} memang tidak menyimpannya. Import menerima
 * file export ini dan juga {@code profiles.json} lama (keduanya punya array {@code profiles}).
 */
public final class ProfileTransfer {

    public static final int VERSION = 1;

    /**
     * Isi file export.
     *
     * @param favorites    profil yang diekspor yang ada di Favorites
     * @param profileOrder urutan profil di dalam folder (mode Reorder), hanya profil yang diekspor
     * @param snippets     isi file export snippet apa adanya, null kalau tidak disertakan
     */
    public record ExportFile(int version, List<String> folders, List<HostProfile> profiles, List<UUID> favorites,
                             List<UUID> profileOrder, JsonNode snippets) {
    }

    /** Isi file yang akan diimpor; {@code favorites} dan {@code profileOrder} memakai id di file. */
    public record Source(List<String> folders, List<HostProfile> profiles, List<UUID> favorites,
                         List<UUID> profileOrder) {

        public Source(List<String> folders, List<HostProfile> profiles) {
            this(folders, profiles, List.of(), List.of());
        }
    }

    /** Status satu profil yang diimpor dibanding yang sudah ada. */
    public enum Status {
        /** belum ada profil dengan id / nama ini */
        NEW,
        /** sudah ada, alamatnya sama: dilewati */
        SAME,
        /** sudah ada dengan alamat lain: user memilih Skip / Overwrite / Keep both */
        CONFLICT
    }

    public enum Action { SKIP, OVERWRITE, KEEP_BOTH }

    /** @param existing profil yang sudah ada dengan id atau nama yang sama, null untuk {@link Status#NEW} */
    public record Row(HostProfile incoming, HostProfile existing, Status status) {
    }

    /**
     * Hasil import: profil yang disimpan sekaligus, dan jumlahnya untuk ringkasan.
     *
     * @param favorites profil baru yang masuk Favorites (id setelah import)
     * @param order     urutan profil baru di dalam folder (id setelah import); kosong = urut nama
     */
    public record Result(List<HostProfile> toSave, List<String> newFolders, List<UUID> favorites, List<UUID> order,
                         int added, int updated, int skipped, UUID firstAdded) {
    }

    /** Akhiran nama untuk pilihan "Keep both". */
    public static final String IMPORTED_SUFFIX = " (imported)";

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private ProfileTransfer() {
    }

    /**
     * @param profiles     profil yang diekspor (urutan dipertahankan)
     * @param snippetsJson isi file export snippet, null kalau snippet tidak disertakan
     */
    public static byte[] export(ProfileSnapshot snapshot, List<HostProfile> profiles, byte[] snippetsJson)
            throws IOException {
        JsonNode snippets = snippetsJson == null ? null : MAPPER.readTree(snippetsJson);
        Set<UUID> ids = new HashSet<>();
        profiles.forEach(p -> ids.add(p.id()));
        return MAPPER.writeValueAsBytes(new ExportFile(VERSION, snapshot.allGroups(), List.copyOf(profiles),
                snapshot.favorites().stream().filter(ids::contains).toList(),
                snapshot.profileOrder().stream().filter(ids::contains).toList(), snippets));
    }

    /** Membaca file export TermUL atau {@code profiles.json}. Profil tidak valid membuat seluruh file ditolak. */
    public static Source read(byte[] json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        JsonNode list = root == null ? null : root.get("profiles");
        if (list == null || !list.isArray()) {
            throw new IOException("Bukan file profil TermUL: tidak ada daftar \"profiles\"");
        }
        var profiles = new ArrayList<HostProfile>();
        for (JsonNode node : list) {
            try {
                profiles.add(MAPPER.treeToValue(node, HostProfile.class));
            } catch (IOException | IllegalArgumentException e) {
                throw new IOException("Profil tidak valid di file: " + e.getMessage(), e);
            }
        }
        var folders = new ArrayList<String>();
        JsonNode f = root.get("folders");
        if (f == null) {
            f = root.get("groups"); // profiles.json
        }
        if (f != null && f.isArray()) {
            f.forEach(n -> {
                String g = HostProfile.normalizeGroup(n.asText(""));
                if (!g.isEmpty()) {
                    folders.add(g);
                }
            });
        }
        return new Source(folders, profiles, ids(root.get("favorites")), ids(root.get("profileOrder")));
    }

    /** Daftar id dari file; entri yang bukan UUID diabaikan (file lama tanpa field ini = kosong). */
    private static List<UUID> ids(JsonNode array) {
        var result = new ArrayList<UUID>();
        if (array != null && array.isArray()) {
            for (JsonNode n : array) {
                try {
                    result.add(UUID.fromString(n.asText("")));
                } catch (IllegalArgumentException e) {
                    // abaikan
                }
            }
        }
        return result;
    }

    /**
     * Mencocokkan profil yang diimpor dengan yang sudah ada: id dulu (import ulang file yang sama memperbarui, bukan
     * menggandakan), lalu nama (tanpa beda huruf besar/kecil).
     */
    public static List<Row> plan(ProfileSnapshot current, List<HostProfile> incoming) {
        var byName = new HashMap<String, HostProfile>();
        current.profiles().forEach(p -> byName.putIfAbsent(p.name().toLowerCase(Locale.ROOT), p));
        var rows = new ArrayList<Row>(incoming.size());
        for (HostProfile in : incoming) {
            HostProfile old = current.find(in.id()).orElseGet(() -> byName.get(in.name().toLowerCase(Locale.ROOT)));
            Status status = old == null ? Status.NEW : sameAddress(old, in) ? Status.SAME : Status.CONFLICT;
            rows.add(new Row(in, old, status));
        }
        return rows;
    }

    static boolean sameAddress(HostProfile a, HostProfile b) {
        return a.host().equalsIgnoreCase(b.host()) && a.port() == b.port() && a.username().equals(b.username())
                && a.protocol() == b.protocol();
    }

    /**
     * Menerapkan rencana import.
     *
     * @param actions     pilihan untuk baris {@link Status#CONFLICT} (indeks sama dengan {@code rows}); baris lain
     *                    tidak dibaca. Tidak ada pilihan = {@link Action#SKIP}
     * @param targetGroup grup untuk profil baru; null = pakai grup dari file
     * @param source      isi file: folder (dibuat kalau {@code targetGroup} null), favorit, dan urutan profil.
     *                    Favorit dan urutan hanya dipakai untuk profil yang ditambahkan; profil yang ditimpa tetap
     *                    memakai favorit dan urutannya sendiri
     */
    public static Result apply(ProfileSnapshot current, List<Row> rows, Map<Integer, Action> actions,
                               String targetGroup, Source source) {
        Set<UUID> used = new HashSet<>();
        current.profiles().forEach(p -> used.add(p.id()));
        Set<String> names = new HashSet<>();
        current.profiles().forEach(p -> names.add(p.name().toLowerCase(Locale.ROOT)));
        var idMap = new HashMap<UUID, UUID>(); // id di file -> id setelah import (untuk jump host)
        var addedIds = new HashMap<UUID, UUID>(); // id di file -> id profil yang ditambahkan
        var toSave = new ArrayList<HostProfile>();
        int added = 0;
        int updated = 0;
        int skipped = 0;
        UUID first = null;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            HostProfile in = r.incoming();
            String group = targetGroup != null ? targetGroup : in.group();
            switch (r.status()) {
                case SAME -> {
                    idMap.put(in.id(), r.existing().id());
                    skipped++;
                }
                case NEW, CONFLICT -> {
                    Action a = r.status() == Status.NEW ? Action.KEEP_BOTH
                            : Objects.requireNonNullElse(actions.get(i), Action.SKIP);
                    switch (a) {
                        case SKIP -> {
                            idMap.put(in.id(), r.existing().id());
                            skipped++;
                        }
                        case OVERWRITE -> {
                            var p = overwrite(r.existing(), in);
                            idMap.put(in.id(), p.id());
                            toSave.add(p);
                            updated++;
                        }
                        case KEEP_BOTH -> {
                            UUID id = used.add(in.id()) ? in.id() : freshId(used);
                            String name = r.status() == Status.NEW ? in.name() : uniqueName(in.name(), names);
                            names.add(name.toLowerCase(Locale.ROOT));
                            idMap.put(in.id(), id);
                            addedIds.put(in.id(), id);
                            toSave.add(copy(in, id, name, group));
                            added++;
                            if (first == null) {
                                first = id;
                            }
                        }
                    }
                }
            }
        }
        // jump host menunjuk ke id di file: arahkan ke id hasil import, atau kosongkan kalau tidak ada
        var known = new HashSet<>(used);
        var fixed = toSave.stream().map(p -> {
            UUID j = p.jumpHostId();
            if (j == null) {
                return p;
            }
            UUID target = idMap.getOrDefault(j, known.contains(j) ? j : null);
            return Objects.equals(target, j) ? p : p.withJumpHostId(target == null || target.equals(p.id()) ? null : target);
        }).toList();
        var newFolders = new ArrayList<String>();
        if (targetGroup == null) {
            var existing = new HashSet<>(current.allGroups());
            source.folders().stream().filter(g -> !existing.contains(g)).forEach(newFolders::add);
        }
        var favorites = source.favorites().stream().map(addedIds::get).filter(Objects::nonNull).distinct().toList();
        var order = source.profileOrder().stream().map(addedIds::get).filter(Objects::nonNull).distinct().toList();
        return new Result(fixed, newFolders, favorites, order, added, updated, skipped, first);
    }

    /** Alamat dan cara login dari file, sisanya (grup, catatan, environment, ...) tetap milik profil yang ada. */
    static HostProfile overwrite(HostProfile old, HostProfile in) {
        return new HostProfile(old.id(), old.name(), old.group(), in.host(), in.port(), in.username(),
                in.authMethod(), in.privateKeyPath(), old.jumpHostId(), old.environment(), old.initialDirectory(),
                old.notes(), old.autoSudo(), old.os(), old.terminalTheme(), in.protocol(), old.sensitive());
    }

    private static HostProfile copy(HostProfile in, UUID id, String name, String group) {
        return new HostProfile(id, name, group, in.host(), in.port(), in.username(), in.authMethod(),
                in.privateKeyPath(), in.jumpHostId(), in.environment(), in.initialDirectory(), in.notes(),
                in.autoSudo(), in.os(), in.terminalTheme(), in.protocol(), in.sensitive());
    }

    private static UUID freshId(Set<UUID> used) {
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (!used.add(id));
        return id;
    }

    static String uniqueName(String base, Set<String> lowerNames) {
        String name = base + IMPORTED_SUFFIX;
        for (int n = 2; lowerNames.contains(name.toLowerCase(Locale.ROOT)); n++) {
            name = base + " (imported " + n + ")";
        }
        return name;
    }
}

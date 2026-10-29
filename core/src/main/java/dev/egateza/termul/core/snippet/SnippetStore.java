package dev.egateza.termul.core.snippet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Snippet, kategori, dan nilai variabel terakhir per host di {@code snippets.json} (config dir), ditulis atomic. Formatnya
 * sama dengan file ekspor snippet (version 2, juga dipakai TermUL mobile), ditambah {@code remembered}. File format lama
 * ({@code format: 1}: satu baris, grup host) dibaca dan ditulis ulang sebagai version 2 pada perubahan berikutnya. File
 * rusak disisihkan dengan nama {@code snippets.json.corrupt-<waktu>} lalu mulai dari daftar kosong, supaya isi lamanya
 * tidak hilang dan aplikasi tetap jalan. Thread-safe; tulis/baca disk di luar EDT, method baca boleh dari mana saja.
 */
public final class SnippetStore {

    private static final Logger log = LoggerFactory.getLogger(SnippetStore.class);
    static final int VERSION = 2;

    /** Isi store; urutan {@code snippets} = urutan manual (snippet baru di depan). */
    record State(List<Snippet> snippets, List<SnippetCategory> categories,
                 Map<String, Map<String, Map<String, String>>> remembered) {
        static final State EMPTY = new State(List.of(), List.of(), Map.of());
    }

    record AppliesTo(List<String> hosts, List<String> folders) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SnippetJson(String id, String title, String command, String category, Boolean favorite,
                       AppliesTo appliesTo, Boolean dangerous, Map<String, String> variables) {
    }

    record CategoryJson(String id, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record FileJson(int version, List<CategoryJson> categories, List<SnippetJson> snippets,
                    Map<String, Map<String, Map<String, String>>> remembered) {
    }

    /** Format lama (sebelum version 2). */
    record LegacySnippet(String id, String name, String command, String group) {
    }

    private final Path file;
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final Object lock = new Object();
    private final List<Consumer<List<Snippet>>> listeners = new CopyOnWriteArrayList<>();
    private volatile State state = State.EMPTY; // ditulis hanya saat memegang lock

    public SnippetStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public List<Snippet> load() {
        synchronized (lock) {
            try {
                state = parse(mapper.readTree(Files.readAllBytes(file)));
                log.info("Memuat {} snippet dari {}", state.snippets().size(), file);
            } catch (NoSuchFileException e) {
                state = State.EMPTY;
            } catch (IOException | IllegalArgumentException | NullPointerException e) {
                state = State.EMPTY;
                quarantine(e);
            }
            return state.snippets();
        }
    }

    /** Semua snippet, urutan manual. */
    public List<Snippet> snapshot() {
        return state.snippets();
    }

    public List<SnippetCategory> categories() {
        return state.categories();
    }

    /** Nama kategori, atau {@code ""} kalau tanpa kategori / kategorinya sudah tidak ada. */
    public String categoryName(String id) {
        return state.categories().stream().filter(c -> c.id().equals(id)).map(SnippetCategory::name).findFirst()
                .orElse("");
    }

    /** Snippet yang berlaku untuk host bernama {@code hostName} di grup {@code hostGroup}, urutan manual. */
    public List<Snippet> forHost(String hostName, String hostGroup) {
        return state.snippets().stream().filter(s -> s.appliesTo(hostName, hostGroup)).toList();
    }

    /** Tambah (paling depan) atau ganti di tempatnya (berdasarkan id). */
    public void save(Snippet snippet) {
        mutate(s -> {
            var list = new ArrayList<>(s.snippets());
            int at = indexOf(list, snippet.id());
            if (at >= 0) {
                list.set(at, snippet);
            } else {
                list.addFirst(snippet);
            }
            return new State(List.copyOf(list), s.categories(), s.remembered());
        });
    }

    public void delete(UUID id) {
        mutate(s -> {
            var remembered = new LinkedHashMap<String, Map<String, Map<String, String>>>();
            s.remembered().forEach((host, perSnippet) -> {
                var rest = new LinkedHashMap<>(perSnippet);
                rest.remove(id.toString());
                if (!rest.isEmpty()) {
                    remembered.put(host, Map.copyOf(rest));
                }
            });
            return new State(s.snippets().stream().filter(x -> !x.id().equals(id)).toList(), s.categories(),
                    Map.copyOf(remembered));
        });
    }

    public void setFavorite(UUID id, boolean favorite) {
        mutate(s -> new State(s.snippets().stream().map(x -> x.id().equals(id) ? x.withFavorite(favorite) : x)
                .toList(), s.categories(), s.remembered()));
    }

    /**
     * Kategori bernama {@code name} (tanpa membedakan huruf besar/kecil); dibuat dan disimpan kalau belum ada.
     *
     * @return kategori, atau null kalau {@code name} kosong (tanpa kategori)
     */
    public SnippetCategory ensureCategory(String name) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) {
            return null;
        }
        var existing = findCategory(state.categories(), n);
        if (existing != null) {
            return existing;
        }
        var created = new SnippetCategory[1];
        mutate(s -> {
            var again = findCategory(s.categories(), n);
            created[0] = again != null ? again : new SnippetCategory(uniqueId(s.categories(), n), n);
            if (again != null) {
                return s;
            }
            var cats = new ArrayList<>(s.categories());
            cats.add(created[0]);
            return new State(s.snippets(), List.copyOf(cats), s.remembered());
        });
        return created[0];
    }

    // --- kelola kategori (id tetap saat ganti nama, supaya file ekspor/impor tetap cocok) ---

    /** Panjang maksimum nama kategori yang dibuat / diganti dari Kelola kategori (prototipe). */
    public static final int MAX_CATEGORY_NAME = 24;

    public enum CategoryProblem { EMPTY, TOO_LONG, DUPLICATE }

    /**
     * Masalah pada nama kategori baru / nama pengganti, atau null kalau boleh.
     *
     * @param selfId   kategori yang sedang diganti namanya (boleh memakai namanya sendiri), null untuk kategori baru
     * @param reserved nama lain yang tidak boleh dipakai (mis. label "Tanpa kategori"), tanpa membedakan huruf besar/kecil
     */
    public static CategoryProblem categoryProblem(List<SnippetCategory> categories, String name, String selfId,
                                                  String... reserved) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) {
            return CategoryProblem.EMPTY;
        }
        if (n.length() > MAX_CATEGORY_NAME) {
            return CategoryProblem.TOO_LONG;
        }
        for (String r : reserved) {
            if (n.equalsIgnoreCase(r)) {
                return CategoryProblem.DUPLICATE;
            }
        }
        boolean taken = categories.stream().anyMatch(c -> !c.id().equals(selfId) && c.name().equalsIgnoreCase(n));
        return taken ? CategoryProblem.DUPLICATE : null;
    }

    /** Kategori baru di urutan terakhir. @throws IllegalArgumentException kalau namanya tidak boleh */
    public SnippetCategory addCategory(String name) {
        var created = new SnippetCategory[1];
        mutate(s -> {
            check(categoryProblem(s.categories(), name, null));
            created[0] = new SnippetCategory(uniqueId(s.categories(), name.strip()), name);
            var cats = new ArrayList<>(s.categories());
            cats.add(created[0]);
            return new State(s.snippets(), List.copyOf(cats), s.remembered());
        });
        return created[0];
    }

    /** Ganti nama; id tetap. @throws IllegalArgumentException kalau namanya tidak boleh */
    public void renameCategory(String id, String name) {
        mutate(s -> {
            check(categoryProblem(s.categories(), name, id));
            if (!contains(s.categories(), id)) {
                return s;
            }
            var cats = s.categories().stream().map(c -> c.id().equals(id) ? new SnippetCategory(id, name) : c)
                    .toList();
            return cats.equals(s.categories()) ? s : new State(s.snippets(), cats, s.remembered());
        });
    }

    /** Geser kategori {@code delta} posisi (−1 naik, +1 turun); diam di ujung. */
    public void moveCategory(String id, int delta) {
        mutate(s -> {
            var cats = new ArrayList<>(s.categories());
            int from = -1;
            for (int i = 0; i < cats.size(); i++) {
                if (cats.get(i).id().equals(id)) {
                    from = i;
                }
            }
            int to = from + delta;
            if (from < 0 || to < 0 || to >= cats.size() || delta == 0) {
                return s;
            }
            cats.add(to, cats.remove(from));
            return new State(s.snippets(), List.copyOf(cats), s.remembered());
        });
    }

    /**
     * Hapus kategori; snippet di dalamnya pindah ke Tanpa kategori (tidak ada snippet yang terhapus).
     *
     * @return jumlah snippet yang dipindah
     */
    public int deleteCategory(String id) {
        int[] moved = {0};
        mutate(s -> {
            if (!contains(s.categories(), id)) {
                return s;
            }
            var snippets = s.snippets().stream().map(x -> {
                if (x.category().equals(id)) {
                    moved[0]++;
                    return x.withCategory("");
                }
                return x;
            }).toList();
            return new State(snippets, s.categories().stream().filter(c -> !c.id().equals(id)).toList(),
                    s.remembered());
        });
        return moved[0];
    }

    private static void check(CategoryProblem problem) {
        if (problem != null) {
            throw new IllegalArgumentException(switch (problem) {
                case EMPTY -> "Nama kategori wajib diisi.";
                case TOO_LONG -> "Nama kategori maksimal " + MAX_CATEGORY_NAME + " karakter.";
                case DUPLICATE -> "Nama kategori sudah dipakai.";
            });
        }
    }

    // --- urutan manual ---

    /** Pindahkan snippet ke posisi {@code toIndex} di urutan manual (dipakai juga oleh menu snippet terminal). */
    public void moveSnippet(UUID id, int toIndex) {
        mutate(s -> {
            var list = new ArrayList<>(s.snippets());
            int from = indexOf(list, id);
            int to = Math.clamp(toIndex, 0, list.size() - 1);
            if (from < 0 || from == to) {
                return s;
            }
            list.add(to, list.remove(from));
            return new State(List.copyOf(list), s.categories(), s.remembered());
        });
    }

    // --- ekspor ---

    /**
     * Isi file ekspor (version 2, format yang sama dengan TermUL mobile; diimpor berdasarkan id). Tidak pernah berisi
     * nilai variabel yang diingat per host.
     *
     * @param extras        sertakan field opsional TermUL ({@code appliesTo}, {@code dangerous}, {@code variables});
     *                      false = hanya id, judul, perintah, kategori, favorit
     * @param allCategories semua kategori; false = hanya kategori yang dipakai {@code snippets}
     */
    public byte[] export(List<Snippet> snippets, boolean extras, boolean allCategories) {
        var used = snippets.stream().map(Snippet::category).collect(java.util.stream.Collectors.toSet());
        var cats = state.categories().stream().filter(c -> allCategories || used.contains(c.id()))
                .map(c -> new CategoryJson(c.id(), c.name())).toList();
        var known = state.categories().stream().map(SnippetCategory::id).collect(java.util.stream.Collectors.toSet());
        var items = snippets.stream().map(s -> {
            var j = toJson(s);
            String category = known.contains(s.category()) ? j.category() : null;
            return extras ? new SnippetJson(j.id(), j.title(), j.command(), category, j.favorite(), j.appliesTo(),
                    j.dangerous(), j.variables())
                    : new SnippetJson(j.id(), j.title(), j.command(), category, j.favorite(), null, null, null);
        }).toList();
        try {
            return mapper.writeValueAsBytes(new FileJson(VERSION, cats, items, null));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Nilai variabel terakhir untuk snippet ini di host {@code hostKey} (mis. id profil); kosong kalau belum ada. */
    public Map<String, String> remembered(String hostKey, UUID snippetId) {
        return state.remembered().getOrDefault(hostKey, Map.of()).getOrDefault(snippetId.toString(), Map.of());
    }

    /** Ingat nilai variabel untuk host ini (hanya nilai yang valid). Tidak menulis disk kalau tidak berubah. */
    public void remember(String hostKey, UUID snippetId, Map<String, String> values) {
        var clean = new LinkedHashMap<String, String>();
        values.forEach((k, v) -> {
            if (SnippetVariables.isValidName(k) && SnippetVariables.isValidValue(v)) {
                clean.put(k, v);
            }
        });
        if (clean.isEmpty() || clean.equals(remembered(hostKey, snippetId))) {
            return;
        }
        mutate(s -> {
            var all = new LinkedHashMap<>(s.remembered());
            var perSnippet = new LinkedHashMap<>(all.getOrDefault(hostKey, Map.of()));
            perSnippet.put(snippetId.toString(), Map.copyOf(clean));
            all.put(hostKey, Map.copyOf(perSnippet));
            return new State(s.snippets(), s.categories(), Map.copyOf(all));
        });
    }

    /** @param listener dipanggil (di thread penulis) setelah isi store berubah dan tersimpan */
    public void addListener(Consumer<List<Snippet>> listener) {
        listeners.add(listener);
    }

    private void mutate(UnaryOperator<State> change) {
        State next;
        synchronized (lock) {
            next = change.apply(state);
            if (next == state) {
                return;
            }
            try {
                AtomicFiles.write(file, mapper.writeValueAsBytes(toJson(next)));
            } catch (IOException e) {
                throw new UncheckedIOException("Snippet tidak bisa disimpan ke " + file, e);
            }
            state = next;
        }
        listeners.forEach(l -> l.accept(next.snippets()));
    }

    // --- format file ---

    State parse(JsonNode root) throws IOException {
        if (!root.has("version") && root.has("format")) {
            return parseLegacy(root);
        }
        var json = mapper.treeToValue(root, FileJson.class);
        var categories = new ArrayList<SnippetCategory>();
        if (json.categories() != null) {
            json.categories().forEach(c -> categories.add(new SnippetCategory(c.id(), c.name())));
        }
        var snippets = new ArrayList<Snippet>();
        if (json.snippets() != null) {
            json.snippets().forEach(x -> snippets.add(fromJson(x)));
        }
        return new State(List.copyOf(snippets), List.copyOf(categories),
                json.remembered() == null ? Map.of() : copyRemembered(json.remembered()));
    }

    private State parseLegacy(JsonNode root) throws IOException {
        var snippets = new ArrayList<Snippet>();
        for (JsonNode node : root.path("snippets")) {
            var old = mapper.treeToValue(node, LegacySnippet.class);
            if (old.command() != null && old.command().strip().contains("\n")) {
                throw new IllegalArgumentException("Snippet format lama tidak boleh beberapa baris.");
            }
            snippets.add(new Snippet(UUID.fromString(old.id()), old.name(), old.command(), false, "", false, false,
                    SnippetScope.ofGroup(old.group()), Map.of()));
        }
        snippets.sort(java.util.Comparator.comparing(s -> s.title().toLowerCase(Locale.ROOT)));
        return new State(List.copyOf(snippets), List.of(), Map.of());
    }

    static Snippet fromJson(SnippetJson x) {
        var a = x.appliesTo();
        SnippetScope scope = SnippetScope.ALL;
        if (a != null && a.hosts() != null && !a.hosts().isEmpty()) {
            scope = new SnippetScope.Host(a.hosts().getFirst());
        } else if (a != null && a.folders() != null && !a.folders().isEmpty()) {
            scope = SnippetScope.ofGroup(a.folders().getFirst());
        }
        return new Snippet(UUID.fromString(x.id()), x.title(), x.command(), false, x.category(),
                Boolean.TRUE.equals(x.favorite()), Boolean.TRUE.equals(x.dangerous()), scope, x.variables());
    }

    static SnippetJson toJson(Snippet s) {
        AppliesTo appliesTo = switch (s.scope()) {
            case SnippetScope.All all -> null;
            case SnippetScope.Group(String path) -> new AppliesTo(null, List.of(path));
            case SnippetScope.Host(String name) -> new AppliesTo(List.of(name), null);
        };
        return new SnippetJson(s.id().toString(), s.title(), s.commandWithEnter(),
                s.category().isEmpty() ? null : s.category(), s.favorite(), appliesTo,
                s.dangerous() ? Boolean.TRUE : null, s.variables().isEmpty() ? null : s.variables());
    }

    private static FileJson toJson(State s) {
        return new FileJson(VERSION, s.categories().stream().map(c -> new CategoryJson(c.id(), c.name())).toList(),
                s.snippets().stream().map(SnippetStore::toJson).toList(), s.remembered());
    }

    private static Map<String, Map<String, Map<String, String>>> copyRemembered(
            Map<String, Map<String, Map<String, String>>> raw) {
        var out = new LinkedHashMap<String, Map<String, Map<String, String>>>();
        raw.forEach((host, perSnippet) -> {
            if (host != null && perSnippet != null) {
                var copy = new LinkedHashMap<String, Map<String, String>>();
                perSnippet.forEach((id, values) -> {
                    if (id != null && values != null) {
                        var clean = new LinkedHashMap<String, String>();
                        values.forEach((k, v) -> {
                            if (SnippetVariables.isValidName(k) && SnippetVariables.isValidValue(v)) {
                                clean.put(k, v);
                            }
                        });
                        copy.put(id, Map.copyOf(clean));
                    }
                });
                out.put(host, Map.copyOf(copy));
            }
        });
        return Map.copyOf(out);
    }

    private static int indexOf(List<Snippet> list, UUID id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static SnippetCategory findCategory(List<SnippetCategory> categories, String name) {
        return categories.stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    /** Id dari nama (huruf kecil, selain huruf/angka jadi {@code -}), diberi akhiran angka kalau sudah dipakai. */
    static String uniqueId(List<SnippetCategory> categories, String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "kategori";
        }
        String candidate = base;
        for (int n = 2; contains(categories, candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }

    private static boolean contains(List<SnippetCategory> categories, String id) {
        return categories.stream().anyMatch(c -> c.id().equals(id));
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
}

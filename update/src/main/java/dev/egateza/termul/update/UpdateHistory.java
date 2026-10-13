package dev.egateza.termul.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Riwayat update yang sudah benar-benar berjalan di instalasi ini, untuk halaman "Yang baru". Disimpan di file JSON
 * kecil di config (bukan di folder update, yang dibersihkan setiap pemasangan), terbaru di depan, maksimal
 * {@link #MAX_ENTRIES}. Catatan rilis disalin dari manifest yang tanda tangannya sudah diverifikasi.
 * File rusak atau hilang dianggap riwayat kosong. Blocking (disk): panggil di luar EDT.
 */
public final class UpdateHistory {

    public static final int MAX_ENTRIES = 5;
    private static final int FORMAT = 1;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Satu rilis yang pernah dipakai. */
    public record Entry(ReleaseVersion version, Instant appliedAt, String notes) {
        public Entry {
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(appliedAt, "appliedAt");
            notes = notes == null ? "" : notes;
        }
    }

    private final Path file;

    public UpdateHistory(Path file) {
        this.file = file;
    }

    /** @return riwayat, terbaru di depan; kosong kalau file belum ada atau tidak bisa dibaca */
    public List<Entry> load() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] data = in.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) {
                return List.of();
            }
            JsonNode root = JSON.readTree(data);
            if (root == null || root.path("format").asInt(-1) != FORMAT) {
                return List.of();
            }
            var entries = new ArrayList<Entry>();
            for (JsonNode e : root.path("entries")) {
                ReleaseVersion version = ReleaseVersion.parseOrNull(e.path("version").asText(null));
                Instant at = instantOrNull(e.path("appliedAt").asText(null));
                if (version != null && at != null) {
                    entries.add(new Entry(version, at, e.path("notes").asText("")));
                }
            }
            return sorted(entries);
        } catch (IOException e) {
            return List.of();
        }
    }

    public boolean contains(ReleaseVersion version) {
        return load().stream().anyMatch(e -> e.version().equals(version));
    }

    /**
     * Tambahkan rilis ke riwayat (sekali per versi), lalu simpan atomic.
     *
     * @return false kalau versi ini sudah tercatat
     */
    public boolean record(Entry entry) throws IOException {
        var entries = new ArrayList<>(load());
        if (entries.stream().anyMatch(e -> e.version().equals(entry.version()))) {
            return false;
        }
        entries.add(entry);
        write(sorted(entries));
        return true;
    }

    private void write(List<Entry> entries) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("format", FORMAT);
        var list = root.putArray("entries");
        for (Entry e : entries) {
            list.addObject().put("version", e.version().toString()).put("appliedAt", e.appliedAt().toString())
                    .put("notes", e.notes());
        }
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, ".update-history-", ".tmp");
        try {
            Files.write(tmp, JSON.writeValueAsBytes(root));
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Terbaru di depan (waktu dipakai, lalu versi), dipangkas ke {@link #MAX_ENTRIES}. */
    private static List<Entry> sorted(List<Entry> entries) {
        return entries.stream()
                .sorted(Comparator.comparing(Entry::appliedAt).thenComparing(Entry::version).reversed())
                .limit(MAX_ENTRIES)
                .toList();
    }

    private static Instant instantOrNull(String text) {
        try {
            return text == null ? null : Instant.parse(text);
        } catch (DateTimeException e) {
            return null;
        }
    }
}

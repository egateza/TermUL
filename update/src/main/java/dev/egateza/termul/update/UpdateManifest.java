package dev.egateza.termul.update;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Isi {@code manifest.json} sebuah rilis: daftar lengkap jar classpath aplikasi beserta hash-nya.
 *
 * @param generation generation installer minimum (lihat {@link UpdateProtocol#GENERATION})
 * @param mainClass  class yang {@code main}-nya dijalankan Bootstrap
 * @param notes      catatan rilis untuk ditampilkan ke user (ikut ditandatangani)
 * @param files      jar classpath, urutan = urutan classpath
 */
public record UpdateManifest(ReleaseVersion version, int generation, String mainClass, String notes,
                             List<FileEntry> files) {

    public static final int FORMAT = 1;
    /** Batas ukuran per jar (bcprov ~7 MB); melindungi dari manifest yang meminta unduhan raksasa. */
    public static final long MAX_FILE_SIZE = 64L * 1024 * 1024;
    public static final int MAX_FILES = 200;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}\\.jar");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+");
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Satu jar. {@code name} adalah nama file flat (tanpa folder), sekaligus nama aset di GitHub Releases. */
    public record FileEntry(String name, String sha256, long size) {
        public FileEntry {
            if (name == null || !NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Nama file tidak valid: " + name);
            }
            if (sha256 == null || !SHA256.matcher(sha256).matches()) {
                throw new IllegalArgumentException("SHA-256 tidak valid untuk " + name);
            }
            if (size <= 0 || size > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("Ukuran tidak valid untuk " + name + ": " + size);
            }
        }
    }

    public UpdateManifest {
        Objects.requireNonNull(version, "version");
        notes = notes == null ? "" : notes;
        files = List.copyOf(files);
        if (generation < 1) {
            throw new IllegalArgumentException("generation tidak valid: " + generation);
        }
        if (mainClass == null || !CLASS_NAME.matcher(mainClass).matches()) {
            throw new IllegalArgumentException("mainClass tidak valid: " + mainClass);
        }
        if (files.isEmpty() || files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Jumlah file tidak valid: " + files.size());
        }
        var names = new HashSet<String>();
        for (FileEntry f : files) {
            // Windows tidak membedakan huruf besar/kecil: dua nama yang hanya beda kapital bertabrakan
            if (!names.add(f.name().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Nama file ganda: " + f.name());
            }
        }
    }

    public long totalSize() {
        return files.stream().mapToLong(FileEntry::size).sum();
    }

    /**
     * Parse dan validasi. Panggil hanya pada byte yang tanda tangannya sudah diverifikasi, kecuali untuk membaca
     * versi agar bisa mengambil file tanda tangannya.
     */
    public static UpdateManifest parse(byte[] json) throws UpdateException {
        try {
            JsonNode root = JSON.readTree(json);
            if (root == null || !root.isObject()) {
                throw new UpdateException("Manifest update tidak valid: bukan objek JSON");
            }
            int format = root.path("format").asInt(-1);
            if (format != FORMAT) {
                throw new UpdateException("Format manifest update tidak dikenal: " + format
                        + ". Pasang installer TermUL terbaru dari halaman rilis.");
            }
            var files = new ArrayList<FileEntry>();
            for (JsonNode f : root.path("files")) {
                files.add(new FileEntry(text(f, "name"), text(f, "sha256"), f.path("size").asLong(-1)));
            }
            return new UpdateManifest(ReleaseVersion.parse(text(root, "version")), root.path("generation").asInt(-1),
                    text(root, "mainClass"), root.path("notes").asText(""), files);
        } catch (IOException e) {
            throw new UpdateException("Manifest update tidak bisa dibaca: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new UpdateException("Manifest update tidak valid: " + e.getMessage(), e);
        }
    }

    /** JSON UTF-8 yang ditandatangani dan diunggah (ReleaseTool). */
    public byte[] toJson() {
        ObjectNode root = JSON.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", version.toString());
        root.put("generation", generation);
        root.put("mainClass", mainClass);
        root.put("notes", notes);
        ArrayNode list = root.putArray("files");
        for (FileEntry f : files) {
            list.addObject().put("name", f.name()).put("sha256", f.sha256()).put("size", f.size());
        }
        try {
            return (JSON.writeValueAsString(root) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || !v.isTextual() ? null : v.asText();
    }
}

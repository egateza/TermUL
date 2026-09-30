package dev.egateza.termul.core.theme;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.egateza.termul.core.io.AtomicFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Menyimpan tema custom, satu file {@code <id>.json} per tema di satu direktori. File yang rusak atau tidak valid
 * dilewati (tidak dihapus, tidak ditimpa) supaya satu tema buruk tidak menggagalkan yang lain.
 */
public final class ThemeStore {

    private static final Logger log = LoggerFactory.getLogger(ThemeStore.class);

    private final Path dir;
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public ThemeStore(Path dir) {
        this.dir = Objects.requireNonNull(dir);
    }

    /** @return semua tema yang valid, urut menurut nama; kosong kalau direktori belum ada */
    public List<CustomTheme> list() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        var result = new ArrayList<CustomTheme>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : files) {
                CustomTheme theme = read(file);
                if (theme != null) {
                    result.add(theme);
                }
            }
        } catch (IOException e) {
            log.warn("Folder tema tidak bisa dibaca: {}", e.getMessage());
        }
        result.sort(Comparator.comparing(t -> t.name().toLowerCase(java.util.Locale.ROOT)));
        return List.copyOf(result);
    }

    public synchronized void save(CustomTheme theme) {
        try {
            Files.createDirectories(dir);
            AtomicFiles.write(fileOf(theme.id()), mapper.writeValueAsBytes(theme));
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal menyimpan tema " + theme.id(), e);
        }
    }

    /** @return true kalau temanya ada dan terhapus */
    public synchronized boolean delete(String id) {
        try {
            return Files.deleteIfExists(fileOf(id));
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal menghapus tema " + id, e);
        }
    }

    /**
     * Baca satu file tema dari lokasi mana pun (impor).
     *
     * @throws IOException              file tidak terbaca atau bukan JSON tema
     * @throws IllegalArgumentException isi tema tidak valid (warna salah, id salah, dst.)
     */
    public CustomTheme importFrom(Path file) throws IOException {
        try {
            return mapper.readValue(Files.readAllBytes(file), CustomTheme.class);
        } catch (com.fasterxml.jackson.databind.JsonMappingException e) {
            // pesan validasi record dibungkus Jackson; ambil pesan aslinya supaya bisa ditampilkan ke user
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalArgumentException(cause.getMessage(), e);
        }
    }

    /** Tulis satu tema ke lokasi mana pun (ekspor untuk berbagi). */
    public void exportTo(CustomTheme theme, Path file) throws IOException {
        AtomicFiles.write(file, mapper.writeValueAsBytes(theme));
    }

    private Path fileOf(String id) {
        // id divalidasi oleh CustomTheme; ini pagar kedua agar id dari luar tidak keluar dari direktori tema
        if (id == null || !id.matches("[a-z0-9][a-z0-9-]{0,39}")) {
            throw new IllegalArgumentException("Id tema tidak valid");
        }
        return dir.resolve(id + ".json");
    }

    private CustomTheme read(Path file) {
        try {
            CustomTheme theme = mapper.readValue(Files.readAllBytes(file), CustomTheme.class);
            String expected = theme.id() + ".json";
            if (!file.getFileName().toString().equals(expected)) {
                log.warn("Tema {} dilewati: nama file tidak sama dengan id ({})", file.getFileName(), theme.id());
                return null;
            }
            return theme;
        } catch (IOException | RuntimeException e) {
            log.warn("Tema {} dilewati: {}", file.getFileName(), e.getMessage());
            return null;
        }
    }
}

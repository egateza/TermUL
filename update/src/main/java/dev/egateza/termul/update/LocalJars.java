package dev.egateza.termul.update;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Jar yang sudah ada di mesin ini (classpath bawaan installer dan folder update yang sedang berjalan), dicari
 * berdasarkan ukuran lalu SHA-256. Hash hanya dihitung untuk file yang ukurannya cocok, dan di-cache.
 * Tidak thread-safe; dipakai dari satu thread background.
 */
public final class LocalJars {

    private final List<Path> candidates;
    private final Map<Path, String> hashes = new HashMap<>();

    public LocalJars(List<Path> candidates) {
        this.candidates = List.copyOf(candidates);
    }

    /** Jar dari {@code java.class.path} (classpath bawaan) plus semua jar di {@code extraDirs}. */
    public static LocalJars fromClassPath(String classPath, List<Path> extraDirs) {
        var list = new ArrayList<Path>();
        if (classPath != null) {
            for (String entry : classPath.split(File.pathSeparator)) {
                if (entry.endsWith(".jar")) {
                    list.add(Path.of(entry));
                }
            }
        }
        for (Path dir : extraDirs) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().endsWith(".jar")).forEach(list::add);
            } catch (IOException e) {
                // folder tidak ada / tidak bisa dibaca: jar-nya diunduh saja
            }
        }
        return new LocalJars(list);
    }

    public Optional<Path> find(UpdateManifest.FileEntry entry) {
        for (Path p : candidates) {
            try {
                if (Files.isRegularFile(p) && Files.size(p) == entry.size() && entry.sha256().equals(hash(p))) {
                    return Optional.of(p);
                }
            } catch (IOException e) {
                // lewati kandidat yang tidak bisa dibaca
            }
        }
        return Optional.empty();
    }

    private String hash(Path p) throws IOException {
        String h = hashes.get(p);
        if (h == null) {
            h = Hashes.sha256(p);
            hashes.put(p, h);
        }
        return h;
    }
}

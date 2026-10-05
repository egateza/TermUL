package dev.egateza.termul.app.sftp;

import dev.egateza.termul.core.Os;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Icon file sesuai association Windows (seperti Explorer/WinSCP), per ekstensi.
 *
 * <p>Shell Windows butuh file lokal, jadi dibuat file contoh kosong {@code probe.<ext>} di {@code probeDir}; icon
 * diambil dari association ekstensinya. File contoh tidak pernah dibuka/dijalankan. Pemuatan di virtual thread
 * (disk I/O + shell), hasilnya di-cache; renderer di EDT hanya membaca cache.
 */
public final class SystemFileIcons {

    private static final Logger log = LoggerFactory.getLogger(SystemFileIcons.class);
    private static final Pattern SAFE_EXT = Pattern.compile("[a-z0-9_-]{1,16}");

    private final Path probeDir;
    private final int size;
    private final boolean enabled;
    private final Map<String, Optional<Icon>> cache = new ConcurrentHashMap<>();
    private final Map<String, List<Runnable>> pending = new HashMap<>(); // EDT

    public SystemFileIcons(Path probeDir, int size) {
        this.probeDir = probeDir;
        this.size = size;
        this.enabled = Os.current().isWindows();
    }

    /**
     * Dipanggil di EDT.
     *
     * @param onLoaded dipanggil di EDT setelah icon ekstensi ini selesai dimuat (mis. repaint tabel)
     * @return icon Windows, atau null kalau belum dimuat / tidak tersedia (pakai icon bawaan aplikasi)
     */
    public Icon icon(String fileName, Runnable onLoaded) {
        String ext = extension(fileName);
        if (!enabled || ext == null) {
            return null;
        }
        Optional<Icon> cached = cache.get(ext);
        if (cached != null) {
            return cached.orElse(null);
        }
        var waiting = pending.get(ext);
        if (waiting != null) {
            waiting.add(onLoaded);
            return null;
        }
        waiting = new ArrayList<>(List.of(onLoaded));
        pending.put(ext, waiting);
        Thread.ofVirtual().name("file-icon-" + ext).start(() -> {
            cache.put(ext, Optional.ofNullable(load(ext)));
            SwingUtilities.invokeLater(() -> pending.remove(ext).forEach(Runnable::run));
        });
        return null;
    }

    private Icon load(String ext) {
        try {
            Files.createDirectories(probeDir);
            Path probe = probeDir.resolve("probe." + ext);
            if (!Files.exists(probe)) {
                Files.createFile(probe);
            }
            File file = probe.toFile();
            return FileSystemView.getFileSystemView().getSystemIcon(file, size, size);
        } catch (IOException | RuntimeException e) {
            log.debug("Icon sistem untuk .{} tidak tersedia: {}", ext, e.toString());
            return null;
        }
    }

    /** Ekstensi huruf kecil yang aman jadi nama file lokal; null kalau tidak ada / tidak aman. */
    static String extension(String fileName) {
        String name = fileName.replaceAll("[. ]+$", "");
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return null;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return SAFE_EXT.matcher(ext).matches() ? ext : null;
    }
}

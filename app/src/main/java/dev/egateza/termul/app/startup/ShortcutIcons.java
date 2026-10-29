package dev.egateza.termul.app.startup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arahkan ikon shortcut ({@code .lnk}) TermUL ke file ikon dari jar (ADR 0010). Hanya shortcut yang targetnya
 * {@code TermUL.exe} instalasi ini yang diubah; shortcut lain, dan shortcut yang ikonnya sudah benar, tidak disentuh.
 */
final class ShortcutIcons {

    private static final Logger log = LoggerFactory.getLogger(ShortcutIcons.class);

    /** Kedalaman folder yang diperiksa: Start Menu menaruh shortcut di subfolder grup ({@code Programs\TermUL}). */
    static final int MAX_DEPTH = 2;

    /** Akses isi shortcut (COM {@code IShellLink} di Windows, palsu di test). */
    interface Links {
        /** Path target shortcut, atau "" kalau bukan file (mis. shortcut advertised / URL). */
        String target(Path lnk) throws IOException;

        /** Path file ikon shortcut, atau "" kalau memakai ikon target. */
        String icon(Path lnk) throws IOException;

        void setIcon(Path lnk, String iconPath) throws IOException;
    }

    private ShortcutIcons() {
    }

    /**
     * @param dirs    folder shortcut (Start Menu, desktop, pin taskbar); yang tidak ada dilewati
     * @param appPath {@code TermUL.exe} instalasi ini
     * @return shortcut yang ikonnya diubah
     */
    static List<Path> refresh(List<Path> dirs, String appPath, Path iconFile, Links links) {
        var changed = new ArrayList<Path>();
        String icon = iconFile.toString();
        for (Path dir : dirs) {
            for (Path lnk : shortcutsIn(dir)) {
                try {
                    if (samePath(links.target(lnk), appPath) && !samePath(links.icon(lnk), icon)) {
                        links.setIcon(lnk, icon);
                        changed.add(lnk);
                    }
                } catch (IOException | RuntimeException e) {
                    // shortcut milik user: jangan sampai satu file rusak menghentikan yang lain
                    log.warn("Ikon shortcut {} tidak bisa diperbarui: {}", lnk, e.getMessage());
                }
            }
        }
        return changed;
    }

    static List<Path> shortcutsIn(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir, MAX_DEPTH)) {
            return files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lnk"))
                    .filter(Files::isRegularFile).toList();
        } catch (IOException | java.io.UncheckedIOException e) {
            log.warn("Folder shortcut {} tidak terbaca: {}", dir, e.getMessage());
            return List.of();
        }
    }

    /** Path Windows sama: tanpa membedakan huruf besar/kecil dan pemisah {@code /} vs {@code \}. */
    static boolean samePath(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        return normalize(a).equals(normalize(b));
    }

    private static String normalize(String p) {
        return p.strip().replace('/', '\\').toLowerCase(Locale.ROOT);
    }
}

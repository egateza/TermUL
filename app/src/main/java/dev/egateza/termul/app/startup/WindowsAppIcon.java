package dev.egateza.termul.app.startup;

import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32Util;
import dev.egateza.termul.core.AppPaths;
import dev.egateza.termul.core.Os;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ikon shortcut dan menu klik kanan Explorer yang ikut berganti lewat update menu (ADR 0010). Ikon di dalam
 * {@code TermUL.exe} hanya berubah lewat installer; shortcut (Start Menu, desktop, pin taskbar) dan menu Explorer bisa
 * menunjuk ke file {@code .ico} terpisah, yang disalin dari jar di sini. Dijalankan sekali per start di thread latar.
 */
public final class WindowsAppIcon {

    private static final Logger log = LoggerFactory.getLogger(WindowsAppIcon.class);

    private WindowsAppIcon() {
    }

    /** Mulai di thread platform latar (COM terikat ke thread OS, jadi bukan virtual thread). Hanya Windows terinstal. */
    public static void refreshInBackground(AppPaths paths) {
        String appPath = System.getProperty(AutoStart.JPACKAGE_APP_PATH);
        if (!Os.current().isWindows() || appPath == null || appPath.isBlank()) {
            return; // dari IDE / JAR: tidak ada shortcut milik instalasi
        }
        var thread = new Thread(() -> refresh(paths.appIconDir(), appPath), "app-icon");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void refresh(Path iconDir, String appPath) {
        try {
            Path icon = AppIconFile.install(iconDir, AppIconFile.bundled());
            AppIconFile.use(icon);
            List<Path> changed;
            try (var links = WinShellLink.open()) {
                changed = ShortcutIcons.refresh(shortcutDirs(), appPath, icon, links);
            }
            var menu = ExplorerMenu.forCurrentOs();
            boolean menuChanged = menu != null && menu.refreshIcon();
            if (!changed.isEmpty() || menuChanged) {
                WinShellLink.notifyChanged(changed);
                log.info("Ikon aplikasi diperbarui: {} shortcut{}", changed.size(),
                        menuChanged ? " + menu klik kanan Explorer" : "");
            }
            AppIconFile.deleteOthers(iconDir, icon);
        } catch (Exception | LinkageError e) {
            // fitur kosmetik: shortcut tetap memakai ikon exe kalau gagal
            log.warn("Ikon shortcut tidak bisa diperbarui: {}", e.toString());
        }
    }

    /** Start Menu (Programs), desktop, dan shortcut yang di-pin ke taskbar / Start milik user ini. */
    static List<Path> shortcutDirs() {
        var dirs = new ArrayList<Path>();
        known(dirs, KnownFolders.FOLDERID_Programs);
        known(dirs, KnownFolders.FOLDERID_Desktop);
        String roaming = System.getenv("APPDATA");
        if (roaming != null && !roaming.isBlank()) {
            Path pinned = Path.of(roaming, "Microsoft", "Internet Explorer", "Quick Launch", "User Pinned");
            dirs.add(pinned.resolve("TaskBar"));
            dirs.add(pinned.resolve("StartMenu"));
        }
        return dirs;
    }

    private static void known(List<Path> dirs, com.sun.jna.platform.win32.Guid.GUID id) {
        try {
            dirs.add(Path.of(Shell32Util.getKnownFolderPath(id)));
        } catch (RuntimeException e) {
            log.debug("Known folder {} tidak tersedia: {}", id, e.getMessage());
        }
    }
}

package dev.egateza.termul.app.startup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ExplorerMenu} lewat cascading menu di {@code HKCU\Software\Classes} (tanpa admin): klik kanan pada folder, pada
 * drive, dan pada area kosong di dalam folder. Submenu ditulis sebagai subkey {@code shell\NN} di bawah key
 * {@value #VERB} dengan {@code SubCommands} kosong. Di Windows 11 menu ini ada di "Show more options", sama seperti
 * entri klasik lain (mis. "Open Git Bash here").
 */
final class WindowsExplorerMenu implements ExplorerMenu {

    private static final Logger log = LoggerFactory.getLogger(WindowsExplorerMenu.class);

    static final String VERB = "TermUL";
    /** Klik kanan area kosong di dalam folder; dipakai juga untuk membaca status. */
    static final String BACKGROUND_KEY = "Software\\Classes\\Directory\\Background\\shell\\" + VERB;
    static final List<String> ROOT_KEYS = List.of(
            BACKGROUND_KEY,
            "Software\\Classes\\Directory\\shell\\" + VERB,
            "Software\\Classes\\Drive\\shell\\" + VERB);
    /** Path TermUL.exe yang memasang menu: menu dari lokasi instalasi lain dianggap belum aktif. */
    static final String APP_PATH_VALUE = "TermULAppPath";

    private final WinRegistry registry;
    private final String appPath;

    WindowsExplorerMenu(WinRegistry registry, String appPath) {
        this.registry = Objects.requireNonNull(registry);
        this.appPath = Objects.requireNonNull(appPath);
    }

    /**
     * Command satu item. {@code %V} = folder yang diklik; {@code --cwd} terakhir karena {@code "C:\"} (root drive)
     * sampai sebagai {@code C:"}, lihat {@link OpenLocalRequest#parse}.
     */
    String command(Entry entry) {
        return "\"" + appPath + "\" " + OpenLocalRequest.ARG_OPEN_LOCAL + entry.shell() + " "
                + OpenLocalRequest.ARG_CWD + " \"%V\"";
    }

    @Override
    public Status status() {
        try {
            String installed = registry.readString(BACKGROUND_KEY, APP_PATH_VALUE);
            return installed != null && installed.equalsIgnoreCase(appPath) ? Status.ENABLED : Status.DISABLED;
        } catch (IOException e) {
            log.warn("Status menu klik kanan Explorer tidak terbaca: {}", e.getMessage());
            return Status.DISABLED;
        }
    }

    @Override
    public void setEnabled(boolean on, List<Entry> entries) throws IOException {
        for (String root : ROOT_KEYS) {
            registry.deleteTree(root); // item lama (profil yang sudah dihapus) ikut hilang
        }
        if (on) {
            for (String root : ROOT_KEYS) {
                write(root, entries);
            }
        }
    }

    @Override
    public void sync(List<Entry> entries) throws IOException {
        if (status() == Status.ENABLED) {
            setEnabled(true, entries);
        }
    }

    @Override
    public boolean refreshIcon() throws IOException {
        if (status() != Status.ENABLED) {
            return false;
        }
        boolean changed = false;
        String icon = icon();
        for (String root : ROOT_KEYS) {
            if (!icon.equals(registry.readString(root, "Icon"))) {
                registry.writeString(root, "Icon", icon);
                changed = true;
            }
        }
        return changed;
    }

    /** File ikon dari jar kalau sudah disalin (ikut update menu, ADR 0010), selain itu ikon di dalam exe. */
    String icon() {
        Path file = AppIconFile.installed();
        return file != null ? file.toString() : appPath + ",0";
    }

    private void write(String root, List<Entry> entries) throws IOException {
        registry.writeString(root, "MUIVerb", VERB);
        registry.writeString(root, "Icon", icon());
        registry.writeString(root, "SubCommands", ""); // kosong = submenu dari subkey "shell"
        int n = 0;
        for (Entry entry : entries.subList(0, Math.min(entries.size(), MAX_ENTRIES))) {
            String item = root + "\\shell\\" + "%02d".formatted(++n); // Explorer mengurutkan item menurut nama key
            registry.writeString(item, "MUIVerb", entry.label().replace("&", "&&")); // & = akselerator menu
            registry.writeString(item + "\\command", "", command(entry));
        }
        registry.writeString(root, APP_PATH_VALUE, appPath); // terakhir: menandai pemasangan lengkap
    }
}

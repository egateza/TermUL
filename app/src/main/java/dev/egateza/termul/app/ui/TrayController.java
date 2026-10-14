package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ikon TermUL di tray (notification area Windows / menu bar macOS) dengan menu: tampilkan window, konek cepat ke host
 * favorit/terakhir, widget streak, dan keluar. Menu memakai {@link PopupMenu} AWT (native), karena {@code JPopupMenu} tidak didukung
 * {@link TrayIcon}. Semua method dan aksi berjalan di EDT.
 */
public final class TrayController {

    private static final Logger log = LoggerFactory.getLogger(TrayController.class);

    /** Batas host di submenu "Hubungkan" supaya menu tidak lebih tinggi dari layar. */
    static final int MAX_HOSTS = 12;

    /** Aksi dari menu tray; dipanggil di EDT. */
    public interface Actions {
        void showWindow();

        void connect(HostProfile profile);

        void setStreakWidget(boolean on);

        void exit();
    }

    private final Actions actions;
    private TrayIcon icon; // EDT; null selama ikon tidak terpasang
    private ProfileSnapshot snapshot = ProfileSnapshot.empty(); // EDT
    private boolean widgetShown; // EDT
    private CheckboxMenuItem widgetItem; // EDT; item di menu yang sedang terpasang

    public TrayController(Actions actions) {
        this.actions = Objects.requireNonNull(actions);
    }

    /** @return false di desktop tanpa tray (mis. sebagian desktop Linux) atau tanpa GUI */
    public static boolean supported() {
        return SystemTray.isSupported();
    }

    /** @return true kalau ikon sedang terpasang (window boleh disembunyikan ke tray) */
    public boolean active() {
        return icon != null;
    }

    /** Pasang atau lepas ikon tray. Kalau OS menolak, ikon tetap tidak terpasang ({@link #active()} false). */
    public void setEnabled(boolean on) {
        if (on == active()) {
            return;
        }
        if (!on) {
            SystemTray.getSystemTray().remove(icon);
            icon = null;
            return;
        }
        if (!supported()) {
            return;
        }
        var tray = SystemTray.getSystemTray();
        var created = new TrayIcon(image(tray.getTrayIconSize().width), "TermUL", buildMenu());
        created.setImageAutoSize(true);
        // Windows: dobel klik membuka window, klik sekali tidak melakukan apa-apa (klik kanan = menu);
        // di macOS klik selalu membuka menu
        created.addActionListener(e -> actions.showWindow());
        try {
            tray.add(created);
            icon = created;
        } catch (AWTException e) {
            log.warn("Ikon tray tidak bisa dipasang: {}", e.getMessage());
        }
    }

    /** Daftar host berubah: submenu "Hubungkan" dibangun ulang. */
    public void setSnapshot(ProfileSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot);
        if (icon != null) {
            icon.setPopupMenu(buildMenu());
        }
    }

    /** Status widget streak berubah (dari menu lain): centang di menu tray ikut. */
    public void setStreakWidgetShown(boolean on) {
        widgetShown = on;
        if (widgetItem != null) {
            widgetItem.setState(on);
        }
    }

    /** Notifikasi singkat dari ikon tray; tidak melakukan apa-apa kalau ikon tidak terpasang. */
    public void notify(String title, String message) {
        if (icon != null) {
            icon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    public void close() {
        setEnabled(false);
    }

    private PopupMenu buildMenu() {
        var menu = new PopupMenu();
        menu.add(item(I18n.t("tray.show"), actions::showWindow));
        var connect = new Menu(I18n.t("tray.connect"));
        var hosts = quickHosts(snapshot);
        if (hosts.isEmpty()) {
            var none = new MenuItem(I18n.t("tray.connect.empty"));
            none.setEnabled(false);
            connect.add(none);
        }
        for (var profile : hosts) {
            connect.add(item(profile.name(), () -> actions.connect(profile)));
        }
        menu.add(connect);
        var widget = new CheckboxMenuItem(I18n.t("tray.streakWidget"), widgetShown);
        widget.addItemListener(e -> actions.setStreakWidget(widget.getState()));
        widgetItem = widget;
        menu.add(widget);
        menu.addSeparator();
        menu.add(item(I18n.t("tray.exit"), actions::exit));
        return menu;
    }

    private static MenuItem item(String label, Runnable action) {
        var item = new MenuItem(label);
        item.addActionListener(e -> action.run()); // ActionEvent menu AWT datang di EDT
        return item;
    }

    /** Favorit lebih dulu (urutan favorit), lalu host terakhir dipakai; tanpa duplikat, paling banyak {@link #MAX_HOSTS}. */
    static List<HostProfile> quickHosts(ProfileSnapshot snapshot) {
        var byId = new LinkedHashMap<UUID, HostProfile>();
        for (var p : snapshot.favoriteProfiles()) {
            byId.putIfAbsent(p.id(), p);
        }
        for (var p : snapshot.recentProfiles()) {
            byId.putIfAbsent(p.id(), p);
        }
        var list = new ArrayList<>(byId.values());
        return List.copyOf(list.subList(0, Math.min(list.size(), MAX_HOSTS)));
    }

    /** Ikon aplikasi yang ukurannya paling dekat (dan tidak lebih kecil) dari ukuran ikon tray. */
    private static Image image(int size) {
        var icons = MainFrame.appIcons();
        return icons.stream()
                .filter(img -> img.getWidth(null) >= size)
                .min(Comparator.comparingInt(img -> img.getWidth(null)))
                .orElseGet(() -> icons.getLast());
    }
}

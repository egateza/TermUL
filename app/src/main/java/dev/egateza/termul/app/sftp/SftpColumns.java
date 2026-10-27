package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.config.AppConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.TableColumn;

/**
 * Kolom tabel SFTP (remote & lokal) yang disembunyikan, berlaku untuk semua panel sekaligus. Diatur lewat klik kanan
 * header tabel atau menu Terminal; disimpan di {@code config.json} ({@link AppConfig#sftpHiddenColumns()}).
 * Kolom nama selalu tampil. Semua method dipanggil di EDT.
 */
public final class SftpColumns {

    /** Id kolom yang bisa disembunyikan → key label i18n, urut seperti tampil di menu. */
    static final Map<String, String> LABELS = labels();

    private static final Set<String> hidden = new LinkedHashSet<>(); // EDT
    private static Consumer<List<String>> save = list -> { };     // EDT
    /**
     * Tabel yang terpasang; weak supaya panel yang sudah ditutup bisa di-GC. {@link Table} disimpan sebagai client
     * property tabelnya, bukan value map: value WeakHashMap yang memegang key-nya membuat entry tidak pernah dilepas
     * (panel SFTP, dan tab terminal tempatnya, tertahan selamanya). EDT.
     */
    private static final Set<JTable> tables = Collections.newSetFromMap(new WeakHashMap<>());
    private static final String TABLE_KEY = SftpColumns.class.getName();

    private SftpColumns() {
    }

    private static Map<String, String> labels() {
        var m = new LinkedHashMap<String, String>();
        m.put("size", "sftp.column.size");
        m.put("type", "sftp.local.column.type");
        m.put("modified", "sftp.column.modified");
        m.put("mode", "sftp.column.mode");
        m.put("owner", "sftp.column.owner");
        m.put("group", "sftp.column.group");
        return m;
    }

    /**
     * Pilihan awal dari config dan cara menyimpannya; panggil sekali saat startup.
     *
     * @param save dipanggil dengan daftar id kolom tersembunyi setiap kali user mengubahnya
     */
    public static void configure(List<String> initiallyHidden, Consumer<List<String>> save) {
        hidden.clear();
        hidden.addAll(initiallyHidden);
        SftpColumns.save = save;
        applyAll();
    }

    private static void applyAll() {
        for (var table : tables) {
            if (table.getClientProperty(TABLE_KEY) instanceof Table t) {
                t.apply();
            }
        }
    }

    public static boolean isHidden(String id) {
        return hidden.contains(id);
    }

    /** Tampilkan/sembunyikan kolom di semua tabel SFTP yang terbuka, lalu simpan. */
    public static void setHidden(String id, boolean hide) {
        if (AppConfig.SFTP_COLUMN_NAME.equals(id) || (hide ? !hidden.add(id) : !hidden.remove(id))) {
            return;
        }
        applyAll();
        save.accept(List.copyOf(hidden));
    }

    /**
     * Pasang pengaturan kolom pada tabel: kolom tersembunyi langsung dilepas dan klik kanan header menampilkan
     * daftar kolom.
     *
     * @param ids id kolom per indeks model
     */
    static void install(JTable table, String[] ids) {
        var t = new Table(table, ids);
        table.putClientProperty(TABLE_KEY, t);
        tables.add(table);
        t.apply();
        var popup = new JPopupMenu();
        popup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                popup.removeAll();
                for (String id : ids) {
                    if (LABELS.containsKey(id)) {
                        popup.add(item(id));
                    }
                }
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        table.getTableHeader().setComponentPopupMenu(popup);
        table.getTableHeader().setToolTipText(I18n.t("sftp.columns.tip"));
    }

    /** Submenu berisi semua kolom (remote & lokal) untuk menu aplikasi; centang dibaca saat menu dibuka. */
    public static JMenu menu(String title) {
        var menu = new JMenu(title);
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                menu.removeAll();
                LABELS.keySet().forEach(id -> menu.add(item(id)));
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        return menu;
    }

    private static JCheckBoxMenuItem item(String id) {
        var item = new JCheckBoxMenuItem(I18n.t(LABELS.get(id)), !hidden.contains(id));
        item.addActionListener(e -> setHidden(id, !item.isSelected()));
        return item;
    }

    /** Kolom asli satu tabel; kolom tersembunyi dilepas dari column model dan dipasang lagi saat ditampilkan. */
    private static final class Table {
        private final JTable table;
        private final String[] ids;
        private final List<TableColumn> all = new ArrayList<>(); // urut indeks model; lebar kolom ikut tersimpan

        Table(JTable table, String[] ids) {
            this.table = table;
            this.ids = ids;
            var model = table.getColumnModel();
            for (int i = 0; i < model.getColumnCount(); i++) {
                all.add(model.getColumn(i));
            }
            all.sort(java.util.Comparator.comparingInt(TableColumn::getModelIndex));
        }

        void apply() {
            var model = table.getColumnModel();
            for (var column : all) {
                boolean hide = hidden.contains(ids[column.getModelIndex()]);
                boolean shown = visibleIndex(column) >= 0;
                if (hide && shown) {
                    model.removeColumn(column);
                } else if (!hide && !shown) {
                    model.addColumn(column);
                    // kembali ke posisi asalnya: setelah kolom tampil yang indeks modelnya lebih kecil
                    int target = 0;
                    for (int i = 0; i < model.getColumnCount() - 1; i++) {
                        if (model.getColumn(i).getModelIndex() < column.getModelIndex()) {
                            target = i + 1;
                        }
                    }
                    model.moveColumn(model.getColumnCount() - 1, target);
                }
            }
        }

        private int visibleIndex(TableColumn column) {
            var model = table.getColumnModel();
            for (int i = 0; i < model.getColumnCount(); i++) {
                if (model.getColumn(i) == column) {
                    return i;
                }
            }
            return -1;
        }
    }
}

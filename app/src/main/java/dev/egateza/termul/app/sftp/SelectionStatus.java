package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.i18n.I18n;
import java.util.Locale;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;

/**
 * Baris di bawah tabel file: jumlah item (yang tampil / total, yang dipilih) dan total ukuran file-nya, mis.
 * "9 dari 99 item dipilih · 46.8 MB". Ukuran folder tidak diketahui tanpa membaca isinya, jadi folder hanya dihitung
 * jumlahnya. Diperbarui saat seleksi, filter, atau isi tabel berubah. EDT.
 */
final class SelectionStatus extends JLabel {

    private final JTable table;
    private final IntPredicate isDirectory;
    private final IntToLongFunction size;

    /**
     * @param isDirectory true kalau baris model tersebut folder
     * @param size        ukuran file pada baris model tersebut (byte)
     */
    SelectionStatus(JTable table, IntPredicate isDirectory, IntToLongFunction size) {
        super(" ");
        this.table = table;
        this.isDirectory = isDirectory;
        this.size = size;
        setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        table.getSelectionModel().addListSelectionListener(e -> refresh());
        table.getModel().addTableModelListener(e -> refresh());
        if (table.getRowSorter() != null) {
            table.getRowSorter().addRowSorterListener(e -> refresh()); // filter nama berubah
        }
        refresh();
    }

    /** Ringkasan untuk ditampilkan. */
    record Summary(int total, int shown, int selected, int selectedFolders, int files, long bytes) {
    }

    void refresh() {
        int[] rows = table.getSelectedRows();
        boolean selection = rows.length > 0;
        if (!selection) { // tanpa seleksi: ringkas semua yang tampil
            rows = new int[table.getRowCount()];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = i;
            }
        }
        int folders = 0;
        int files = 0;
        long bytes = 0;
        for (int viewRow : rows) {
            int row = table.convertRowIndexToModel(viewRow);
            if (isDirectory.test(row)) {
                folders++;
            } else {
                files++;
                bytes += size.applyAsLong(row);
            }
        }
        var summary = new Summary(table.getModel().getRowCount(), table.getRowCount(), selection ? rows.length : 0,
                selection ? folders : 0, files, bytes);
        setText(text(summary));
        setToolTipText(files == 0 ? null : I18n.t("sftp.status.bytes", String.format(Locale.getDefault(), "%,d", bytes)));
    }

    static String text(Summary s) {
        String count;
        if (s.selected() > 0) {
            count = I18n.t("sftp.status.selected", String.valueOf(s.selected()), String.valueOf(s.shown()));
            if (s.selectedFolders() > 0) {
                count += " " + I18n.t("sftp.status.folders", String.valueOf(s.selectedFolders()));
            }
        } else if (s.shown() < s.total()) {
            count = I18n.t("sftp.status.filtered", String.valueOf(s.shown()), String.valueOf(s.total()));
        } else {
            count = I18n.t("sftp.status.items", String.valueOf(s.total()));
        }
        return s.files() == 0 ? count : count + "  ·  " + Formats.size(s.bytes());
    }
}

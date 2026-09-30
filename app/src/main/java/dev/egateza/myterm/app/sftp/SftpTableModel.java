package dev.egateza.myterm.app.sftp;

import dev.egateza.myterm.sftp.RemoteEntry;
import java.util.Comparator;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** Model tabel isi direktori remote. Hanya diakses di EDT. */
public final class SftpTableModel extends AbstractTableModel {

    public static final int COL_NAME = 0;
    public static final int COL_SIZE = 1;
    public static final int COL_MODIFIED = 2;
    public static final int COL_MODE = 3;
    public static final int COL_OWNER = 4;
    public static final int COL_GROUP = 5;

    private static final String[] COLUMNS = {"Nama", "Ukuran", "Diubah", "Mode", "Owner", "Group"};

    private List<RemoteEntry> entries = List.of();

    public void setEntries(List<RemoteEntry> entries) {
        this.entries = List.copyOf(entries);
        fireTableDataChanged();
    }

    public RemoteEntry entryAt(int row) {
        return entries.get(row);
    }

    public List<RemoteEntry> entries() {
        return entries;
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    /** Nilai mentah per kolom: sorting memakai comparator di {@link #comparator(int)}. */
    @Override
    public Object getValueAt(int row, int column) {
        return entries.get(row);
    }

    /** Teks tampilan. */
    public static String display(RemoteEntry e, int column) {
        return switch (column) {
            case COL_NAME -> e.isDirectory() ? e.name() + "/" : e.name();
            case COL_SIZE -> e.isDirectory() ? "" : Formats.size(e.size());
            case COL_MODIFIED -> Formats.time(e.modified());
            case COL_MODE -> e.modeString();
            case COL_OWNER -> e.owner();
            case COL_GROUP -> e.group();
            default -> "";
        };
    }

    /** Comparator per kolom; direktori selalu di atas untuk kolom nama. */
    public static Comparator<RemoteEntry> comparator(int column) {
        Comparator<RemoteEntry> dirsFirst = Comparator.comparing(e -> !e.isDirectory());
        return switch (column) {
            case COL_NAME -> RemoteEntry.DIRECTORIES_FIRST;
            case COL_SIZE -> dirsFirst.thenComparingLong(RemoteEntry::size);
            case COL_MODIFIED -> Comparator.comparing(RemoteEntry::modified);
            case COL_MODE -> Comparator.comparingInt(RemoteEntry::mode);
            case COL_OWNER -> Comparator.comparing(RemoteEntry::owner);
            case COL_GROUP -> Comparator.comparing(RemoteEntry::group);
            default -> RemoteEntry.DIRECTORIES_FIRST;
        };
    }
}

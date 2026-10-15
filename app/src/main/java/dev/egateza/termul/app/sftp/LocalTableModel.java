package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.i18n.I18n;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.table.AbstractTableModel;

/** Model tabel isi direktori lokal untuk tampilan SFTP dua panel. Hanya diakses di EDT. */
public final class LocalTableModel extends AbstractTableModel {

    public static final int COL_NAME = 0;
    public static final int COL_SIZE = 1;
    public static final int COL_TYPE = 2;
    public static final int COL_MODIFIED = 3;

    /** Id kolom per indeks (lihat {@link SftpColumns}). */
    static final String[] COLUMN_IDS = {"name", "size", "type", "modified"};

    private static final String[] COLUMN_KEYS = {"sftp.column.name", "sftp.column.size", "sftp.local.column.type",
            "sftp.column.modified"};

    /** Satu file/direktori lokal; atribut dibaca sekali saat listing (di luar EDT). */
    public record Entry(String name, Path path, boolean directory, long size, Instant modified) {

        /** "Folder", atau ekstensi + " file" (mis. "PHP file"), seperti kolom Type di Explorer. */
        public String type() {
            if (directory) {
                return I18n.t("sftp.local.type.folder");
            }
            String ext = SystemFileIcons.extension(name);
            return ext == null ? I18n.t("sftp.local.type.plain") : I18n.t("sftp.local.type.file", ext.toUpperCase(Locale.ROOT));
        }
    }

    private List<Entry> entries = List.of();

    public void setEntries(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        fireTableDataChanged();
    }

    public Entry entryAt(int row) {
        return entries.get(row);
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_KEYS.length;
    }

    @Override
    public String getColumnName(int column) {
        return I18n.t(COLUMN_KEYS[column]);
    }

    @Override
    public Object getValueAt(int row, int column) {
        return entries.get(row);
    }

    public static String display(Entry e, int column) {
        return switch (column) {
            case COL_NAME -> e.name();
            case COL_SIZE -> e.directory() ? "" : Formats.size(e.size());
            case COL_TYPE -> e.type();
            case COL_MODIFIED -> Formats.time(e.modified());
            default -> "";
        };
    }

    private static final Comparator<Entry> DIRS_FIRST = Comparator.comparing(e -> !e.directory());

    /** Comparator per kolom; direktori selalu di atas kecuali untuk kolom waktu. */
    public static Comparator<Entry> comparator(int column) {
        Comparator<Entry> byName = DIRS_FIRST.thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
        return switch (column) {
            case COL_SIZE -> DIRS_FIRST.thenComparingLong(Entry::size);
            case COL_TYPE -> DIRS_FIRST.thenComparing(Entry::type).thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
            case COL_MODIFIED -> Comparator.comparing(Entry::modified);
            default -> byName;
        };
    }

    /**
     * Membaca isi direktori (disk I/O: jangan dipanggil di EDT). File tersembunyi dilewati, seperti default Explorer;
     * entri yang atributnya tidak bisa dibaca (mis. tanpa izin) juga dilewati.
     */
    public static List<Entry> list(Path dir) throws IOException {
        var result = new ArrayList<Entry>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                try {
                    if (Files.isHidden(p)) {
                        continue;
                    }
                    var attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    result.add(new Entry(p.getFileName().toString(), p, attrs.isDirectory(), attrs.size(),
                            attrs.lastModifiedTime().toInstant()));
                } catch (IOException | SecurityException e) {
                    // file sistem yang terkunci (pagefile.sys, dll.) tidak ditampilkan
                }
            }
        }
        result.sort(comparator(COL_NAME));
        return result;
    }
}

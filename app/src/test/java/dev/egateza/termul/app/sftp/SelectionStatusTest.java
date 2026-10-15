package dev.egateza.termul.app.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.i18n.I18n;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelectionStatusTest {

    /** Kolom 0 = nama, 1 = ukuran (-1 = folder). */
    private final DefaultTableModel model = new DefaultTableModel(new Object[][] {
            {"docs", -1L}, {"a.txt", 1024L * 1024}, {"b.txt", 512L}, {"c.log", 2048L}}, new Object[] {"name", "size"});
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private SelectionStatus status;

    @BeforeEach
    void setUp() {
        I18n.use("id");
        table.setRowSorter(sorter);
        status = new SelectionStatus(table, row -> (long) model.getValueAt(row, 1) < 0,
                row -> (long) model.getValueAt(row, 1));
    }

    @Test
    void tanpaSeleksiMeringkasSemuaItem() {
        assertThat(status.getText()).isEqualTo("4 item  ·  1.0 MB");
        assertThat(status.getToolTipText()).contains("byte");
    }

    @Test
    void seleksiMenghitungJumlahDanUkuranFileSaja() {
        table.setRowSelectionInterval(0, 2); // docs, a.txt, b.txt

        assertThat(status.getText())
                .isEqualTo("3 dari 4 item dipilih (1 folder, ukurannya tidak dihitung)  ·  1.0 MB");

        table.setRowSelectionInterval(2, 3);
        assertThat(status.getText()).isEqualTo("2 dari 4 item dipilih  ·  2.5 KB");
    }

    @Test
    void hanyaFolderTanpaUkuran() {
        table.setRowSelectionInterval(0, 0);

        assertThat(status.getText()).isEqualTo("1 dari 4 item dipilih (1 folder, ukurannya tidak dihitung)");
        assertThat(status.getToolTipText()).isNull();
    }

    @Test
    void filterMengikutiBarisYangTampil() {
        sorter.setRowFilter(RowFilter.regexFilter("txt", 0));

        assertThat(status.getText()).isEqualTo("2 dari 4 item  ·  1.0 MB");

        model.removeRow(1); // isi folder berubah
        assertThat(status.getText()).isEqualTo("1 dari 3 item  ·  512 B");
    }
}

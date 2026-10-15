package dev.egateza.termul.app.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import javax.swing.JTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SftpColumnsTest {

    private final List<List<String>> saved = new ArrayList<>();

    @AfterEach
    void reset() {
        SftpColumns.configure(List.of(), list -> { });
    }

    private static List<String> visible(JTable table, String[] ids) {
        var model = table.getColumnModel();
        return IntStream.range(0, model.getColumnCount()).mapToObj(i -> ids[model.getColumn(i).getModelIndex()]).toList();
    }

    @Test
    void kolomTersembunyiDariConfigLangsungDilepas() {
        SftpColumns.configure(List.of("owner", "group"), saved::add);
        var table = new JTable(new SftpTableModel());
        SftpColumns.install(table, SftpTableModel.COLUMN_IDS);

        assertThat(visible(table, SftpTableModel.COLUMN_IDS)).containsExactly("name", "size", "modified", "mode");
        assertThat(saved).isEmpty();
    }

    @Test
    void sembunyikanDanTampilkanBerlakuDiSemuaTabelDanKembaliKePosisiAsal() {
        SftpColumns.configure(List.of(), saved::add);
        var remote = new JTable(new SftpTableModel());
        var local = new JTable(new LocalTableModel());
        SftpColumns.install(remote, SftpTableModel.COLUMN_IDS);
        SftpColumns.install(local, LocalTableModel.COLUMN_IDS);
        remote.getColumnModel().getColumn(SftpTableModel.COL_MODIFIED).setPreferredWidth(155);

        SftpColumns.setHidden("modified", true);
        SftpColumns.setHidden("mode", true);
        assertThat(visible(remote, SftpTableModel.COLUMN_IDS)).containsExactly("name", "size", "owner", "group");
        assertThat(visible(local, LocalTableModel.COLUMN_IDS)).containsExactly("name", "size", "type");

        SftpColumns.setHidden("modified", false);
        assertThat(visible(remote, SftpTableModel.COLUMN_IDS)).containsExactly("name", "size", "modified", "owner", "group");
        assertThat(visible(local, LocalTableModel.COLUMN_IDS)).containsExactly("name", "size", "type", "modified");
        assertThat(remote.getColumnModel().getColumn(2).getPreferredWidth()).as("lebar kolom tetap").isEqualTo(155);
        assertThat(saved).containsExactly(List.of("modified"), List.of("modified", "mode"), List.of("mode"));
    }

    @Test
    void kolomNamaTidakBisaDisembunyikan() {
        SftpColumns.configure(List.of(), saved::add);
        var table = new JTable(new SftpTableModel());
        SftpColumns.install(table, SftpTableModel.COLUMN_IDS);

        SftpColumns.setHidden("name", true);
        SftpColumns.setHidden("size", false); // sudah tampil: tidak menyimpan apa-apa

        assertThat(visible(table, SftpTableModel.COLUMN_IDS)).contains("name");
        assertThat(saved).isEmpty();
        assertThat(SftpColumns.LABELS).doesNotContainKey("name");
    }
}

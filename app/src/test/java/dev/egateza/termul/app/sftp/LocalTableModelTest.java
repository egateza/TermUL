package dev.egateza.termul.app.sftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalTableModelTest {

    @TempDir
    Path tmp;

    private static LocalTableModel.Entry file(String name, long size, long mtime) {
        return new LocalTableModel.Entry(name, Path.of(name), false, size, Instant.ofEpochSecond(mtime));
    }

    private static LocalTableModel.Entry dir(String name) {
        return new LocalTableModel.Entry(name, Path.of(name), true, 0, Instant.EPOCH);
    }

    @Test
    void listingMenaruhDirektoriDiAtasTanpaBedaHurufBesarKecil() throws IOException {
        Files.writeString(tmp.resolve("b.php"), "<?php");
        Files.writeString(tmp.resolve("A.txt"), "halo");
        Files.createDirectory(tmp.resolve("zeta"));

        var entries = LocalTableModel.list(tmp);

        assertThat(entries).extracting(LocalTableModel.Entry::name).containsExactly("zeta", "A.txt", "b.php");
        var txt = entries.get(1);
        assertThat(txt.directory()).isFalse();
        assertThat(txt.size()).isEqualTo(4);
        assertThat(txt.path()).isEqualTo(tmp.resolve("A.txt"));
        assertThat(entries.getFirst().directory()).isTrue();
    }

    @Test
    void direktoriTidakAdaGagal() {
        assertThatThrownBy(() -> LocalTableModel.list(tmp.resolve("tidak-ada"))).isInstanceOf(IOException.class);
    }

    @Test
    void tampilanKolom() {
        var php = file("index.php", 2048, 0);
        assertThat(LocalTableModel.display(php, LocalTableModel.COL_NAME)).isEqualTo("index.php");
        assertThat(LocalTableModel.display(php, LocalTableModel.COL_SIZE)).isEqualTo("2.0 KB");
        assertThat(LocalTableModel.display(php, LocalTableModel.COL_TYPE)).contains("PHP");
        assertThat(LocalTableModel.display(dir("src"), LocalTableModel.COL_SIZE)).isEmpty();
        // tanpa ekstensi: tipe generik yang sama, dan berbeda dari folder
        assertThat(file("Makefile", 1, 0).type()).isEqualTo(file("README", 1, 0).type()).isNotEqualTo(dir("x").type());
    }

    @Test
    void filterNamaSamaDenganPanelRemote() {
        assertThat(SftpTableModel.namePredicate("  ")).isNull();
        var contains = SftpTableModel.namePredicate("Controller");
        assertThat(contains.test("transactions_controller.php")).isTrue();
        var wildcard = SftpTableModel.namePredicate("*.php");
        assertThat(wildcard.test("Index.PHP")).isTrue();
        assertThat(wildcard.test("index.php.bak")).isFalse();
    }

    @Test
    void sortUkuranMenaruhDirektoriDiAtas() {
        var list = new ArrayList<>(List.of(file("big", 900, 0), dir("d"), file("small", 1, 0)));
        list.sort(LocalTableModel.comparator(LocalTableModel.COL_SIZE));
        assertThat(list).extracting(LocalTableModel.Entry::name).containsExactly("d", "small", "big");
    }
}

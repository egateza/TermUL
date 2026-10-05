package dev.egateza.termul.app.sftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.sftp.RemoteEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SftpTableModelTest {

    private static RemoteEntry file(String name, long size, long mtime) {
        return new RemoteEntry(name, "/x/" + name, RemoteEntry.Type.FILE, size, Instant.ofEpochSecond(mtime), 0644, "dev", "dev");
    }

    private static RemoteEntry dir(String name) {
        return new RemoteEntry(name, "/x/" + name, RemoteEntry.Type.DIRECTORY, 4096, Instant.EPOCH, 0755, "root", "root");
    }

    @Test
    void tampilanKolom() {
        var f = file("a.log", 2048, 0);
        assertThat(SftpTableModel.display(f, SftpTableModel.COL_NAME)).isEqualTo("a.log");
        assertThat(SftpTableModel.display(dir("etc"), SftpTableModel.COL_NAME)).isEqualTo("etc/");
        assertThat(SftpTableModel.display(dir("etc"), SftpTableModel.COL_SIZE)).isEmpty();
        assertThat(SftpTableModel.display(f, SftpTableModel.COL_SIZE)).isEqualTo("2.0 KB");
        assertThat(SftpTableModel.display(f, SftpTableModel.COL_MODE)).isEqualTo("rw-r--r--");
    }

    @Test
    void sortUkuranMenaruhDirektoriDiAtas() {
        var list = new ArrayList<>(List.of(file("big", 900, 0), dir("d"), file("small", 1, 0)));
        list.sort(SftpTableModel.comparator(SftpTableModel.COL_SIZE));
        assertThat(list).extracting(RemoteEntry::name).containsExactly("d", "small", "big");
    }

    @Test
    void filterNamaKosongBerartiTanpaFilter() {
        assertThat(SftpTableModel.nameFilter(null)).isNull();
        assertThat(SftpTableModel.nameFilter("   ")).isNull();
    }

    @Test
    void filterNamaMengandungTeksTanpaBedaHuruf() {
        var match = SftpTableModel.nameFilter(" Nginx ");
        assertThat(match.test(file("nginx.conf", 1, 0))).isTrue();
        assertThat(match.test(dir("NGINX-old"))).isTrue();
        assertThat(match.test(file("apache2.conf", 1, 0))).isFalse();
    }

    @Test
    void filterNamaWildcardCocokSeluruhNama() {
        var conf = SftpTableModel.nameFilter("*.CONF");
        assertThat(conf.test(file("nginx.conf", 1, 0))).isTrue();
        assertThat(conf.test(file("nginx.conf.bak", 1, 0))).isFalse();

        var single = SftpTableModel.nameFilter("log?.txt");
        assertThat(single.test(file("log1.txt", 1, 0))).isTrue();
        assertThat(single.test(file("log12.txt", 1, 0))).isFalse();
    }

    @Test
    void filterNamaWildcardMemperlakukanKarakterRegexSebagaiTeks() {
        var match = SftpTableModel.nameFilter("a+b(1)*");
        assertThat(match.test(file("a+b(1).txt", 1, 0))).isTrue();
        assertThat(match.test(file("aab1.txt", 1, 0))).isFalse();
    }

    @Test
    void formatUkuranDanMode() {
        assertThat(Formats.size(0)).isEqualTo("0 B");
        assertThat(Formats.size(1023)).isEqualTo("1023 B");
        assertThat(Formats.size(1536)).isEqualTo("1.5 KB");
        assertThat(Formats.size(500L * 1024 * 1024)).isEqualTo("500 MB");
        assertThat(Formats.parseMode("644")).isEqualTo(0644);
        assertThat(Formats.parseMode("0755")).isEqualTo(0755);
        assertThat(Formats.parseMode("4755")).isEqualTo(04755);
        assertThatThrownBy(() -> Formats.parseMode("999")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Formats.parseMode("rwx")).isInstanceOf(IllegalArgumentException.class);
    }
}

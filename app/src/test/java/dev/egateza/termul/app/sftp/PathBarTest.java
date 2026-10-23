package dev.egateza.termul.app.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.sftp.PathBar.Crumb;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class PathBarTest {

    // ------------------------------------------------------------------ pemecahan path

    @Test
    void pathRemoteJadiSegmenDariRoot() {
        assertThat(PathBar.remoteCrumbs("/etc/nginx/sites-available")).containsExactly(
                new Crumb("/", "/"), new Crumb("etc", "/etc"), new Crumb("nginx", "/etc/nginx"),
                new Crumb("sites-available", "/etc/nginx/sites-available"));
    }

    @Test
    void rootRemoteDanGarisMiringBerlebih() {
        assertThat(PathBar.remoteCrumbs("/")).containsExactly(new Crumb("/", "/"));
        assertThat(PathBar.remoteCrumbs("/home//dev/")).containsExactly(
                new Crumb("/", "/"), new Crumb("home", "/home"), new Crumb("dev", "/home/dev"));
    }

    @Test
    void namaFolderBerspasiTetapUtuh() {
        assertThat(PathBar.remoteCrumbs("/tmp/ikut folder")).last()
                .isEqualTo(new Crumb("ikut folder", "/tmp/ikut folder"));
    }

    @Test
    void driveWindowsTampilTanpaBackslash() {
        assertThat(PathBar.crumbs("C:\\", List.of("Users", "dev"), "\\")).containsExactly(
                new Crumb("C:", "C:\\"), new Crumb("Users", "C:\\Users"), new Crumb("dev", "C:\\Users\\dev"));
    }

    @Test
    void uncWindows() {
        assertThat(PathBar.crumbs("\\\\server\\share\\", List.of("data"), "\\")).containsExactly(
                new Crumb("\\\\server\\share", "\\\\server\\share\\"), new Crumb("data", "\\\\server\\share\\data"));
    }

    @Test
    void pathLokalMengikutiFileSystemOsIni() {
        Path root = Path.of("").toAbsolutePath().getRoot();
        Path dir = root.resolve("satu").resolve("dua");

        var crumbs = PathBar.localCrumbs(dir);

        assertThat(crumbs).hasSize(3);
        assertThat(crumbs.getFirst().path()).isEqualTo(root.toString());
        assertThat(crumbs.get(1)).isEqualTo(new Crumb("satu", root.resolve("satu").toString()));
        assertThat(crumbs.get(2)).isEqualTo(new Crumb("dua", dir.toString()));
    }

    // ------------------------------------------------------------------ pelipatan segmen

    @Test
    void semuaMuatTanpaDilipat() {
        assertThat(PathBar.firstVisible(new int[] {20, 40, 40}, 25, 100)).isZero();
    }

    @Test
    void segmenKiriDilipatKeTombolLainnya() {
        // 25 ("…") + 40 + 40 = 105 > 100: hanya segmen terakhir + satu sebelumnya tidak muat bersama "…"
        assertThat(PathBar.firstVisible(new int[] {20, 40, 40, 40}, 25, 110)).isEqualTo(2);
        assertThat(PathBar.firstVisible(new int[] {20, 40, 40, 40}, 25, 100)).isEqualTo(3);
    }

    @Test
    void segmenTerakhirSelaluTampilWalauTidakMuat() {
        assertThat(PathBar.firstVisible(new int[] {20, 40, 300}, 25, 100)).isEqualTo(2);
    }

    @Test
    void satuSegmenTerlaluLebarTidakDilipat() {
        assertThat(PathBar.firstVisible(new int[] {300}, 25, 100)).isZero();
    }

    // ------------------------------------------------------------------ perilaku

    private final List<String> submitted = new ArrayList<>();

    private PathBar bar() {
        var bar = new PathBar(submitted::add, null);
        bar.setSize(600, 30);
        bar.setPath("/etc/nginx", PathBar.remoteCrumbs("/etc/nginx"));
        bar.doLayout();
        return bar;
    }

    private static <T extends Component> List<T> find(Container root, Class<T> type) {
        var result = new ArrayList<T>();
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                result.add(type.cast(c));
            }
            if (c instanceof Container child) {
                result.addAll(find(child, type));
            }
        }
        return result;
    }

    private static JTextField field(PathBar bar) {
        return find(bar, JTextField.class).getFirst();
    }

    @Test
    void klikSegmenMembukaFolderItu() {
        var bar = bar();

        find(bar, JButton.class).stream().filter(b -> "etc".equals(b.getText())).findFirst().orElseThrow().doClick();

        assertThat(submitted).containsExactly("/etc");
        assertThat(bar.isEditing()).isFalse();
    }

    @Test
    void segmenTerakhirBukanTombol() {
        assertThat(find(bar(), JButton.class)).extracting(JButton::getText).doesNotContain("nginx");
    }

    @Test
    void enterDiModeEditMengirimTeksTanpaSpasiTepi() {
        var bar = bar();
        bar.startEdit();
        assertThat(field(bar).getText()).isEqualTo("/etc/nginx");

        field(bar).setText("  /var/log  ");
        field(bar).postActionEvent();

        assertThat(submitted).containsExactly("/var/log");
    }

    @Test
    void berhasilDibukaMenutupModeEdit() {
        var bar = bar();
        bar.startEdit();
        field(bar).setText("/var/log");
        field(bar).postActionEvent();

        bar.setPath("/var/log", PathBar.remoteCrumbs("/var/log"));

        assertThat(bar.isEditing()).isFalse();
    }

    @Test
    void gagalDibukaFieldTetapTerbukaDenganTandaError() {
        var bar = bar();
        bar.startEdit();
        field(bar).setText("/tidak/ada");
        field(bar).postActionEvent();

        bar.showError("No such file");

        assertThat(bar.isEditing()).isTrue();
        assertThat(field(bar).getText()).isEqualTo("/tidak/ada");
        assertThat(field(bar).getClientProperty("JComponent.outline")).isEqualTo("error");

        field(bar).setText("/tidak/ad"); // mengetik lagi menghapus tanda error
        assertThat(field(bar).getClientProperty("JComponent.outline")).isNull();
    }

    @Test
    void folderBergantiDariLuarTidakMenghapusKetikan() {
        var bar = bar();
        bar.startEdit();
        field(bar).setText("/var/lo");

        bar.setPath("/home/dev", PathBar.remoteCrumbs("/home/dev")); // mis. "Ikuti folder terminal"

        assertThat(bar.isEditing()).isTrue();
        assertThat(field(bar).getText()).isEqualTo("/var/lo");
    }

    @Test
    void escMembatalkanEdit() {
        var bar = bar();
        bar.startEdit();
        field(bar).setText("/var/lo");

        field(bar).getActionMap().get("cancelEdit").actionPerformed(null);

        assertThat(bar.isEditing()).isFalse();
        assertThat(field(bar).getText()).isEqualTo("/etc/nginx");
        assertThat(submitted).isEmpty();
    }
}

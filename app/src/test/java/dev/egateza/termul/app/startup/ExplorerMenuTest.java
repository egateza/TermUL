package dev.egateza.termul.app.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.core.Os;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ExplorerMenuTest {

    private static final String EXE = "C:\\Program Files\\TermUL\\TermUL.exe";
    private static final UUID BASH = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PWSH = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Test
    void hanyaWindowsDanHanyaHasilInstalasi() {
        assertThat(ExplorerMenu.of(Os.MAC, k -> EXE, AutoStartTest.FakeRegistry::new)).isNull();
        var menu = ExplorerMenu.of(Os.WINDOWS, k -> null, AutoStartTest.FakeRegistry::new);
        assertThat(menu.status()).isEqualTo(ExplorerMenu.Status.UNSUPPORTED);
        assertThatThrownBy(() -> menu.setEnabled(true, List.of())).isInstanceOf(IOException.class);
    }

    @Test
    void memasangSubmenuDiSemuaLokasiKlikKanan() throws Exception {
        var registry = new AutoStartTest.FakeRegistry();
        var menu = windows(registry);
        assertThat(menu.status()).isEqualTo(ExplorerMenu.Status.DISABLED);

        menu.setEnabled(true, List.of(new ExplorerMenu.Entry(BASH, "Buka Git Bash di sini"),
                new ExplorerMenu.Entry(PWSH, "Buka Tom & Jerry di sini")));

        assertThat(menu.status()).isEqualTo(ExplorerMenu.Status.ENABLED);
        for (String root : WindowsExplorerMenu.ROOT_KEYS) {
            assertThat(registry.readString(root, "MUIVerb")).isEqualTo("TermUL");
            assertThat(registry.readString(root, "SubCommands")).isEmpty();
            assertThat(registry.readString(root, "Icon")).isEqualTo(EXE + ",0");
            assertThat(registry.readString(root + "\\shell\\01", "MUIVerb")).isEqualTo("Buka Git Bash di sini");
            assertThat(registry.readString(root + "\\shell\\01\\command", ""))
                    .isEqualTo("\"" + EXE + "\" --open-local=" + BASH + " --cwd \"%V\"");
            assertThat(registry.readString(root + "\\shell\\02", "MUIVerb")).isEqualTo("Buka Tom && Jerry di sini");
        }
    }

    @Test
    void syncMenggantiDaftarHanyaKalauTerpasang() throws Exception {
        var registry = new AutoStartTest.FakeRegistry();
        var menu = windows(registry);
        menu.sync(List.of(new ExplorerMenu.Entry(BASH, "a")));
        assertThat(registry.values).as("belum dinyalakan user").isEmpty();

        menu.setEnabled(true, List.of(new ExplorerMenu.Entry(BASH, "a"), new ExplorerMenu.Entry(PWSH, "b")));
        menu.sync(List.of(new ExplorerMenu.Entry(PWSH, "b")));
        assertThat(registry.readString(WindowsExplorerMenu.BACKGROUND_KEY + "\\shell\\01", "MUIVerb")).isEqualTo("b");
        assertThat(registry.readString(WindowsExplorerMenu.BACKGROUND_KEY + "\\shell\\02", "MUIVerb"))
                .as("item profil yang dihapus ikut hilang").isNull();

        menu.setEnabled(false, List.of());
        assertThat(registry.values).isEmpty();
        assertThat(menu.status()).isEqualTo(ExplorerMenu.Status.DISABLED);
    }

    @Test
    void menuDariLokasiInstalasiLainBelumDianggapAktif() throws Exception {
        var registry = new AutoStartTest.FakeRegistry();
        new WindowsExplorerMenu(registry, "D:\\lama\\TermUL.exe").setEnabled(true, List.of());
        assertThat(windows(registry).status()).isEqualTo(ExplorerMenu.Status.DISABLED);
    }

    @Test
    void jumlahItemDibatasi() throws Exception {
        var registry = new AutoStartTest.FakeRegistry();
        var entries = IntStream.range(0, ExplorerMenu.MAX_ENTRIES + 3)
                .mapToObj(i -> new ExplorerMenu.Entry(UUID.randomUUID(), "s" + i)).toList();
        windows(registry).setEnabled(true, entries);
        String root = WindowsExplorerMenu.BACKGROUND_KEY + "\\shell\\";
        assertThat(registry.readString(root + "%02d".formatted(ExplorerMenu.MAX_ENTRIES), "MUIVerb")).isNotNull();
        assertThat(registry.readString(root + "%02d".formatted(ExplorerMenu.MAX_ENTRIES + 1), "MUIVerb")).isNull();
    }

    private static WindowsExplorerMenu windows(WinRegistry registry) {
        return new WindowsExplorerMenu(registry, EXE);
    }
}

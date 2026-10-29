package dev.egateza.termul.app.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.egateza.termul.core.Os;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ikon shortcut yang ikut update lewat menu (ADR 0010). */
class AppIconTest {

    @TempDir
    Path tmp;

    @Test
    void ikonDiJarAdaDanBerformatIco() throws IOException {
        byte[] ico = AppIconFile.bundled();
        assertThat(ico.length).isGreaterThan(6);
        assertThat(ico[2]).as("header ICO: tipe 1").isEqualTo((byte) 1);
    }

    @Test
    void namaFileMengikutiIsiSupayaCacheIkonWindowsTidakDipakai() throws IOException {
        byte[] a = {0, 0, 1, 0, 1};
        byte[] b = {0, 0, 1, 0, 2};
        assertThat(AppIconFile.fileName(a)).matches("termul-[0-9a-f]{12}\\.ico").isNotEqualTo(AppIconFile.fileName(b));

        Path first = AppIconFile.install(tmp, a);
        Path second = AppIconFile.install(tmp, b);
        assertThat(Files.readAllBytes(first)).isEqualTo(a);
        Files.writeString(tmp.resolve("lain.txt"), "jangan dihapus");

        AppIconFile.deleteOthers(tmp, second);
        assertThat(first).doesNotExist();
        assertThat(second).exists();
        assertThat(tmp.resolve("lain.txt")).exists();
    }

    @Test
    void hanyaShortcutMilikInstalasiIniYangDiubah() throws IOException {
        String exe = "C:\\Users\\u\\AppData\\Local\\TermUL\\TermUL.exe";
        Path start = Files.createDirectories(tmp.resolve("Programs").resolve("TermUL"));
        Path desktop = Files.createDirectories(tmp.resolve("Desktop"));
        Path ours = Files.createFile(start.resolve("TermUL.lnk"));
        Path pinned = Files.createFile(desktop.resolve("termul.LNK"));
        Path done = Files.createFile(desktop.resolve("sudah.lnk"));
        Path other = Files.createFile(desktop.resolve("Notepad.lnk"));
        Path broken = Files.createFile(desktop.resolve("rusak.lnk"));
        Files.createFile(desktop.resolve("bukan-shortcut.txt"));
        Path icon = tmp.resolve("termul-abc.ico");

        var links = new FakeLinks();
        links.targets.put(ours, exe);
        links.targets.put(pinned, "c:/users/u/appdata/local/termul/termul.exe");
        links.targets.put(done, exe);
        links.icons.put(done, icon.toString());
        links.targets.put(other, "C:\\Windows\\notepad.exe");

        var changed = ShortcutIcons.refresh(List.of(tmp.resolve("Programs"), desktop, tmp.resolve("tidak-ada")), exe,
                icon, links);

        assertThat(changed).containsExactlyInAnyOrder(ours, pinned);
        assertThat(links.icons.get(ours)).isEqualTo(icon.toString());
        assertThat(links.icons).doesNotContainKey(other);
        assertThat(links.failed).as("shortcut rusak dilewati, yang lain tetap diproses").containsExactly(broken);
    }

    @Test
    void perbandinganPathWindows() {
        assertThat(ShortcutIcons.samePath("C:\\A\\TermUL.exe", "c:/a/termul.EXE")).isTrue();
        assertThat(ShortcutIcons.samePath("", "")).isFalse();
        assertThat(ShortcutIcons.samePath("C:\\A\\TermUL.exe", "C:\\B\\TermUL.exe")).isFalse();
    }

    /** Shortcut sungguhan: dibuat Windows (WScript.Shell), lalu ikonnya diubah lewat COM IShellLink. */
    @Test
    void shortcutAsliBisaDibacaDanDiubahIkonnya() throws Exception {
        assumeTrue(Os.current().isWindows(), "hanya Windows");
        Path lnk = tmp.resolve("uji.lnk");
        String target = Path.of(System.getenv("WINDIR"), "System32", "notepad.exe").toString();
        var ps = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('" + lnk + "');$s.TargetPath='" + target
                        + "';$s.Save()").redirectErrorStream(true).start();
        assumeTrue(ps.waitFor(60, TimeUnit.SECONDS) && ps.exitValue() == 0 && Files.exists(lnk),
                "PowerShell tidak bisa membuat shortcut di mesin ini");
        Path icon = AppIconFile.install(tmp, AppIconFile.bundled());

        try (var links = WinShellLink.open()) {
            assertThat(ShortcutIcons.samePath(links.target(lnk), target)).isTrue();
            assertThat(links.icon(lnk)).isNotEqualTo(icon.toString());
            assertThat(ShortcutIcons.refresh(List.of(tmp), target, icon, links)).containsExactly(lnk);
            assertThat(links.icon(lnk)).isEqualTo(icon.toString());
            assertThat(links.target(lnk)).as("target tidak ikut berubah").isEqualToIgnoringCase(target);
        }
    }

    private static final class FakeLinks implements ShortcutIcons.Links {
        final Map<Path, String> targets = new HashMap<>();
        final Map<Path, String> icons = new HashMap<>();
        final List<Path> failed = new ArrayList<>();

        @Override
        public String target(Path lnk) throws IOException {
            if (lnk.getFileName().toString().equals("rusak.lnk")) {
                failed.add(lnk);
                throw new IOException("Load gagal");
            }
            return targets.getOrDefault(lnk, "");
        }

        @Override
        public String icon(Path lnk) {
            return icons.getOrDefault(lnk, "");
        }

        @Override
        public void setIcon(Path lnk, String iconPath) {
            icons.put(lnk, iconPath);
        }
    }
}

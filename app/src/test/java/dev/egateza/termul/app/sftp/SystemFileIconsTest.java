package dev.egateza.termul.app.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class SystemFileIconsTest {

    @TempDir
    Path dir;

    @Test
    void ekstensiHanyaYangAmanUntukNamaFileLokal() {
        assertThat(SystemFileIcons.extension("BWN.PHP")).isEqualTo("php");
        assertThat(SystemFileIcons.extension("log4j2.xml")).isEqualTo("xml");
        assertThat(SystemFileIcons.extension("x.library-ms")).isEqualTo("library-ms");
        assertThat(SystemFileIcons.extension(".bashrc")).isNull();
        assertThat(SystemFileIcons.extension("README")).isNull();
        assertThat(SystemFileIcons.extension("a.b:c")).isNull();
        assertThat(SystemFileIcons.extension("a." + "x".repeat(17))).isNull();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void iconDimuatDiBackgroundLaluDiCache() throws Exception {
        var icons = new SystemFileIcons(dir, 16);
        var loaded = new CountDownLatch(1);
        Icon[] first = new Icon[1];
        SwingUtilities.invokeAndWait(() -> first[0] = icons.icon("BWN.php", loaded::countDown));

        assertThat(first[0]).isNull();
        assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
        Icon[] second = new Icon[1];
        SwingUtilities.invokeAndWait(() -> second[0] = icons.icon("other.PHP", () -> { }));
        assertThat(second[0]).isNotNull();
        assertThat(Files.size(dir.resolve("probe.php"))).isZero();
    }
}

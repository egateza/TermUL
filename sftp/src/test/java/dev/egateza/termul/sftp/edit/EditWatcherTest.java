package dev.egateza.termul.sftp.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditWatcherTest {

    @TempDir
    Path dir;

    private final List<Path> changes = new CopyOnWriteArrayList<>();
    private EditWatcher watcher;

    @BeforeEach
    void setUp() throws Exception {
        watcher = new EditWatcher(Duration.ofMillis(300), changes::add);
    }

    @AfterEach
    void tearDown() {
        watcher.close();
    }

    @Test
    void beberapaSaveBeruntunDigabungMenjadiSatu() throws Exception {
        Path f = Files.writeString(dir.resolve("a.txt"), "0");
        watcher.watch(f);

        for (int i = 1; i <= 5; i++) {
            Files.writeString(f, "v" + i);
            Thread.sleep(30);
        }

        await().atMost(Duration.ofSeconds(5)).until(() -> changes.size() == 1);
        Thread.sleep(600);
        assertThat(changes).containsExactly(f.toAbsolutePath().normalize());
    }

    @Test
    void atomicSaveLewatRenameTerdeteksi() throws Exception {
        Path f = Files.writeString(dir.resolve("config.yml"), "a");
        watcher.watch(f);

        Path tmp = Files.writeString(dir.resolve(".config.yml.swp"), "b");
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        await().atMost(Duration.ofSeconds(5)).until(() -> !changes.isEmpty());
        assertThat(changes).allMatch(p -> p.equals(f.toAbsolutePath().normalize()));
    }

    @Test
    void fileLainDanFileYangSudahUnwatchDiabaikan() throws Exception {
        Path watched = Files.writeString(dir.resolve("w.txt"), "a");
        Path other = Files.writeString(dir.resolve("o.txt"), "a");
        watcher.watch(watched);

        Files.writeString(other, "b");
        Thread.sleep(800);
        assertThat(changes).isEmpty();

        watcher.unwatch(watched);
        Files.writeString(watched, "b");
        Thread.sleep(800);
        assertThat(changes).isEmpty();
    }
}

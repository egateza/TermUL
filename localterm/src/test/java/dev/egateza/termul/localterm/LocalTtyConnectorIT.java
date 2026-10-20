package dev.egateza.termul.localterm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jediterm.core.util.TermSize;
import dev.egateza.termul.core.Os;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** PTY sungguhan lewat pty4j (library native). Tanpa Testcontainers. */
class LocalTtyConnectorIT {

    @TempDir
    Path dir;

    private final LocalTerminalFactory factory = new LocalTerminalFactory(Os.current());

    /**
     * Baca output terus-menerus di thread latar, seperti thread emulator JediTerm. Wajib: di macOS shell yang keluar
     * menunggu output-nya di PTY dibaca dulu, jadi test yang berhenti membaca membuat shell tidak pernah berakhir.
     */
    private static StringBuffer pump(LocalTtyConnector tty) {
        var out = new StringBuffer();
        Thread.ofPlatform().daemon().start(() -> {
            char[] buf = new char[1024];
            try {
                int n;
                while ((n = tty.read(buf, 0, buf.length)) >= 0) {
                    out.append(buf, 0, n);
                }
            } catch (java.io.IOException e) {
                // PTY ditutup
            }
        });
        return out;
    }

    private static void awaitOutput(StringBuffer out, String marker) {
        await().atMost(Duration.ofSeconds(10)).until(() -> out.toString().contains(marker));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void shellUnixMenjalankanPerintahResizeDanKodeKeluar() throws Exception {
        var shell = new LocalShell(List.of("/bin/sh"), dir, LocalShells.environment(Os.current(), System.getenv()));
        var tty = factory.open(shell);
        var closed = new AtomicInteger();
        tty.addCloseListener(c -> closed.incrementAndGet());

        var out = pump(tty);
        tty.write("echo MARK-$((40+2)); pwd\r");
        awaitOutput(out, "MARK-42");

        tty.resize(new TermSize(132, 40));
        Thread.sleep(LocalTtyConnector.RESIZE_DEBOUNCE_MILLIS * 3);
        tty.write("stty size; exit 3\r");
        awaitOutput(out, "40 132");

        await().atMost(Duration.ofSeconds(10)).until(() -> closed.get() == 1);
        assertThat(tty.exitCode()).contains(3);
        assertThat(tty.isClosedByUser()).isFalse();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void tutupTabMematikanShellDanPohonProsesnya() throws Exception {
        var shell = new LocalShell(List.of("/bin/sh"), dir, LocalShells.environment(Os.current(), System.getenv()));
        var tty = factory.open(shell);
        var out = pump(tty);
        tty.write("sleep 300 & echo STARTED\r");
        awaitOutput(out, "STARTED");
        await().atMost(Duration.ofSeconds(5)).until(() -> tty.runningCommands().contains("sleep"));
        var children = ProcessHandle.of(tty.pid()).map(h -> h.descendants().toList()).orElse(List.of());
        assertThat(children).isNotEmpty();

        tty.closeByUser();

        await().atMost(Duration.ofMillis(LocalTtyConnector.HANGUP_GRACE_MILLIS + 5000))
                .until(() -> tty.exitCode().isPresent() && children.stream().noneMatch(ProcessHandle::isAlive));
        assertThat(tty.isClosedByUser()).isTrue();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void cmdLewatConPty() throws Exception {
        var shell = new LocalShell(List.of("cmd.exe", "/c", "echo MARK-42 & exit 3"), dir,
                LocalShells.environment(Os.current(), System.getenv()));
        var tty = factory.open(shell);
        var closed = new AtomicInteger();
        tty.addCloseListener(c -> closed.incrementAndGet());

        awaitOutput(pump(tty), "MARK-42");
        await().atMost(Duration.ofSeconds(10)).until(() -> closed.get() == 1);
        assertThat(tty.exitCode()).contains(3);
    }

    @Test
    void shellYangTidakAdaGagalDenganPesanJelas() {
        var shell = new LocalShell(List.of(dir.resolve("tidak-ada").toString()), dir, Map.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> factory.open(shell))
                .isInstanceOf(LocalShellException.class)
                .hasMessageContaining("Gagal menjalankan shell lokal");
    }
}

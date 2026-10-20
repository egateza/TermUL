package dev.egateza.termul.localterm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jediterm.core.util.TermSize;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LocalTtyConnectorTest {

    /** Shell palsu: output lewat pipe, input ditampung, berakhir saat {@link #exit} atau hang up. */
    static final class FakePty implements PtyHandle {
        final PipedOutputStream shellOut = new PipedOutputStream();
        final PipedInputStream in;
        final ByteArrayOutputStream typed = new ByteArrayOutputStream();
        final List<String> resizes = new CopyOnWriteArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger hangUps = new AtomicInteger();
        volatile int code;
        volatile boolean ignoreHangUp;

        FakePty() throws IOException {
            in = new PipedInputStream(shellOut);
        }

        void exit(int exitCode) {
            code = exitCode;
            done.countDown();
        }

        @Override
        public InputStream input() {
            return in;
        }

        @Override
        public OutputStream output() {
            return typed;
        }

        @Override
        public void resize(int columns, int rows) {
            resizes.add(columns + "x" + rows);
        }

        @Override
        public boolean isAlive() {
            return done.getCount() > 0;
        }

        @Override
        public int waitFor() throws InterruptedException {
            done.await();
            return code;
        }

        @Override
        public long pid() {
            return Long.MAX_VALUE; // tidak ada proses dengan pid ini: pohon proses kosong
        }

        @Override
        public void hangUp() {
            hangUps.incrementAndGet();
            if (!ignoreHangUp) {
                exit(129); // 128 + SIGHUP
            }
        }

        @Override
        public void destroyForcibly() {
            exit(137);
        }
    }

    @Test
    void writeBerurutanSampaiKeShell() throws Exception {
        var pty = new FakePty();
        var tty = new LocalTtyConnector(pty, "t#1");

        tty.write("ls ");
        tty.write("-la\r".getBytes(StandardCharsets.UTF_8));

        await().atMost(Duration.ofSeconds(2))
                .until(() -> pty.typed.toString(StandardCharsets.UTF_8).equals("ls -la\r"));
        pty.exit(0);
    }

    @Test
    void outputShellTerbacaSebagaiTeksUtf8() throws Exception {
        var pty = new FakePty();
        var tty = new LocalTtyConnector(pty, "t#2");
        pty.shellOut.write("héllo ✓".getBytes(StandardCharsets.UTF_8));
        pty.shellOut.flush();

        char[] buf = new char[32];
        int n = tty.read(buf, 0, buf.length);

        assertThat(new String(buf, 0, n)).isEqualTo("héllo ✓");
        pty.exit(0);
    }

    @Test
    void resizeBeruntunHanyaMengirimUkuranTerakhir() throws Exception {
        var pty = new FakePty();
        var tty = new LocalTtyConnector(pty, "t#3");

        tty.resize(new TermSize(100, 30));
        tty.resize(new TermSize(110, 31));
        tty.resize(new TermSize(120, 40));
        tty.resize(new TermSize(0, 10)); // diabaikan

        await().atMost(Duration.ofSeconds(2)).until(() -> !pty.resizes.isEmpty());
        Thread.sleep(LocalTtyConnector.RESIZE_DEBOUNCE_MILLIS * 2);
        assertThat(pty.resizes).containsExactly("120x40");
        pty.exit(0);
    }

    @Test
    void shellExitMemanggilCloseListenerSekaliDenganKodeKeluar() throws Exception {
        var pty = new FakePty();
        var tty = new LocalTtyConnector(pty, "t#4");
        var calls = new AtomicInteger();
        tty.addCloseListener(c -> calls.incrementAndGet());

        pty.exit(3);

        await().atMost(Duration.ofSeconds(2)).until(() -> calls.get() == 1);
        assertThat(tty.exitCode()).contains(3);
        assertThat(tty.isClosedByUser()).isFalse();
        assertThat(tty.isConnected()).isFalse();
        // listener yang didaftarkan setelah shell berakhir tetap dipanggil, sekali
        var late = new AtomicInteger();
        tty.addCloseListener(c -> late.incrementAndGet());
        assertThat(late.get()).isEqualTo(1);
        tty.close();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void tutupOlehUserMenutupPtyDanTidakDobel() throws Exception {
        var pty = new FakePty();
        var tty = new LocalTtyConnector(pty, "t#5");
        var calls = new AtomicInteger();
        tty.addCloseListener(c -> calls.incrementAndGet());

        tty.closeByUser();
        tty.closeByUser();
        tty.close();

        await().atMost(Duration.ofSeconds(2)).until(() -> calls.get() == 1);
        assertThat(pty.hangUps.get()).isEqualTo(1);
        assertThat(tty.isClosedByUser()).isTrue();
        assertThat(tty.exitCode()).contains(129);
        tty.write("x"); // setelah ditutup: diabaikan, tanpa exception
    }

    @Test
    void shellYangMengabaikanHangUpDimatikanPaksaSetelahTenggat() throws Exception {
        var pty = new FakePty();
        pty.ignoreHangUp = true;
        var tty = new LocalTtyConnector(pty, "t#6");

        tty.closeByUser();

        await().atMost(Duration.ofMillis(LocalTtyConnector.HANGUP_GRACE_MILLIS + 2000))
                .until(() -> tty.exitCode().isPresent());
        assertThat(tty.exitCode()).contains(137);
    }

    @Test
    void namaProsesTanpaFolder() {
        assertThat(LocalTtyConnector.baseName("/usr/bin/vim")).isEqualTo("vim");
        assertThat(LocalTtyConnector.baseName("C:\\Windows\\System32\\ping.exe")).isEqualTo("ping.exe");
        assertThat(LocalTtyConnector.baseName("top")).isEqualTo("top");
    }
}

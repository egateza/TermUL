package dev.egateza.termul.core.instance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleInstanceTest {

    @TempDir
    Path dir;

    @Test
    void instanceKeduaMemintaInstancePertamaTampil() throws Exception {
        try (var first = primary(SingleInstance.acquire(dir))) {
            var shown = new CountDownLatch(1);
            first.listen(shown::countDown);

            assertThat(SingleInstance.acquire(dir)).isInstanceOf(SingleInstance.Signaled.class);
            assertThat(shown.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void permintaanSebelumHandlerTerpasangDijalankanSekaliSaatListen() throws Exception {
        try (var first = primary(SingleInstance.acquire(dir))) {
            assertThat(SingleInstance.acquire(dir)).isInstanceOf(SingleInstance.Signaled.class);
            assertThat(SingleInstance.acquire(dir)).isInstanceOf(SingleInstance.Signaled.class);

            var calls = new AtomicInteger();
            first.listen(calls::incrementAndGet);
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void tokenSalahDiabaikan() throws Exception {
        try (var first = primary(SingleInstance.acquire(dir))) {
            var calls = new AtomicInteger();
            first.listen(calls::incrementAndGet);
            try (var socket = new Socket(InetAddress.getLoopbackAddress(), first.port())) {
                socket.setSoTimeout(SingleInstance.IO_TIMEOUT_MS * 2);
                socket.getOutputStream().write(("00".repeat(32) + "\nshow\n").getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                assertThat(socket.getInputStream().read()).as("tanpa jawaban, koneksi ditutup").isEqualTo(-1);
            }
            assertThat(calls).hasValue(0);
        }
    }

    @Test
    void setelahDitutupInstanceBaruMenjadiPrimary() throws Exception {
        primary(SingleInstance.acquire(dir)).close();
        assertThat(Files.exists(dir.resolve(SingleInstance.PORT_FILE))).isFalse();

        try (var again = primary(SingleInstance.acquire(dir))) {
            assertThat(again.port()).isPositive();
        }
    }

    @Test
    void lockDipegangTapiTidakAdaYangMenjawab() throws Exception {
        Files.writeString(dir.resolve(SingleInstance.PORT_FILE), "1\n" + "ab".repeat(32) + "\n");
        assertThat(SingleInstance.signal(dir.resolve(SingleInstance.PORT_FILE)))
                .isInstanceOf(SingleInstance.Unreachable.class);
    }

    @Test
    void requestTerlaluPanjangAtauTerpotongDitolak() throws Exception {
        assertThat(SingleInstance.readRequest(stream("x".repeat(400) + "\n\n"))).isNull();
        assertThat(SingleInstance.readRequest(stream("abc\n"))).isNull();
        assertThat(SingleInstance.readRequest(stream("abc\nshow\n"))).containsExactly("abc", "show");
    }

    private static ByteArrayInputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
    }

    private static SingleInstance primary(SingleInstance.Result result) {
        assertThat(result).isInstanceOf(SingleInstance.Primary.class);
        return ((SingleInstance.Primary) result).instance();
    }
}

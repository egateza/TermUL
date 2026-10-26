package dev.egateza.termul.core.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
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
            first.listen(r -> shown.countDown());

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
            first.listen(r -> calls.incrementAndGet());
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void tokenSalahDiabaikan() throws Exception {
        try (var first = primary(SingleInstance.acquire(dir))) {
            var calls = new AtomicInteger();
            first.listen(r -> calls.incrementAndGet());
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
        assertThat(SingleInstance.signal(dir.resolve(SingleInstance.PORT_FILE), SingleInstance.Request.show()))
                .isInstanceOf(SingleInstance.Unreachable.class);
    }

    @Test
    void requestTerlaluPanjangAtauTerpotongDitolak() throws Exception {
        assertThat(SingleInstance.readRequest(stream("x".repeat(20_000) + "\n\n"))).isNull();
        assertThat(SingleInstance.readRequest(stream("abc\n"))).isNull();
        assertThat(SingleInstance.readRequest(stream("abc\nshow\n"))).containsExactly("abc", "show");
    }

    @Test
    void permintaanDenganArgumenSampaiUtuh() throws Exception {
        try (var first = primary(SingleInstance.acquire(dir))) {
            var received = new ArrayBlockingQueue<SingleInstance.Request>(1);
            first.listen(received::add);
            var request = new SingleInstance.Request("open-local", "abc\nD:\\Proyek Ünicode\\日本");

            assertThat(SingleInstance.acquire(dir, request)).isInstanceOf(SingleInstance.Signaled.class);
            assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo(request);
        }
    }

    @Test
    void antreanSebelumListenUrutTanpaDuplikat() throws Exception {
        try (var first = primary(SingleInstance.acquire(dir))) {
            var a = new SingleInstance.Request("open-local", "a");
            var b = new SingleInstance.Request("open-local", "b");
            SingleInstance.acquire(dir, a);
            SingleInstance.acquire(dir, b);
            SingleInstance.acquire(dir, a);

            var received = new ArrayList<SingleInstance.Request>();
            first.listen(received::add);
            assertThat(received).containsExactly(a, b);
        }
    }

    @Test
    void perintahDanArgumenDivalidasi() {
        assertThatThrownBy(() -> new SingleInstance.Request("Show me", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SingleInstance.Request("show", "x".repeat(SingleInstance.MAX_ARGUMENT_CHARS + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SingleInstance.Request.decode("open-local !!bukan-base64")).isNull();
        assertThat(SingleInstance.Request.decode("show")).isEqualTo(SingleInstance.Request.show());
    }

    private static ByteArrayInputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
    }

    private static SingleInstance primary(SingleInstance.Result result) {
        assertThat(result).isInstanceOf(SingleInstance.Primary.class);
        return ((SingleInstance.Primary) result).instance();
    }
}

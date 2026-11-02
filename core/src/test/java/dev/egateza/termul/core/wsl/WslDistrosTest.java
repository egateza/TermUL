package dev.egateza.termul.core.wsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WslDistrosTest {

    /** wsl.exe palsu: daftar distro terpasang dan perintah yang dijalankan. */
    private static final class FakeRunner implements WslRunner {
        final List<String> installed = new ArrayList<>(List.of("Ubuntu-24.04", "Debian"));
        final List<List<String>> commands = new ArrayList<>();
        boolean available = true;
        boolean fails;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public Result run(List<String> args, Duration timeout) throws IOException {
            commands.add(args);
            if (fails) {
                throw new IOException("wsl.exe tidak selesai");
            }
            return installed.isEmpty() ? new Result(-1, "Tidak ada distro") : new Result(0, String.join("\r\n", installed));
        }
    }

    private final FakeRunner runner = new FakeRunner();
    private final WslDistros wsl = new WslDistros(runner);

    @Test
    void daftarDistroTerpasangLewatWslListQuiet() {
        assertThat(wsl.installed()).containsExactly("Ubuntu-24.04", "Debian");
        assertThat(runner.commands).containsExactly(List.of("--list", "--quiet"));
    }

    @Test
    void distroInternalDockerDesktopDilewati() {
        runner.installed.addAll(List.of("docker-desktop", "docker-desktop-data"));

        assertThat(wsl.installed()).containsExactly("Ubuntu-24.04", "Debian");
    }

    @Test
    void kosongKalauBelumAdaDistroWslTidakAdaAtauGagal() {
        runner.installed.clear();
        assertThat(wsl.installed()).isEmpty();

        runner.installed.add("Debian");
        runner.fails = true;
        assertThat(wsl.installed()).isEmpty();

        runner.available = false;
        runner.commands.clear();
        assertThat(wsl.installed()).isEmpty();
        assertThat(runner.commands).as("wsl.exe tidak dijalankan kalau tidak ada").isEmpty();
    }

    @Test
    void namesMelewatiBarisYangBukanNamaDistro() {
        assertThat(WslDistros.names("Ubuntu\r\n\r\n  docker-desktop  \nBukan nama distro\n"))
                .containsExactly("Ubuntu", "docker-desktop");
        assertThat(WslDistros.isValidName("Debian & calc")).isFalse();
        assertThat(WslDistros.isValidName("-d")).isFalse();
    }

    @Test
    void outputUtf16LeDanUtf8DidecodeBenar() {
        byte[] utf16 = "Ubuntu\r\nDebian\r\n".getBytes(StandardCharsets.UTF_16LE);
        byte[] bom = new byte[utf16.length + 2];
        bom[0] = (byte) 0xFF;
        bom[1] = (byte) 0xFE;
        System.arraycopy(utf16, 0, bom, 2, utf16.length);

        assertThat(WslOutput.lines(WslOutput.decode(utf16))).containsExactly("Ubuntu", "Debian");
        assertThat(WslOutput.lines(WslOutput.decode(bom))).containsExactly("Ubuntu", "Debian");
        assertThat(WslOutput.decode("ega\n".getBytes(StandardCharsets.UTF_8))).isEqualTo("ega\n");
        assertThat(WslOutput.decode(new byte[0])).isEmpty();
    }

    @Test
    void runnerAsliTanpaWslExeTidakTersedia() {
        var missing = new ProcessWslRunner(java.nio.file.Path.of("tidak-ada", "wsl.exe"));

        assertThat(missing.available()).isFalse();
    }
}
